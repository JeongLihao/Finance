package finance.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 一个聚落的宏观经济状态：生活水平、人口增长余数、就业与收入、各商品本地账本、当天的需求分摊。
 */
public final class SettlementEconomy {
    public static final int HISTORY_DAYS = 60;
    public static final int MAX_GOODS = 64;

    /** 一天的人口记录。 */
    public record Point(long day, int sol, int population, long employed, long income) { }

    final UUID settlementId;
    int solBps = 5_000;
    long growthMilli;
    long lastProcessedDay = -1;
    boolean unrest;
    long employed;
    long incomeToday;
    long lastIncome;
    final TreeMap<String, GoodBook> goods = new TreeMap<>();
    /** 当天分摊：类别 → 商品 → 数量。只在日结内使用，不落盘。 */
    final EnumMap<NeedCategory, Map<String, Long>> plan = new EnumMap<>(NeedCategory.class);
    final EnumMap<NeedCategory, Double> required = new EnumMap<>(NeedCategory.class);
    final EnumMap<NeedCategory, Double> consumedUnits = new EnumMap<>(NeedCategory.class);
    final EnumMap<NeedCategory, Double> lastFulfilment = new EnumMap<>(NeedCategory.class);
    final ArrayDeque<Point> history = new ArrayDeque<>();

    SettlementEconomy(UUID settlementId) {
        this.settlementId = settlementId;
    }

    public UUID settlementId() { return settlementId; }
    public int solBps() { return solBps; }
    public boolean unrest() { return unrest; }
    public long employed() { return employed; }
    public long lastIncome() { return lastIncome; }
    public long lastProcessedDay() { return lastProcessedDay; }
    public Map<String, GoodBook> goods() { return Collections.unmodifiableMap(goods); }
    public Map<NeedCategory, Double> lastFulfilment() { return Collections.unmodifiableMap(lastFulfilment); }
    public List<Point> history() { return new ArrayList<>(history); }

    /** 取商品账本，不存在则创建（超过上限返回 null）。 */
    GoodBook book(String commodityId) {
        GoodBook b = goods.get(commodityId);
        if (b == null && commodityId != null && goods.size() < MAX_GOODS) {
            b = new GoodBook();
            goods.put(commodityId, b);
        }
        return b;
    }

    /** 本地价格系数；没有账本时为中性。 */
    public int factorBps(String commodityId) {
        GoodBook b = goods.get(commodityId);
        return b == null ? MacroFormulas.NEUTRAL_BPS : b.factorBps;
    }

    /** 当天这个商品在哪些类别里各分到多少。 */
    Map<NeedCategory, Long> planFor(String commodityId) {
        EnumMap<NeedCategory, Long> out = new EnumMap<>(NeedCategory.class);
        plan.forEach((c, m) -> {
            Long q = m.get(commodityId);
            if (q != null && q > 0) out.put(c, q);
        });
        return out;
    }

    void addIncome(long amount) {
        incomeToday = GoodBook.sat(incomeToday, amount);
    }

    void pushHistory(long day, int population) {
        history.addLast(new Point(day, solBps, population, employed, lastIncome));
        while (history.size() > HISTORY_DAYS) history.removeFirst();
    }
}
