package finance.marketdata;

import finance.chart.MarketInstrumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecentTradeServiceTest {
    @AfterEach void clear() { RecentTradeService.clear(); }

    @Test void keepsTwentyNewestTradesInNewestFirstOrder() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 1; i <= 25; i++) {
            assertTrue(RecentTradeService.record(MarketInstrumentType.STOCK, "ABC", i, i,
                    base.plusSeconds(i), i % 2 == 0 ? TradeDirection.BUY : TradeDirection.SELL));
        }
        var trades = RecentTradeService.get(MarketInstrumentType.STOCK, "ABC");
        assertEquals(20, trades.size());
        assertEquals(25, trades.get(0).price());
        assertEquals(6, trades.get(19).price());
        assertEquals(TradeDirection.SELL, trades.get(0).direction());
    }

    @Test void instrumentsRemainIsolated() {
        RecentTradeService.record(MarketInstrumentType.COMMODITY, "iron", 10, 1, TradeDirection.BUY);
        assertTrue(RecentTradeService.get(MarketInstrumentType.STOCK, "IRON").isEmpty());
        assertEquals(1, RecentTradeService.get(MarketInstrumentType.COMMODITY, "iron").size());
    }
}
