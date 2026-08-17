package finance.marketdata;

import finance.chart.CandlestickSeries;
import finance.chart.MarketInstrumentKey;
import finance.chart.MarketInstrumentType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketRankingServiceTest {
    @Test void buildsFourStableTopListsFromLatestBars() {
        Map<MarketInstrumentKey, CandlestickSeries> values = new LinkedHashMap<>();
        values.put(new MarketInstrumentKey(MarketInstrumentType.STOCK, "UP"), series(100, 120, 10, 40));
        values.put(new MarketInstrumentKey(MarketInstrumentType.STOCK, "DOWN"), series(100, 80, 20, 10));
        values.put(new MarketInstrumentKey(MarketInstrumentType.COMMODITY, "flat"), series(100, 100, 5, 100));

        MarketRankingSnapshot result = MarketRankingService.rank(values, 2);
        assertEquals("flat", result.commodityGainers().get(0).id());
        assertEquals("UP", result.stockGainers().get(0).id());
        assertEquals("DOWN", result.stockLosers().get(0).id());
        assertEquals("flat", result.commodityVolumeLeaders().get(0).id());
        assertEquals("flat", result.unusualVolume().get(0).id());
        assertEquals(2, result.stockGainers().size());
    }

    private static CandlestickSeries series(long firstPrice, long secondPrice, long firstVolume, long secondVolume) {
        CandlestickSeries result = new CandlestickSeries();
        result.recordTrade(1, firstPrice, firstVolume);
        result.recordTrade(2, secondPrice, secondVolume);
        return result;
    }
}
