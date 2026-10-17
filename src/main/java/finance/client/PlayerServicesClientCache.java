package finance.client;

import finance.network.PlayerServicesResponsePacket;

import java.util.concurrent.atomic.AtomicLong;

/** “更多操作”界面的客户端快照缓存（沿用 MacroClientCache 的请求/超时模式；requestId &lt; 0 是动作后的推送，总是接受）。 */
public final class PlayerServicesClientCache {
    public enum State { NOT_REQUESTED, LOADING, READY, SLOW }

    public record Entry(State state, long requestId, long at, PlayerServicesResponsePacket data) { }

    private static final AtomicLong SEQ = new AtomicLong();
    private static Entry entry = new Entry(State.NOT_REQUESTED, 0, 0, null);

    private PlayerServicesClientCache() { }

    public static synchronized long begin() {
        long id = SEQ.updateAndGet(v -> v == Long.MAX_VALUE ? 1 : v + 1);
        entry = new Entry(State.LOADING, id, System.currentTimeMillis(), entry.data());
        return id;
    }

    public static synchronized void accept(PlayerServicesResponsePacket p) {
        if (p == null) return;
        if (p.requestId() < 0 || p.requestId() == entry.requestId()) {
            entry = new Entry(State.READY, entry.requestId(), 0, p);
        }
    }

    public static synchronized Entry get() {
        if (entry.state() == State.LOADING && System.currentTimeMillis() - entry.at() > 3000) {
            entry = new Entry(State.SLOW, entry.requestId(), entry.at(), entry.data());
        }
        return entry;
    }

    public static synchronized void clear() {
        entry = new Entry(State.NOT_REQUESTED, 0, 0, null);
    }
}
