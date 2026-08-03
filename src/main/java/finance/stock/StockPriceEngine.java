package finance.stock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 股票混合定价引擎 —— 基本面锚 + 盘口推动 + 动量衰减 + 噪音。
 * 参考 MarketPrice 设计，实现价格的短期波动与长期均值回归。
 *
 * <h3>定价公式</h3>
 * <pre>
 *   fairValue = (资产价值 + 平滑利润 × 景气PE) × 风险折价 / totalShares  ← 基本面锚（每 MC 天更新）
 *   基准 = 现价向 fairValue 缓慢回归
 *   盘口推动 = 成交量 / floatShares × IMPACT_SCALE（买入 +，卖出 -）
 *   最终价 = 基准 × (1 + tradeMomentum) + noiseOffset，夹逼在 fairValue 的 [0.3, 3.0] 倍
 * </pre>
 */
public class StockPriceEngine {

    // ---- 参数 ----

    /** 价格向 fairValue 回归的速度（每 tick） */
    private static final double MEAN_REVERT_FACTOR = 0.08;

    /** 动量缩放：成交量占流通股 1% 时产生的动量百分比 */
    private static final double MOMENTUM_SCALE = 18;

    /** 每分钟动量衰减率（乘 0.5 即减半） */
    private static final double MOMENTUM_DECAY = 0.5;

    /** 动量低于此阈值时归零 */
    private static final double MOMENTUM_MIN = 0.001;

    /** 噪音偏移最大幅度（相对于 fairValue 的百分比） */
    private static final double MAX_NOISE_RATIO = 0.06;

    /** 价格夹逼范围：fairValue × [MIN, MAX] */
    private static final double MIN_PRICE_RATIO = 0.45;
    private static final double MAX_PRICE_RATIO = 1.85;

    /** 每只股票最多保留的价格快照数量。 */
    static final int MAX_SNAPSHOTS = 200;

    // ---- 字段 ----

    private final String symbol;
    private long currentPrice;

    /** 公司公允价（基本面锚，每 MC 天更新） */
    private long fairValue;

    /** 买卖盘动量（会随时间衰减） */
    private double tradeMomentum;

    /** 噪音偏移（整价） */
    private int noiseOffset;

    // ---- 24h 统计 ----
    private long dayHigh;
    private long dayLow;
    private long dayVolume;
    private long dayOpen;

    // ---- 价格历史 ----
    private final List<PriceSnapshot> snapshots = new ArrayList<>();

    // ================================================================
    // 构造
    // ================================================================

    public StockPriceEngine(String symbol, long initialPrice, long initialFairValue) {
        this.symbol = symbol;
        this.currentPrice = initialPrice;
        this.fairValue = initialFairValue;
        this.dayHigh = initialPrice;
        this.dayLow = initialPrice;
        this.dayOpen = initialPrice;
        this.dayVolume = 0;
        this.tradeMomentum = 0;
        this.noiseOffset = 0;
    }

    // ================================================================
    // 基础 getter
    // ================================================================

    public String getSymbol() {
        return symbol;
    }

    public long getCurrentPrice() {
        return currentPrice;
    }

    public long getFairValue() {
        return fairValue;
    }

    public double getTradeMomentum() {
        return tradeMomentum;
    }

    public int getNoiseOffset() {
        return noiseOffset;
    }

    public long getDayHigh() {
        return dayHigh;
    }

    public long getDayLow() {
        return dayLow;
    }

    public long getDayVolume() {
        return dayVolume;
    }

    public long getDayOpen() {
        return dayOpen;
    }

    public double getDayChange() {
        if (dayOpen == 0) return 0;
        return (double) (currentPrice - dayOpen) / dayOpen * 100;
    }

    public List<PriceSnapshot> getSnapshots() {
        return snapshots;
    }

    // ================================================================
    // 定价逻辑
    // ================================================================

    /**
     * 基本面更新（每 MC 天调用一次）。
     * 重新计算 fairValue，驱动价格回归。
     *
     * <h3>P3 改进：加入 PE 系数</h3>
     * <pre>
     *   assetValue = 现金 + 折价库存
     *   profitValue = 7日平滑利润 × 景气 PE
     *   riskDiscount = 行业景气差时下调估值
     *   fairValue = (assetValue + profitValue) × riskDiscount / totalShares
     * </pre>
     *
     * @param companyAssetValue 股票基本面资产值（现金 + 折价库存）
     * @param totalShares       总股本
     * @param smoothedDailyProfit 最近 7 日平滑利润
     * @param industrySentiment 行业景气（核心商品价格 / 基准价）
     */
    public void updateFairValue(long companyAssetValue, long totalShares,
                                long smoothedDailyProfit, double industrySentiment) {
        if (totalShares <= 0) return;

        double sentiment = clamp(industrySentiment, 0.35, 2.0);
        double pe = clamp(6.0 + sentiment * 4.0, 5.0, 14.0);
        double riskDiscount = clamp(0.55 + sentiment * 0.35, 0.45, 1.15);

        long profitCapitalization = Math.round(smoothedDailyProfit * pe);
        long grossValue = Math.max(0, companyAssetValue + profitCapitalization);
        long adjustedValue = Math.round(grossValue * riskDiscount);
        long newFairValue = Math.max(1, adjustedValue / totalShares);
        this.fairValue = newFairValue;

        // 基本面更新后重算价格
        recalculate();
    }

    /**
     * 成交时调用 —— 盘口推动动量。
     * 买入push价格向上、卖出向下，幅度 ∝ 成交量占流通股比例。
     *
     * @param tradePrice     成交单价
     * @param tradeVolume    成交数量
     * @param floatShares    流通股（分母）
     * @param isBuy          true = 买入，false = 卖出
     */
    public void recordTrade(long tradePrice, long tradeVolume, long floatShares, boolean isBuy) {
        // 更新 24h 统计
        if (dayVolume == 0) {
            dayHigh = tradePrice;
            dayLow = tradePrice;
        } else {
            if (tradePrice > dayHigh) dayHigh = tradePrice;
            if (tradePrice < dayLow) dayLow = tradePrice;
        }
        dayVolume += tradeVolume;

        // 计算动量冲击
        if (floatShares > 0) {
            double volumeRatio = (double) tradeVolume / floatShares; // 占流通股的比例
            double impact = volumeRatio * MOMENTUM_SCALE; // 单位：百分比点
            tradeMomentum += isBuy ? impact : -impact;
        }

        // 立即重算价格
        recalculate();
        recordSnapshot(tradeVolume);
    }

    /**
     * 每分钟衰减动量（Tick 中调用）。
     */
    public void tickMomentum() {
        tradeMomentum *= MOMENTUM_DECAY;
        if (Math.abs(tradeMomentum) < MOMENTUM_MIN) {
            tradeMomentum = 0;
        }
    }

    /**
     * 每 3 分钟刷新噪音（Tick 中调用）。
     */
    public void tickNoise() {
        if (fairValue <= 0) return;
        int maxNoise = Math.max(1, (int) Math.round(fairValue * MAX_NOISE_RATIO));
        noiseOffset += ThreadLocalRandom.current().nextInt(3) - 1;
        if (noiseOffset > maxNoise) noiseOffset = maxNoise;
        if (noiseOffset < -maxNoise) noiseOffset = -maxNoise;
    }

    /**
     * 动量衰减或噪音变化后，重新计算价格（在 tick 中调用）。
     */
    public void recalculateFromCurrent() {
        recalculate();
    }

    public void recordSnapshot(long volume) {
        snapshots.add(new PriceSnapshot(LocalDateTime.now(), currentPrice, volume));
        trimSnapshots();
    }

    public void addSnapshotDirect(PriceSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshots.add(snapshot);
        trimSnapshots();
    }

    public void clearSnapshots() {
        snapshots.clear();
    }

    /**
     * 核心定价计算。
     */
    private void recalculate() {
        if (fairValue <= 0) return;

        // 1. 向 fairValue 回归
        long target = fairValue;
        long base = Math.round(currentPrice + (target - currentPrice) * MEAN_REVERT_FACTOR);

        // 2. 动量 + 噪音
        long price = Math.round(base * (1.0 + tradeMomentum / 100.0)) + noiseOffset;

        // 3. 夹逼在 fairValue 的 [0.3, 3.0] 倍
        long floor = Math.max(1, Math.round(fairValue * MIN_PRICE_RATIO));
        long ceiling = Math.round(fairValue * MAX_PRICE_RATIO);
        currentPrice = Math.max(floor, Math.min(ceiling, price));
    }

    // ================================================================
    // 每日重置
    // ================================================================

    public void newDayReset() {
        dayOpen = currentPrice;
        dayHigh = currentPrice;
        dayLow = currentPrice;
        dayVolume = 0;
        recordSnapshot(0);
    }

    // ================================================================
    // 持久化支持
    // ================================================================

    public void setCurrentPrice(long price) {
        this.currentPrice = price;
    }

    public void setFairValue(long fairValue) {
        this.fairValue = fairValue;
    }

    public void setTradeMomentum(double momentum) {
        this.tradeMomentum = momentum;
    }

    public void setNoiseOffset(int offset) {
        int maxNoise = fairValue > 0 ? Math.max(1, (int) Math.round(fairValue * MAX_NOISE_RATIO)) : 0;
        this.noiseOffset = Math.max(-maxNoise, Math.min(maxNoise, offset));
    }

    public void setDayOpen(long dayOpen) {
        this.dayOpen = dayOpen;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void trimSnapshots() {
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.subList(0, snapshots.size() - MAX_SNAPSHOTS).clear();
        }
    }

    public static class PriceSnapshot {
        private final LocalDateTime timestamp;
        private final long price;
        private final long volume;

        public PriceSnapshot(LocalDateTime timestamp, long price, long volume) {
            this.timestamp = timestamp;
            this.price = price;
            this.volume = volume;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public long getPrice() {
            return price;
        }

        public long getVolume() {
            return volume;
        }
    }
}
