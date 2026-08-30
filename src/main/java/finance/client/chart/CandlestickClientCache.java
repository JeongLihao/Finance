package finance.client.chart;

import finance.chart.Candlestick;
import finance.chart.MarketInstrumentKey;
import finance.chart.MarketInstrumentType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import finance.marketdata.OrderBookSnapshot;
import finance.marketdata.RecentTradeEntry;
import finance.marketdata.MarketRankingSnapshot;

public final class CandlestickClientCache {
    public enum State { NOT_REQUESTED, LOADING, READY, EMPTY, SLOW }

    public record CacheKey(MarketInstrumentType type, String id, int limit) {}
    public record Entry(State state, List<Candlestick> bars, long serverCurrentMcDay,
                        boolean latestBarComplete, long requestedAtMillis,
                        OrderBookSnapshot orderBook, List<RecentTradeEntry> recentTrades,
                        MarketRankingSnapshot rankings) {}

    private static final long SLOW_AFTER_MILLIS = 3_000L;
    private static final Map<CacheKey, Entry> CACHE = new HashMap<>();
    private static final Map<MarketInstrumentKey, Long> LATEST_REQUESTS = new HashMap<>();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final MarketRankingSnapshot EMPTY_RANKINGS = new MarketRankingSnapshot(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private static final Entry EMPTY_ENTRY = new Entry(State.NOT_REQUESTED, List.of(), 0, false, 0,
            new OrderBookSnapshot(List.of(), List.of()), List.of(), EMPTY_RANKINGS);

    private CandlestickClientCache() {}

    public static long nextRequestId() {
        return REQUEST_SEQUENCE.updateAndGet(value -> value == Long.MAX_VALUE ? 1 : value + 1);
    }

    public static boolean begin(long requestId, MarketInstrumentType type, String id, int limit) {
        return begin(requestId, type, id, limit, System.currentTimeMillis());
    }

    static boolean begin(long requestId, MarketInstrumentType type, String id, int limit, long nowMillis) {
        MarketInstrumentKey instrument = MarketInstrumentKey.tryCreate(type, id);
        if (requestId <= 0 || instrument == null || limit <= 0) return false;
        CacheKey key = new CacheKey(type, instrument.id(), limit);
        LATEST_REQUESTS.put(instrument, requestId);
        CACHE.put(key, new Entry(State.LOADING, List.of(), 0, false, nowMillis,
                new OrderBookSnapshot(List.of(), List.of()), List.of(), emptyRankings()));
        return true;
    }

    public static boolean accept(long requestId, MarketInstrumentType type, String id, int limit,
                                 long serverCurrentMcDay, boolean latestBarComplete, List<Candlestick> bars) {
        return accept(requestId, type, id, limit, serverCurrentMcDay, latestBarComplete, bars,
                new OrderBookSnapshot(List.of(), List.of()), List.of(), emptyRankings());
    }

    public static boolean accept(long requestId, MarketInstrumentType type, String id, int limit,
                                 long serverCurrentMcDay, boolean latestBarComplete, List<Candlestick> bars,
                                 OrderBookSnapshot orderBook, List<RecentTradeEntry> recentTrades,
                                 MarketRankingSnapshot rankings) {
        MarketInstrumentKey instrument = MarketInstrumentKey.tryCreate(type, id);
        if (instrument == null || !Long.valueOf(requestId).equals(LATEST_REQUESTS.get(instrument))) return false;
        List<Candlestick> safeBars = bars == null ? List.of() : List.copyOf(bars);
        CacheKey key = new CacheKey(type, instrument.id(), limit);
        CACHE.put(key, new Entry(safeBars.isEmpty() ? State.EMPTY : State.READY, safeBars,
                Math.max(0, serverCurrentMcDay), latestBarComplete, 0,
                orderBook == null ? new OrderBookSnapshot(List.of(), List.of()) : orderBook,
                recentTrades == null ? List.of() : List.copyOf(recentTrades),
                rankings == null ? emptyRankings() : rankings));
        return true;
    }

    public static Entry get(MarketInstrumentType type, String id, int limit) {
        return get(type, id, limit, System.currentTimeMillis());
    }

    static Entry get(MarketInstrumentType type, String id, int limit, long nowMillis) {
        MarketInstrumentKey instrument = MarketInstrumentKey.tryCreate(type, id);
        if (instrument == null) return emptyEntry();
        CacheKey key = new CacheKey(type, instrument.id(), limit);
        Entry entry = CACHE.get(key);
        if (entry == null) return emptyEntry();
        if (entry.state == State.LOADING && nowMillis - entry.requestedAtMillis >= SLOW_AFTER_MILLIS) {
            entry = new Entry(State.SLOW, entry.bars, entry.serverCurrentMcDay,
                    entry.latestBarComplete, entry.requestedAtMillis, entry.orderBook, entry.recentTrades,
                    entry.rankings);
            CACHE.put(key, entry);
        }
        return entry;
    }

    public static void clear() {
        CACHE.clear();
        LATEST_REQUESTS.clear();
    }

    private static Entry emptyEntry() {
        return EMPTY_ENTRY;
    }

    private static MarketRankingSnapshot emptyRankings() {
        return EMPTY_RANKINGS;
    }
}
