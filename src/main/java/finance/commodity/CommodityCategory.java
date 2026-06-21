package finance.commodity;

/**
 * 商品分类 —— 基于 Minecraft 物品栏分类。
 */
public enum CommodityCategory {
    BUILDING_BLOCKS("建筑方块"),
    RAW_MATERIALS("原材料"),
    TOOLS("工具"),
    COMBAT("战斗"),
    FOOD("食物"),
    REDSTONE("红石"),
    BREWING("药水"),
    TRANSPORTATION("交通运输"),
    MISCELLANEOUS("杂项");

    private final String displayName;

    CommodityCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
