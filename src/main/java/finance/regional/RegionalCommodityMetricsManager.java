package finance.regional;

import finance.data.EconomySavedData;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class RegionalCommodityMetricsManager {
    public static final int MAX_KEYS = 32_768;
    public static final int MAX_HISTORY_DAYS = 120;
    private static final Map<RegionalCommodityKey, Deque<RegionalCommoditySnapshot>> HISTORY = new LinkedHashMap<>();
    private static final Map<RegionalCommodityKey, DailyAccumulator> LIVE = new LinkedHashMap<>();
    private static final Set<RegionalCommodityKey> KNOWN_KEYS = new HashSet<>();
    private static long lastClosedDay = -1;

    private RegionalCommodityMetricsManager() {}

    static synchronized DailyAccumulator accumulator(RegionalCommodityKey key, long day) {
        if (key == null || day < 0) return null;
        DailyAccumulator existing = LIVE.get(key);
        if (existing != null && existing.day == day) return existing;
        if (existing != null || !knownKey(key) && uniqueKeyCount() >= MAX_KEYS) return null;
        DailyAccumulator created = new DailyAccumulator(day);
        LIVE.put(key, created);
        KNOWN_KEYS.add(key);
        return created;
    }

    public static synchronized List<RegionalCommoditySnapshot> history(RegionalCommodityKey key) {
        Deque<RegionalCommoditySnapshot> rows = HISTORY.get(key);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    public static synchronized RegionalCommoditySnapshot latest(RegionalCommodityKey key) {
        Deque<RegionalCommoditySnapshot> rows = HISTORY.get(key);
        return rows == null ? null : rows.peekLast();
    }

    /** Bounded public snapshot for GUI/network callers; never copies the complete history table. */
    public static synchronized List<HistoryView> recentHistories(int limit, int days) {
        if (limit <= 0 || days <= 0) return List.of();
        int boundedLimit = Math.min(MAX_KEYS, limit), boundedDays = Math.min(MAX_HISTORY_DAYS, days);
        List<HistoryView> out = new ArrayList<>(Math.min(boundedLimit, HISTORY.size()));
        for (var entry : HISTORY.entrySet()) {
            if (out.size() >= boundedLimit) break;
            if (entry.getValue().isEmpty()) continue;
            List<RegionalCommoditySnapshot> rows = new ArrayList<>(entry.getValue());
            int from = Math.max(0, rows.size() - boundedDays);
            out.add(new HistoryView(entry.getKey(), List.copyOf(rows.subList(from, rows.size()))));
        }
        return List.copyOf(out);
    }

    /** Allocation-free lookup used by survey target scoring. */
    public static synchronized int maxSmoothedShortage(UUID regionId) {
        if (regionId == null) return 0;
        int max = 0;
        for (var entry : HISTORY.entrySet()) {
            if (!entry.getKey().regionId().equals(regionId) || entry.getValue().isEmpty()) continue;
            max = Math.max(max, entry.getValue().peekLast().smoothedShortageScore());
        }
        return max;
    }

    /** Streams one immutable row at a time so persistence does not duplicate the whole world history in memory. */
    public static synchronized void visitHistories(BiConsumer<RegionalCommodityKey,List<RegionalCommoditySnapshot>> visitor) {
        if (visitor == null) return;
        HISTORY.forEach((key, rows) -> visitor.accept(key, List.copyOf(rows)));
    }

    public static synchronized void visitLive(BiConsumer<RegionalCommodityKey,DailyAccumulator> visitor) {
        if (visitor == null) return;
        LIVE.forEach((key, value) -> visitor.accept(key, value.copy()));
    }

    public record HistoryView(RegionalCommodityKey key,List<RegionalCommoditySnapshot> rows) {}

    public static synchronized Map<RegionalCommodityKey, DailyAccumulator> liveCopy() {
        Map<RegionalCommodityKey, DailyAccumulator> copy = new LinkedHashMap<>();
        LIVE.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    static synchronized List<Map.Entry<RegionalCommodityKey, DailyAccumulator>> liveForDay(long day) {
        List<Map.Entry<RegionalCommodityKey, DailyAccumulator>> rows = new ArrayList<>();
        LIVE.forEach((key, value) -> { if (value.day == day) rows.add(Map.entry(key, value.copy())); });
        return rows;
    }

    static synchronized void append(RegionalCommodityKey key, RegionalCommoditySnapshot snapshot) {
        if (key == null || snapshot == null || !knownKey(key) && uniqueKeyCount() >= MAX_KEYS) return;
        Deque<RegionalCommoditySnapshot> rows = HISTORY.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        KNOWN_KEYS.add(key);
        if (!rows.isEmpty() && rows.peekLast().day() >= snapshot.day()) return;
        rows.addLast(snapshot);
        while (rows.size() > finance.config.FinanceConfig.regionalHistoryDays()) rows.removeFirst();
        LIVE.remove(key);
        lastClosedDay = Math.max(lastClosedDay, snapshot.day());
        EconomySavedData.markDirty();
    }

    public static synchronized long lastClosedDay() { return lastClosedDay; }
    public static synchronized void restoreLastClosedDay(long day) { lastClosedDay = Math.max(-1, day); }
    public static synchronized void restoreSnapshot(RegionalCommodityKey key, RegionalCommoditySnapshot row) {
        if (key == null || row == null || !knownKey(key) && uniqueKeyCount() >= MAX_KEYS) return;
        Deque<RegionalCommoditySnapshot> rows = HISTORY.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        KNOWN_KEYS.add(key);
        if (rows.isEmpty() || rows.peekLast().day() < row.day()) rows.addLast(row);
        while (rows.size() > MAX_HISTORY_DAYS) rows.removeFirst();
    }
    public static synchronized void restoreLive(RegionalCommodityKey key, DailyAccumulator value) {
        if (key != null && value != null && (knownKey(key) || uniqueKeyCount() < MAX_KEYS)) {
            LIVE.put(key, value.copy());
            KNOWN_KEYS.add(key);
        }
    }
    public static synchronized void clearDirect() { HISTORY.clear(); LIVE.clear(); KNOWN_KEYS.clear(); lastClosedDay = -1; }

    private static boolean knownKey(RegionalCommodityKey key) {
        return KNOWN_KEYS.contains(key);
    }

    private static int uniqueKeyCount() {
        return KNOWN_KEYS.size();
    }

    public static final class DailyAccumulator {
        private static final long MAX_QUANTITY_PER_DAY = 1_000_000L;
        final long day;
        int opened, filled, expired, deliveredOnTime, deliveredLate, activeShipments;
        long requested, delivered;
        BigInteger paid = BigInteger.ZERO;
        public DailyAccumulator(long day) { if (day < 0) throw new IllegalArgumentException("day"); this.day = day; }
        void opened(long quantity) { opened = capInt(opened + 1L); requested = capQuantity(requested, quantity); }
        void filled(long quantity, long reward, boolean onTime) { filled = capInt(filled + 1L); delivered = capQuantity(delivered, quantity); if (reward > 0) paid = paid.add(BigInteger.valueOf(reward)); if (onTime) deliveredOnTime = capInt(deliveredOnTime + 1L); else deliveredLate = capInt(deliveredLate + 1L); }
        void noteExpired() { expired = capInt(expired + 1L); }
        DailyAccumulator copy() { DailyAccumulator c=new DailyAccumulator(day);c.opened=opened;c.filled=filled;c.expired=expired;c.deliveredOnTime=deliveredOnTime;c.deliveredLate=deliveredLate;c.activeShipments=activeShipments;c.requested=requested;c.delivered=delivered;c.paid=paid;return c; }
        private static long capQuantity(long current,long added){if(added<=0)return current;return Math.min(MAX_QUANTITY_PER_DAY,current>MAX_QUANTITY_PER_DAY-added?MAX_QUANTITY_PER_DAY:current+added);}
        private static int capInt(long value){return (int)Math.min(1_000_000L,Math.max(0,value));}
        public long day(){return day;} public int opened(){return opened;} public int filled(){return filled;} public int expiredCount(){return expired;} public int deliveredOnTime(){return deliveredOnTime;} public int deliveredLate(){return deliveredLate;} public int activeShipments(){return activeShipments;} public long requested(){return requested;} public long delivered(){return delivered;} public BigInteger paid(){return paid;}
        public void restore(int opened,int filled,int expired,int onTime,int late,int active,long requested,long delivered,BigInteger paid){this.opened=capInt(opened);this.filled=capInt(filled);this.expired=capInt(expired);this.deliveredOnTime=capInt(onTime);this.deliveredLate=capInt(late);this.activeShipments=capInt(active);this.requested=Math.min(MAX_QUANTITY_PER_DAY,Math.max(0,requested));this.delivered=Math.min(MAX_QUANTITY_PER_DAY,Math.max(0,delivered));this.paid=paid==null||paid.signum()<0?BigInteger.ZERO:paid.min(BigInteger.valueOf(Long.MAX_VALUE));}
    }
}
