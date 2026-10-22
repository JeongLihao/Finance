package finance.macro;

import finance.village.VillageProfession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * 人口需求（纯函数）：职业构成 → 各类别需要的份数 → 按价格在替代品之间分摊成商品数量。
 *
 * <p>食物、住房、工具三项的人均份数与旧版 {@link VillageProfession#needsBasket()} 一致，
 * 取暖和衣物是新增需求。</p>
 */
public final class MacroNeeds {
    private MacroNeeds() {}

    /** 受雇工人在职业篮子之外的额外需求：有了收入就多消费。 */
    private static final Map<NeedCategory, Double> WORKER_EXTRA =
            Map.of(NeedCategory.FOOD, 0.5, NeedCategory.CLOTHING, 0.3);

    /** 某职业每人每天各类别的需求份数。 */
    public static Map<NeedCategory, Double> perCapita(VillageProfession profession) {
        EnumMap<NeedCategory, Double> m = new EnumMap<>(NeedCategory.class);
        if (profession == null) return m;
        switch (profession) {
            case FARMER -> put(m, 2.0, 0.2, 0.1, 0.3, 0.1);
            case ARTISAN -> put(m, 1.0, 1.0, 0.3, 0.4, 0.2);
            case GUARD -> put(m, 1.5, 0.1, 0.5, 0.3, 0.2);
            case OTHER -> put(m, 1.0, 0.3, 0.1, 0.3, 0.1);
        }
        return m;
    }

    private static void put(
            EnumMap<NeedCategory, Double> m,
            double food,
            double housing,
            double tools,
            double heating,
            double clothing) {
        m.put(NeedCategory.FOOD, food);
        m.put(NeedCategory.HOUSING, housing);
        m.put(NeedCategory.TOOLS, tools);
        m.put(NeedCategory.HEATING, heating);
        m.put(NeedCategory.CLOTHING, clothing);
    }

    /**
     * 整个聚落每天各类别需要的份数。
     *
     * @param mix      职业人数
     * @param employed 受雇工人数（另加额外需求）
     */
    public static Map<NeedCategory, Double> requiredUnits(Map<VillageProfession, Integer> mix, long employed) {
        EnumMap<NeedCategory, Double> total = new EnumMap<>(NeedCategory.class);
        for (NeedCategory c : NeedCategory.values()) total.put(c, 0.0);
        if (mix != null) {
            for (Map.Entry<VillageProfession, Integer> e : mix.entrySet()) {
                int count = e.getValue() == null ? 0 : Math.max(0, e.getValue());
                if (count == 0) continue;
                perCapita(e.getKey()).forEach((c, v) -> total.merge(c, v * count, Double::sum));
            }
        }
        long workers = Math.max(0, employed);
        if (workers > 0) WORKER_EXTRA.forEach((c, v) -> total.merge(c, v * workers, Double::sum));
        return total;
    }

    /**
     * 把一个类别的需求份数按“每份价格”的平方反比分摊到替代品上。
     *
     * @param units      需要的份数
     * @param registered 商品是否已注册（未注册的不参与）
     * @param available  商品当前是否有供给；全部没有供给时按全部已注册候选分摊（短缺）
     * @param unitPrice  商品本地单价（≤ 0 的视为不可用）
     * @return 商品 id → 数量（只含 &gt; 0 的项）
     */
    public static Map<String, Long> allocate(
            NeedCategory category,
            double units,
            Predicate<String> registered,
            Predicate<String> available,
            ToLongFunction<String> unitPrice) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (category == null || !(units > 0)) return out;
        Map<String, Double> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : category.unitsPerItem().entrySet()) {
            if (registered.test(e.getKey()) && unitPrice.applyAsLong(e.getKey()) > 0)
                candidates.put(e.getKey(), e.getValue());
        }
        if (candidates.isEmpty()) return out;
        Map<String, Double> pool = new LinkedHashMap<>();
        candidates.forEach((id, u) -> {
            if (available.test(id)) pool.put(id, u);
        });
        if (pool.isEmpty()) pool.putAll(candidates);

        Map<String, Double> weights = new LinkedHashMap<>();
        double sum = 0;
        String cheapest = null;
        double cheapestPerUnit = Double.MAX_VALUE;
        for (Map.Entry<String, Double> e : pool.entrySet()) {
            double perUnit = unitPrice.applyAsLong(e.getKey()) / e.getValue();
            double w = 1.0 / (perUnit * perUnit);
            weights.put(e.getKey(), w);
            sum += w;
            if (perUnit < cheapestPerUnit) {
                cheapestPerUnit = perUnit;
                cheapest = e.getKey();
            }
        }
        double covered = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double share = units * e.getValue() / sum;
            double perItem = pool.get(e.getKey());
            long qty = (long) Math.floor(share / perItem + 1e-9);
            if (qty > 0) out.put(e.getKey(), qty);
            covered += qty * perItem;
        }
        // 余数交给最便宜的商品，仍按整件向下取整；最便宜的一件装不下余数时（如 1.6 份取暖、煤一块 2 份），
        // 按每份价格从低到高交给装得下的替代品，避免小聚落整类需求被取整成 0
        if (cheapest != null) {
            long extra = (long) Math.floor((units - covered) / pool.get(cheapest) + 1e-9);
            if (extra > 0) {
                out.merge(cheapest, extra, Long::sum);
                covered += extra * pool.get(cheapest);
            }
            List<String> byPrice = new ArrayList<>(pool.keySet());
            byPrice.sort(Comparator.comparingDouble(id -> unitPrice.applyAsLong(id) / pool.get(id)));
            for (String id : byPrice) {
                long more = (long) Math.floor((units - covered) / pool.get(id) + 1e-9);
                if (more <= 0) continue;
                out.merge(id, more, Long::sum);
                covered += more * pool.get(id);
            }
        }
        return out;
    }
}
