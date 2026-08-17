package finance.client.chart;

import finance.chart.Candlestick;
import finance.chart.MarketInstrumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandlestickClientCacheTest {
    @AfterEach void clear() { CandlestickClientCache.clear(); }

    @Test void delayedOldResponseCannotOverwriteNewWindow() {
        assertTrue(CandlestickClientCache.begin(1, MarketInstrumentType.STOCK, "ABC", 30, 100));
        assertTrue(CandlestickClientCache.begin(2, MarketInstrumentType.STOCK, "ABC", 120, 200));
        assertFalse(CandlestickClientCache.accept(1, MarketInstrumentType.STOCK, "ABC", 30,
                5, true, List.of(Candlestick.carry(5, 10))));
        assertTrue(CandlestickClientCache.accept(2, MarketInstrumentType.STOCK, "ABC", 120,
                6, false, List.of(Candlestick.carry(6, 20))));
        assertEquals(20, CandlestickClientCache.get(MarketInstrumentType.STOCK, "ABC", 120, 300)
                .bars().get(0).close());
        assertEquals(CandlestickClientCache.State.LOADING,
                CandlestickClientCache.get(MarketInstrumentType.STOCK, "ABC", 30, 300).state());
    }

    @Test void loadingBecomesSlowWithoutErasingState() {
        CandlestickClientCache.begin(3, MarketInstrumentType.COMMODITY, "iron", 60, 1_000);
        assertEquals(CandlestickClientCache.State.LOADING,
                CandlestickClientCache.get(MarketInstrumentType.COMMODITY, "iron", 60, 3_999).state());
        assertEquals(CandlestickClientCache.State.SLOW,
                CandlestickClientCache.get(MarketInstrumentType.COMMODITY, "iron", 60, 4_000).state());
    }

    @Test void emptyResponseIsDistinctFromNotRequested() {
        CandlestickClientCache.begin(4, MarketInstrumentType.STOCK, "EMPTY", 30, 0);
        CandlestickClientCache.accept(4, MarketInstrumentType.STOCK, "EMPTY", 30, 10, true, List.of());
        assertEquals(CandlestickClientCache.State.EMPTY,
                CandlestickClientCache.get(MarketInstrumentType.STOCK, "EMPTY", 30, 1).state());
        assertEquals(CandlestickClientCache.State.NOT_REQUESTED,
                CandlestickClientCache.get(MarketInstrumentType.STOCK, "OTHER", 30, 1).state());
    }
}
