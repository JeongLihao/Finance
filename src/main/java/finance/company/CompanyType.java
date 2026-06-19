package finance.company;

import java.util.List;
import java.util.Map;

/**
 * 公司行业类型。
 */
public enum CompanyType {

    MINING("矿业",        List.of("iron", "coal"),       Map.of("iron", 100, "coal", 50), Map.of()),
    AGRICULTURE("农业",    List.of("wheat"),              Map.of("wheat", 80),             Map.of()),
    ENERGY("能源",         List.of("coal"),               Map.of("coal", 120),             Map.of()),
    MANUFACTURING("制造业", List.of("iron", "steel"),     Map.of("steel", 40),             Map.of("iron", 80)),
    LOGISTICS("物流",      List.of(),                     Map.of(),                        Map.of()),
    BANKING("银行",        List.of(),                     Map.of(),                        Map.of());

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
