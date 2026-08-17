package finance.bank;

import finance.config.FinanceConfig;
import java.math.BigInteger;

public final class BankRegulatoryService {
    private BankRegulatoryService() { }
    public static long requiredReserves(CommercialBank bank) {
        if (bank == null) return Long.MAX_VALUE;
        BankBalanceSheet s=bank.ledger().balanceSheet();
        BigInteger required=BigInteger.valueOf(s.demandDeposits()).multiply(BigInteger.valueOf(FinanceConfig.bankDemandReserveBps()))
                .add(BigInteger.valueOf(s.timeDeposits()).multiply(BigInteger.valueOf(FinanceConfig.bankTimeReserveBps()))).divide(BigInteger.valueOf(10_000));
        return required.max(BigInteger.valueOf(FinanceConfig.bankMinimumReserve())).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static long riskWeightedAssets(CommercialBank bank){if(bank==null)return Long.MAX_VALUE;BankBalanceSheet s=bank.ledger().balanceSheet();BigInteger rwa=BigInteger.valueOf(s.companyLoans()).add(BigInteger.valueOf(s.interbankAssets()).divide(BigInteger.TWO)).add(BigInteger.valueOf(s.bondAssets()).divide(BigInteger.valueOf(5)));return rwa.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();}
    public static int capitalAdequacyBps(CommercialBank bank){long rwa=riskWeightedAssets(bank);long equity=bank==null?Long.MIN_VALUE:bank.ledger().balanceSheet().equity();if(rwa==0)return equity>=0?100_000:Integer.MIN_VALUE;return ratio(equity,rwa);}
    public static int liquidityCoverageBps(CommercialBank bank){if(bank==null)return 0;BankBalanceSheet s=bank.ledger().balanceSheet();BigInteger out=BigInteger.valueOf(s.demandDeposits()).add(BigInteger.valueOf(s.timeDeposits()).divide(BigInteger.TEN));if(out.signum()==0)return 100_000;return ratio(s.reserves(),out.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());}
    public static void evaluate(CommercialBank bank){if(bank==null||bank.status()==BankStatus.FAILED||bank.status()==BankStatus.MERGED)return;BankBalanceSheet s=bank.ledger().balanceSheet();int normal=FinanceConfig.bankMinimumCapitalBps(),restricted=Math.max(2,normal*3/4),resolution=Math.max(1,normal/2),car=capitalAdequacyBps(bank);long required=requiredReserves(bank);if(!s.balanced()||s.equity()<=0||car<resolution)bank.setStatus(BankStatus.RESOLUTION);else if(car<restricted)bank.setStatus(BankStatus.RESTRICTED);else if(car<normal||s.reserves()<required)bank.setStatus(BankStatus.WATCH);else bank.setStatus(BankStatus.ACTIVE);}
    private static int ratio(long numerator,long denominator){if(denominator<=0)return 100_000;BigInteger r=BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(10_000)).divide(BigInteger.valueOf(denominator));return r.max(BigInteger.valueOf(Integer.MIN_VALUE)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();}
}
