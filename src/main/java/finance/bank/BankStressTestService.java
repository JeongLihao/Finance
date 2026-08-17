package finance.bank;

import finance.config.FinanceConfig;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure, bounded stress calculator. It never posts entries or changes live bank status. */
public final class BankStressTestService {
    public static final int MAX_CONTAGION_ROUNDS = 32;

    private BankStressTestService() { }

    public record Scenario(int companyLoanLossBps, int depositOutflowBps,
                           int interbankLossBps, int rateShockBps) {
        public Scenario {
            if (companyLoanLossBps < 0 || companyLoanLossBps > 10_000
                    || depositOutflowBps < 0 || depositOutflowBps > 10_000
                    || interbankLossBps < 0 || interbankLossBps > 10_000
                    || Math.abs(rateShockBps) > 10_000) throw new IllegalArgumentException();
        }
    }

    public record BankResult(UUID bankId, long assetsBefore, long loss, long equityAfter,
                             int capitalBps, int liquidityBps, boolean needsSupport,
                             boolean resolution) { }

    public record SystemResult(List<BankResult> banks, long capitalShortfall,
                               long liquidityShortfall, long insuranceExposure,
                               int contagionRounds, UUID criticalBank) { }

    public static SystemResult run(Scenario scenario) {
        if (scenario == null) return new SystemResult(List.of(), 0, 0, 0, 0, null);
        List<CommercialBank> banks = BankingManager.banks().values().stream()
                .limit(BankingManager.MAX_BANKS).toList();
        Map<UUID, BigInteger> contagionLoss = new LinkedHashMap<>();
        Set<UUID> failed = new LinkedHashSet<>();
        int rounds = 0;
        int roundLimit = Math.min(MAX_CONTAGION_ROUNDS, FinanceConfig.bankStressMaxRounds());
        for (; rounds < roundLimit; rounds++) {
            boolean changed = false;
            for (CommercialBank bank : banks) {
                BigInteger loss = baseLoss(bank.ledger().balanceSheet(), scenario)
                        .add(contagionLoss.getOrDefault(bank.id(), BigInteger.ZERO));
                if (BigInteger.valueOf(bank.ledger().balanceSheet().equity()).subtract(loss).signum() <= 0
                        && failed.add(bank.id())) {
                    changed = true;
                    for (InterbankLoan loan : InterbankMarketService.loans().values()) {
                        if (loan.status() == InterbankLoanStatus.ACTIVE
                                && loan.borrowerBankId().equals(bank.id())) {
                            contagionLoss.merge(loan.lenderBankId(),
                                    BigInteger.valueOf(loan.principal()), BigInteger::add);
                        }
                    }
                }
            }
            if (!changed) break;
        }

        List<BankResult> results = new ArrayList<>();
        long capitalGap = 0, liquidityGap = 0, insurance = 0, maxLoss = -1;
        UUID critical = null;
        int minimumCapital = FinanceConfig.bankMinimumCapitalBps();
        for (CommercialBank bank : banks) {
            BankBalanceSheet sheet = bank.ledger().balanceSheet();
            BigInteger exactLoss = baseLoss(sheet, scenario)
                    .add(contagionLoss.getOrDefault(bank.id(), BigInteger.ZERO));
            long loss = exactLoss.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            long equityAfter = BigInteger.valueOf(sheet.equity()).subtract(exactLoss)
                    .max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            int capitalBps = ratio(equityAfter, Math.max(1, BankRegulatoryService.riskWeightedAssets(bank)));
            long outflow = BigInteger.valueOf(sheet.demandDeposits())
                    .multiply(BigInteger.valueOf(scenario.depositOutflowBps()))
                    .divide(BigInteger.valueOf(10_000)).longValue();
            long liquidAfter = sheet.reserves() - outflow;
            int liquidityBps = ratio(Math.max(0, liquidAfter), Math.max(1, outflow));
            boolean resolution = equityAfter <= 0 || capitalBps < Math.max(1, minimumCapital / 2);
            boolean needsSupport = liquidAfter < 0 || capitalBps < minimumCapital;
            if (equityAfter < 0) capitalGap = safeAdd(capitalGap, -equityAfter);
            if (liquidAfter < 0) liquidityGap = safeAdd(liquidityGap, -liquidAfter);
            if (resolution) insurance = safeAdd(insurance,
                    Math.min(safeAdd(sheet.demandDeposits(), sheet.timeDeposits()), DepositInsuranceService.fund()));
            if (loss > maxLoss) { maxLoss = loss; critical = bank.id(); }
            results.add(new BankResult(bank.id(), sheet.totalAssets(), loss, equityAfter,
                    capitalBps, liquidityBps, needsSupport, resolution));
        }
        return new SystemResult(List.copyOf(results), capitalGap, liquidityGap,
                insurance, rounds, critical);
    }

    private static BigInteger baseLoss(BankBalanceSheet sheet, Scenario scenario) {
        BigInteger credit = BigInteger.valueOf(sheet.companyLoans())
                .multiply(BigInteger.valueOf(scenario.companyLoanLossBps()));
        BigInteger interbank = BigInteger.valueOf(sheet.interbankAssets())
                .multiply(BigInteger.valueOf(scenario.interbankLossBps()));
        // Positive rate shocks reduce fixed-rate bond value in this simplified stress model.
        BigInteger rate = BigInteger.valueOf(sheet.bondAssets())
                .multiply(BigInteger.valueOf(Math.max(0, scenario.rateShockBps())));
        return credit.add(interbank).add(rate).divide(BigInteger.valueOf(10_000));
    }

    private static int ratio(long numerator, long denominator) {
        BigInteger value = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(10_000))
                .divide(BigInteger.valueOf(denominator));
        return value.max(BigInteger.valueOf(Integer.MIN_VALUE))
                .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    private static long safeAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
