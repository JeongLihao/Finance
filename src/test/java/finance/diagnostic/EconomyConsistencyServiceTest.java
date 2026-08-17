package finance.diagnostic;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.futures.FuturesPosition;
import finance.futures.MarginManager;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyConsistencyServiceTest {
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();}
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}

    @Test void cleanMinimalEconomyProducesStructuredHealthyReport(){AccountManager.deposit(UUID.randomUUID(),100);DiagnosticReport report=EconomyConsistencyService.run(0);assertTrue(report.healthy());assertEquals(1,report.count(DiagnosticSeverity.INFO));assertTrue(report.durationNanos()>=0);}
    @Test void unmatchedFuturesPositionIsFatalAndCheckerNeverRepairsIt(){UUID owner=UUID.randomUUID(),contract=UUID.randomUUID();MarginManager.putPositionDirect(new FuturesPosition(owner,contract,1,100,100,0));DiagnosticReport report=EconomyConsistencyService.run(0);assertTrue(report.count(DiagnosticSeverity.ERROR)>0||report.count(DiagnosticSeverity.FATAL)>0);assertNotNull(MarginManager.findPosition(owner,contract));}
    @Test void diagnosticReportsAndPausedModulesRoundTrip(){DiagnosticReport report=DiagnosticManager.runFull(3);ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.DEBT,ModuleRunState.PAUSED,"test",3);CompoundTag saved=new EconomySavedData().save(new CompoundTag());EconomySavedData.resetRuntimeState();EconomySavedData.load(saved);assertEquals(report.reportId(),DiagnosticManager.latest().reportId());assertEquals(ModuleRunState.PAUSED,ModuleHealthRegistry.status(ModuleHealthRegistry.Module.DEBT).state());}
    @Test void startupCheckRunsOneModulePerTickAndCompletes(){StartupSelfCheckService.schedule(0);int ticks=0;while(StartupSelfCheckService.pending()&&ticks++<20)StartupSelfCheckService.tick();assertFalse(StartupSelfCheckService.pending());assertNotNull(DiagnosticManager.latest());assertTrue(ticks>1);}
}
