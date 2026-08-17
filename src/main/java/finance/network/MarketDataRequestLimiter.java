package finance.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MarketDataRequestLimiter {
    static final int MAX_REQUESTS_PER_SECOND = 8;
    private static final long WINDOW_TICKS = 20;
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> RECENT_KEYS = new HashMap<>();

    private MarketDataRequestLimiter() {}

    public static synchronized boolean allow(UUID playerId, long serverTick) {
        if (playerId == null || serverTick < 0) return false;
        Window current = WINDOWS.get(playerId);
        if (current == null || serverTick < current.startedAt || serverTick - current.startedAt >= WINDOW_TICKS) {
            WINDOWS.put(playerId, new Window(serverTick, 1));
            return true;
        }
        if (current.count >= MAX_REQUESTS_PER_SECOND) return false;
        WINDOWS.put(playerId, new Window(current.startedAt, current.count + 1));
        return true;
    }

    public static synchronized boolean allow(UUID playerId, long serverTick, String requestKey) {
        if (playerId == null || requestKey == null || requestKey.isBlank()) return false;
        Map<String, Long> recent = RECENT_KEYS.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Long previous = recent.get(requestKey);
        if (previous != null && serverTick >= previous && serverTick - previous < 4) return false;
        if (!allow(playerId, serverTick)) return false;
        recent.put(requestKey, serverTick);
        recent.entrySet().removeIf(entry -> serverTick >= entry.getValue() && serverTick - entry.getValue() >= 20);
        return true;
    }

    public static synchronized void clear() { WINDOWS.clear(); RECENT_KEYS.clear(); }

    private record Window(long startedAt, int count) {}
}
