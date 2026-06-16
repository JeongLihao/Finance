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
 * <h3>动态价格</h3>
 * 每次 NPC 交易后 midPrice 随供求关系自动调整：
 * <ul>
 *   <li>玩家卖给 NPC → 供过于求 → midPrice 下跌</li>
 *   <li>玩家从 NPC 买 → 供不应求 → midPrice 上涨</li>
 *   <li>波动幅度 = max(1, quantity × basePrice / BASE_LIQUIDITY)</li>
 *   <li>波动范围：basePrice × 0.1 ~ basePrice × 10</li>
 * </ul>
 */
public class MarketPrice {

    // ---- 价格波动参数 ----

    /** 流动性基准：需要交易多少价值才能让价格变动 1 */
    private static final int BASE_LIQUIDITY = 1000;

    /** 价格下限比例（最低跌到基准价的 10%） */
    private static final double MIN_PRICE_RATIO = 0.1;

    /** 价格上限比例（最高涨到基准价的 10 倍） */
    private static final double MAX_PRICE_RATIO = 10.0;

    /** 每种商品最多保留的快照数量 */
    static final int MAX_SNAPSHOTS = 200;

    /** 最小影响数量：低于该数量的交易不引发价格波动（2 组 = 128） */
    private static final int MIN_TRADE_QUANTITY = 128;

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
     * NPC 从玩家买入商品后调用 —— 供过于求，降价。
     */
    public void adjustAfterNpcBuy(int quantity) {
        if (quantity < MIN_TRADE_QUANTITY) return;
        long impact = Math.max(1, (long) quantity * basePrice / BASE_LIQUIDITY);
        long floor = Math.max(1, (long) (basePrice * MIN_PRICE_RATIO));
        midPrice = Math.max(floor, midPrice - impact);
    }

    /**
     * NPC 向玩家卖出商品后调用 —— 供不应求，涨价。
     */
    public void adjustAfterNpcSell(int quantity) {
        if (quantity < MIN_TRADE_QUANTITY) return;
        long impact = Math.max(1, (long) quantity * basePrice / BASE_LIQUIDITY);
        long ceiling = (long) (basePrice * MAX_PRICE_RATIO);
        midPrice = Math.min(ceiling, midPrice + impact);
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
