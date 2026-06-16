package finance.company;

import java.util.List;
import java.util.Map;

/**
 * 公司行业类型。
 */
public enum CompanyType {

    MINING(List.of("iron", "coal"),    Map.of("iron", 100, "coal", 50), Map.of()),
    AGRICULTURE(List.of("wheat"),      Map.of("wheat", 80),             Map.of()),
    ENERGY(List.of("coal"),            Map.of("coal", 120),             Map.of()),
    MANUFACTURING(List.of("iron", "steel"), Map.of("steel", 40),        Map.of("iron", 80)),
    LOGISTICS(List.of(),               Map.of(),                       Map.of()),
    BANKING(List.of(),                 Map.of(),                       Map.of());

    private final List<String> commodityIds;
    private final Map<String, Integer> dailyProduction;
    private final Map<String, Integer> dailyConsumption;

    CompanyType(List<String> commodityIds, Map<String, Integer> dailyProduction,
                Map<String, Integer> dailyConsumption) {
        this.commodityIds = commodityIds;
        this.dailyProduction = dailyProduction;
        this.dailyConsumption = dailyConsumption;
    }

    public List<String> getCommodityIds() { return commodityIds; }

    public Map<String, Integer> getDailyProduction() { return dailyProduction; }

    /** 每天生产前需要消耗的原料及数量 */
    public Map<String, Integer> getDailyConsumption() { return dailyConsumption; }
}
