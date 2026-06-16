package finance.company;

import java.util.List;

/**
 * 公司行业类型。
 */
public enum CompanyType {

    MINING(List.of("iron", "coal")),
    AGRICULTURE(List.of("wheat")),
    ENERGY(List.of("coal")),
    LOGISTICS(List.of()),
    BANKING(List.of());

    private final List<String> commodityIds;

    CompanyType(List<String> commodityIds) {
        this.commodityIds = commodityIds;
    }

    /** 该行业公司生产/持有的商品 ID 列表 */
    public List<String> getCommodityIds() {
        return commodityIds;
    }
}
