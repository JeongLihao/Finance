package finance.commodity;

/**
 * 商品定义 —— 可在市场中交易的商品类型。
 */
public class Commodity {

    /** 商品唯一 ID（用于命令参数和持久化） */
    private final String id;

    /** 对应的 Minecraft 物品 ID（如 minecraft:iron_ingot），null 表示纯虚拟商品 */
    private final String itemId;

    /** 商品显示名称 */
    private final String displayName;

    private final CommodityCategory category;

    /** 基础价格（初始参考价） */
    private long basePrice;

    /** 央行储备分档：1~3 由央行投放（2、3 随全服成交额解锁），0 = 只有玩家卖了才上架，-1 = 旧规则。 */
    private int reserveTier = -1;

    public Commodity(String id, String displayName, CommodityCategory category, long basePrice) {
        this(id, null, displayName, category, basePrice);
    }

    public Commodity(String id, String itemId, String displayName, CommodityCategory category, long basePrice) {
        this.id = id;
        this.itemId = itemId;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice;
    }

    public Commodity reserveTier(int tier) {
        this.reserveTier = Math.max(-1, Math.min(3, tier));
        return this;
    }

    public int getReserveTier() {
        return reserveTier;
    }

    public String getId() {
        return id;
    }

    /** 对应的 MC 物品 ID，null 表示纯虚拟商品 */
    public String getItemId() {
        return itemId;
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
}
