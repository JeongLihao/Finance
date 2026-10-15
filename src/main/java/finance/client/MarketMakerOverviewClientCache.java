package finance.client;

import finance.network.MarketMakerOverviewResponsePacket;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 做市商总览快照的客户端缓存（Phase 4）。
 * follow {@link FuturesClientCache} 的请求/响应模式。
 */
public final class MarketMakerOverviewClientCache {
    public enum State {NOT_REQUESTED, LOADING, READY, SLOW}

    public record Entry(State state, long requestId, long requestedAt,
                        MarketMakerOverviewResponsePacket data) {
    }

    private static final AtomicLong SEQ = new AtomicLong();
    private static Entry entry = new Entry(State.NOT_REQUESTED, 0, 0, null);

    private MarketMakerOverviewClientCache() {
    }

    public static long begin() {
        long id = SEQ.updateAndGet(v -> v == Long.MAX_VALUE ? 1 : v + 1);
        entry = new Entry(State.LOADING, id, System.currentTimeMillis(), null);
        return id;
    }

    public static void accept(MarketMakerOverviewResponsePacket p) {
        if (p != null && p.requestId() == entry.requestId()) {
            entry = new Entry(State.READY, p.requestId(), 0, p);
        }
    }

    public static Entry get() {
        if (entry.state() == State.LOADING
                && System.currentTimeMillis() - entry.requestedAt() > 3000) {
            entry = new Entry(State.SLOW, entry.requestId(), entry.requestedAt(), null);
        }
        return entry;
    }

    public static void clear() {
        entry = new Entry(State.NOT_REQUESTED, 0, 0, null);
    }
}
