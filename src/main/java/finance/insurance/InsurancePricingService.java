package finance.insurance;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.debt.CompanyCreditService;
import finance.debt.CompanyLoan;
import finance.debt.CreditRating;
import finance.util.MathUtil;

import java.math.BigInteger;
import java.util.UUID;

public final class InsurancePricingService {
    private InsurancePricingService(){}
    public record Quote(InsuranceProduct product,UUID objectId,long coverage,long deductible,int payoutRatioBps,long premium,int riskFactorBps,int version,String explanation){}
    public static Quote quote(InsuranceProduct product,UUID objectId,long coverage,int termDays){if(product==null||objectId==null||coverage<=0||termDays<2||termDays>3650)return null;Company company=null;CompanyLoan loan=null;if(product==InsuranceProduct.BANK_LOAN_CREDIT){loan=finance.debt.CompanyLoanManager.loans().get(objectId);company=loan==null?null:CompanyManager.getCompany(loan.companyId());}else company=CompanyManager.getCompany(objectId);if(company==null)return null;
        int base=product==InsuranceProduct.INVENTORY_DISASTER?250:product==InsuranceProduct.BUSINESS_INTERRUPTION?350:450;int risk=10_000;long assets=Math.max(1,company.getEstimatedValue()),debt=CompanyCreditService.totalDebt(company.getCompanyId());risk+=Math.min(10_000,(int)Math.min(Integer.MAX_VALUE,BigInteger.valueOf(debt).multiply(BigInteger.valueOf(5_000)).divide(BigInteger.valueOf(assets)).longValue()));if(product==InsuranceProduct.BANK_LOAN_CREDIT){CreditRating rating=CompanyCreditService.rate(company);risk+=rating.spreadBasisPoints()*2;if(loan==null||loan.outstandingPrincipal()<=0)return null;}else if(product==InsuranceProduct.INVENTORY_DISASTER&&company.inventoryValue()==0)return null;else if(product==InsuranceProduct.BUSINESS_INTERRUPTION&&company.getRecentProfits().size()<3)return null;
        risk=Math.max(7_500,Math.min(30_000,risk));long deductible=Math.max(1,coverage/20);int ratio=8_000;BigInteger p=BigInteger.valueOf(coverage).multiply(BigInteger.valueOf(base)).multiply(BigInteger.valueOf(risk)).multiply(BigInteger.valueOf(termDays)).divide(BigInteger.valueOf(10_000L*10_000L*365L)).add(BigInteger.valueOf(25));long premium=p.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();return new Quote(product,objectId,coverage,deductible,ratio,Math.max(1,premium),risk,1,"基础费率"+base+"bp，风险因子"+risk+"bp，期限"+termDays+"日");}
}
