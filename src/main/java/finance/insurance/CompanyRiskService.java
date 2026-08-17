package finance.insurance;

import finance.company.Company;
import finance.debt.CompanyLoanManager;
import finance.futures.MarginManager;
import finance.market.NpcMarketMaker;

import java.math.BigInteger;
import java.util.UUID;

public final class CompanyRiskService {
 private CompanyRiskService(){}
 public record Summary(long cash,long inventoryValue,double largestInventoryPercent,long loanBalance,long futuresEquity,long insuredInventory,long uninsuredInventory,long pendingClaims,String hedgeStatus,String level,String reasons){}
 public static Summary calculate(Company c){if(c==null)return new Summary(0,0,0,0,0,0,0,0,"none","UNKNOWN","company missing");long inventory=c.inventoryValue(),largest=0;for(var e:c.getInventory().entrySet()){var p=NpcMarketMaker.getMarketPrice(e.getKey());if(p!=null)largest=Math.max(largest,finance.fund.FundMath.cap(BigInteger.valueOf(p.getMidPrice()).multiply(BigInteger.valueOf(e.getValue()))));}double concentration=inventory>0?largest*100.0/inventory:0;long coverage=InsuranceManager.policies().values().stream().filter(p->p.companyId()!=null&&p.companyId().equals(c.getCompanyId())&&p.product()==InsuranceProduct.INVENTORY_DISASTER&&p.status()==PolicyStatus.ACTIVE).mapToLong(InsurancePolicy::remainingLimit).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);long pending=InsuranceManager.claims().values().stream().filter(cl->InsuranceManager.policies().get(cl.policyId())!=null&&c.getCompanyId().equals(InsuranceManager.policies().get(cl.policyId()).companyId())).mapToLong(InsuranceClaim::unpaidAmount).reduce(0,(a,b)->a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b);var margin=MarginManager.accounts().get(c.getCompanyId());long futures=margin==null?0:margin.cashBalance();long loans=CompanyLoanManager.outstandingPrincipal(c.getCompanyId());String level=c.getCash()<Math.max(1,loans/10)||concentration>75?"HIGH":coverage<inventory/2?"MEDIUM":"LOW";String hedge=futures==0?"uncovered":futures<inventory/2?"partially covered":"possibly over-covered";return new Summary(c.getCash(),inventory,concentration,loans,futures,Math.min(coverage,inventory),Math.max(0,inventory-coverage),pending,hedge,level,"cash buffer, inventory concentration, debt, insurance, futures exposure");}
}
