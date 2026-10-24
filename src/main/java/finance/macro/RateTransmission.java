package finance.macro;

import finance.config.FinanceConfig;
import finance.policy.MonetaryPolicyService;

/**
 * 利率传导：央行基准利率经需求和库存影响物价。纯函数部分无状态，方便单测。
 *
 * <p>实际利率缺口 = (基准利率 − 预期通胀) − (中性利率 − 物价目标)。预期通胀取实际 CPI 通胀和物价目标各一半
 * （适应性预期），并限制在 ±5%，避免“通胀 → 实际利率变低 → 多买 → 更通胀”在两次议息之间自我加速。</p>
 *
 * <ul>
 *   <li>需求端：缺口为正（利率偏紧）时村民少买，偏松时多买，见 {@link #consumptionBps}；</li>
 *   <li>供给端：国际市场定价用的参考库存随缺口缩放，偏紧时现有库存显得过剩、价格回落，见 {@link #referenceStockBps}；
 *       NPC 公司自动卖货也随缺口多卖或少卖，见 {@link #sellRatioBps}。</li>
 * </ul>
 */
public final class RateTransmission {
    public static final int NEUTRAL_BPS = 10_000;
    public static final int MIN_CONSUMPTION_BPS = 8_500;
    public static final int MAX_CONSUMPTION_BPS = 11_500;
    public static final int MIN_REFERENCE_BPS = 8_000;
    public static final int MAX_REFERENCE_BPS = 12_000;
    /** 预期通胀计入实际 CPI 通胀的上限（基点）。 */
    static final int MAX_EXPECTED_INFLATION_BPS = 500;
    /** 实际利率缺口的上限（基点）。 */
    static final int MAX_GAP_BPS = 1_000;

    private RateTransmission() {}

    /** 当前实际利率缺口（基点）：正数偏紧，负数偏松。 */
    public static int realRateGapBps() {
        // 宏观经济没开或 CPI 历史不足两天时，按通胀正好等于目标处理（缺口只看名义利率偏离中性多少）
        int inflation = MacroEconomyService.active()
                        && MacroEconomyManager.snapshots().size() >= 2
                ? (int) Math.max(
                        -1_000_000, Math.min(1_000_000, Math.round(MacroIndicators.annualizedInflationPct() * 100)))
                : FinanceConfig.cbInflationTargetBps();
        return realRateGapBps(
                MonetaryPolicyService.benchmarkRateBasisPoints(),
                inflation,
                FinanceConfig.cbInflationTargetBps(),
                FinanceConfig.policyNeutralRateBps());
    }

    /** 实际利率缺口（基点），见类注释。 */
    public static int realRateGapBps(int rateBps, int inflationBps, int targetBps, int neutralBps) {
        long actual = Math.max(-MAX_EXPECTED_INFLATION_BPS, Math.min(MAX_EXPECTED_INFLATION_BPS, (long) inflationBps));
        long expected = (actual + targetBps) / 2;
        long gap = ((long) rateBps - expected) - ((long) neutralBps - targetBps);
        return (int) Math.max(-MAX_GAP_BPS, Math.min(MAX_GAP_BPS, gap));
    }

    /**
     * 消费倾向（基点，10000 = 不变）：缺口每 100 基点，消费少 {@code sensitivity}%；食物是必需品，敏感度减半。
     * 限制在 {@link #MIN_CONSUMPTION_BPS}～{@link #MAX_CONSUMPTION_BPS}。
     */
    public static int consumptionBps(int gapBps, boolean necessity, double sensitivity) {
        double s = Math.max(0, sensitivity) * (necessity ? 0.5 : 1.0);
        long v = Math.round(NEUTRAL_BPS - gapBps * s);
        return (int) Math.max(MIN_CONSUMPTION_BPS, Math.min(MAX_CONSUMPTION_BPS, v));
    }

    /** 参考库存倍率（基点）：缺口每 100 基点，参考库存低 {@code sensitivity}%，限制在 80%～120%。 */
    public static int referenceStockBps(int gapBps, double sensitivity) {
        long v = Math.round(NEUTRAL_BPS - gapBps * Math.max(0, sensitivity));
        return (int) Math.max(MIN_REFERENCE_BPS, Math.min(MAX_REFERENCE_BPS, v));
    }

    /** NPC 公司自动卖货比例的倍率（基点）：和非食物消费倾向反着走，利率高多卖、少囤货。 */
    public static int sellRatioBps(int gapBps, double sensitivity) {
        return 2 * NEUTRAL_BPS - consumptionBps(gapBps, false, sensitivity);
    }

    // ---------------------------------------------------------------- 按当前利率

    public static int consumptionBps(NeedCategory category) {
        return consumptionBps(
                realRateGapBps(), category == NeedCategory.FOOD, FinanceConfig.macroRateDemandSensitivity());
    }

    public static int referenceStockBps() {
        return referenceStockBps(realRateGapBps(), FinanceConfig.macroRateStockSensitivity());
    }

    public static int sellRatioBps() {
        return sellRatioBps(realRateGapBps(), FinanceConfig.macroRateDemandSensitivity());
    }
}
