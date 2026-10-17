package finance.company;

import java.util.List;
import java.util.Map;

/**
 * 公司行业类型 —— 对应 MC 物品栏分类。
 */
public enum CompanyType {

    RAW_MATERIALS("原材料",  List.of("iron"),               Map.of("iron", 100),             Map.of()),
    BUILDING_BLOCKS("建筑方块", List.of("stone"),            Map.of("stone", 120),            Map.of()),
    FOOD("食物",            List.of("wheat"),              Map.of("wheat", 80),             Map.of()),
    // ---- 宏观经济生产链（见 docs/MACRO_ECONOMY.md）----
    FOOD_PROCESSING("食品加工", List.of("bread"),            Map.of("bread", 30),             Map.of("wheat", 40)),
    FORESTRY("林业",        List.of("wood"),               Map.of("wood", 100),             Map.of()),
    COAL_MINING("煤矿",     List.of("coal"),               Map.of("coal", 80),              Map.of("iron", 5)),
    TEXTILE("纺织",         List.of("wool"),               Map.of("wool", 40),              Map.of("wheat", 10));

    private final String displayName;
    private final List<String> commodityIds;
    private final Map<String, Integer> dailyProduction;
    private final Map<String, Integer> dailyConsumption;

    CompanyType(String displayName, List<String> commodityIds, Map<String, Integer> dailyProduction,
                Map<String, Integer> dailyConsumption) {
        this.displayName = displayName;
        this.commodityIds = commodityIds;
        this.dailyProduction = dailyProduction;
        this.dailyConsumption = dailyConsumption;
    }

    public String getDisplayName() { return displayName; }

    public List<String> getCommodityIds() { return commodityIds; }

    public Map<String, Integer> getDailyProduction() { return dailyProduction; }

    /** 每天生产前需要消耗的原料及数量 */
    public Map<String, Integer> getDailyConsumption() { return dailyConsumption; }
}
