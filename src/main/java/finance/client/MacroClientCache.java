package finance.client;

import finance.network.MacroResponsePacket;

import java.util.concurrent.atomic.AtomicLong;

/** 宏观经济页签的客户端快照缓存（沿用 RegionalRiskClientCache 的请求/超时模式）。 */
public final class MacroClientCache {
    public enum State { NOT_REQUESTED, LOADING, READY, SLOW }

    public record Entry(State state, long requestId, long at, MacroResponsePacket data) { }

    private static final AtomicLong SEQ = new AtomicLong();
    private static Entry entry = new Entry(State.NOT_REQUESTED, 0, 0, null);

    private MacroClientCache() { }

    public static long begin() {
        long id = SEQ.updateAndGet(v -> v == Long.MAX_VALUE ? 1 : v + 1);
        MacroResponsePacket previous = entry.data();
        entry = new Entry(State.LOADING, id, System.currentTimeMillis(), previous);
        return id;
    }

    public static void accept(MacroResponsePacket p) {
        if (p != null && p.requestId() == entry.requestId()) entry = new Entry(State.READY, p.requestId(), 0, p);
    }

    public static Entry get() {
        if (entry.state() == State.LOADING && System.currentTimeMillis() - entry.at() > 3000) {
            entry = new Entry(State.SLOW, entry.requestId(), entry.at(), entry.data());
        }
        return entry;
    }

    public static void clear() {
        entry = new Entry(State.NOT_REQUESTED, 0, 0, null);
    }
}
