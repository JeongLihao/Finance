package finance.fund;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.index.MarketIndexService;
import finance.market.CentralBank;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.company.Company;
import finance.company.CompanyManager;

import java.math.BigInteger;
import java.util.*;

/** Authoritative primary-market fund service. All public mutations are synchronized and server-side. */
public final class FundManager {
    public static final long SHARE_SCALE=10_000L, INITIAL_NAV=10_000L;
    public static final int MAX_FUNDS=64,MAX_POSITIONS=16_384,MAX_REQUESTS=4_096,MAX_PLANS=4_096;
    private static final Map<String,FundDefinition> DEFINITIONS=new LinkedHashMap<>();
    private static final Map<String,FundState> STATES=new LinkedHashMap<>();
    private static final Map<UUID,Map<String,PlayerFundPosition>> POSITIONS=new LinkedHashMap<>();
    private static final Map<UUID,FundRedemptionRequest> REQUESTS=new LinkedHashMap<>();
    private static final Map<UUID,FundInvestmentPlan> PLANS=new LinkedHashMap<>();
    private static final Set<String> OPERATION_KEYS=new LinkedHashSet<>();
    private static final Set<String> RISK_ACKNOWLEDGEMENTS=new LinkedHashSet<>();
    private FundManager(){}

    public static synchronized void seedDefaultsIfNeeded(){
        if(!DEFINITIONS.isEmpty())return;
        registerDirect(new FundDefinition("all-share","全市场股票指数基金",FundType.BROAD_STOCK_INDEX,"listed",20,10,10,100,7,2_500),new FundState());
        registerDirect(new FundDefinition("materials-theme","原材料主题指数基金",FundType.SECTOR_STOCK_INDEX,"RAW_MATERIALS",25,10,15,100,7,3_500),new FundState());
        registerDirect(new FundDefinition("money-short","货币与短债基金",FundType.MONEY_MARKET,"cash+bills+short-bonds",10,5,5,100,7,5_000),new FundState());
        EconomySavedData.markDirty();
    }
    public static synchronized Result subscribe(UUID player,String fundId,long amount,long day,String operationKey){
        String id=FundDefinition.normalize(fundId);FundDefinition definition=DEFINITIONS.get(id);FundState state=STATES.get(id);
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.FUND))return Result.fail("基金模块只读或暂停");
        if(player==null||definition==null||state==null||amount<definition.minimumSubscription()||day<0)return Result.fail("申购参数无效");
        if(!hasRiskAcknowledgement(player,definition.type()))return Result.fail("首次购买前请确认净值波动、亏损和赎回延迟风险");
        if(state.status()!=FundStatus.ACTIVE)return Result.fail("基金当前不接受申购");
        String key=operationKey==null||operationKey.isBlank()?"subscribe:"+UUID.randomUUID():"subscribe:"+operationKey;
        if(OPERATION_KEYS.contains(key))return Result.fail("重复申购请求");
        FundValuationService.Valuation valuation=FundValuationService.value(definition,state,day);if(valuation.nav()<=0||valuation.degraded())return Result.fail("净值不可可靠计算");
        long fee=FundMath.feeFloor(amount,definition.subscriptionFeeBps()),invested=amount-fee;
        long shares=FundMath.ratioFloor(invested,SHARE_SCALE,valuation.nav());if(shares<=0)return Result.fail("申购金额不足以获得有效份额");
        PlayerFundPosition position=position(player,id);if((position!=null&&!position.canAdd(shares,amount))||!state.canAddShares(shares)||!state.canAddFees(fee))return Result.fail("份额、成本或费用溢出");
        if(position==null)position=positionInternal(player,id);
        if((fee>0&&!AccountManager.canDeposit(CentralBank.UUID,fee))||!AccountManager.moveFunds(player,definition.custodyAccountId(),amount))return Result.fail("余额不足或基金托管账户溢出");
        if(fee>0&&!AccountManager.moveFunds(definition.custodyAccountId(),CentralBank.UUID,fee)){AccountManager.moveFunds(definition.custodyAccountId(),player,amount);return Result.fail("申购费结算失败");}
        try{position.add(shares,amount);state.addShares(shares);state.addFees(fee);rememberOperation(key);}catch(ArithmeticException ex){if(fee>0)AccountManager.moveFunds(CentralBank.UUID,definition.custodyAccountId(),fee);AccountManager.moveFunds(definition.custodyAccountId(),player,amount);return Result.fail("基金结算溢出");}
        AccountManager.addTransactionRecord(new TransactionRecord(player,definition.custodyAccountId(),amount,TransactionType.FUND_SUBSCRIBE,player,definition.displayName(),shares));
        updateNav(id,day);EconomySavedData.markDirty();return Result.ok("申购成功",shares,amount);
    }
    public static synchronized Result requestRedemption(UUID player,String fundId,long shares,long day,String operationKey){
        String id=FundDefinition.normalize(fundId);FundDefinition definition=DEFINITIONS.get(id);FundState state=STATES.get(id);PlayerFundPosition position=position(player,id);
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.FUND))return Result.fail("基金模块只读或暂停");
        if(player==null||definition==null||state==null||position==null||shares<=0||day<0)return Result.fail("赎回参数无效");
        if(state.status()==FundStatus.REDEMPTION_PAUSED||state.status()==FundStatus.CLOSED)return Result.fail("基金当前暂停赎回");
        String key=operationKey==null||operationKey.isBlank()?"redeem:"+UUID.randomUUID():"redeem:"+operationKey;if(OPERATION_KEYS.contains(key))return Result.fail("重复赎回请求");
        if(!ensureRequestCapacity())return Result.fail("赎回队列已满，请等待现有请求结算");
        long precision=Math.max(1,SHARE_SCALE/100);if(shares%precision!=0||!position.freeze(shares))return Result.fail("可用份额不足或不符合赎回精度");
        UUID requestId=UUID.randomUUID();FundRedemptionRequest request=new FundRedemptionRequest(requestId,player,id,shares,day,FundRedemptionRequest.Status.PENDING);REQUESTS.put(requestId,request);rememberOperation(key);
        Result executed=executeRedemption(request,day);EconomySavedData.markDirty();return executed.success()?executed:new Result(true,"赎回请求已排队",shares,0,requestId);
    }
    private static Result executeRedemption(FundRedemptionRequest request,long day){
        FundDefinition definition=DEFINITIONS.get(request.fundId());FundState state=STATES.get(request.fundId());PlayerFundPosition position=position(request.playerId(),request.fundId());
        if(definition==null||state==null||position==null){request.status(FundRedemptionRequest.Status.FAILED,"基金或持仓不存在",0);return Result.fail("基金或持仓不存在");}
        FundValuationService.Valuation valuation=FundValuationService.value(definition,state,day);if(valuation.nav()<=0){request.status(FundRedemptionRequest.Status.PENDING,"净值不可用",0);return Result.fail("净值不可用");}
        long gross=FundMath.ratioFloor(request.shareUnits(),valuation.nav(),SHARE_SCALE),fee=FundMath.feeFloor(gross,definition.redemptionFeeBps()),payment=gross-fee;
        if(payment<=0||request.shareUnits()>position.frozenShareUnits()||request.shareUnits()>state.totalShareUnits()
                ||AccountManager.getBalance(definition.custodyAccountId())<gross||!AccountManager.canDeposit(request.playerId(),payment)||!AccountManager.canDeposit(CentralBank.UUID,fee)){request.status(FundRedemptionRequest.Status.PENDING,"等待基金流动性、份额一致性或玩家收款容量",0);return Result.fail("等待流动性");}
        request.status(FundRedemptionRequest.Status.EXECUTING,"",0);
        if(!AccountManager.moveFunds(definition.custodyAccountId(),request.playerId(),payment)){request.status(FundRedemptionRequest.Status.PENDING,"付款失败，等待重试",0);return Result.fail("付款失败");}
        if(fee>0&&!AccountManager.moveFunds(definition.custodyAccountId(),CentralBank.UUID,fee)){AccountManager.moveFunds(request.playerId(),definition.custodyAccountId(),payment);request.status(FundRedemptionRequest.Status.PENDING,"赎回费结算失败",0);return Result.fail("赎回费结算失败");}
        if(!position.redeemFrozen(request.shareUnits(),payment)||!state.removeShares(request.shareUnits())){if(fee>0)AccountManager.moveFunds(CentralBank.UUID,definition.custodyAccountId(),fee);AccountManager.moveFunds(request.playerId(),definition.custodyAccountId(),payment);request.status(FundRedemptionRequest.Status.PENDING,"份额结算失败",0);return Result.fail("份额结算失败");}
        try{state.addFees(fee);}catch(ArithmeticException ignored){}
        request.status(FundRedemptionRequest.Status.PAID,"",payment);AccountManager.addTransactionRecord(new TransactionRecord(definition.custodyAccountId(),request.playerId(),payment,TransactionType.FUND_REDEEM,request.playerId(),definition.displayName(),request.shareUnits()));
        updateNav(request.fundId(),day);return new Result(true,"赎回成功",request.shareUnits(),payment,request.id());
    }
    public static synchronized boolean cancelRedemption(UUID player,UUID requestId){FundRedemptionRequest r=REQUESTS.get(requestId);if(r==null||!r.playerId().equals(player)||r.status()!=FundRedemptionRequest.Status.PENDING)return false;PlayerFundPosition p=position(player,r.fundId());if(p==null||!p.unfreeze(r.shareUnits()))return false;r.status(FundRedemptionRequest.Status.CANCELLED,"玩家撤销",0);EconomySavedData.markDirty();return true;}
    public static synchronized void processDay(long day){if(day<0||!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.FUND))return;seedDefaultsIfNeeded();for(String id:new ArrayList<>(DEFINITIONS.keySet())){accrueFee(id,day);updateConstituents(id,day);rebalance(id,day);updateNav(id,day);}raiseRedemptionLiquidity(day);for(FundRedemptionRequest r:new ArrayList<>(REQUESTS.values()))if(r.status()==FundRedemptionRequest.Status.PENDING)executeRedemption(r,day);for(FundInvestmentPlan p:new ArrayList<>(PLANS.values()))if(p.status()==FundInvestmentPlan.Status.ACTIVE&&p.nextExecutionDay()<=day&&p.lastAttemptDay()!=day){Result result=subscribe(p.playerId(),p.fundId(),p.amount(),day,"plan:"+p.id()+":"+day);p.attempted(day,result.success());}}
    private static void accrueFee(String id,long day){FundDefinition d=DEFINITIONS.get(id);FundState s=STATES.get(id);if(day<=s.lastFeeDay())return;FundValuationService.Valuation v=FundValuationService.value(d,s,day);long fee=FundMath.ratioFloor(v.netAssets(),d.managementFeeBps(),10_000L*365L);if(fee>0&&s.canAddFees(fee)&&AccountManager.getBalance(d.custodyAccountId())>=fee&&AccountManager.moveFunds(d.custodyAccountId(),CentralBank.UUID,fee))s.addFees(fee);s.markFeeDay(day);}
    public static synchronized boolean register(FundDefinition definition){if(definition==null||DEFINITIONS.containsKey(definition.id())||DEFINITIONS.size()>=MAX_FUNDS)return false;registerDirect(definition,new FundState());EconomySavedData.markDirty();return true;}
    public static synchronized boolean setStatus(String id,FundStatus status,String reason){FundState s=STATES.get(FundDefinition.normalize(id));if(s==null||status==null)return false;if(status==FundStatus.ACTIVE)s.resume();else s.suspend(status,reason);EconomySavedData.markDirty();return true;}
    private static void updateConstituents(String id,long day){FundDefinition d=DEFINITIONS.get(id);FundState s=STATES.get(id);if(s.constituentEffectiveDay()==day)return;if(s.constituentEffectiveDay()<0||day-s.constituentEffectiveDay()>=d.rebalanceIntervalDays()){s.updateConstituents(day);s.markRebalance(day);}}
    private static void rebalance(String id,long day){FundDefinition d=DEFINITIONS.get(id);FundState s=STATES.get(id);if(s.lastRebalanceDay()!=day)return;long cash=AccountManager.getBalance(d.custodyAccountId());if(cash<=d.minimumSubscription())return;
        if(d.type()==FundType.MONEY_MARKET){long invest=cash*60/100;if(invest>=d.minimumSubscription())finance.fixedincome.CentralBankBillManager.subscribe(d.custodyAccountId(),7,invest,day);return;}
        List<Stock> eligible=new ArrayList<>();for(Stock stock:StockMarketManager.getListedStocks()){Company company=CompanyManager.getCompany(stock.getCompanyId());if(stock.getLastPrice()>0&&company!=null&&(d.type()==FundType.BROAD_STOCK_INDEX||company.getType().name().equals(d.selectionRule())))eligible.add(stock);}eligible.sort(Comparator.comparing(Stock::getSymbol));if(eligible.isEmpty()){s.suspend(FundStatus.SUBSCRIPTION_PAUSED,"没有可交易的指数成分");return;}long budget=cash*80/100/eligible.size();for(Stock stock:eligible){long quantity=Math.min(Integer.MAX_VALUE,budget/stock.getLastPrice());if(quantity>0)StockMarketManager.placeLimitBuy(d.custodyAccountId(),stock.getSymbol(),stock.getLastPrice(),quantity);}}
    private static void raiseRedemptionLiquidity(long day){for(FundRedemptionRequest request:REQUESTS.values()){if(request.status()!=FundRedemptionRequest.Status.PENDING)continue;FundDefinition d=DEFINITIONS.get(request.fundId());FundState s=STATES.get(request.fundId());if(d==null||s==null||d.type()==FundType.MONEY_MARKET)continue;long gross=FundMath.ratioFloor(request.shareUnits(),s.currentNav(),SHARE_SCALE);if(AccountManager.getBalance(d.custodyAccountId())>=gross)continue;for(var entry:new ArrayList<>(StockPortfolioManager.getPortfolio(d.custodyAccountId()).entrySet())){Stock stock=StockMarketManager.getStock(entry.getKey());if(stock==null)continue;long need=gross-AccountManager.getBalance(d.custodyAccountId());long quantity=Math.min(entry.getValue().getQuantity(),Math.max(1,(need+stock.getLastPrice()-1)/stock.getLastPrice()));StockMarketManager.placeLimitSell(d.custodyAccountId(),stock.getSymbol(),stock.getLastPrice(),quantity);if(AccountManager.getBalance(d.custodyAccountId())>=gross)break;}}}
    public static synchronized FundValuationService.Valuation updateNav(String id,long day){FundDefinition d=DEFINITIONS.get(FundDefinition.normalize(id));FundState s=STATES.get(FundDefinition.normalize(id));if(d==null||s==null)return new FundValuationService.Valuation(0,0,0,0,0,0,true,"missing fund");FundValuationService.Valuation v=FundValuationService.value(d,s,day);if(v.nav()<=0)s.suspend(FundStatus.SUBSCRIPTION_PAUSED,v.riskReason());else{s.recordNav(new FundNavPoint(day,v.nav(),v.netAssets(),s.totalShareUnits(),benchmarkLevel(d),v.degraded()));if(v.degraded()&&s.status()==FundStatus.ACTIVE)s.suspend(FundStatus.SUBSCRIPTION_PAUSED,v.riskReason());}return v;}
    private static long benchmarkLevel(FundDefinition d){return switch(d.type()){case MONEY_MARKET->INITIAL_NAV;case BROAD_STOCK_INDEX,SECTOR_STOCK_INDEX->{var index=MarketIndexService.states().get(d.type()==FundType.BROAD_STOCK_INDEX?MarketIndexService.STOCK_COMPOSITE:"sector:"+d.selectionRule());var latest=index==null?null:index.latest();yield latest==null||!Double.isFinite(latest.value())||latest.value()<=0?INITIAL_NAV:Math.max(1,Math.round(latest.value()*10));}};}
    public static synchronized Result createPlan(UUID player,String fundId,long amount,int interval,long firstDay){if(player==null||!DEFINITIONS.containsKey(FundDefinition.normalize(fundId))||amount<=0||interval<=0||interval>3650||firstDay<0||PLANS.size()>=MAX_PLANS)return Result.fail("定投参数无效");FundInvestmentPlan p=new FundInvestmentPlan(UUID.randomUUID(),player,fundId,amount,interval,firstDay,FundInvestmentPlan.Status.ACTIVE);PLANS.put(p.id(),p);EconomySavedData.markDirty();return new Result(true,"定投已创建",0,0,p.id());}
    public static synchronized boolean setPlanStatus(UUID player,UUID id,FundInvestmentPlan.Status status){FundInvestmentPlan p=PLANS.get(id);if(p==null||!p.playerId().equals(player)||status==null)return false;p.status(status);EconomySavedData.markDirty();return true;}
    public static synchronized boolean acknowledgeRisk(UUID player,FundType type){if(player==null||type==null)return false;String key=player+":"+type.name();if(!RISK_ACKNOWLEDGEMENTS.contains(key)&&RISK_ACKNOWLEDGEMENTS.size()>=20_000)return false;RISK_ACKNOWLEDGEMENTS.add(key);EconomySavedData.markDirty();return true;}
    public static synchronized boolean hasRiskAcknowledgement(UUID player,FundType type){return player!=null&&type!=null&&RISK_ACKNOWLEDGEMENTS.contains(player+":"+type.name());}
    private static PlayerFundPosition positionInternal(UUID player,String id){return POSITIONS.computeIfAbsent(player,k->new LinkedHashMap<>()).computeIfAbsent(id,k->new PlayerFundPosition());}
    public static synchronized PlayerFundPosition position(UUID player,String id){Map<String,PlayerFundPosition> map=POSITIONS.get(player);return map==null?null:map.get(FundDefinition.normalize(id));}
    public static synchronized Map<String,FundDefinition> definitions(){return Collections.unmodifiableMap(new LinkedHashMap<>(DEFINITIONS));}
    public static synchronized Map<String,FundState> states(){return Collections.unmodifiableMap(new LinkedHashMap<>(STATES));}
    public static synchronized Map<UUID,Map<String,PlayerFundPosition>> positions(){Map<UUID,Map<String,PlayerFundPosition>> copy=new LinkedHashMap<>();POSITIONS.forEach((k,v)->copy.put(k,Collections.unmodifiableMap(new LinkedHashMap<>(v))));return Collections.unmodifiableMap(copy);}
    public static synchronized Map<UUID,FundRedemptionRequest> requests(){return Collections.unmodifiableMap(new LinkedHashMap<>(REQUESTS));}
    public static synchronized Map<UUID,FundInvestmentPlan> plans(){return Collections.unmodifiableMap(new LinkedHashMap<>(PLANS));}
    public static synchronized List<FundRedemptionRequest> requestsFor(UUID player,int limit){if(player==null||limit<=0)return List.of();List<FundRedemptionRequest> out=new ArrayList<>(Math.min(limit,REQUESTS.size()));for(FundRedemptionRequest value:REQUESTS.values()){if(value.playerId().equals(player))out.add(value);if(out.size()>=limit)break;}return List.copyOf(out);}
    public static synchronized List<FundInvestmentPlan> plansFor(UUID player,int limit){if(player==null||limit<=0)return List.of();List<FundInvestmentPlan> out=new ArrayList<>(Math.min(limit,PLANS.size()));for(FundInvestmentPlan value:PLANS.values()){if(value.playerId().equals(player))out.add(value);if(out.size()>=limit)break;}return List.copyOf(out);}
    public static synchronized Set<String> operationKeys(){return Collections.unmodifiableSet(new LinkedHashSet<>(OPERATION_KEYS));}
    public static synchronized Set<String> riskAcknowledgements(){return Collections.unmodifiableSet(new LinkedHashSet<>(RISK_ACKNOWLEDGEMENTS));}
    public static synchronized void registerDirect(FundDefinition d,FundState s){if(d!=null&&s!=null&&DEFINITIONS.size()<MAX_FUNDS){DEFINITIONS.put(d.id(),d);STATES.put(d.id(),s);}}
    public static synchronized void putPositionDirect(UUID player,String id,PlayerFundPosition p){if(player!=null&&p!=null&&POSITIONS.size()<MAX_POSITIONS)POSITIONS.computeIfAbsent(player,k->new LinkedHashMap<>()).put(FundDefinition.normalize(id),p);}
    public static synchronized void putRequestDirect(FundRedemptionRequest r){if(r!=null&&REQUESTS.size()<MAX_REQUESTS)REQUESTS.put(r.id(),r);}
    public static synchronized void putPlanDirect(FundInvestmentPlan p){if(p!=null&&PLANS.size()<MAX_PLANS)PLANS.put(p.id(),p);}
    public static synchronized void addOperationKeyDirect(String key){if(key!=null&&!key.isBlank()&&OPERATION_KEYS.size()<20_000)OPERATION_KEYS.add(key);}
    public static synchronized void addRiskAcknowledgementDirect(String key){if(key!=null&&!key.isBlank()&&RISK_ACKNOWLEDGEMENTS.size()<20_000)RISK_ACKNOWLEDGEMENTS.add(key);}
    public static synchronized void clearDirect(){DEFINITIONS.clear();STATES.clear();POSITIONS.clear();REQUESTS.clear();PLANS.clear();OPERATION_KEYS.clear();RISK_ACKNOWLEDGEMENTS.clear();}
    private static boolean ensureRequestCapacity(){if(REQUESTS.size()<MAX_REQUESTS)return true;var iterator=REQUESTS.entrySet().iterator();while(iterator.hasNext()&&REQUESTS.size()>=MAX_REQUESTS){FundRedemptionRequest.Status status=iterator.next().getValue().status();if(status==FundRedemptionRequest.Status.PAID||status==FundRedemptionRequest.Status.CANCELLED||status==FundRedemptionRequest.Status.FAILED)iterator.remove();}return REQUESTS.size()<MAX_REQUESTS;}
    private static void rememberOperation(String key){if(key==null||key.isBlank())return;OPERATION_KEYS.add(key);while(OPERATION_KEYS.size()>20_000)OPERATION_KEYS.remove(OPERATION_KEYS.iterator().next());}
    public record Result(boolean success,String message,long shareUnits,long amount,UUID referenceId){static Result ok(String m,long s,long a){return new Result(true,m,s,a,null);}static Result fail(String m){return new Result(false,m,0,0,null);}}
}
