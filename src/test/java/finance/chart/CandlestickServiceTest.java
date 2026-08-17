package finance.chart;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandlestickServiceTest {

    @AfterEach
    void clear() {
        CandlestickService.clearDirect();
    }

    @Test
    void aggregatesTradesIntoDailyOhlcvWithoutDuplicates() {
        assertTrue(CandlestickService.recordTrade(MarketInstrumentType.COMMODITY, "iron", 3, 10, 2));
        assertTrue(CandlestickService.recordTrade(MarketInstrumentType.COMMODITY, "iron", 3, 15, 4));
        assertTrue(CandlestickService.recordTrade(MarketInstrumentType.COMMODITY, "iron", 3, 8, 1));

        Candlestick bar = CandlestickService.getBars(MarketInstrumentType.COMMODITY, "iron", 30).get(0);
        assertEquals(10, bar.open());
        assertEquals(15, bar.high());
        assertEquals(8, bar.low());
        assertEquals(8, bar.close());
        assertEquals(7, bar.volume());
    }

    @Test
    void carriesPreviousCloseAcrossNoTradeDaysButNeverInventsInitialPrice() {
        CandlestickService.closeDay(4);
        assertTrue(CandlestickService.getBars(MarketInstrumentType.STOCK, "ABC", 30).isEmpty());

        CandlestickService.recordTrade(MarketInstrumentType.STOCK, "abc", 2, 20, 3);
        CandlestickService.closeDay(4);
        List<Candlestick> bars = CandlestickService.getBars(MarketInstrumentType.STOCK, "ABC", 30);

        assertEquals(3, bars.size());
        assertEquals(2, bars.get(0).mcDay());
        assertEquals(0, bars.get(1).volume());
        assertEquals(20, bars.get(2).close());
    }

    @Test
    void keepsLatestOneHundredTwentyBarsAndRejectsOldTrades() {
        CandlestickService.recordTrade(MarketInstrumentType.STOCK, "ABC", 0, 10, 1);
        CandlestickService.closeDay(200);

        List<Candlestick> bars = CandlestickService.getBars(MarketInstrumentType.STOCK, "ABC", 200);
        assertEquals(120, bars.size());
        assertEquals(81, bars.get(0).mcDay());
        assertEquals(200, bars.get(119).mcDay());
        assertFalse(CandlestickService.recordTrade(MarketInstrumentType.STOCK, "ABC", 50, 12, 1));
    }

    @Test
    void volumeSaturatesAtLongMaximum() {
        CandlestickService.recordTrade(MarketInstrumentType.STOCK, "ABC", 1, 10, Long.MAX_VALUE);
        CandlestickService.recordTrade(MarketInstrumentType.STOCK, "ABC", 1, 11, 2);

        assertEquals(Long.MAX_VALUE,
                CandlestickService.getBars(MarketInstrumentType.STOCK, "ABC", 1).get(0).volume());
    }
}
