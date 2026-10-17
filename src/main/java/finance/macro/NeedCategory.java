package finance.macro;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人口需求类别。人口不直接需要某种商品，而是需要“食物”“取暖”这样的类别，
 * 每个类别可以由几种商品满足，每件商品折算成若干份需求。
 */
public enum NeedCategory {
    FOOD("食物", 35, goods("wheat", 1.0, "bread", 3.0)),
    HOUSING("住房", 15, goods("stone", 1.0, "wood", 1.0)),
    TOOLS("工具", 15, goods("iron", 1.0)),
    HEATING("取暖", 15, goods("coal", 2.0, "wood", 1.0)),
    CLOTHING("衣物", 20, goods("wool", 1.0));

    private final String displayName;
    private final int weight;
    private final Map<String, Double> unitsPerItem;

    NeedCategory(String displayName, int weight, Map<String, Double> unitsPerItem) {
        this.displayName = displayName;
        this.weight = weight;
        this.unitsPerItem = unitsPerItem;
    }

    public String displayName() { return displayName; }

    /** 计算生活水平时的权重，全部类别之和为 100。 */
    public int weight() { return weight; }

    /** 商品 id → 每件折算的需求份数（按声明顺序）。 */
    public Map<String, Double> unitsPerItem() { return unitsPerItem; }

    /** 某商品在本类别里每件折算的份数；不属于本类别返回 0。 */
    public double unitsOf(String commodityId) {
        Double v = unitsPerItem.get(commodityId);
        return v == null ? 0.0 : v;
    }

    private static Map<String, Double> goods(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], (Double) pairs[i + 1]);
        return java.util.Collections.unmodifiableMap(map);
    }
}
