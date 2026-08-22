package finance.simulation;

import finance.data.EconomySavedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameplayLongRunSimulationServiceTest {
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void warehouseAndContractsSurviveAFullYearOfRestartsWithoutMintingMoney() {
        var result = GameplayLongRunSimulationService.run(365, 20_260_820L);
        assertEquals(365, result.days());
        assertEquals(12, result.restarts());
        assertTrue(result.completedContracts() > 40);
        assertTrue(result.moneyConserved());
        assertTrue(result.referencesRecovered());
    }
}
