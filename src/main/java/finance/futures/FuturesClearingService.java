package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.Candlestick;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.ProportionalAllocator;

import java.math.BigInteger;
import java.util.*;

/** Variation-margin clearing. It never mints: payouts are limited to collected losses plus the guarantee fund. */
public final class FuturesClearingService {
    public static final UUID CLEARING_MEMBER_ID=UUID.fromString("00000000-0000-0000-0000-00000000f001");
    public static final int MAX_HISTORY=500;
    private static final Map<UUID,Long> LAST_SETTLEMENT_PRICE=new LinkedHashMap<>(),LAST_SETTLEMENT_DAY=new LinkedHashMap<>();
    private static final List<FuturesSettlementRecord> HISTORY=new ArrayList<>();
    private static long guaranteeFund;private static boolean fundInitialized;private static long todayFundUse;private static long lastRiskDay=-1;
    private FuturesClearingService(){}

    public static synchronized boolean closeDay(long completedDay){
        if(completedDay<0)return false;boolean changed=false;todayFundUse=lastRiskDay==completedDay?todayFundUse:0;lastRiskDay=completedDay;
        for(FuturesContract c:FuturesMarketManager.contracts().values()){
            if(!c.canTrade()||completedDay>=c.maturityDay())continue;
            long price=settlementPrice(c,completedDay);
            if(price>0&&settle(c,price,completedDay,false))changed=true;
            if(completedDay>=c.lastTradingDay()){FuturesMarketManager.finishLastTradingDay(c);changed=true;}
        }
        FuturesRiskService.evaluateAll(completedDay);if(changed)EconomySavedData.markDirty();return changed;
    }

    static synchronized boolean finalSettle(FuturesContract c,long day){
        if(c==null||c.status()!=FuturesContractStatus.SETTLING||day<c.maturityDay())return false;
        long price=finalPrice(c);if(price<=0)return false;
        if(!FuturesMarketManager.cancelForContract(c.id()))return false;
        if(!settle(c,price,day,true))return false;
        MarginManager.closeContractPositions(c.id());c.setFinalSettlementPrice(price);c.setStatus(FuturesContractStatus.SETTLED);
        AccountManager.addTransactionRecord(new TransactionRecord(CLEARING_MEMBER_ID,CLEARING_MEMBER_ID,0,TransactionType.FUTURES_EXPIRY,CLEARING_MEMBER_ID,c.code(),FuturesMarketManager.openInterest(c.id())));
        EconomySavedData.markDirty();return true;
    }

    static synchronized boolean settle(FuturesContract c,long price,long day,boolean finalSettlement){
        if(c==null||price<=0||day<0||LAST_SETTLEMENT_DAY.getOrDefault(c.id(),-1L)>=day)return false;
        Map<UUID,Long> pnl=new LinkedHashMap<>();Set<UUID> owners=new LinkedHashSet<>();
        MarginManager.positions().forEach((k,p)->{if(k.contractId().equals(c.id()))owners.add(k.ownerId());});
        MarginManager.pendingVariations().forEach((k,v)->{if(k.contractId().equals(c.id()))owners.add(k.ownerId());});
        BigInteger net=BigInteger.ZERO,totalGain=BigInteger.ZERO;
        try{
            for(UUID owner:owners){FuturesPosition p=MarginManager.findPosition(owner,c.id());long open=p==null?0:FuturesMath.signedPnl(p.settlementReferencePrice(),price,c.contractSize(),p.signedQuantity());long value=Math.addExact(open,MarginManager.pendingVariation(owner,c.id()));pnl.put(owner,value);net=net.add(BigInteger.valueOf(value));if(value>0)totalGain=totalGain.add(BigInteger.valueOf(value));}
        }catch(ArithmeticException ex){return false;}
        if(net.signum()!=0||totalGain.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)return false;
        long gross=totalGain.longValue(),collected=0;Map<UUID,Long> debits=new LinkedHashMap<>(),weights=new LinkedHashMap<>();
        for(var e:pnl.entrySet())if(e.getValue()<0){long capacity=Math.max(0,MarginManager.account(e.getKey()).cashBalance()-MarginManager.account(e.getKey()).frozenForOrders());long debit=Math.min(capacity,-e.getValue());debits.put(e.getKey(),debit);collected=safeAdd(collected,debit);}else if(e.getValue()>0)weights.put(e.getKey(),e.getValue());
        long shortage=Math.max(0,gross-collected),fundUse=Math.min(guaranteeFund(),shortage),available=safeAdd(collected,fundUse);
        List<ProportionalAllocator.Allocation> allocations=ProportionalAllocator.allocate(Math.min(gross,available),weights);
        for(var a:allocations)if(!MarginManager.canCredit(a.id(),a.amount()))return false;
        for(var e:debits.entrySet())MarginManager.applySettlement(e.getKey(),c.id(),e.getValue(),0,day);
        Set<UUID> credited=new HashSet<>();for(var a:allocations){MarginManager.applySettlement(a.id(),c.id(),0,a.amount(),day);credited.add(a.id());}
        for(UUID owner:owners)if(!debits.containsKey(owner)&&!credited.contains(owner))MarginManager.applySettlement(owner,c.id(),0,0,day);
        guaranteeFund-=fundUse;todayFundUse=safeAdd(todayFundUse,fundUse);
        for(FuturesPosition p:List.copyOf(MarginManager.positions().values()))if(p.contractId().equals(c.id()))p.setSettlementReferencePrice(price);
        long haircut=Math.max(0,gross-Math.min(gross,available));LAST_SETTLEMENT_PRICE.put(c.id(),price);LAST_SETTLEMENT_DAY.put(c.id(),day);
        HISTORY.add(new FuturesSettlementRecord(c.id(),day,price,gross,collected,fundUse,haircut,finalSettlement));if(HISTORY.size()>MAX_HISTORY)HISTORY.subList(0,HISTORY.size()-MAX_HISTORY).clear();
        for(var e:debits.entrySet())if(e.getValue()>0)AccountManager.addTransactionRecord(new TransactionRecord(e.getKey(),CLEARING_MEMBER_ID,e.getValue(),TransactionType.FUTURES_DAILY_SETTLEMENT,e.getKey(),c.code(),1));
        for(var a:allocations)if(a.amount()>0)AccountManager.addTransactionRecord(new TransactionRecord(CLEARING_MEMBER_ID,a.id(),a.amount(),TransactionType.FUTURES_DAILY_SETTLEMENT,a.id(),c.code(),1));
        return true;
    }

    private static long settlementPrice(FuturesContract c,long day){
        long candidate=0;for(Candlestick bar:CandlestickService.getBars(MarketInstrumentType.FUTURES,c.id().toString(),120))if(bar.mcDay()==day&&bar.volume()>=FinanceConfig.futuresMinimumSettlementVolume())candidate=bar.close();
        if(candidate<=0)candidate=LAST_SETTLEMENT_PRICE.getOrDefault(c.id(),0L);MarketPrice spot=NpcMarketMaker.getMarketPrice(c.commodityId());long spotPrice=spot==null?0:spot.getMidPrice();if(candidate<=0)candidate=spotPrice;if(candidate<=0)return 0;
        if(spotPrice>0){BigInteger band=BigInteger.valueOf(spotPrice).multiply(BigInteger.valueOf(FinanceConfig.futuresMaxSpotDeviationBps())).divide(BigInteger.valueOf(10_000));long d=band.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();candidate=Math.max(Math.max(1,spotPrice-d),Math.min(candidate,safeAdd(spotPrice,d)));}return candidate;
    }
    private static long finalPrice(FuturesContract c){List<Candlestick> bars=CandlestickService.getBars(MarketInstrumentType.COMMODITY,c.commodityId(),120);BigInteger sum=BigInteger.ZERO;int count=0,window=FinanceConfig.futuresFinalSettlementWindowDays();for(int i=bars.size()-1;i>=0&&count<window;i--){Candlestick b=bars.get(i);if(b.mcDay()<=c.maturityDay()&&b.close()>0){sum=sum.add(BigInteger.valueOf(b.close()));count++;}}if(count>0)return sum.divide(BigInteger.valueOf(count)).longValue();MarketPrice spot=NpcMarketMaker.getMarketPrice(c.commodityId());return spot==null?0:spot.getMidPrice();}
    static synchronized boolean allocateFundCollateral(long amount){if(amount<=0||guaranteeFund()<amount||!MarginManager.creditCollateral(CLEARING_MEMBER_ID,amount))return false;guaranteeFund-=amount;return true;}
    static synchronized void reclaimFundCollateral(long amount){if(amount<=0)return;MarginAccount account=MarginManager.account(CLEARING_MEMBER_ID);if(account.cashBalance()>=amount){account.forceDebit(amount);guaranteeFund=safeAdd(guaranteeFund,amount);}}
    public static synchronized long guaranteeFund(){if(!fundInitialized){guaranteeFund=FinanceConfig.futuresInitialGuaranteeFund();fundInitialized=true;}return guaranteeFund;}
    public static synchronized long guaranteeFundForAudit(){return fundInitialized?guaranteeFund:FinanceConfig.futuresInitialGuaranteeFund();}
    public static long todayFundUse(){return todayFundUse;}public static long lastRiskDay(){return lastRiskDay;}public static long lastSettlementPrice(UUID id){return LAST_SETTLEMENT_PRICE.getOrDefault(id,0L);}public static long lastSettlementDay(UUID id){return LAST_SETTLEMENT_DAY.getOrDefault(id,-1L);}public static List<FuturesSettlementRecord> history(){return List.copyOf(HISTORY);}
    public static void restoreFund(long value,long use,long riskDay){guaranteeFund=Math.max(0,value);fundInitialized=true;todayFundUse=Math.max(0,use);lastRiskDay=Math.max(-1,riskDay);}public static void putSettlementDirect(UUID id,long price,long day){if(id!=null&&price>0&&day>=0){LAST_SETTLEMENT_PRICE.put(id,price);LAST_SETTLEMENT_DAY.put(id,day);}}public static void addHistoryDirect(FuturesSettlementRecord r){if(r!=null&&HISTORY.size()<MAX_HISTORY)HISTORY.add(r);}public static void clearDirect(){LAST_SETTLEMENT_PRICE.clear();LAST_SETTLEMENT_DAY.clear();HISTORY.clear();guaranteeFund=0;fundInitialized=false;todayFundUse=0;lastRiskDay=-1;}
    private static long safeAdd(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
}
