package finance.market;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品中间价 —— NPC 做市商的报价基准。
 *
 * <h3>定价机制</h3>
 * NPC 双向报价围绕中间价展开：
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — NPC 买入价（玩家卖给 NPC）</li>
 *   <li>askPrice = midPrice × (1 + spread) — NPC 卖出价（玩家从 NPC 买入）</li>
 * </ul>
 *
 * <h3>动态价格（库存驱动）</h3>
 * 价格由 NPC 库存直接决定：
 * <ul>
 *   <li>midPrice = basePrice × REFERENCE_STOCK / npcStock</li>
 *   <li>库存越多 → 供过于求 → 价格越低</li>
 *   <li>库存越少 → 供不应求 → 价格越高</li>
 *   <li>波动范围：basePrice × 0.1 ~ basePrice × 10</li>
 * </ul>
 */
public class MarketPrice {

    // ---- 价格波动参数 ----

    /** 价格下限比例（最低跌到基准价的 10%） */
    private static final double MIN_PRICE_RATIO = 0.1;

    /** 价格上限比例（最高涨到基准价的 10 倍） */
    private static final double MAX_PRICE_RATIO = 10.0;

    /** NPC 参考库存量（库存 = 此值时价格 = basePrice） */
    static final long REFERENCE_STOCK = 100_000;

    /** 每种商品最多保留的快照数量 */
    static final int MAX_SNAPSHOTS = 200;

    // ---- 字段 ----

    private final String commodityId;

    /** 当前中间价 */
    private long midPrice;

    /** 初始基准价（来自 CommodityRegistry，作为价格锚和波动计算基准） */
    private final long basePrice;

    /** 价差比例，默认 0.05（5%） */
    private double spread;

    // ---- 24h 统计 ----

    private long dayHigh;
    private long dayLow;
    private int dayVolume;
    private long dayOpen;

    // ---- 价格历史 ----

    private final List<PriceSnapshot> snapshots = new ArrayList<>();

    // ================================================================
    // 构造
    // ================================================================

    public MarketPrice(String commodityId, long basePrice, double spread) {
        this.commodityId = commodityId;
        this.basePrice = basePrice;
        this.midPrice = basePrice;
        this.spread = spread;
        this.dayHigh = basePrice;
        this.dayLow = basePrice;
        this.dayOpen = basePrice;
        this.dayVolume = 0;
    }

    // ================================================================
    // 基础 getter
    // ================================================================

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

    // ---- 24h 统计 getter ----

    public long getDayHigh() { return dayHigh; }
    public long getDayLow() { return dayLow; }
    public int getDayVolume() { return dayVolume; }
    public long getDayOpen() { return dayOpen; }

    /** 24h 涨跌幅（相对于 dayOpen 的百分比，正=涨，负=跌，0=持平） */
    public double getDayChange() {
        if (dayOpen == 0) return 0;
        return (double) (midPrice - dayOpen) / dayOpen * 100;
    }

    public List<PriceSnapshot> getSnapshots() {
        return snapshots;
    }

    // ================================================================
    // 价格调整
    // ================================================================

    /**
     * 根据 NPC 当前库存重新计算 midPrice。
     * 交易后由 NpcMarketMaker 调用。
     *
     * @param npcStock NPC 当前持有该商品的数量
     */
    public void recomputePrice(long npcStock) {
        if (npcStock <= 0) return;

        double ratio = (double) REFERENCE_STOCK / npcStock;
        long floor = Math.max(1, (long) (basePrice * MIN_PRICE_RATIO));
        long ceiling = (long) (basePrice * MAX_PRICE_RATIO);
        midPrice = Math.max(floor, Math.min(ceiling, (long) (basePrice * ratio)));
    }

    // ================================================================
    // 成交记录与快照
    // ================================================================

    /**
     * 记录一次 NPC 交易，更新 24h 统计和价格快照。
     *
     * @param tradePrice 成交单价
     * @param quantity   成交量
     */
    public void recordTrade(long tradePrice, int quantity) {
        // 更新 24h 统计
        if (dayVolume == 0) {
            dayHigh = tradePrice;
            dayLow = tradePrice;
        } else {
            if (tradePrice > dayHigh) dayHigh = tradePrice;
            if (tradePrice < dayLow) dayLow = tradePrice;
        }
        dayVolume += quantity;

        // 记录快照
        snapshots.add(new PriceSnapshot(LocalDateTime.now(), midPrice, quantity));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    // ---- setter ----

    public void setMidPrice(long midPrice) {
        this.midPrice = midPrice;
    }

    public void setSpread(double spread) {
        this.spread = spread;
    }

    /** 设置 24h 开盘价（持久化恢复时使用） */
    public void setDayOpen(long dayOpen) {
        this.dayOpen = dayOpen;
    }

    /**
     * 从已加载的快照重新计算 dayHigh、dayLow、dayVolume。
     * 持久化恢复时调用。
     */
    public void recomputeDayStats() {
        dayHigh = midPrice;
        dayLow = midPrice;
        dayVolume = 0;

        for (PriceSnapshot snap : snapshots) {
            if (snap.price > dayHigh) dayHigh = snap.price;
            if (snap.price < dayLow) dayLow = snap.price;
            dayVolume += snap.volume;
        }
    }

    // ================================================================
    // PriceSnapshot —— 单次价格快照
    // ================================================================

    /**
     * 价格快照 —— 记录每次 NPC 交易后的 midPrice 和成交量。
     * 为后续 K 线图和价格历史图表提供数据。
     */
    public static class PriceSnapshot {

        private final LocalDateTime timestamp;
        private final long price;
        private final int volume;

        public PriceSnapshot(LocalDateTime timestamp, long price, int volume) {
            this.timestamp = timestamp;
            this.price = price;
            this.volume = volume;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public long getPrice() { return price; }
        public int getVolume() { return volume; }
    }
}
