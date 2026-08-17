package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.marketdata.RecentTradeService;
import finance.marketdata.TradeDirection;

import java.time.LocalDateTime;
import java.util.*;

/** Standardized cash-settled commodity futures with price/time-priority matching. */
public final class FuturesMarketManager {
    public static final int MAX_CONTRACTS=256,MAX_TRADES=500;
    private static final Map<UUID,FuturesContract> CONTRACTS=new LinkedHashMap<>();
    private static final List<FuturesOrder> ORDERS=new ArrayList<>();
    private static final List<FuturesTrade> TRADES=new ArrayList<>();
    private static final Map<UUID,Long> LAST_PRICES=new LinkedHashMap<>();
    private static long nextSequence=1;
    private FuturesMarketManager(){}

    public static synchronized Result createStandard(String commodityId,long listingDay,long lastTradingDay,long maturityDay){
        if(!FinanceConfig.futuresEnabled()||commodityId==null||!CommodityRegistry.isRegistered(commodityId)
                ||CONTRACTS.size()>=MAX_CONTRACTS)return Result.fail("期货合约参数无效");
        String code=(commodityId.replace("minecraft:","").replaceAll("[^a-zA-Z0-9]","")+maturityDay).toUpperCase(Locale.ROOT);
        if(code.length()>16)code=code.substring(0,16);
        final String contractCode=code;
        if(contractCode.isBlank()||CONTRACTS.values().stream().anyMatch(c->c.code().equals(contractCode)))return Result.fail("期货合约代码重复");
        try{
            FuturesContract c=new FuturesContract(UUID.randomUUID(),contractCode,commodityId,FinanceConfig.futuresContractSize(),listingDay,lastTradingDay,maturityDay,FuturesSettlementType.CASH,listingDay<=CandlestickService.currentMcDay()?FuturesContractStatus.TRADING:FuturesContractStatus.SCHEDULED);
            CONTRACTS.put(c.id(),c);EconomySavedData.markDirty();return Result.ok(c.id(),"期货合约已创建");
        }catch(IllegalArgumentException ex){return Result.fail("期货合约日期无效");}
    }

    public static synchronized Result place(UUID player,UUID contractId,FuturesOrderSide side,long price,long quantity){
        FuturesContract c=CONTRACTS.get(contractId);MarginAccount account=MarginManager.account(player);
        if(!FinanceConfig.futuresEnabled()||player==null||c==null||side==null||!c.canTrade()||price<=0||quantity<=0||quantity>FinanceConfig.futuresMaxPosition()
                ||price%FinanceConfig.futuresMinimumTick()!=0||ORDERS.size()>=FinanceConfig.futuresMaxOrders()||nextSequence<=0||nextSequence==Long.MAX_VALUE)
            return Result.fail("期货订单参数无效");
        long reserve=incrementalReservation(player,c,side,price,quantity);
        if(reserve<0||c.status()==FuturesContractStatus.LAST_TRADING_DAY&&reserve>0||account.riskStatus()==MarginRiskStatus.DEFAULTED||(account.riskStatus()!=MarginRiskStatus.NORMAL&&reserve>0))return Result.fail("账户或最后交易日不允许扩大仓位");
        if(!MarginManager.freezeOrder(player,reserve))return Result.fail("保证金不足");
        FuturesOrder order=new FuturesOrder(UUID.randomUUID(),player,contractId,side,price,quantity,nextSequence++,reserve);
        return matchOrBook(order);
    }

    private static long incrementalReservation(UUID owner,FuturesContract c,FuturesOrderSide side,long price,long quantity){
        FuturesPosition p=MarginManager.findPosition(owner,c.id());long current=p==null?0:p.signedQuantity();
        long same=ORDERS.stream().filter(o->o.playerId().equals(owner)&&o.contractId().equals(c.id())&&o.side()==side).mapToLong(FuturesOrder::remainingQuantity).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);
        long before,after;try{before=Math.addExact(current,side==FuturesOrderSide.BUY?same:-same);after=Math.addExact(before,side==FuturesOrderSide.BUY?quantity:-quantity);}catch(ArithmeticException ex){return -1;}
        if(Math.abs(after)>FinanceConfig.futuresMaxPosition())return -1;
        long beforeRisk=Math.abs(before),afterRisk=Math.abs(after);if(afterRisk<=beforeRisk)return 0;
        long add=afterRisk-beforeRisk;return FuturesMath.margin(price,c.contractSize(),add,FinanceConfig.futuresInitialMarginBps());
    }

    private static Result matchOrBook(FuturesOrder incoming){
        List<FuturesOrder> candidates=ORDERS.stream().filter(o->o.contractId().equals(incoming.contractId())&&o.side()!=incoming.side())
                .sorted(oppositeComparator(incoming.side())).toList();
        for(FuturesOrder resting:candidates){
            if(incoming.remainingQuantity()==0)break;
            if(resting.playerId().equals(incoming.playerId())||!crosses(incoming,resting))continue;
            long qty=Math.min(incoming.remainingQuantity(),resting.remainingQuantity());long price=resting.limitPrice();
            FuturesOrder buy=incoming.side()==FuturesOrderSide.BUY?incoming:resting,sell=incoming.side()==FuturesOrderSide.SELL?incoming:resting;
            if(!execute(buy,sell,price,qty,incoming.side()==FuturesOrderSide.BUY))break;
            if(resting.remainingQuantity()==0)ORDERS.remove(resting);
        }
        if(incoming.remainingQuantity()>0)ORDERS.add(incoming);
        EconomySavedData.markDirty();return Result.ok(incoming.orderId(),incoming.remainingQuantity()==0?"期货订单已成交":"期货订单已挂入订单簿");
    }

    private static boolean execute(FuturesOrder buy,FuturesOrder sell,long price,long quantity,boolean buyInitiated){
        FuturesContract c=CONTRACTS.get(buy.contractId());if(c==null||!c.canTrade())return false;
        FuturesPosition bp=MarginManager.findPosition(buy.playerId(),c.id()),sp=MarginManager.findPosition(sell.playerId(),c.id());
        if(bp==null)bp=new FuturesPosition(buy.playerId(),c.id(),0,0,0,0);
        if(sp==null)sp=new FuturesPosition(sell.playerId(),c.id(),0,0,0,0);
        FuturesPosition.Preview b=bp.preview(FuturesOrderSide.BUY,quantity,price,c.contractSize()),s=sp.preview(FuturesOrderSide.SELL,quantity,price,c.contractSize());
        long br=buy.reservationFor(quantity),sr=sell.reservationFor(quantity);
        if(b==null||s==null||br<0||sr<0||Math.abs(b.signedQuantity())>FinanceConfig.futuresMaxPosition()||Math.abs(s.signedQuantity())>FinanceConfig.futuresMaxPosition()
                ||!MarginManager.canCommit(buy.playerId(),c.id(),b,br)||!MarginManager.canCommit(sell.playerId(),c.id(),s,sr))return false;
        MarginManager.commit(buy.playerId(),c.id(),b,br);MarginManager.commit(sell.playerId(),c.id(),s,sr);
        buy.fill(quantity,br);sell.fill(quantity,sr);
        long day=CandlestickService.currentMcDay();FuturesTrade trade=new FuturesTrade(buy.playerId(),sell.playerId(),c.id(),price,quantity,day,LocalDateTime.now());
        TRADES.add(trade);if(TRADES.size()>MAX_TRADES)TRADES.subList(0,TRADES.size()-MAX_TRADES).clear();LAST_PRICES.put(c.id(),price);
        String id=c.id().toString();CandlestickService.recordTrade(MarketInstrumentType.FUTURES,id,day,price,quantity);
        RecentTradeService.record(MarketInstrumentType.FUTURES,id,day,price,quantity,trade.timestamp(),buyInitiated?TradeDirection.BUY:TradeDirection.SELL);
        AccountManager.addTransactionRecord(new TransactionRecord(buy.playerId(),sell.playerId(),0,TransactionType.FUTURES_TRADE,buy.playerId(),c.code(),quantity));
        AccountManager.addTransactionRecord(new TransactionRecord(sell.playerId(),buy.playerId(),0,TransactionType.FUTURES_TRADE,sell.playerId(),c.code(),quantity));
        return true;
    }

    public static synchronized boolean cancel(UUID player,UUID orderId){
        Iterator<FuturesOrder> it=ORDERS.iterator();while(it.hasNext()){FuturesOrder o=it.next();if(!o.orderId().equals(orderId)||!o.playerId().equals(player))continue;
            if(!MarginManager.releaseOrder(player,o.reservedMargin()))return false;it.remove();EconomySavedData.markDirty();return true;}return false;
    }
    public static synchronized boolean cancelForContract(UUID contract){List<FuturesOrder> list=ORDERS.stream().filter(o->o.contractId().equals(contract)).toList();for(FuturesOrder o:list)if(MarginManager.account(o.playerId()).frozenForOrders()<o.reservedMargin())return false;for(FuturesOrder o:list){MarginManager.releaseOrder(o.playerId(),o.reservedMargin());ORDERS.remove(o);}return true;}
    public static synchronized boolean cancelForPlayer(UUID player){List<FuturesOrder> list=ORDERS.stream().filter(o->o.playerId().equals(player)).toList();for(FuturesOrder o:list)if(MarginManager.account(player).frozenForOrders()<o.reservedMargin())return false;for(FuturesOrder o:list){MarginManager.releaseOrder(player,o.reservedMargin());ORDERS.remove(o);}return true;}
    static synchronized void recordLiquidation(UUID owner,FuturesContract c,long price,long quantity,FuturesOrderSide ownerSide){long day=CandlestickService.currentMcDay();FuturesTrade t=ownerSide==FuturesOrderSide.SELL?new FuturesTrade(FuturesClearingService.CLEARING_MEMBER_ID,owner,c.id(),price,quantity,day,LocalDateTime.now()):new FuturesTrade(owner,FuturesClearingService.CLEARING_MEMBER_ID,c.id(),price,quantity,day,LocalDateTime.now());TRADES.add(t);if(TRADES.size()>MAX_TRADES)TRADES.subList(0,TRADES.size()-MAX_TRADES).clear();LAST_PRICES.put(c.id(),price);CandlestickService.recordTrade(MarketInstrumentType.FUTURES,c.id().toString(),day,price,quantity);RecentTradeService.record(MarketInstrumentType.FUTURES,c.id().toString(),day,price,quantity,t.timestamp(),ownerSide==FuturesOrderSide.BUY?TradeDirection.BUY:TradeDirection.SELL);}
    public static synchronized void processDay(long day){for(FuturesContract c:CONTRACTS.values()){
        if(c.status()!=FuturesContractStatus.SETTLED&&c.status()!=FuturesContractStatus.CANCELLED&&!CommodityRegistry.isRegistered(c.commodityId())){if(cancelForContract(c.id()))c.setStatus(FuturesContractStatus.CANCELLED);continue;}
        if(c.status()==FuturesContractStatus.SCHEDULED&&day>=c.listingDay())c.setStatus(FuturesContractStatus.TRADING);
        if(c.status()==FuturesContractStatus.TRADING&&day>=c.lastTradingDay())c.setStatus(FuturesContractStatus.LAST_TRADING_DAY);
        if(c.status()==FuturesContractStatus.SETTLING&&day>=c.maturityDay())FuturesClearingService.finalSettle(c,day);
    }}
    public static synchronized boolean hasLiveContractForCommodity(String commodity){return commodity!=null&&CONTRACTS.values().stream().anyMatch(c->c.commodityId().equals(commodity)&&c.status()!=FuturesContractStatus.SETTLED&&c.status()!=FuturesContractStatus.CANCELLED);}
    static synchronized void finishLastTradingDay(FuturesContract c){if(cancelForContract(c.id()))c.setStatus(FuturesContractStatus.SETTLING);}
    public static long riskPrice(UUID id){FuturesContract c=CONTRACTS.get(id);if(c==null)return 0;long last=LAST_PRICES.getOrDefault(id,0L);if(last>0)return last;long settled=FuturesClearingService.lastSettlementPrice(id);if(settled>0)return settled;MarketPrice spot=NpcMarketMaker.getMarketPrice(c.commodityId());return spot==null?0:spot.getMidPrice();}
    public static FuturesContract contract(UUID id){return CONTRACTS.get(id);} public static Map<UUID,FuturesContract> contracts(){return Collections.unmodifiableMap(CONTRACTS);}
    public static List<FuturesOrder> orders(){return List.copyOf(ORDERS);}public static List<FuturesTrade> trades(){return List.copyOf(TRADES);}public static long nextSequence(){return nextSequence;}
    public static long openInterest(UUID contract){return MarginManager.positions().values().stream().filter(p->p.contractId().equals(contract)&&p.signedQuantity()>0).mapToLong(FuturesPosition::quantity).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);}
    public static long dailyVolume(UUID contract,long day){return TRADES.stream().filter(t->t.contractId().equals(contract)&&t.mcDay()==day).mapToLong(FuturesTrade::quantity).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);}
    public static void putContractDirect(FuturesContract c){if(c!=null&&CONTRACTS.size()<MAX_CONTRACTS)CONTRACTS.put(c.id(),c);}public static void addOrderDirect(FuturesOrder o){if(o!=null&&ORDERS.size()<FinanceConfig.futuresMaxOrders())ORDERS.add(o);}public static void addTradeDirect(FuturesTrade t){if(t!=null&&TRADES.size()<MAX_TRADES){TRADES.add(t);LAST_PRICES.put(t.contractId(),t.price());}}
    public static void restoreSequence(long s){nextSequence=s>0?s:1;}public static void clearDirect(){CONTRACTS.clear();ORDERS.clear();TRADES.clear();LAST_PRICES.clear();nextSequence=1;}
    private static boolean crosses(FuturesOrder i,FuturesOrder r){return i.side()==FuturesOrderSide.BUY?i.limitPrice()>=r.limitPrice():i.limitPrice()<=r.limitPrice();}
    private static Comparator<FuturesOrder> oppositeComparator(FuturesOrderSide side){Comparator<FuturesOrder> p=Comparator.comparingLong(FuturesOrder::limitPrice);if(side==FuturesOrderSide.SELL)p=p.reversed();return p.thenComparingLong(FuturesOrder::sequence);}
    public record Result(boolean success,UUID id,String message){static Result ok(UUID id,String m){return new Result(true,id,m);}static Result fail(String m){return new Result(false,null,m);}}
}
