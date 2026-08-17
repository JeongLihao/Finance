package finance.marketdata;

import finance.chart.Candlestick;
import finance.chart.CandlestickSeries;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class MarketRankingService {
    public static final int DEFAULT_LIMIT = 5;
    private MarketRankingService() {}

    public static MarketRankingSnapshot current() {
        return rank(CandlestickService.getSeriesDirect(), DEFAULT_LIMIT);
    }

    public static MarketRankingSnapshot rank(Map<MarketInstrumentKey, CandlestickSeries> series, int limit) {
        int safeLimit = Math.max(0, Math.min(20, limit));
        List<MarketRankingEntry> entries = new ArrayList<>();
        if (series != null) for (Map.Entry<MarketInstrumentKey, CandlestickSeries> item : series.entrySet()) {
            List<Candlestick> bars = item.getValue().getBars(6);
            if (bars.size() < 2) continue;
            Candlestick latest = bars.get(bars.size() - 1);
            long previousClose = bars.size() > 1 ? bars.get(bars.size() - 2).close() : latest.open();
            double change = previousClose > 0 ? (double) (latest.close() - previousClose) / previousClose * 100 : 0;
            int previousCount = Math.min(5, bars.size() - 1);
            double average = 0;
            for (int i = bars.size() - 1 - previousCount; i < bars.size() - 1; i++) average += bars.get(i).volume();
            if (previousCount > 0) average /= previousCount;
            double ratio = average > 0 ? latest.volume() / average : 0;
            entries.add(new MarketRankingEntry(item.getKey().type(), item.getKey().id(), change,
                    latest.volume(), ratio));
        }
        return new MarketRankingSnapshot(
                changes(entries, finance.chart.MarketInstrumentType.COMMODITY, true, safeLimit),
                changes(entries, finance.chart.MarketInstrumentType.COMMODITY, false, safeLimit),
                changes(entries, finance.chart.MarketInstrumentType.STOCK, true, safeLimit),
                changes(entries, finance.chart.MarketInstrumentType.STOCK, false, safeLimit),
                volumes(entries, finance.chart.MarketInstrumentType.COMMODITY, safeLimit),
                volumes(entries, finance.chart.MarketInstrumentType.STOCK, safeLimit),
                sorted(entries.stream().filter(entry -> entry.volumeRatio() > 0).toList(),
                        Comparator.comparingDouble(MarketRankingEntry::volumeRatio).reversed(), safeLimit));
    }

    private static List<MarketRankingEntry> changes(List<MarketRankingEntry> entries,
                                                     finance.chart.MarketInstrumentType type,
                                                     boolean gainers, int limit) {
        Comparator<MarketRankingEntry> comparator = Comparator.comparingDouble(MarketRankingEntry::changePercent);
        if (gainers) comparator = comparator.reversed();
        return sorted(entries.stream().filter(entry -> entry.type() == type).toList(), comparator, limit);
    }

    private static List<MarketRankingEntry> volumes(List<MarketRankingEntry> entries,
                                                     finance.chart.MarketInstrumentType type, int limit) {
        return sorted(entries.stream().filter(entry -> entry.type() == type && entry.volume() > 0).toList(),
                Comparator.comparingLong(MarketRankingEntry::volume).reversed(), limit);
    }

    private static List<MarketRankingEntry> sorted(List<MarketRankingEntry> entries,
                                                   Comparator<MarketRankingEntry> comparator, int limit) {
        return entries.stream().sorted(comparator.thenComparing(MarketRankingEntry::id)).limit(limit).toList();
    }
}
