package finance.index;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MarketIndexStateTest {
    @Test void startsAtOneThousandAndTracksPriceMovement() {
        MarketIndexState state = new MarketIndexState("test");
        assertTrue(state.close(0, BigDecimal.valueOf(5000), "A:10;"));
        assertEquals(1000, state.latest().value(), 0.00001);
        assertTrue(state.close(1, BigDecimal.valueOf(5500), "A:10;"));
        assertEquals(1100, state.latest().value(), 0.00001);
    }

    @Test void constituentAndShareChangesAdjustDivisorWithoutArtificialJump() {
        MarketIndexState state = new MarketIndexState("test");
        state.close(0, BigDecimal.valueOf(5000), "A:10;");
        state.close(1, BigDecimal.valueOf(9000), "A:10;B:10;");
        assertEquals(1000, state.latest().value(), 0.00001);
        state.close(2, BigDecimal.valueOf(18000), "A:20;B:20;");
        assertEquals(1000, state.latest().value(), 0.00001);
    }

    @Test void invalidAndDuplicateDaysAreRejectedAndHistoryIsBounded() {
        MarketIndexState state = new MarketIndexState("test");
        assertFalse(state.close(0, BigDecimal.ZERO, "A"));
        for (int day=0; day<150; day++) state.close(day, BigDecimal.valueOf(100+day), "A");
        assertEquals(MarketIndexState.MAX_HISTORY, state.history().size());
        assertFalse(state.close(149, BigDecimal.TEN, "A"));
    }
}
