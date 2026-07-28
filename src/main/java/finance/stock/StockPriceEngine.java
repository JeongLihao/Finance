package finance.stock;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 股票混合定价引擎 —— 基本面锚 + 盘口推动 + 动量衰减 + 噪音。
 * 参考 MarketPrice 设计，实现价格的短期波动与长期均值回归。
 *
 * <h3>定价公式</h3>
 * <pre>
 *   fairValue = (公司总估值 + 盈利能力 × PE系数) / totalShares  ← 基本面锚（每 MC 天更新）
 *   基准 = 现价向 fairValue 缓慢回归
 *   盘口推动 = 成交量 / floatShares × IMPACT_SCALE（买入 +，卖出 -）
 *   最终价 = 基准 × (1 + tradeMomentum) + noiseOffset，夹逼在 fairValue 的 [0.3, 3.0] 倍
 * </pre>
 */
public class StockPriceEngine {

    // ---- 参数 ----

    /** 价格向 fairValue 回归的速度（每 tick） */
    private static final double MEAN_REVERT_FACTOR = 0.05;

    /** 动量缩放：成交量占流通股 1% 时产生的动量百分比 */
    private static final double MOMENTUM_SCALE = 30;

    /** 每分钟动量衰减率（乘 0.5 即减半） */
    private static final double MOMENTUM_DECAY = 0.5;

    /** 动量低于此阈值时归零 */
    private static final double MOMENTUM_MIN = 0.001;

    /** 噪音偏移最大幅度（相对于 fairValue 的百分比） */
    private static final double MAX_NOISE_RATIO = 0.15;

    /** 价格夹逼范围：fairValue × [MIN, MAX] */
    private static final double MIN_PRICE_RATIO = 0.3;
    private static final double MAX_PRICE_RATIO = 3.0;

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

    // ================================================================
    // 定价逻辑
    // ================================================================

    /**
     * 基本面更新（每 MC 天调用一次）。
     * 重新计算 fairValue，驱动价格回归。
     *
     * @param companyTotalValue 公司总估值（现金 + 库存市值）
     * @param totalShares       总股本
     * @param dailyProfit       最近日利润（未来用于 P3 分红/PE 计算）
     */
    public void updateFairValue(long companyTotalValue, long totalShares, long dailyProfit) {
        if (totalShares <= 0) return;

        // 简化版：暂时只考虑估值 / totalShares（P3 时加 PE 系数）
        long newFairValue = Math.max(1, companyTotalValue / totalShares);
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
}
