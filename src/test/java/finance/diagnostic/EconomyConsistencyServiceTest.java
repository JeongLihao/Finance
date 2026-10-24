package finance.diagnostic;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.futures.FuturesPosition;
import finance.futures.MarginManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

class EconomyConsistencyServiceTest {
    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void cleanMinimalEconomyProducesStructuredHealthyReport() {
        AccountManager.deposit(UUID.randomUUID(), 100);
        DiagnosticReport report = EconomyConsistencyService.run(0);
        assertTrue(report.healthy());
        assertEquals(1, report.count(DiagnosticSeverity.INFO));
        assertTrue(report.durationNanos() >= 0);
    }

    @Test
    void fullRunAlsoCoversRivalsCaravanNpcAndMacroChecks() {
        UUID owner = UUID.randomUUID();
        finance.company.Company company = new finance.company.Company(
                UUID.randomUUID(), "Player Co", finance.company.CompanyType.FOOD, 1_000, owner);
        finance.company.CompanyManager.registerDirect(company);
        finance.rival.RivalManager.restoreDirect(
                0,
                0,
                0,
                0,
                java.util.List.of(new finance.rival.RivalProfile(
                        company.getCompanyId(), finance.rival.RivalPersonality.AGGRESSIVE, "bread", 0)));
        DiagnosticReport report = EconomyConsistencyService.run(0);
        assertTrue(
                report.issues().stream().anyMatch(i -> "PROFILE_ON_PLAYER_COMPANY".equals(i.code())),
                "完整体检要跑到竞争公司检查，和按模块检查一致");
    }

    @Test
    void unmatchedFuturesPositionIsFatalAndCheckerNeverRepairsIt() {
        UUID owner = UUID.randomUUID(), contract = UUID.randomUUID();
        MarginManager.putPositionDirect(new FuturesPosition(owner, contract, 1, 100, 100, 0));
        DiagnosticReport report = EconomyConsistencyService.run(0);
        assertTrue(report.count(DiagnosticSeverity.ERROR) > 0 || report.count(DiagnosticSeverity.FATAL) > 0);
        assertNotNull(MarginManager.findPosition(owner, contract));
    }

    @Test
    void diagnosticReportsAndPausedModulesRoundTrip() {
        DiagnosticReport report = DiagnosticManager.runFull(3);
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.DEBT, ModuleRunState.PAUSED, "test", 3);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);
        assertEquals(report.reportId(), DiagnosticManager.latest().reportId());
        assertEquals(
                ModuleRunState.PAUSED,
                ModuleHealthRegistry.status(ModuleHealthRegistry.Module.DEBT).state());
    }

    @Test
    void startupCheckRunsOneModulePerTickAndCompletes() {
        StartupSelfCheckService.schedule(0);
        int ticks = 0;
        int bound = ModuleHealthRegistry.Module.values().length + 5;
        while (StartupSelfCheckService.pending() && ticks++ < bound) StartupSelfCheckService.tick();
        assertFalse(StartupSelfCheckService.pending());
        assertNotNull(DiagnosticManager.latest());
        assertTrue(ticks > 1);
    }
}
