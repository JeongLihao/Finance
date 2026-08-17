package finance.policy;

import finance.data.EconomySavedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonetaryPolicyServiceTest {
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void rateChangeIsBoundedAndOnlyOneChangePerDayIsAllowed() {
        int initial = MonetaryPolicyService.benchmarkRateBasisPoints();
        assertTrue(MonetaryPolicyService.setBenchmarkRate(1, initial + 25, "test"));
        assertFalse(MonetaryPolicyService.setBenchmarkRate(1, initial + 50, "second"));
        assertFalse(MonetaryPolicyService.setBenchmarkRate(2, -1, "bad"));
        assertEquals(initial + 25, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1, MonetaryPolicyService.history().size());
    }
}
