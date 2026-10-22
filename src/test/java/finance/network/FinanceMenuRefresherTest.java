package finance.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinanceMenuRefresherTest {

    @Test
    void periodicRefreshFiresOncePerPeriodForEachPlayer() {
        UUID player = UUID.randomUUID();
        int due = 0;
        for (long tick = 0; tick < FinanceMenuRefresher.PERIOD_TICKS * 5L; tick++) {
            if (FinanceMenuRefresher.periodicDue(player, tick)) due++;
        }
        assertEquals(5, due);
    }

    @Test
    void dirtyFlagIsConsumedOnce() {
        FinanceMenuRefresher.clear();
        UUID player = UUID.randomUUID();
        assertFalse(FinanceMenuRefresher.takeDirty(player));
    }
}
