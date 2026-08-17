package finance.simulation;

import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class LongRunSimulationServiceTest {
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();}
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}
    @Test void fixedSeedRunsFullYearWithRestartsAndConservesInvariants(){var result=LongRunSimulationService.run(365,7_331L);assertEquals(365,result.daily().size());assertEquals(12,result.restartCount());assertTrue(result.deterministic());assertTrue(result.healthy(),result.finalReport().summary()+" badDays="+result.daily().stream().filter(day->day.issueCount()>0).limit(10).toList());assertTrue(result.daily().stream().allMatch(day->day.issueCount()==0));}
    @Test void stressModeRunsOneThousandDaysAndRemainsDeterministic(){var result=LongRunSimulationService.run(1_000,9_001L);assertEquals(1_000,result.daily().size());assertEquals(33,result.restartCount());assertTrue(result.deterministic());assertTrue(result.healthy(),result.finalReport().summary());assertThrows(IllegalArgumentException.class,()->LongRunSimulationService.run(1_001,1));}
}
