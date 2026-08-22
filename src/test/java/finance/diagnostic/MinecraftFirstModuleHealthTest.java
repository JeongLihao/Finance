package finance.diagnostic;

import finance.contract.*;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftFirstModuleHealthTest {
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void fatalContractInvariantPausesOnlyContractModuleDuringIncrementalStartupAudit() {
        FinanceContract broken = new FinanceContract(UUID.randomUUID(), ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, UUID.randomUUID(), "iron", 1, 0, 100,
                UUID.randomUUID(), null, 0, 3, null, ContractStatus.OPEN, "");
        assertTrue(ContractManager.restore(broken));

        DiagnosticReport contractReport = EconomyConsistencyService.runModule(ModuleHealthRegistry.Module.CONTRACT, 1);
        assertTrue(contractReport.issues().stream().anyMatch(issue -> issue.severity() == DiagnosticSeverity.FATAL
                && issue.module().equals("CONTRACT") && issue.code().equals("CONTRACT_ESCROW_MISMATCH")));

        StartupSelfCheckService.schedule(1);
        while (StartupSelfCheckService.tick()) { /* one bounded module per call */ }
        assertEquals(ModuleRunState.PAUSED,
                ModuleHealthRegistry.status(ModuleHealthRegistry.Module.CONTRACT).state());
        assertEquals(ModuleRunState.ACTIVE,
                ModuleHealthRegistry.status(ModuleHealthRegistry.Module.MARKET).state());
    }
}
