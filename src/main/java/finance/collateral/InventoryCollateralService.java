package finance.collateral;

import finance.account.AccountManager;
import finance.bank.BankingManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.commodity.CommodityInventoryManager;
import finance.data.EconomySavedData;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.LoanLenderType;
import finance.debt.LoanStatus;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyOperatingMode;
import finance.gameplay.company.CompanyPermission;
import finance.market.NpcMarketMaker;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;

import java.math.BigInteger;
import java.util.UUID;

public final class InventoryCollateralService {
    public static final int INITIAL_LTV_BPS=6000,MAINTENANCE_LTV_BPS=7500,LIQUIDATION_LTV_BPS=9000;
    private InventoryCollateralService(){}
    public record Result(boolean success,UUID id,String message){static Result ok(UUID id,String m){return new Result(true,id,m);}static Result fail(String m){return new Result(false,null,m);}}

    public static synchronized Result apply(UUID actor,UUID companyId,UUID bankId,String commodity,int quantity,
                                            long day,String operationKey){
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.COLLATERAL)||actor==null||companyId==null
                ||bankId==null||commodity==null||commodity.isBlank()||commodity.length()>64||quantity<=0||day<0
                ||operationKey==null||operationKey.isBlank()||operationKey.length()>48)return Result.fail("finance.collateral.invalid");
        Company company=CompanyManager.getCompany(companyId);
        if(company==null||company.isBankruptcyRisk()||BankingManager.bank(bankId)==null
                ||!CompanyMembershipService.hasPermission(companyId,actor,CompanyPermission.SPEND_COMPANY_CASH)
                ||CompanyGameplayManager.profileFor(company).operatingMode()==CompanyOperatingMode.LEGACY_AUTOMATIC)
            return Result.fail("finance.collateral.denied");
        var duplicate=InventoryCollateralManager.findOperation(companyId,actor+":"+operationKey);if(duplicate!=null)return Result.ok(duplicate.id(),"finance.collateral.duplicate");
        UUID custody=CompanyInventoryFacade.custodyId(companyId);
        if(InventoryCollateralManager.available(custody,commodity)<quantity)return Result.fail("finance.collateral.inventory");
        var valuation=CollateralValuationService.value(commodity,quantity,null);
        if(valuation==null)return Result.fail("finance.collateral.no_price");
        int initialLtv=finance.config.FinanceConfig.collateralInitialLtvBps();
        long principal=BigInteger.valueOf(valuation.discountedValue()).multiply(BigInteger.valueOf(initialLtv))
                .divide(BigInteger.valueOf(10000)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        if(principal<=0)return Result.fail("finance.collateral.no_value");
        UUID agreementId=UUID.randomUUID(),placeholderLoan=UUID.randomUUID();
        InventoryCollateralAgreement pending=new InventoryCollateralAgreement(agreementId,companyId,bankId,
                placeholderLoan,custody,commodity,quantity,day,valuation.unitPrice(),valuation.discountedValue(),
                valuation.haircutBps(),initialLtv,finance.config.FinanceConfig.collateralMaintenanceLtvBps(),finance.config.FinanceConfig.collateralLiquidationLtvBps(),
                InventoryCollateralStatus.PENDING);
        pending.recordOperation(actor+":"+operationKey);
        if(!InventoryCollateralManager.register(pending))return Result.fail("finance.collateral.limit");
        int term=30,interval=7;
        var loanResult=CompanyLoanManager.applyCommercial(company.getOwnerId(),companyId,bankId,principal,day,term,interval);
        if(!loanResult.success()){InventoryCollateralManager.removePending(agreementId);return Result.fail("finance.collateral.loan_denied");}
        InventoryCollateralAgreement active=new InventoryCollateralAgreement(agreementId,companyId,bankId,
                loanResult.id(),custody,commodity,quantity,day,valuation.unitPrice(),valuation.discountedValue(),
                valuation.haircutBps(),initialLtv,finance.config.FinanceConfig.collateralMaintenanceLtvBps(),finance.config.FinanceConfig.collateralLiquidationLtvBps(),
                InventoryCollateralStatus.ACTIVE);
        active.recordOperation(actor+":"+operationKey);
        InventoryCollateralManager.removePending(agreementId);
        if(!InventoryCollateralManager.register(active))throw new IllegalStateException("validated collateral replacement failed");
        EconomySavedData.markDirty();return Result.ok(agreementId,"finance.collateral.active");
    }

    public static synchronized Result supplement(UUID actor,UUID agreementId,int quantity,long day,String key){
        InventoryCollateralAgreement value=InventoryCollateralManager.get(agreementId);Company company=value==null?null:CompanyManager.getCompany(value.companyId());
        if(value==null||company==null||value.status()!=InventoryCollateralStatus.MARGIN_CALL||quantity<=0
                ||!CompanyMembershipService.hasPermission(value.companyId(),actor,CompanyPermission.SPEND_COMPANY_CASH)
                ||key==null||key.isBlank()||value.hasOperation(actor+":"+key))return Result.fail("finance.collateral.invalid");
        if(InventoryCollateralManager.available(value.custodyId(),value.commodityId())<quantity
                ||value.pledgedQuantity()>Integer.MAX_VALUE-quantity)return Result.fail("finance.collateral.inventory");
        value.quantity(value.pledgedQuantity()+quantity);value.recordOperation(actor+":"+key);revalue(value,day);EconomySavedData.markDirty();return Result.ok(value.id(),"finance.collateral.supplemented");
    }

    public static synchronized void processDay(long day){
        for(var value:InventoryCollateralManager.nextBatch(finance.config.FinanceConfig.collateralDailyBatch())){
            CompanyLoan loan=CompanyLoanManager.loans().get(value.loanId());
            if(loan==null)continue;
            if(value.status()==InventoryCollateralStatus.LIQUIDATING||value.status()==InventoryCollateralStatus.RELEASE_PENDING){processLiquidation(value,loan,day,false);continue;}
            if(!value.reservesInventory())continue;
            if(loan.status()==LoanStatus.REPAID||loan.outstandingPrincipal()==0){value.status(InventoryCollateralStatus.REPAID,day);EconomySavedData.markDirty();continue;}
            revalue(value,day);int ltv=CollateralValuationService.ltvBps(loan.outstandingPrincipal(),value.currentDiscountedValue());
            if(loan.status()==LoanStatus.DEFAULTED||(ltv>=value.liquidationLtvBps()&&value.status()==InventoryCollateralStatus.MARGIN_CALL&&day>value.marginCallDay())){
                if(InventoryCollateralManager.moveToLiquidation(value))processLiquidation(value,loan,day,false);continue;
            }
            if(ltv>=value.maintenanceLtvBps())value.status(InventoryCollateralStatus.MARGIN_CALL,day);
            else{value.status(InventoryCollateralStatus.ACTIVE,day);releaseExcess(value,loan);}
        }
    }

    private static void revalue(InventoryCollateralAgreement value,long day){var valuation=CollateralValuationService.value(value.commodityId(),value.pledgedQuantity(),value.haircutBps());value.value(day,valuation==null?0:valuation.discountedValue());}
    private static void releaseExcess(InventoryCollateralAgreement value,CompanyLoan loan){
        if(value.currentDiscountedValue()<=0||loan.outstandingPrincipal()<=0)return;
        BigInteger requiredValue=BigInteger.valueOf(loan.outstandingPrincipal()).multiply(BigInteger.valueOf(10000)).add(BigInteger.valueOf(value.initialLtvBps()-1L)).divide(BigInteger.valueOf(value.initialLtvBps()));
        BigInteger perUnit=BigInteger.valueOf(value.currentDiscountedValue()).divide(BigInteger.valueOf(Math.max(1,value.pledgedQuantity())));
        if(perUnit.signum()<=0)return;int required=requiredValue.add(perUnit).subtract(BigInteger.ONE).divide(perUnit).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();if(required<value.pledgedQuantity())value.quantity(Math.max(1,required));
    }
    private static void processLiquidation(InventoryCollateralAgreement value,CompanyLoan loan,long day,boolean sellAll){
        UUID proceeds=value.id();AccountManager.getOrCreateSystemAccount(proceeds);
        long cash=AccountManager.getBalance(proceeds);long applied=Math.min(cash,loan.outstandingPrincipal());
        if(applied>0&&BankingManager.canApplyLoanRecovery(value.bankId(),applied)&&AccountManager.withdraw(proceeds,applied)){
            if(!CompanyLoanManager.applyCollateralRecovery(value.loanId(),applied,day,UUID.nameUUIDFromBytes((value.id()+":"+value.liquidationRecovered()+":"+applied).getBytes(java.nio.charset.StandardCharsets.UTF_8)))){AccountManager.deposit(proceeds,applied);return;}
            value.recordRecovery(applied);
        }
        if(loan.outstandingPrincipal()>0||sellAll){int stock=CommodityInventoryManager.getCommodityAmount(value.id(),value.commodityId());if(stock>0){NpcMarketMaker.npcBuy(value.id(),value.commodityId(),stock);return;}}
        Company company=CompanyManager.getCompany(value.companyId());long surplus=AccountManager.getBalance(proceeds);if(surplus>0&&company!=null&&!MoneyTransferService.transfer(MoneyEndpoints.account(proceeds),MoneyEndpoints.company(company),surplus).success()){value.status(InventoryCollateralStatus.RELEASE_PENDING,day);return;}
        int unsold=CommodityInventoryManager.getCommodityAmount(value.id(),value.commodityId());if(unsold>0&&(company==null||!CommodityInventoryManager.canAddCommodity(value.custodyId(),value.commodityId(),unsold)||!CommodityInventoryManager.removeCommodity(value.id(),value.commodityId(),unsold)||!CommodityInventoryManager.addCommodity(value.custodyId(),value.commodityId(),unsold))){value.status(InventoryCollateralStatus.RELEASE_PENDING,day);return;}
        value.status(InventoryCollateralStatus.LIQUIDATED,day);EconomySavedData.markDirty();
    }
    public static long auditedRecovery(UUID loanId){return InventoryCollateralManager.totalRecovery(loanId);}
    public static boolean liquidationPending(UUID loanId){return InventoryCollateralManager.hasLiquidationPending(loanId);}
    /** Forces secured claims ahead of the general bankruptcy pool. Returns false while goods or proceeds remain unsettled. */
    public static synchronized boolean prepareCompanyBankruptcy(UUID companyId,long day){
        if(companyId==null||day<0)return false;
        boolean complete=true;
        for(var value:InventoryCollateralManager.forCompany(companyId)){
            if(!value.companyId().equals(companyId)||value.status()==InventoryCollateralStatus.REPAID||value.status()==InventoryCollateralStatus.LIQUIDATED)continue;
            if(value.status()==InventoryCollateralStatus.PENDING){InventoryCollateralManager.removePending(value.id());continue;}
            CompanyLoan loan=CompanyLoanManager.loans().get(value.loanId());
            if(loan==null){complete=false;continue;}
            if(loan.status()!=LoanStatus.REPAID&&loan.status()!=LoanStatus.CANCELLED)CompanyLoanManager.markCollateralDefault(loan.id(),day);
            if(value.reservesInventory()&&!InventoryCollateralManager.moveToLiquidation(value)){complete=false;continue;}
            if(value.status()==InventoryCollateralStatus.LIQUIDATING||value.status()==InventoryCollateralStatus.RELEASE_PENDING)processLiquidation(value,loan,day,true);
            if(value.status()!=InventoryCollateralStatus.LIQUIDATED&&value.status()!=InventoryCollateralStatus.REPAID)complete=false;
        }
        return complete;
    }
}
