package finance.chart;

import finance.data.EconomySavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CandlestickService {

    private static final Map<MarketInstrumentKey, CandlestickSeries> SERIES = new LinkedHashMap<>();
    private static long currentMcDay;

    private CandlestickService() {
    }

    public static void observeDay(long mcDay) {
        if (mcDay >= 0) currentMcDay = mcDay;
    }

    public static long currentMcDay() { return currentMcDay; }

    public static boolean recordTrade(MarketInstrumentType type, String id, long price, long quantity) {
        return recordTrade(type, id, currentMcDay, price, quantity);
    }

    public static boolean recordTrade(MarketInstrumentType type, String id, long mcDay,
                                      long price, long quantity) {
        MarketInstrumentKey key = MarketInstrumentKey.tryCreate(type, id);
        if (key == null || mcDay < 0 || price <= 0 || quantity <= 0) return false;
        boolean changed = SERIES.computeIfAbsent(key, ignored -> new CandlestickSeries())
                .recordTrade(mcDay, price, quantity);
        if (changed) EconomySavedData.markDirty();
        return changed;
    }

    public static void closeDay(long completedMcDay) {
        if (completedMcDay < 0) return;
        for (CandlestickSeries series : SERIES.values()) series.fillThrough(completedMcDay);
        EconomySavedData.markDirty();
    }

    public static List<Candlestick> getBars(MarketInstrumentType type, String id, int limit) {
        MarketInstrumentKey key = MarketInstrumentKey.tryCreate(type, id);
        CandlestickSeries series = key == null ? null : SERIES.get(key);
        return series == null ? List.of() : series.getBars(limit);
    }

    public static Map<MarketInstrumentKey, CandlestickSeries> getSeriesDirect() {
        return Collections.unmodifiableMap(SERIES);
    }

    public static void putSeriesDirect(MarketInstrumentKey key, CandlestickSeries series) {
        if (key != null && series != null && !series.getBars(CandlestickSeries.MAX_BARS).isEmpty()) {
            SERIES.put(key, series);
        }
    }

    public static void clearDirect() {
        SERIES.clear();
        currentMcDay = 0;
    }
}
