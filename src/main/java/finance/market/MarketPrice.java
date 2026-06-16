package finance.market;

/**
 * 商品中间价 —— NPC 做市商的报价基准。
 * <p>
 * NPC 双向报价围绕中间价展开：
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — NPC 买入价（玩家卖给 NPC）</li>
 *   <li>askPrice = midPrice × (1 + spread) — NPC 卖出价（玩家从 NPC 买入）</li>
 * </ul>
 * 价差是 NPC 的利润来源，也是后续价格波动模块的锚点。
 * </p>
 */
public class MarketPrice {

    private final String commodityId;

    /** 当前中间价 */
    private long midPrice;

    /** 初始基础价（来自 CommodityRegistry，作为价格锚） */
    private final long basePrice;

    /** 价差比例，默认 0.05（5%） */
    private double spread;

    public MarketPrice(String commodityId, long basePrice, double spread) {
        this.commodityId = commodityId;
        this.basePrice = basePrice;
        this.midPrice = basePrice;
        this.spread = spread;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public long getMidPrice() {
        return midPrice;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public double getSpread() {
        return spread;
    }

    /** NPC 买入价（玩家卖商品给 NPC 时的单价），最低为 1 */
    public long getBidPrice() {
        return Math.max(1, (long) (midPrice * (1 - spread)));
    }

    /** NPC 卖出价（玩家从 NPC 买商品时的单价） */
    public long getAskPrice() {
        return (long) (midPrice * (1 + spread));
    }

    // ---- setter 供价格波动模块使用 ----

    public void setMidPrice(long midPrice) {
        this.midPrice = midPrice;
    }

    public void setSpread(double spread) {
        this.spread = spread;
    }
}
