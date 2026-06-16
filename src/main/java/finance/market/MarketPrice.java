package finance.market;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import finance.event.MarketEvent;

/**
 * 商品中间价 —— NPC 做市商的报价基准。
 *
 * <h3>定价机制（混合模型 D）</h3>
 * <pre>
 *   finalPrice = fundamentalPrice + tradeMomentum + noiseOffset + eventImpact
 * </pre>
 * <ul>
 *   <li>fundamentalPrice = basePrice × REFERENCE_STOCK / npcStock（库存驱动，主导 ~70%）</li>
 *   <li>tradeMomentum（成交动能，单笔交易的短期冲击，随时间衰减 ~20%）</li>
 *   <li>noiseOffset（市场噪音 ±1~3%，模拟运输成本/信息误差 ~10%）</li>
 *   <li>eventImpact（主题事件倍率，偶发）</li>
 * </ul>
 *
 * <h3>报价</h3>
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — NPC 买入价</li>
 *   <li>askPrice = midPrice × (1 + spread) — NPC 卖出价</li>
 * </ul>
 */
public class MarketPrice {

    // ---- 价格波动参数 ----

    /** 价格下限比例（最低跌到基准价的 10%） */
    private static final double MIN_PRICE_RATIO = 0.1;

    /** 价格上限比例（最高涨到基准价的 10 倍） */
    private static final double MAX_PRICE_RATIO = 10.0;

    /** NPC 参考库存量（库存 = 此值时价格 = basePrice） */
    public static final long REFERENCE_STOCK = 100_000;

    /** 每种商品最多保留的快照数量 */
    static final int MAX_SNAPSHOTS = 200;

    /** 价格灵敏度：stock 偏离 REFERENCE 1% 时，fundamental 变动 SENSITIVITY% */
    private static final double SENSITIVITY = 5.0;

    /** 动量缩放：一笔相当于 REFERENCE 1% 的交易产生 MOMENTUM_SCALE% 的动量溢价 */
    private static final double MOMENTUM_SCALE = 50;

    /** 每分钟动量衰减率（乘 0.5 即减半） */
    private static final double MOMENTUM_DECAY = 0.5;

    /** 噪音偏移最大幅度（相对于 basePrice 20%） */
    private static final double MAX_NOISE_RATIO = 0.2;

    /** 动量衰减到低于此阈值则归零 */
    private static final double MOMENTUM_MIN = 0.001;

    private static final Random RANDOM = new Random();

    // ---- 字段 ----

    private final String commodityId;

    /** 当前中间价 */
    private long midPrice;

    /** 初始基准价（来自 CommodityRegistry，作为价格锚和波动计算基准） */
    private final long basePrice;

    /** 价差比例，默认 0.05（5%） */
    private double spread;

    /** 累计成交动量（会随时间衰减） */
    private double tradeMomentum;

    /** 当前噪音偏移（整价偏移，如 -1/0/+1） */
    private int noiseOffset;

    /** 当前生效的事件，null 表示无 */
    private MarketEvent activeEvent;

    /** 最近一次计算的 NPC 库存（用于 tick 后重算） */
    private long lastNpcStock;

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
        this.lastNpcStock = REFERENCE_STOCK;
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
    // 混合定价引擎
    // ================================================================

    /**
     * 每次 NPC 交易后调用。
     * @param npcStock    NPC 当前库存（交易后）
     * @param npcWasBuyer true = NPC 买入（玩家卖出，利空）
     * @param quantity    成交量
     */
    public void onNpcTrade(long npcStock, boolean npcWasBuyer, int quantity) {
        if (npcStock <= 0) return;

        // 动能：交易量占参考库存的比例 × 缩放系数
        double impact = (double) quantity / REFERENCE_STOCK * MOMENTUM_SCALE;
        tradeMomentum += npcWasBuyer ? -impact : impact;

        recalculate(npcStock);
    }

    /** 每分钟衰减动能 */
    public void tickMomentum() {
        tradeMomentum *= MOMENTUM_DECAY;
        if (Math.abs(tradeMomentum) < MOMENTUM_MIN) tradeMomentum = 0;
    }

    /** 每 3 分钟噪音随机游走，幅度不超过 basePrice 的 20% */
    public void tickNoise() {
        int maxNoise = Math.max(1, (int) Math.round(basePrice * MAX_NOISE_RATIO));
        noiseOffset += RANDOM.nextInt(3) - 1;
        if (noiseOffset > maxNoise) noiseOffset = maxNoise;
        if (noiseOffset < -maxNoise) noiseOffset = -maxNoise;
    }

    /** 应用主题事件 */
    public void applyEvent(MarketEvent event) {
        this.activeEvent = event;
    }

    /** 移除主题事件 */
    public void removeEvent() {
        this.activeEvent = null;
    }

    public boolean hasActiveEvent() {
        return activeEvent != null;
    }

    public MarketEvent getActiveEvent() {
        return activeEvent;
    }

    // ---- 内部计算 ----

    /**
     * 综合计算 finalPrice = fundamental + momentum + noise + event。
     */
    private void recalculate(long npcStock) {
        this.lastNpcStock = npcStock;

        // 1. 库存基准价
        double deviation = (double)(REFERENCE_STOCK - npcStock) / REFERENCE_STOCK;
        double fundamental = basePrice * (1.0 + deviation * SENSITIVITY);

        // 2. 事件倍率：确保对低价商品也有可见效果（至少 ±1）
        if (activeEvent != null) {
            double before = fundamental;
            fundamental *= activeEvent.getPriceMultiplier();
            if (Math.abs(fundamental - before) < 1.0 && Math.abs(fundamental - before) > 1e-6) {
                fundamental = before + Math.signum(fundamental - before);
            }
        }

        // 3. 动能 × 基准价 + 噪音整价偏移
        long price = Math.round(fundamental * (1.0 + tradeMomentum)) + noiseOffset;

        // 5. 限制波动范围
        long floor = Math.max(1, Math.round(basePrice * MIN_PRICE_RATIO));
        long ceiling = Math.round(basePrice * MAX_PRICE_RATIO);
        midPrice = Math.max(floor, Math.min(ceiling, price));
    }

    /**
     * 根据 NPC 当前库存重新计算 midPrice（无成交动能和噪音时使用）。
     * 保留以兼容持久化恢复和 seedNpcIfNeeded。
     */
    public void recomputePrice(long npcStock) {
        recalculate(npcStock);
    }

    /** 使用最近库存重新计算（tickMomentum/tickNoise 后调用） */
    public void recalculateFromCurrent() {
        if (lastNpcStock > 0) {
            recalculate(lastNpcStock);
        }
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
     * 重置 24h 统计和快照（服务器启动时调用，清除旧数据）。
     */
    public void resetDayStats() {
        dayHigh = midPrice;
        dayLow = midPrice;
        dayVolume = 0;
        dayOpen = midPrice;
        snapshots.clear();
        tradeMomentum = 0;
        noiseOffset = 0;
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
