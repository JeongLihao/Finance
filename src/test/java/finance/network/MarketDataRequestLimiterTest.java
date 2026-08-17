package finance.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataRequestLimiterTest {
    @AfterEach void clear() { MarketDataRequestLimiter.clear(); }

    @Test void limitsEachPlayerIndependentlyAndRecoversNextWindow() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (int i = 0; i < MarketDataRequestLimiter.MAX_REQUESTS_PER_SECOND; i++) {
            assertTrue(MarketDataRequestLimiter.allow(first, i));
        }
        assertFalse(MarketDataRequestLimiter.allow(first, 8));
        assertTrue(MarketDataRequestLimiter.allow(second, 8));
        assertTrue(MarketDataRequestLimiter.allow(first, 20));
    }

    @Test void serverTickResetStartsFreshWindow() {
        UUID player = UUID.randomUUID();
        assertTrue(MarketDataRequestLimiter.allow(player, 100));
        assertTrue(MarketDataRequestLimiter.allow(player, 0));
    }

    @Test void identicalMarketRequestIsDeduplicatedForFourTicks() {
        UUID player = UUID.randomUUID();
        assertTrue(MarketDataRequestLimiter.allow(player, 10, "STOCK:ABC:30"));
        assertFalse(MarketDataRequestLimiter.allow(player, 11, "STOCK:ABC:30"));
        assertTrue(MarketDataRequestLimiter.allow(player, 11, "STOCK:ABC:60"));
        assertTrue(MarketDataRequestLimiter.allow(player, 14, "STOCK:ABC:30"));
    }
}
