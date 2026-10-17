package finance.policy;

import finance.config.FinanceConfig;

/**
 * 基准利率政策规则 —— 纯函数，不触碰世界状态，可单测。
 *
 * <p>类 Taylor 规则：目标利率 = 中性利率 ＋ a×(通胀 − 物价目标) − b×衰退指标，
 * 再经单次步长钳制与上下限钳制。噪音（缺口小于最小调整单位）直接维持。</p>
 */
public final class PolicyRuleService {

    public enum Direction { HIKE, HOLD, CUT }

    public record Evaluation(
            int currentBps,
            double annualizedInflationPct,
            double slackRatio,
            int targetBps,
            int recommendedBps,
            Direction direction,
            String explanation) {
    }

    private PolicyRuleService() {
    }

    /**
     * 评估政策建议。
     *
     * @param currentBps     当前基准利率（基点）
     * @param priceIndexNow  当前价格指数（≈100 为基期）
     * @param priceIndexBase 对照期价格指数（约 30 天前；&le;0 表示无历史，按无通胀处理）
     * @param slackRatio     衰退指标 0..1（破产风险公司占比），越界按边界钳制
     */
    public static Evaluation evaluate(int currentBps, double priceIndexNow,
                                      double priceIndexBase, double slackRatio) {
        double inflation = 0.0;
        if (priceIndexBase > 0 && priceIndexNow > 0) {
            inflation = (priceIndexNow - priceIndexBase) / priceIndexBase * (365.0 / 30.0) * 100.0;
        }
        double slack = Math.min(1.0, Math.max(0.0, slackRatio));

        int neutral = FinanceConfig.policyNeutralRateBps();
        double a = FinanceConfig.policyInflationResponse();
        double b = FinanceConfig.policySlackResponse();
        // 通胀缺口 = 实际通胀 − 央行物价目标，单位基点（高出目标 1% = 100bps）；衰退单位：基点（10% 占比 = 1000bps）
        double gapBps = inflation * 100.0 - FinanceConfig.cbInflationTargetBps();
        long target = Math.round(neutral + a * gapBps - b * slack * 10_000.0);
        int min = FinanceConfig.minBenchmarkRateBasisPoints();
        int max = FinanceConfig.maxBenchmarkRateBasisPoints();
        int targetClamped = (int) Math.min(max, Math.max(min, target));

        int maxStep = Math.max(1, FinanceConfig.policyMaxStepBps());
        int minStep = Math.max(1, FinanceConfig.policyMinStepBps());
        long delta = (long) targetClamped - currentBps;
        long stepped = Math.min(maxStep, Math.max(-maxStep, delta));
        int recommended;
        Direction direction;
        if (Math.abs(stepped) < minStep) {
            recommended = currentBps;
            direction = Direction.HOLD;
        } else {
            recommended = (int) Math.min(max, Math.max(min, (long) currentBps + stepped));
            direction = recommended > currentBps ? Direction.HIKE
                    : recommended < currentBps ? Direction.CUT : Direction.HOLD;
        }

        String explanation = String.format(java.util.Locale.ROOT,
                "通胀%.2f%%（目标%.2f%%）/衰退%.1f%%/目标利率%d基点", inflation,
                FinanceConfig.cbInflationTargetBps() / 100.0, slack * 100.0, targetClamped);
        return new Evaluation(currentBps, inflation, slack, targetClamped,
                recommended, direction, explanation);
    }

    /** 方向文案（中文，用于前瞻与公告）；null 视为未知，返回占位符而非 NPE。 */
    public static String directionLabel(Direction direction) {
        if (direction == null) return "—";
        return switch (direction) {
            case HIKE -> "上调";
            case CUT -> "下调";
            case HOLD -> "维持";
        };
    }
}
