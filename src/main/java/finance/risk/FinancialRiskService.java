package finance.risk;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.debt.*;
import finance.policy.MonetaryPolicyService;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

public final class FinancialRiskService {
    private FinancialRiskService() { }

    public static Snapshot snapshot() {
        BigInteger bonds = BigInteger.ZERO, loans = BigInteger.ZERO, delinquent = BigInteger.ZERO, defaults = BigInteger.ZERO;
        int defaultContracts = 0, debtContracts = 0, highRisk = 0;
        EnumMap<CreditRating, Integer> ratings = new EnumMap<>(CreditRating.class);
        for (Company company : CompanyManager.getCompanies()) {
            CreditRating rating = CompanyCreditService.rate(company); ratings.merge(rating, 1, Integer::sum);
            if (rating.ordinal() >= CreditRating.B.ordinal()) highRisk++;
        }
        for (CorporateBond bond : CorporateBondManager.bonds().values()) {
            if (bond.status() == BondStatus.ACTIVE || bond.status() == BondStatus.DEFAULTED) {
                debtContracts++; BigInteger value = BigInteger.valueOf(bond.faceValue()).multiply(BigInteger.valueOf(bond.subscribedQuantity()));
                bonds = bonds.add(value); if (bond.status() == BondStatus.DEFAULTED) { defaults = defaults.add(value); defaultContracts++; }
            }
        }
        for (CompanyLoan loan : CompanyLoanManager.loans().values()) {
            if (loan.status() != LoanStatus.REPAID && loan.status() != LoanStatus.CANCELLED) {
                debtContracts++; BigInteger value = BigInteger.valueOf(loan.outstandingPrincipal()); loans = loans.add(value);
                if (loan.status() == LoanStatus.DELINQUENT) delinquent = delinquent.add(value);
                if (loan.status() == LoanStatus.DEFAULTED) { defaults = defaults.add(value); defaultContracts++; }
            }
        }
        double defaultRate = debtContracts == 0 ? 0 : (double) defaultContracts / debtContracts * 100;
        String level = defaultRate >= finance.config.FinanceConfig.highDefaultRatePercent() || highRisk >= 5 ? "高"
                : defaultRate >= finance.config.FinanceConfig.mediumDefaultRatePercent() || highRisk > 0 ? "中" : "低";
        return new Snapshot(safeLong(bonds), safeLong(loans), safeLong(delinquent), safeLong(defaults), defaultRate,
                highRisk, MonetaryPolicyService.benchmarkRateBasisPoints(), level, Map.copyOf(ratings));
    }

    public static String compactSummary() {
        Snapshot s = snapshot();
        long day = finance.chart.CandlestickService.currentMcDay();
        long bondVolume = finance.bondmarket.BondMarketManager.trades().stream()
                .filter(t -> t.mcDay() == day).mapToLong(t -> t.quantity()).reduce(0, finance.util.MathUtil::saturatedAddNonNegative);
        double averageYield = CorporateBondManager.bonds().values().stream().filter(b -> b.status() == BondStatus.ACTIVE)
                .mapToInt(b -> FixedIncomeValuationService.referenceYieldBasisPoints(b, day)).average().orElse(0);
        long billBalance = finance.fixedincome.CentralBankBillManager.bills().values().stream()
                .filter(b -> b.status() == finance.fixedincome.CentralBankBillStatus.ACTIVE)
                .flatMap(b -> b.principalByPlayer().values().stream()).mapToLong(Long::longValue)
                .reduce(0, finance.util.MathUtil::saturatedAddNonNegative);
        var futures=finance.futures.FuturesRiskMetricsService.snapshot();
        return "利率 " + formatRate(s.benchmarkRateBps()) + " 债券 " + s.bondDebt() + " 贷款 " + s.loanDebt()
                + " 逾期 " + s.delinquentDebt() + " 违约率 " + String.format(java.util.Locale.ROOT, "%.1f%%", s.defaultRate())
                + " 风险 " + s.riskLevel() + " 债市量 " + bondVolume
                + " 平均收益 " + String.format(java.util.Locale.ROOT, "%.2f%%", averageYield / 100.0)
                + " 票据 " + billBalance + " 期货敞口 " + futures.notional() + " 追保/违约 " + futures.marginCalls() + "/" + futures.defaults()
                + " 保障基金 " + futures.guaranteeFund();
    }
    private static String formatRate(int bps) { return String.format(java.util.Locale.ROOT, "%.2f%%", bps / 100.0); }
    private static long safeLong(BigInteger value) { return value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
    public record Snapshot(long bondDebt, long loanDebt, long delinquentDebt, long defaultedDebt,
                           double defaultRate, int highRiskCompanies, int benchmarkRateBps,
                           String riskLevel, Map<CreditRating, Integer> ratingDistribution) { }
}
