package finance.insurance;

import finance.account.*;
import finance.bank.BankingManager;
import finance.company.*;
import finance.data.EconomySavedData;
import finance.debt.*;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class InsuranceManager {
    public static final int MAX_POLICIES=4096,MAX_CLAIMS=8192,MAX_EVENTS=8192,MAX_BATCH=32;
    private static final long DEFAULT_CAPITAL=2_000_000,MAX_POLICY_COVERAGE=10_000_000,MAX_COMPANY_EXPOSURE=20_000_000,MAX_PRODUCT_EXPOSURE=100_000_000;
    private static final InsurancePool POOL=new InsurancePool();
    private static final Map<UUID,InsurancePolicy> POLICIES=new LinkedHashMap<>();
    private static final Map<UUID,InsuranceClaim> CLAIMS=new LinkedHashMap<>();
    private static final Map<UUID,InsuredLossEvent> EVENTS=new LinkedHashMap<>();
    private static final Set<String> KEYS=new LinkedHashSet<>();
    private InsuranceManager(){}

    public static synchronized void initializeIfNeeded(){
        if(POOL.initialized())return;
        var existing=AccountManager.getAccounts().get(InsurancePool.ACCOUNT_ID);
        if(existing==null){existing=AccountManager.getAccount(InsurancePool.ACCOUNT_ID);if(!existing.setBalance(DEFAULT_CAPITAL))throw new IllegalStateException("insurance pool initialization failed");}
        POOL.initialize(DEFAULT_CAPITAL);EconomySavedData.markDirty();
    }
    public static synchronized InsurancePricingService.Quote quote(UUID requester,InsuranceProduct product,UUID object,long coverage,int termDays){
        initializeIfNeeded();if(!eligible(requester,product,object)||coverage>MAX_POLICY_COVERAGE)return null;
        return InsurancePricingService.quote(product,object,coverage,termDays);
    }
    public static synchronized Result purchase(UUID requester,InsuranceProduct product,UUID object,long coverage,int termDays,long day,String operationKey){
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.INSURANCE))return Result.fail("Insurance module is paused");
        initializeIfNeeded();String key="policy:"+(operationKey==null?"":operationKey);
        if(operationKey==null||operationKey.isBlank()||KEYS.contains(key))return Result.fail("Invalid or duplicate purchase request");
        if(POOL.newBusinessPaused())return Result.fail("Insurance capacity is unavailable");
        var quote=quote(requester,product,object,coverage,termDays);if(quote==null)return Result.fail("Object is ineligible or history is insufficient");
        if(hasOverlapping(product,object,day))return Result.fail("An overlapping active policy already exists");
        CompanyLoan capacityLoan=product==InsuranceProduct.BANK_LOAN_CREDIT?CompanyLoanManager.loans().get(object):null;
        UUID capacityCompany=capacityLoan==null?object:capacityLoan.companyId();
        if(wouldExceed(companyExposure(capacityCompany),coverage,MAX_COMPANY_EXPOSURE)||wouldExceed(productExposure(product),coverage,MAX_PRODUCT_EXPOSURE))return Result.fail("Insurance capacity for this company or product is unavailable");
        long exposure=activeExposure(),cash=AccountManager.getBalance(InsurancePool.ACCOUNT_ID);
        if(exposure>0&&BigInteger.valueOf(exposure).add(BigInteger.valueOf(coverage)).compareTo(BigInteger.valueOf(Math.max(1,cash)).multiply(BigInteger.TEN))>0){POOL.pause(true);return Result.fail("Insurance capacity is unavailable");}
        CompanyLoan loan=product==InsuranceProduct.BANK_LOAN_CREDIT?CompanyLoanManager.loans().get(object):null;
        UUID companyId=loan==null?object:loan.companyId(),beneficiary=loan==null?requester:loan.lenderId();
        long effective,expiry;try{effective=Math.addExact(day,1);expiry=Math.addExact(effective,termDays);}catch(ArithmeticException e){return Result.fail("Policy date overflow");}
        UUID tx=UUID.randomUUID();boolean premiumPaid=product==InsuranceProduct.BANK_LOAN_CREDIT
                ?BankingManager.payInsurancePremium(requester,InsurancePool.ACCOUNT_ID,quote.premium(),day,tx)
                :AccountManager.moveFunds(requester,InsurancePool.ACCOUNT_ID,quote.premium());
        if(!premiumPaid)return Result.fail("Premium payment failed");
        try{
            InsurancePolicy p=new InsurancePolicy(UUID.randomUUID(),product,requester,companyId,object,beneficiary,effective,expiry,PolicyStatus.PENDING,coverage,quote.deductible(),quote.payoutRatioBps(),coverage,quote.premium(),tx,quote.riskFactorBps(),quote.version(),day);
            POOL.premium(quote.premium());POLICIES.put(p.id(),p);KEYS.add(key);record(requester,InsurancePool.ACCOUNT_ID,quote.premium(),TransactionType.INSURANCE_PREMIUM,requester,"insurance-premium/"+product,1);EconomySavedData.markDirty();return Result.ok(p.id(),"Policy purchased; coverage starts next day");
        }catch(Exception e){
            if(product!=InsuranceProduct.BANK_LOAN_CREDIT)AccountManager.moveFunds(InsurancePool.ACCOUNT_ID,requester,quote.premium());
            return Result.fail("Policy creation failed");
        }
    }
    private static boolean eligible(UUID requester,InsuranceProduct product,UUID object){
        if(requester==null||product==null||object==null)return false;
        if(product==InsuranceProduct.BANK_LOAN_CREDIT){CompanyLoan l=CompanyLoanManager.loans().get(object);return l!=null&&l.lenderType()==LoanLenderType.COMMERCIAL_BANK&&l.lenderId().equals(requester)&&l.status()!=LoanStatus.DEFAULTED&&l.status()!=LoanStatus.REPAID;}
        Company c=CompanyManager.getCompany(object);return c!=null&&requester.equals(c.getOwnerId());
    }
    private static boolean hasOverlapping(InsuranceProduct p,UUID object,long day){return POLICIES.values().stream().anyMatch(x->x.product()==p&&x.insuredObjectId().equals(object)&&(x.status()==PolicyStatus.ACTIVE||x.status()==PolicyStatus.PENDING)&&x.expiryDay()>=day);}
    public static synchronized Result cancel(UUID requester,UUID policyId,long day){InsurancePolicy p=POLICIES.get(policyId);if(requester==null||p==null||!requester.equals(p.holderId()))return Result.fail("Policy not found or access denied");if(p.status()!=PolicyStatus.PENDING&&p.status()!=PolicyStatus.ACTIVE&&p.status()!=PolicyStatus.SUSPENDED)return Result.fail("Policy cannot be cancelled in its current state");p.restoreStatus(PolicyStatus.CANCELLED,day);EconomySavedData.markDirty();return Result.ok(policyId,"Policy cancelled; paid premium is not refundable");}
    private static long companyExposure(UUID company){return exposureMatching(p->company!=null&&company.equals(p.companyId()));}
    private static long productExposure(InsuranceProduct product){return exposureMatching(p->p.product()==product);}
    private static long exposureMatching(java.util.function.Predicate<InsurancePolicy> predicate){BigInteger n=BigInteger.ZERO;for(var p:POLICIES.values())if((p.status()==PolicyStatus.ACTIVE||p.status()==PolicyStatus.PENDING)&&predicate.test(p))n=n.add(BigInteger.valueOf(p.remainingLimit()));return cap(n);}
    private static boolean wouldExceed(long current,long added,long limit){return BigInteger.valueOf(current).add(BigInteger.valueOf(added)).compareTo(BigInteger.valueOf(limit))>0;}

    public static synchronized Result createWarehouseAccident(UUID companyId,String commodity,int quantity,long day,long seed){
        Company c=CompanyManager.getCompany(companyId);MarketPrice price=NpcMarketMaker.getMarketPrice(commodity);
        int total=c==null?0:finance.gameplay.company.CompanyInventoryFacade.totalInventory(c,commodity);
        int available=c==null?0:finance.gameplay.company.CompanyInventoryFacade.availableInsurableInventory(c,commodity);
        int lost=Math.min(Math.max(0,quantity),available);
        if(c==null||price==null||lost<=0)return Result.fail("No verifiable unpledged inventory loss");UUID id=id("warehouse:"+companyId+":"+commodity+":"+day+":"+seed);if(EVENTS.containsKey(id))return Result.fail("Event already processed");
        long value=cap(BigInteger.valueOf(price.getMidPrice()).multiply(BigInteger.valueOf(lost)));
        var consumed=finance.gameplay.company.CompanyInventoryFacade.consumeInsurableLoss(c,commodity,lost);if(consumed==null)return Result.fail("Inventory loss failed");
        try{
            int intensity=(int)Math.min(10_000L,(long)lost*10_000L/Math.max(1,total));InsuredLossEvent event=new InsuredLossEvent(id,RiskEventType.WAREHOUSE_ACCIDENT,companyId,companyId,day,day,seed,intensity,commodity,total,lost,price.getMidPrice(),value,0,"authority total="+total+" available="+available+" pledged="+(total-available)+" removed="+lost,true);
            EVENTS.put(id,event);autoClaim(event);EconomySavedData.markDirty();return Result.ok(id,"Warehouse accident recorded");
        }catch(RuntimeException failure){if(!finance.gameplay.company.CompanyInventoryFacade.rollback(c,consumed))throw new IllegalStateException("insured inventory rollback failed",failure);return Result.fail("Loss event creation failed");}
    }
    public static synchronized Result createBusinessInterruption(UUID companyId,long day,long seed){
        Company c=CompanyManager.getCompany(companyId);var stopped=finance.gameplay.company.CompanyFacilityManager.forCompany(companyId).stream().filter(f->f.status()!=finance.gameplay.company.CompanyFacilityStatus.ACTIVE&&f.status()!=finance.gameplay.company.CompanyFacilityStatus.DISABLED&&f.status()!=finance.gameplay.company.CompanyFacilityStatus.ORPHANED&&f.statusSinceDay()>=0&&f.lastProcessedDay()>=day).min(java.util.Comparator.comparingLong(finance.gameplay.company.CompanyFacilityRecord::statusSinceDay)).orElse(null);
        long stoppedDays=stopped==null?0:day-stopped.statusSinceDay()+1;if(c==null||stoppedDays<=1||c.getRecentProfits().size()<3)return Result.fail("Insufficient verified shutdown or profit history");UUID id=id("shutdown:"+companyId+":"+stopped.statusSinceDay()+":"+stopped.status());if(EVENTS.containsKey(id))return Result.fail("Event already processed");
        long average=(long)c.getRecentProfits().stream().mapToLong(Long::longValue).filter(v->v>0).average().orElse(0);CompanyFinancialReport latest=c.getLatestFinancialReport();long actualRevenue=latest!=null&&latest.mcDay()>=stopped.statusSinceDay()?Math.max(0,latest.revenue()):0;long loss=Math.max(0,cap(BigInteger.valueOf(Math.max(0,average)).multiply(BigInteger.valueOf(stoppedDays)))-actualRevenue);
        int intensity=(int)Math.min(10_000L,stoppedDays>10?10_000L:stoppedDays*1_000L);InsuredLossEvent event=new InsuredLossEvent(id,RiskEventType.FACILITY_SHUTDOWN,companyId,companyId,stopped.statusSinceDay(),day,seed,intensity,"",0,0,0,loss,0,"facility="+stopped.facilityId()+" reason="+stopped.status()+" since="+stopped.statusSinceDay()+" average="+average+" actual="+actualRevenue,true);
        EVENTS.put(id,event);autoClaim(event);EconomySavedData.markDirty();return Result.ok(id,"Business interruption recorded");
    }
    public static synchronized Result createLoanDefault(UUID loanId,long day){
        CompanyLoan l=CompanyLoanManager.loans().get(loanId);if(l==null||l.status()!=LoanStatus.DEFAULTED||l.lenderType()!=LoanLenderType.COMMERCIAL_BANK||finance.collateral.InventoryCollateralService.liquidationPending(loanId))return Result.fail("Loan has no finalized eligible credit loss");UUID id=id("loan-default:"+loanId);if(EVENTS.containsKey(id))return Result.fail("Event already processed");
        long recovered=finance.collateral.InventoryCollateralService.auditedRecovery(loanId);long gross=cap(BigInteger.valueOf(l.outstandingPrincipal()).add(BigInteger.valueOf(recovered)));InsuredLossEvent event=new InsuredLossEvent(id,RiskEventType.LOAN_DEFAULT,l.companyId(),loanId,day,day,0,10_000,"",0,0,0,gross,recovered,"gross-default="+gross+" audited-collateral-recovery="+recovered+" net="+l.outstandingPrincipal(),true);
        EVENTS.put(id,event);autoClaim(event);EconomySavedData.markDirty();return Result.ok(id,"Credit loss recorded");
    }
    private static void autoClaim(InsuredLossEvent event){
        for(InsurancePolicy p:POLICIES.values()){
            if(!p.insuredObjectId().equals(event.objectId())||!p.activeAt(event.occurredDay())||!matches(p.product(),event.type()))continue;
            if(CLAIMS.values().stream().anyMatch(c->c.policyId().equals(p.id())&&c.eventId().equals(event.id())))continue;
            long eligible=Math.max(0,event.verifiedLoss()-event.offsetCompensation()-p.deductible());long ratio=finance.fund.FundMath.ratioFloor(event.verifiedLoss(),p.payoutRatioBps(),10_000);long approved=Math.min(p.remainingLimit(),Math.min(eligible,Math.max(0,ratio)));UUID claimId=id(p.id()+":"+event.id());ClaimStatus status=approved>0?ClaimStatus.APPROVED:ClaimStatus.REJECTED;
            CLAIMS.put(claimId,new InsuranceClaim(claimId,p.id(),event.id(),p.holderId(),event.verifiedLoss(),event.verifiedLoss(),approved,event.occurredDay(),event.occurredDay(),event.occurredDay(),status,approved>0?"authoritative event approved":"below deductible or no remaining limit",event.evidence(),0,-1,null));
            if(approved>0)p.consume(approved,event.occurredDay());
        }
    }
    private static boolean matches(InsuranceProduct p,RiskEventType e){return p==InsuranceProduct.INVENTORY_DISASTER&&e==RiskEventType.WAREHOUSE_ACCIDENT||p==InsuranceProduct.BUSINESS_INTERRUPTION&&e==RiskEventType.FACILITY_SHUTDOWN||p==InsuranceProduct.BANK_LOAN_CREDIT&&e==RiskEventType.LOAN_DEFAULT;}

    public static synchronized int processPayments(long day){
        initializeIfNeeded();if(day<0)return 0;int count=0;
        for(InsuranceClaim claim:CLAIMS.values()){
            if(count>=MAX_BATCH)break;if(claim.status()!=ClaimStatus.APPROVED&&claim.status()!=ClaimStatus.PARTIALLY_PAID)continue;InsurancePolicy p=POLICIES.get(claim.policyId());long amount=Math.min(claim.unpaidAmount(),AccountManager.getBalance(InsurancePool.ACCOUNT_ID));if(p==null||amount<=0)continue;UUID tx=id("insurance-payment:"+claim.id()+":"+claim.paidAmount());boolean paid;
            if(p.product()==InsuranceProduct.BANK_LOAN_CREDIT){paid=AccountManager.withdraw(InsurancePool.ACCOUNT_ID,amount);if(paid&&!CompanyLoanManager.applyInsuranceRecovery(p.insuredObjectId(),amount,day,tx)){AccountManager.deposit(InsurancePool.ACCOUNT_ID,amount);paid=false;}}
            else paid=AccountManager.moveFunds(InsurancePool.ACCOUNT_ID,p.beneficiaryId(),amount);
            if(!paid)continue;claim.paid(amount,day,tx);POOL.claim(amount);record(InsurancePool.ACCOUNT_ID,p.beneficiaryId(),amount,TransactionType.INSURANCE_CLAIM,p.holderId(),"insurance-claim/"+p.product(),1);count++;
        }
        if(day>POOL.lastProcessedDay())POOL.day(day);POOL.pause(BigInteger.valueOf(activeExposure()).compareTo(BigInteger.valueOf(Math.max(1,AccountManager.getBalance(InsurancePool.ACCOUNT_ID))).multiply(BigInteger.TEN))>0);EconomySavedData.markDirty();return count;
    }
    public static synchronized void processDay(long day){initializeIfNeeded();for(InsurancePolicy p:POLICIES.values()){if(p.status()==PolicyStatus.PENDING&&day>=p.effectiveDay())p.restoreStatus(PolicyStatus.ACTIVE,day);if((p.status()==PolicyStatus.ACTIVE||p.status()==PolicyStatus.SUSPENDED)&&day>p.expiryDay())p.expire(day);if(p.status()==PolicyStatus.ACTIVE&&p.product()==InsuranceProduct.BUSINESS_INTERRUPTION)createBusinessInterruption(p.companyId(),day,p.id().getMostSignificantBits());}for(CompanyLoan l:CompanyLoanManager.loans().values())if(l.status()==LoanStatus.DEFAULTED&&l.lenderType()==LoanLenderType.COMMERCIAL_BANK)createLoanDefault(l.id(),day);processPayments(day);}
    public static synchronized long activeExposure(){BigInteger n=BigInteger.ZERO;for(InsurancePolicy p:POLICIES.values())if(p.status()==PolicyStatus.ACTIVE||p.status()==PolicyStatus.PENDING)n=n.add(BigInteger.valueOf(p.remainingLimit()));return cap(n);}
    public static synchronized long approvedUnpaid(){BigInteger n=BigInteger.ZERO;for(InsuranceClaim c:CLAIMS.values())if(c.status()==ClaimStatus.APPROVED||c.status()==ClaimStatus.PARTIALLY_PAID)n=n.add(BigInteger.valueOf(c.unpaidAmount()));return cap(n);}
    public static InsurancePool pool(){return POOL;}public static synchronized Map<UUID,InsurancePolicy> policies(){return Collections.unmodifiableMap(new LinkedHashMap<>(POLICIES));}public static synchronized Map<UUID,InsuranceClaim> claims(){return Collections.unmodifiableMap(new LinkedHashMap<>(CLAIMS));}public static synchronized Map<UUID,InsuredLossEvent> events(){return Collections.unmodifiableMap(new LinkedHashMap<>(EVENTS));}public static synchronized Set<String> keys(){return Collections.unmodifiableSet(new LinkedHashSet<>(KEYS));}
    public static synchronized void putPolicyDirect(InsurancePolicy p){if(p!=null&&POLICIES.size()<MAX_POLICIES)POLICIES.put(p.id(),p);}public static synchronized void putClaimDirect(InsuranceClaim c){if(c!=null&&CLAIMS.size()<MAX_CLAIMS)CLAIMS.put(c.id(),c);}public static synchronized void putEventDirect(InsuredLossEvent e){if(e!=null&&EVENTS.size()<MAX_EVENTS)EVENTS.put(e.id(),e);}public static synchronized void putKeyDirect(String k){if(k!=null&&!k.isBlank()&&KEYS.size()<20_000)KEYS.add(k);}public static synchronized void clearDirect(){POLICIES.clear();CLAIMS.clear();EVENTS.clear();KEYS.clear();POOL.restore(false,false,0,0,0,0,-1);}
    private static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}private static long cap(BigInteger n){return n.max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();}private static void record(UUID from,UUID to,long amount,TransactionType type,UUID player,String object,long quantity){AccountManager.addTransactionRecord(new TransactionRecord(from,to,amount,type,player,object,quantity));}
    public record Result(boolean success,UUID id,String message){static Result ok(UUID id,String m){return new Result(true,id,m);}static Result fail(String m){return new Result(false,null,m);}}
}
