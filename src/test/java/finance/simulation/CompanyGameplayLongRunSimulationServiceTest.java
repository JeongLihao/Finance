package finance.simulation;

import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class CompanyGameplayLongRunSimulationServiceTest {
    @BeforeEach void setup(){ EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup(){ EconomySavedData.resetRuntimeState(); }

    @Test void hybridCompanySurvivesAFullYearWithoutSameDayDoubleProduction(){
        var result=CompanyGameplayLongRunSimulationService.run(365,20_260_820L);
        assertEquals(12,result.restarts());
        assertEquals(365,result.producedDays());
        assertTrue(result.noDoubleProduction());
        assertTrue(result.nonNegativeAssets());
        assertTrue(result.referencesRecovered());
    }
}
