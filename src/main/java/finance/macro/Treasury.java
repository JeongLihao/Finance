package finance.macro;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 国库状态：系统账户 {@link #UUID} 的余额就是国库现金（随账户落盘），这里只存债务、紧缩标记和收支账。
 */
public final class Treasury {
    /** 国库账户。不是玩家账户，只能由财政服务转入转出。 */
    public static final UUID UUID = java.util.UUID.nameUUIDFromBytes("finance-macro-treasury".getBytes(StandardCharsets.UTF_8));
    public static final int HISTORY_DAYS = 60;

    /** 收支科目。 */
    public enum Item {
        TAX("利润税", true), TARIFF("关税", true), FEES("规费", true),
        ADMIN("行政开支", false), WELFARE("福利", false), INTEREST("利息", false),
        BORROW("借款", true), REPAY("还款", false);

        private final String displayName;
        private final boolean inflow;

        Item(String displayName, boolean inflow) {
            this.displayName = displayName;
            this.inflow = inflow;
        }

        public String displayName() { return displayName; }
        public boolean inflow() { return inflow; }
    }

    /** 一天的收支记录。 */
    public record Day(long day, long balance, long debt, Map<Item, Long> flows, boolean austerity) { }

    private static long debt;
    private static boolean austerity;
    private static long lastProcessedDay = Long.MIN_VALUE;
    private static long cumulativeIssuance;
    private static final EnumMap<Item, Long> TODAY = new EnumMap<>(Item.class);
    private static final ArrayDeque<Day> HISTORY = new ArrayDeque<>();

    private Treasury() { }

    public static long debt() { return debt; }
    public static boolean austerity() { return austerity; }
    public static long lastProcessedDay() { return lastProcessedDay; }
    public static long cumulativeIssuance() { return cumulativeIssuance; }

    static void setDebt(long v) { debt = Math.max(0, v); }
    static void setAusterity(boolean v) { austerity = v; }
    static void setLastProcessedDay(long v) { lastProcessedDay = v; }
    static void addIssuance(long v) { cumulativeIssuance = GoodBook.sat(cumulativeIssuance, v); }
    static void restoreIssuance(long v) { cumulativeIssuance = Math.max(0, v); }

    static void book(Item item, long amount) {
        if (item == null || amount <= 0) return;
        TODAY.merge(item, amount, GoodBook::sat);
    }

    public static Map<Item, Long> today() { return Collections.unmodifiableMap(new EnumMap<>(TODAY)); }

    /** 收盘：把今天的收支记进历史并清零。 */
    static void close(long day, long balance) {
        HISTORY.addLast(new Day(day, balance, debt, Map.copyOf(TODAY), austerity));
        while (HISTORY.size() > HISTORY_DAYS) HISTORY.removeFirst();
        TODAY.clear();
    }

    static void restoreDay(Day d) {
        if (d == null) return;
        HISTORY.addLast(d);
        while (HISTORY.size() > HISTORY_DAYS) HISTORY.removeFirst();
    }

    static void restoreToday(Item item, long amount) { book(item, amount); }

    public static List<Day> history() { return new ArrayList<>(HISTORY); }

    public static Day lastDay() { return HISTORY.peekLast(); }

    public static void clearDirect() {
        debt = 0;
        austerity = false;
        lastProcessedDay = Long.MIN_VALUE;
        cumulativeIssuance = 0;
        TODAY.clear();
        HISTORY.clear();
    }
}
