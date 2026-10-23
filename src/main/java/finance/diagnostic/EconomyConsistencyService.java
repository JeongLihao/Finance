package finance.diagnostic;

import finance.bank.*;
import finance.debt.*;
import finance.futures.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-only cross-module invariant checker. It never repairs or mutates economic state. */
public final class EconomyConsistencyService {
    public static final int MAX_ISSUES = 1_000;

    private EconomyConsistencyService() {}

    public static DiagnosticReport run(long mcDay) {
        long started = System.nanoTime();
        List<DiagnosticIssue> issues = new ArrayList<>();
        AccountMarketChecks.checkAccounts(issues);
        AccountMarketChecks.checkInventories(issues);
        AccountMarketChecks.checkOrders(issues);
        CreditChecks.checkBondsAndLoans(issues);
        WorldChecks.checkWarehouses(issues);
        WorldChecks.checkLogistics(issues);
        WorldChecks.checkSettlements(issues);
        WorldChecks.checkExploration(issues);
        CompanyChecks.checkContracts(issues);
        CompanyChecks.checkCompanyGameplay(issues, mcDay);
        CreditChecks.checkBanking(issues);
        CreditChecks.checkFutures(issues);
        CreditChecks.checkFunds(issues, mcDay);
        CreditChecks.checkInsurance(issues);
        CompanyChecks.checkGovernance(issues);
        AccountMarketChecks.checkHistory(issues);
        AccountMarketChecks.checkCycle(issues, mcDay);
        WorldChecks.checkCaravan(issues);
        NpcChecks.checkNpcEconomy(issues);
        NpcChecks.checkRivals(issues);
        for (DiagnosticIssue i : finance.macro.MacroConsistency.check())
            add(issues, i.severity(), i.module(), i.code(), i.subject(), i.message());
        if (issues.isEmpty()) add(issues, DiagnosticSeverity.INFO, "GLOBAL", "CONSISTENT", "economy", "全部检查项都通过");
        return new DiagnosticReport(
                UUID.randomUUID(),
                Instant.now(),
                Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started),
                issues);
    }

    public static DiagnosticReport runModule(ModuleHealthRegistry.Module module, long mcDay) {
        long started = System.nanoTime();
        List<DiagnosticIssue> issues = new ArrayList<>();
        if (module != null)
            switch (module) {
                case ACCOUNT -> AccountMarketChecks.checkAccounts(issues);
                case MARKET -> {
                    AccountMarketChecks.checkInventories(issues);
                    AccountMarketChecks.checkOrders(issues);
                }
                case WAREHOUSE -> WorldChecks.checkWarehouses(issues);
                case LOGISTICS -> WorldChecks.checkLogistics(issues);
                case SETTLEMENT -> WorldChecks.checkSettlements(issues);
                case EXPLORATION -> WorldChecks.checkExploration(issues);
                case CONTRACT -> CompanyChecks.checkContracts(issues);
                case COMPANY_GAMEPLAY -> CompanyChecks.checkCompanyGameplay(issues, mcDay);
                case STOCK -> {
                    AccountMarketChecks.checkInventories(issues);
                    AccountMarketChecks.checkOrders(issues);
                    CompanyChecks.checkGovernance(issues);
                }
                case DEBT -> CreditChecks.checkBondsAndLoans(issues);
                case BANKING -> CreditChecks.checkBanking(issues);
                case CARAVAN -> WorldChecks.checkCaravan(issues);
                case NPC_ECONOMY -> NpcChecks.checkNpcEconomy(issues);
                case RIVALS -> NpcChecks.checkRivals(issues);
                case MACRO_ECONOMY -> {
                    for (DiagnosticIssue i : finance.macro.MacroConsistency.check())
                        add(issues, i.severity(), i.module(), i.code(), i.subject(), i.message());
                }
                case FUTURES -> CreditChecks.checkFutures(issues);
                case FUND -> CreditChecks.checkFunds(issues, mcDay);
                case INSURANCE -> CreditChecks.checkInsurance(issues);
                case HISTORY -> AccountMarketChecks.checkHistory(issues);
                case CYCLE -> AccountMarketChecks.checkCycle(issues, mcDay);
            }
        if (issues.isEmpty())
            add(
                    issues,
                    DiagnosticSeverity.INFO,
                    module == null ? "GLOBAL" : module.name(),
                    "CONSISTENT",
                    module == null ? "economy" : module.name().toLowerCase(),
                    "该模块检查项都通过");
        return new DiagnosticReport(
                UUID.randomUUID(),
                Instant.now(),
                Math.max(-1, mcDay),
                Math.max(0, System.nanoTime() - started),
                issues);
    }

    static void add(
            List<DiagnosticIssue> out,
            DiagnosticSeverity severity,
            String module,
            String code,
            String subject,
            String message) {
        if (out.size() < MAX_ISSUES) out.add(new DiagnosticIssue(severity, module, code, subject, message));
    }
}
