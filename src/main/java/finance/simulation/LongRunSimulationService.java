package finance.simulation;

import finance.account.AccountManager;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.cycle.FinancialCycleService;
import finance.data.EconomySavedData;
import finance.diagnostic.DiagnosticReport;
import finance.diagnostic.DiagnosticSeverity;
import finance.diagnostic.EconomyConsistencyService;
import finance.fund.FundManager;
import finance.fund.FundType;
import finance.metrics.EconomyMetricsService;
import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Deterministic headless stability simulation using production managers and save/load paths. */
public final class LongRunSimulationService {
    public static final int STANDARD_DAYS = 365;
    public static final int STRESS_DAYS = 1_000;
    public static final int MAX_DAILY = 1_000;
    private LongRunSimulationService() { }

    public record Daily(long day, long liquidMoney, long bankDeposits, long bankAssets,
                        int issueCount, long durationNanos) { }
    public record Result(long seed, int days, List<Daily> daily, DiagnosticReport finalReport,
                         long restartCount, long durationNanos, boolean deterministic) {
        public boolean healthy() { return finalReport.healthy() && daily.stream().noneMatch(d -> d.issueCount() > 0); }
    }

    public static synchronized Result run(int days, long seed) {
        if (days < 1 || days > STRESS_DAYS) throw new IllegalArgumentException("days");
        CompoundTag original = new EconomySavedData().save(new CompoundTag());
        long started = System.nanoTime();
        try {
            Result first = runOnce(days, seed);
            Result second = runOnce(days, seed);
            boolean same = fingerprint(first).equals(fingerprint(second));
            return new Result(seed, days, first.daily(), first.finalReport(), first.restartCount(),
                    System.nanoTime() - started, same);
        } finally {
            EconomySavedData.resetRuntimeState();
            EconomySavedData.load(original);
        }
    }

    private static Result runOnce(int days, long seed) {
        EconomySavedData.resetRuntimeState();
        Random random = new Random(seed);
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID id = UUID.nameUUIDFromBytes(("phase7-player-" + seed + "-" + i).getBytes(StandardCharsets.UTF_8));
            players.add(id);
            AccountManager.deposit(id, 10_000L * (i + 1));
        }
        UUID owner = players.get(0);
        UUID companyId = UUID.nameUUIDFromBytes(("phase7-company-" + seed).getBytes(StandardCharsets.UTF_8));
        CompanyManager.registerDirect(new Company(companyId, "SimulationCo", CompanyType.RAW_MATERIALS, 500_000, owner));
        BankingManager.ensureDefaultBanks();
        FundManager.seedDefaultsIfNeeded();
        FundManager.acknowledgeRisk(players.get(7), FundType.MONEY_MARKET);
        FundManager.subscribe(players.get(7), "money-short", 1_000, 0, "simulation-initial");

        List<CommercialBank> banks = BankingManager.banks().values().stream().toList();
        List<Daily> daily = new ArrayList<>();
        long restarts = 0, started = System.nanoTime();
        for (int day = 0; day < days; day++) {
            long tick = System.nanoTime();
            for (int i = 0; i < players.size(); i++) {
                UUID player = players.get(i);
                CommercialBank bank = banks.get((i + day) % banks.size());
                if (random.nextInt(4) == 0) BankingManager.depositPlayer(player, bank.id(),
                        Math.min(100, Math.max(1, AccountManager.getBalance(player) / 20)), day);
                var account = BankingManager.demandAccount(bank.id(), player, finance.bank.CustomerType.PLAYER, day, false);
                if (account != null && account.available() > 20 && random.nextInt(7) == 0)
                    BankingManager.withdrawPlayer(player, account.id(), 10, day);
            }
            FinancialCycleService.advanceTo(day);
            if (day > 0) FinancialCycleService.closeMarketDay(day - 1);
            if (day > 0 && day % 30 == 0) {
                CompoundTag saved = new EconomySavedData().save(new CompoundTag());
                EconomySavedData.resetRuntimeState();
                EconomySavedData.load(saved);
                banks = BankingManager.banks().values().stream().toList();
                restarts++;
            }
            DiagnosticReport report = EconomyConsistencyService.run(day);
            var money = EconomyMetricsService.getCurrentMetrics();
            long deposits = BankingManager.banks().values().stream().map(CommercialBank::ledger)
                    .map(finance.bank.BankLedger::balanceSheet)
                    .mapToLong(s -> safeAdd(s.demandDeposits(), s.timeDeposits()))
                    .reduce(0, LongRunSimulationService::safeAdd);
            long assets = BankingManager.banks().values().stream().map(CommercialBank::ledger)
                    .map(finance.bank.BankLedger::balanceSheet)
                    .mapToLong(finance.bank.BankBalanceSheet::totalAssets)
                    .reduce(0, LongRunSimulationService::safeAdd);
            long issueTotal = report.count(DiagnosticSeverity.ERROR) + report.count(DiagnosticSeverity.FATAL);
            int failures = (int) Math.min(Integer.MAX_VALUE, issueTotal);
            daily.add(new Daily(day, money.totalMoney(), deposits, assets, failures, System.nanoTime() - tick));
        }
        DiagnosticReport finalReport = EconomyConsistencyService.run(days - 1);
        return new Result(seed, days, List.copyOf(daily), finalReport, restarts,
                System.nanoTime() - started, true);
    }

    private static String fingerprint(Result result) {
        Daily d = result.daily().get(result.daily().size() - 1);
        return d.day() + ":" + d.liquidMoney() + ":" + d.bankDeposits() + ":" + d.bankAssets()
                + ":" + result.restartCount() + ":" + result.finalReport().counts();
    }
    private static long safeAdd(long a, long b) {
        return BigInteger.valueOf(a).add(BigInteger.valueOf(b)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}
