package finance.chart;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketSummaryTest {
    @Test void summaryUsesRequestedWindowExtremesAndMarksCurrentDayVolumeSpike() {
        List<Candlestick> bars = List.of(
                new Candlestick(1, 100, 110, 90, 105, 10),
                new Candlestick(2, 105, 108, 95, 100, 10),
                new Candlestick(3, 100, 125, 99, 120, 50));
        MarketSummary summary = MarketSummary.from(bars, 3);
        assertEquals(125, summary.high());
        assertEquals(90, summary.low());
        assertEquals(70, summary.volume());
        assertEquals(20, summary.change());
        assertTrue(summary.volumeSpike());
    }
}
