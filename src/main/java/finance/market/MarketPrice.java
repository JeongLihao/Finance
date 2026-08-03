package finance.market;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import finance.event.MarketEvent;

/**
 * 商品中间价 —— 国际市场的报价基准。
 *
 * <h3>定价机制（混合模型 D）</h3>
 * <pre>
 *   finalPrice = fundamentalPrice × (1 + tradeMomentum) + noiseOffset (+ eventImpact via fundamentalPrice multiplier)
 * </pre>
 * <ul>
 *   <li>fundamentalPrice = basePrice × supplyFactor（库存驱动，但平滑夹逼）</li>
 *   <li>tradeMomentum（成交动能，单笔交易的短期冲击，随时间衰减）</li>
 *   <li>trendMomentum（供需锚持续偏离时形成的缓慢趋势）</li>
 *   <li>noiseOffset（市场噪音 ±1~3%，模拟运输成本/信息误差 ~10%）</li>
 *   <li>eventImpact（主题事件倍率，偶发）</li>
 * </ul>
 *
 * <h3>报价</h3>
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — 国际市场买入价</li>
 *   <li>askPrice = midPrice × (1 + spread) — 国际市场卖出价</li>
 * </ul>
 */
public class MarketPrice {

    // ---- 价格波动参数 ----

    /** 价格下限比例（最低跌到基准价的 35%） */
    private static final double MIN_PRICE_RATIO = 0.35;

    /** 价格上限比例（最高涨到基准价的 260%） */
    private static final double MAX_PRICE_RATIO = 2.6;

    /** 国际市场参考库存量（库存 = 此值时价格 = basePrice） */
    public static final long REFERENCE_STOCK = 100_000;

    /** 每种商品最多保留的快照数量 */
    static final int MAX_SNAPSHOTS = 200;

    /** 供需弹性：库存翻倍/腰斩时，价格不会线性暴涨暴跌 */
    private static final double SUPPLY_ELASTICITY = 0.35;

    /** 动量缩放：一笔相当于 REFERENCE 1% 的交易产生 MOMENTUM_SCALE% 的动量溢价 */
    private static final double MOMENTUM_SCALE = 18;

    /** 每分钟动量衰减率（乘 0.5 即减半） */
    private static final double MOMENTUM_DECAY = 0.5;

    /** 噪音偏移最大幅度（相对于 basePrice 6%） */
    private static final double MAX_NOISE_RATIO = 0.06;

    /** 价格向目标价靠拢的比例，避免单日/单次重算跳变过大 */
    private static final double PRICE_SMOOTHING = 0.35;

    /** 趋势动量衰减率 */
    private static final double TREND_DECAY = 0.85;

    /** 趋势动量最大幅度 */
    private static final double MAX_TREND = 0.25;

    /** 动量衰减到低于此阈值则归零 */
    private static final double MOMENTUM_MIN = 0.001;


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

    /** 供需目标价长期高/低于现价时形成的趋势动量 */
    private double trendMomentum;

    /** 当前噪音偏移（整价偏移，如 -1/0/+1） */
    private int noiseOffset;

    /** 当前生效的事件，null 表示无 */
    private MarketEvent activeEvent;

    /** 最近一次计算的国际市场库存（用于 tick 后重算） */
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

    /** 国际市场买入价（玩家卖出商品时的单价），最低为 1 */
    public long getBidPrice() {
        return Math.max(1, (long) (midPrice * (1 - spread)));
    }

    /** 国际市场卖出价（玩家买入商品时的单价） */
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
     * 每次国际市场交易后调用。
     * @param npcStock    国际市场当前库存（交易后）
     * @param npcWasBuyer true = 国际市场买入（玩家卖出，利空）
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
        noiseOffset += ThreadLocalRandom.current().nextInt(3) - 1;
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

    public double getTradeMomentum() { return tradeMomentum; }
    public double getTrendMomentum() { return trendMomentum; }
    public int getNoiseOffset() { return noiseOffset; }

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

        // 1. 供需基准价：库存越高越便宜，但使用幂函数平滑，避免公司日常卖货造成单边崩盘。
        double safeStock = Math.max(1.0, npcStock);
        double supplyFactor = Math.pow((double) REFERENCE_STOCK / safeStock, SUPPLY_ELASTICITY);
        supplyFactor = clamp(supplyFactor, MIN_PRICE_RATIO, MAX_PRICE_RATIO);
        double fundamental = basePrice * supplyFactor;

        // 2. 事件倍率：确保对低价商品也有可见效果（至少 ±1）
        if (activeEvent != null) {
            double before = fundamental;
            fundamental *= activeEvent.getPriceMultiplier();
            if (Math.abs(fundamental - before) < 1.0 && Math.abs(fundamental - before) > 1e-6) {
                fundamental = before + Math.signum(fundamental - before);
            }
        }

        // 3. 趋势：目标价持续高/低于现价时缓慢形成，而不是一次交易立刻打满。
        double trendSignal = (fundamental - midPrice) / Math.max(1.0, basePrice);
        trendMomentum = clamp(trendMomentum * TREND_DECAY + trendSignal * (1.0 - TREND_DECAY),
                -MAX_TREND, MAX_TREND);

        // 4. 动能 + 趋势 + 噪音。交易动能是短期冲击，趋势是慢变量。
        double targetPrice = fundamental * (1.0 + tradeMomentum + trendMomentum * 0.35) + noiseOffset;
        long smoothedPrice = Math.round(midPrice + (targetPrice - midPrice) * PRICE_SMOOTHING);

        // 5. 限制波动范围
        long floor = Math.max(1, Math.round(basePrice * MIN_PRICE_RATIO));
        long ceiling = Math.round(basePrice * MAX_PRICE_RATIO);
        midPrice = Math.max(floor, Math.min(ceiling, smoothedPrice));
    }

    /**
     * 根据国际市场当前库存重新计算 midPrice（无成交动能和噪音时使用）。
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
     * 记录一次国际市场交易，更新 24h 统计和价格快照。
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
        trimSnapshots();
    }

    public void recordPeriodicSnapshot() {
        snapshots.add(new PriceSnapshot(LocalDateTime.now(), midPrice, 0));
        trimSnapshots();
    }

    public void clearSnapshots() {
        snapshots.clear();
    }

    // ---- setter ----

    public void setMidPrice(long midPrice) {
        this.midPrice = midPrice;
    }

    public void setSpread(double spread) {
        this.spread = spread;
    }

    public void setTradeMomentum(double tradeMomentum) {
        this.tradeMomentum = tradeMomentum;
    }

    public void setTrendMomentum(double trendMomentum) {
        this.trendMomentum = clamp(trendMomentum, -MAX_TREND, MAX_TREND);
    }

    public void setNoiseOffset(int noiseOffset) {
        int maxNoise = Math.max(1, (int) Math.round(basePrice * MAX_NOISE_RATIO));
        if (noiseOffset > maxNoise) {
            this.noiseOffset = maxNoise;
        } else if (noiseOffset < -maxNoise) {
            this.noiseOffset = -maxNoise;
        } else {
            this.noiseOffset = noiseOffset;
        }
    }

    /** 设置 24h 开盘价（持久化恢复时使用） */
    public void setDayOpen(long dayOpen) {
        this.dayOpen = dayOpen;
    }

    /**
     * 重置全部统计（新商品首次初始化时调用）。
     */
    public void resetDayStats() {
        dayHigh = midPrice;
        dayLow = midPrice;
        dayVolume = 0;
        dayOpen = midPrice;
        snapshots.clear();
        tradeMomentum = 0;
        trendMomentum = 0;
        noiseOffset = 0;
    }

    /**
     * 仅重置 24h OHLC（服务器重启时用于已持久化的商品，
     * 保留 snapshots、momentum、noiseOffset）。
     */
    public void resetDayStatsOnly() {
        dayHigh = midPrice;
        dayLow = midPrice;
        dayVolume = 0;
        dayOpen = midPrice;
    }

    /** 每个 MC 天结束时调用，重置日内统计并记录新一天的开盘价 */
    public void newDayReset() {
        dayOpen = midPrice;
        dayHigh = midPrice;
        dayLow = midPrice;
        dayVolume = 0;
    }

    /** 从磁盘恢复快照（持久化加载时使用） */
    public void addSnapshotDirect(PriceSnapshot snap) {
        snapshots.add(snap);
        trimSnapshots();
    }

    /** 设置最近一次计算的国际市场库存（持久化恢复后使用，使 recalculateFromCurrent 可用） */
    public void setLastNpcStock(long stock) {
        this.lastNpcStock = stock;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void trimSnapshots() {
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.subList(0, snapshots.size() - MAX_SNAPSHOTS).clear();
        }
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
     * 价格快照 —— 记录每次国际市场交易后的 midPrice 和成交量。
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
