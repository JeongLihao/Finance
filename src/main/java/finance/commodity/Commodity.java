package finance.commodity;

/**
 * 商品定义 —— 可在市场中交易的商品类型。
 */
public class Commodity {

    /** 商品唯一 ID（用于命令参数和持久化） */
    private final String id;

    /** 商品显示名称 */
    private final String displayName;

    private final CommodityCategory category;

    /** 基础价格（初始参考价） */
    private long basePrice;

    public Commodity(
            String id,
            String displayName,
            CommodityCategory category,
            long basePrice
    ) {

        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CommodityCategory getCategory() {
        return category;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(long basePrice) {
        this.basePrice = basePrice;
    }
}
