package finance.hedge;

import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.futures.*;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;

import java.math.BigInteger;
import java.util.UUID;

/** Thin, read-only interpretation layer over the existing personal futures engine. */
public final class CompanyHedgeService {
    private CompanyHedgeService(){}
    public record Result(boolean success,UUID id,String message){static Result ok(UUID id,String m){return new Result(true,id,m);}static Result fail(String m){return new Result(false,null,m);}}
    public record Coverage(HedgeCoverageStatus status,int coverageBps,long coveredQuantity,long signedLots,
                           MarginRiskStatus marginRisk,long realizedPnl,boolean personalAccount){}

    public static synchronized Result create(UUID actor,UUID companyId,UUID contractId,HedgeObjectiveType type,
                                              long targetQuantity,long day,long deadline,String operationKey){
        if(!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.HEDGE)||actor==null||companyId==null||contractId==null
                ||type==null||targetQuantity<=0||day<0||deadline<=day||deadline-day>3650||operationKey==null
                ||operationKey.isBlank()||operationKey.length()>48)return Result.fail("finance.hedge.invalid");
        if(CompanyManager.getCompany(companyId)==null||!CompanyMembershipService.hasPermission(companyId,actor,CompanyPermission.MANAGE_PRODUCTION))return Result.fail("finance.hedge.denied");
        if(CompanyHedgeManager.operationExists(companyId,actor,operationKey))return Result.fail("finance.hedge.duplicate");
        FuturesContract contract=FuturesMarketManager.contract(contractId);
        if(contract==null||contract.status()==FuturesContractStatus.SETTLED||contract.maturityDay()<deadline)return Result.fail("finance.hedge.contract");
        CompanyHedgeObjective value=new CompanyHedgeObjective(UUID.randomUUID(),companyId,actor,contractId,
                contract.commodityId(),type,targetQuantity,day,deadline,operationKey);
        if(!CompanyHedgeManager.register(value))return Result.fail("finance.hedge.limit");
        EconomySavedData.markDirty();return Result.ok(value.id(),"finance.hedge.created_personal");
    }

    public static Coverage coverage(CompanyHedgeObjective value,long day){
        if(value==null)return new Coverage(HedgeCoverageStatus.UNHEDGED,0,0,0,MarginRiskStatus.NORMAL,0,true);
        FuturesContract contract=FuturesMarketManager.contract(value.contractId());
        FuturesPosition position=contract==null?null:MarginManager.findPosition(value.operatorId(),value.contractId());
        long signed=position==null?0:position.signedQuantity();
        boolean direction=value.type()==HedgeObjectiveType.INPUT_COST?signed>0:signed<0;
        BigInteger units=direction&&contract!=null?BigInteger.valueOf(Math.abs(signed)).multiply(BigInteger.valueOf(contract.contractSize())):BigInteger.ZERO;
        long covered=units.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        int bps=BigInteger.valueOf(covered).multiply(BigInteger.valueOf(10_000)).divide(BigInteger.valueOf(value.targetQuantity()))
                .min(BigInteger.valueOf(20_000)).intValue();
        HedgeCoverageStatus status=day>value.deadlineDay()?HedgeCoverageStatus.EXPIRED:bps==0?HedgeCoverageStatus.UNHEDGED:bps<10_000?HedgeCoverageStatus.PARTIAL:bps==10_000?HedgeCoverageStatus.TARGET_MET:HedgeCoverageStatus.OVER_HEDGED;
        MarginAccount account=MarginManager.accounts().get(value.operatorId());
        return new Coverage(status,bps,covered,signed,account==null?MarginRiskStatus.NORMAL:account.riskStatus(),position==null?0:position.realizedPnl(),true);
    }

    public static synchronized Result cancel(UUID actor,UUID objectiveId){
        CompanyHedgeObjective value=CompanyHedgeManager.get(objectiveId);
        if(value==null||actor==null||!value.operatorId().equals(actor)&&!CompanyMembershipService.hasPermission(value.companyId(),actor,CompanyPermission.MANAGE_PRODUCTION))return Result.fail("finance.hedge.denied");
        CompanyHedgeManager.remove(objectiveId);EconomySavedData.markDirty();return Result.ok(objectiveId,"finance.hedge.cancelled");
    }
}
