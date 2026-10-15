package finance.village;

import java.util.UUID;

/**
 * 玩家在村庄集市的摊位：每个玩家在每个聚落对每种商品最多一个摊位，
 * 一个摊位只卖一种商品、一个单价，自带库存。
 *
 * <p>反刷钱：上架必须扣玩家背包实物（由调用方保证），没货不能卖；
 * 售出即时结算，库存不足的购买直接失败。</p>
 */
public final class VillageStall {
    private final UUID id;
    private final UUID settlementId;
    private final UUID ownerId;
    private final String commodityId;
    private long unitPrice;
    private int stock;

    /**
     * @param id          摊位 id，非 null
     * @param settlementId 聚落 id，非 null
     * @param ownerId     摊主玩家 id，非 null
     * @param commodityId 商品 id，非空、长度 ≤ 64
     * @param unitPrice   单价（金额单位），必须 ≥ 1
     * @throws IllegalArgumentException 校验失败时抛出（沿用 LocalDemand 风格）
     */
    public VillageStall(UUID id, UUID settlementId, UUID ownerId, String commodityId, long unitPrice) {
        if (id == null || settlementId == null || ownerId == null)
            throw new IllegalArgumentException("id/settlementId/ownerId 不能为空");
        if (commodityId == null || commodityId.isBlank() || commodityId.length() > 64)
            throw new IllegalArgumentException("commodityId 非法");
        if (unitPrice < 1) throw new IllegalArgumentException("unitPrice 必须 ≥ 1");
        this.id = id;
        this.settlementId = settlementId;
        this.ownerId = ownerId;
        this.commodityId = commodityId;
        this.unitPrice = unitPrice;
    }

    public UUID id() { return id; }

    public UUID settlementId() { return settlementId; }

    public UUID ownerId() { return ownerId; }

    public String commodityId() { return commodityId; }

    public long unitPrice() { return unitPrice; }

    /** 改价；{@code p < 1} 时抛 IllegalArgumentException。 */
    public void setUnitPrice(long p) {
        if (p < 1) throw new IllegalArgumentException("unitPrice 必须 ≥ 1");
        this.unitPrice = p;
    }

    public int stock() { return stock; }

    /**
     * 入库；{@code n <= 0} 抛 IllegalArgumentException，int 溢出同样抛 IllegalArgumentException。
     */
    public void addStock(int n) {
        if (n <= 0) throw new IllegalArgumentException("入库数量必须 > 0");
        if (stock > Integer.MAX_VALUE - n) throw new IllegalArgumentException("摊位库存溢出");
        stock += n;
    }

    /**
     * 出库；{@code n <= 0} 或库存不足返回 false，否则扣减并返回 true。
     */
    public boolean takeStock(int n) {
        if (n <= 0 || n > stock) return false;
        stock -= n;
        return true;
    }
}
