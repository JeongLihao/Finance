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

    /**
     * 市价每分钟向每股价值回归的比例。0.5%/分钟：半衰期约 2 小时现实时间（约 7 个游戏日），
     * 大家持续买入时泡沫能吹起来，没人买了才慢慢回落。
     */
    static final double REVERT_PER_MINUTE = 0.005;

    /** 成交冲击：成交量占流通股 1%，市价就动 1.5%（买涨卖跌，永久推动，靠均值回归慢慢拉回）。 */
    static final double TRADE_IMPACT = 1.5;

    /** 熔断：5 分钟内跌超 15%，暂停交易 1 分钟。 */
    static final double BREAKER_DROP = 0.15;

    static final int BREAKER_WINDOW_MINUTES = 5;
    static final int BREAKER_HALT_MINUTES = 1;

    /** 动量缩放：成交量占流通股 1% 时产生的动量百分比 */
    private static final double MOMENTUM_SCALE = 18;

    /** 每分钟动量衰减率（乘 0.5 即减半） */
    private static final double MOMENTUM_DECAY = 0.5;

    /** 动量低于此阈值时归零 */
    private static final double MOMENTUM_MIN = 0.001;

    /** 噪音偏移最大幅度（相对于 fairValue 的百分比）：只叠加在显示价上，不累积进市价。 */
    private static final double MAX_NOISE_RATIO = 0.01;

    /** 市价范围：fairValue × [MIN, MAX]。放宽到 0.2~5 倍，泡沫和股灾才有空间。 */
    static final double MIN_PRICE_RATIO = 0.2;

    static final double MAX_PRICE_RATIO = 5.0;

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

    /** 市价（不含噪音）：成交推动它，每分钟向 fairValue 回归；显示价 = 市价 + 噪音。≤ 0 表示还没初始化。 */
    private double anchor = -1;

    /** 最近几分钟的市价（熔断用），最旧的在前。 */
    private final java.util.ArrayDeque<Double> minuteAnchors = new java.util.ArrayDeque<>();

    /** 熔断剩余分钟数；累计熔断次数（报纸和面板用，不存档）。 */
    private int breakerMinutes;

    private int breakerTrips;

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
    public void updateFairValue(
            long companyAssetValue, long totalShares, long smoothedDailyProfit, double industrySentiment) {
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

    /** 直接设定每股价值（新估值，见 {@link StockValuation}），现价随后逐步向它靠拢。 */
    public void setFairValueTarget(long newFairValue) {
        this.fairValue = Math.max(1, newFairValue);
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

        // 计算动量冲击（动量只用于显示和熔断判断以外的提示；真正推动价格的是市价冲击）
        if (floatShares > 0) {
            double volumeRatio = (double) tradeVolume / floatShares; // 占流通股的比例
            double impact = volumeRatio * MOMENTUM_SCALE; // 单位：百分比点
            tradeMomentum += isBuy ? impact : -impact;
            double move = Math.min(0.5, volumeRatio * TRADE_IMPACT);
            anchor = anchor() * (isBuy ? 1.0 + move : 1.0 - move);
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

    /** 这笔成交推动之后的市价（不真的推动）：平仓按它成交，加杠杆来回买卖一定亏。 */
    public long impactedPrice(long qty, long floatShares, boolean buy) {
        if (floatShares <= 0 || qty <= 0) return getAnchorPrice();
        double move = Math.min(0.5, (double) qty / floatShares * TRADE_IMPACT);
        return Math.max(1, Math.round(anchor() * (buy ? 1.0 + move : 1.0 - move)));
    }

    /** 市价（不含噪音）。 */
    public long getAnchorPrice() {
        return Math.max(1, Math.round(anchor()));
    }

    private double anchor() {
        if (anchor <= 0) anchor = Math.max(1, currentPrice - noiseOffset);
        return anchor;
    }

    /** 是否在熔断中（暂停交易）。 */
    public boolean isBreakerHalted() {
        return breakerMinutes > 0;
    }

    public int getBreakerTrips() {
        return breakerTrips;
    }

    /** 和 BREAKER_WINDOW_MINUTES 分钟前比跌超 BREAKER_DROP 就熔断；熔断后重新计窗口，避免恢复后立刻再触发。 */
    private void checkBreaker() {
        if (breakerMinutes > 0 || minuteAnchors.isEmpty()) return;
        double reference = minuteAnchors.peekFirst();
        if (reference > 0 && anchor() < reference * (1.0 - BREAKER_DROP)) {
            breakerMinutes = BREAKER_HALT_MINUTES;
            breakerTrips++;
            minuteAnchors.clear();
            minuteAnchors.addLast(anchor());
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
     * 每分钟调用一次：熔断倒计时、市价向 fairValue 回归一点、记下这一分钟的市价，再算显示价。
     */
    public void recalculateFromCurrent() {
        if (breakerMinutes > 0) breakerMinutes--;
        if (fairValue > 0) anchor = anchor() + (fairValue - anchor()) * REVERT_PER_MINUTE;
        minuteAnchors.addLast(anchor());
        while (minuteAnchors.size() > BREAKER_WINDOW_MINUTES + 1) minuteAnchors.removeFirst();
        recalculate();
    }

    /**
     * 财报季：按预期差冲击股价。
     *
     * <p>fairValue 按 {@code shockBps × fvPassThrough} 永久修正（预期差意味着未来盈利预期真的变了），
     * currentPrice 按 shockBps 即时冲击；最后走 recalculate() 夹逼在 fairValue × [0.45, 1.85]，
     * 单次财报不可能闪崩归零。
     *
     * @param shockBps      最终股价冲击（bps，可正可负；调用方已做钳制）
     * @param fvPassThrough fairValue 透传比例 [0, 1]
     */
    public void applyEarningsShock(double shockBps, double fvPassThrough) {
        if (fairValue <= 0) {
            return;
        }
        double shockPct = Math.max(-0.5, Math.min(0.5, shockBps / 10000.0));
        if (shockPct == 0) {
            return;
        }
        double pass = Math.max(0.0, Math.min(1.0, fvPassThrough));
        fairValue = Math.max(1, Math.round(fairValue * (1.0 + shockPct * pass)));
        anchor = anchor() * (1.0 + shockPct);
        currentPrice = Math.max(1, Math.round(currentPrice * (1.0 + shockPct)));
        recalculate();
        recordSnapshot(0);
    }

    /**
     * 分红除权：按每股分红额下调现价与 fairValue（不低于 1），走 recalculate() 夹逼。
     *
     * <p>fairValue 必须同步下调：分红是真实现金流出，公司价值确实减少。
     * 若只降现价不降 fairValue，钉在 0.45×fairValue 下限的股票会被夹逼完整推回，
     * 股东拿了分红现金、股价一分没降，形成永久分红漏损（且下限只随 fairValue
     * 下降 0.45 倍，漏损不会自我修复）；同时均值回归会在除权瞬间收回约 8% 的下调。
     * fairValue 同步下调后，(fairValue − currentPrice) 不变，均值回归不再吞除权，
     * 夹逼下限同步下移，次日 updateFairValue 会按基本面重算，自愈。
     */
    public void applyExDividend(long perShare) {
        if (perShare <= 0 || fairValue <= 0) {
            return;
        }
        fairValue = Math.max(1, fairValue - perShare);
        anchor = Math.max(1, anchor() - perShare);
        currentPrice = Math.max(1, currentPrice - perShare);
        recalculate();
        recordSnapshot(0); // 与 applyEarningsShock 一致：除权也在 K 线留点
    }

    public void recordSnapshot(long volume) {
        snapshots.add(new PriceSnapshot(LocalDateTime.now(), currentPrice, volume));
        trimSnapshots();
    }

    /** 定时快照：价格没变就不记，走势图上每个点都是一次价格变动。 */
    public void recordSnapshotIfChanged() {
        if (!snapshots.isEmpty() && snapshots.get(snapshots.size() - 1).getPrice() == currentPrice) return;
        recordSnapshot(0);
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
        // 市价夹在 fairValue × [0.2, 5]；显示价 = 市价 + 噪音（噪音不累积进市价）
        double floor = Math.max(1, fairValue * MIN_PRICE_RATIO);
        double ceiling = fairValue * MAX_PRICE_RATIO;
        anchor = Math.max(floor, Math.min(ceiling, anchor()));
        checkBreaker();
        long price = Math.round(anchor) + noiseOffset;
        currentPrice = Math.max(Math.max(1, Math.round(floor)), Math.min(Math.round(ceiling), price));
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
        this.anchor = price;
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
