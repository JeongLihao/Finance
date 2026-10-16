package finance.macro;

import java.util.Map;

/**
 * 宏观经济里的纯公式：本地价格系数、生活水平、人口增长。全部无状态，方便单测。
 */
public final class MacroFormulas {
    public static final int NEUTRAL_BPS = 10_000;
    public static final int MIN_FACTOR_BPS = 2_500;
    public static final int MAX_FACTOR_BPS = 17_500;
    public static final int MAX_SOL = 10_000;
    /** 人均日收入每 1 元带来的生活水平加成。 */
    public static final int INCOME_SOL_PER_UNIT = 150;
    public static final int MAX_INCOME_SOL = 1_500;

    private MacroFormulas() { }

    /**
     * 维多利亚 3 式目标价格系数：{@code 10000 × (1 + 0.75 × clamp((需求−供给)/min(需求,供给), −1, 1))}。
     * 需求为 0 时不提价；供给为 0 而有需求时取上限。
     */
    public static int targetFactorBps(long demand, long supply) {
        long d = Math.max(0, demand);
        long s = Math.max(0, supply);
        if (d == 0 && s == 0) return NEUTRAL_BPS;
        if (d == 0) return MIN_FACTOR_BPS;
        if (s == 0) return MAX_FACTOR_BPS;
        double gap = (double) (d - s) / Math.max(1, Math.min(d, s));
        gap = Math.max(-1.0, Math.min(1.0, gap));
        return clampFactor((int) Math.round(NEUTRAL_BPS * (1.0 + 0.75 * gap)));
    }

    /** 从当前系数向目标移动，每天最多 {@code stepBps}。 */
    public static int stepFactor(int currentBps, int targetBps, int stepBps) {
        int step = Math.max(1, stepBps);
        int cur = clampFactor(currentBps);
        int tgt = clampFactor(targetBps);
        if (tgt > cur) return Math.min(tgt, cur + step);
        return Math.max(tgt, cur - step);
    }

    public static int clampFactor(int bps) {
        return Math.max(MIN_FACTOR_BPS, Math.min(MAX_FACTOR_BPS, bps));
    }

    /**
     * 当日目标生活水平：各类别满足率（0..1）按权重加权，乘以饥荒系数 {@code 0.5 + 0.5 × 食物满足率}，
     * 再加收入加成和法律修正。吃不饱时其他需求满足得再好也过不好。
     *
     * @param fulfilment      类别 → 满足率；缺项按 0
     * @param incomePerCapita 人均日收入（工资 + 福利）
     * @param bonusBps        其他修正（如劳工保护），可为负
     */
    public static int targetSol(Map<NeedCategory, Double> fulfilment, double incomePerCapita, int bonusBps) {
        double total = 0;
        int weights = 0;
        for (NeedCategory c : NeedCategory.values()) {
            double f = fulfilment == null ? 0 : fulfilment.getOrDefault(c, 0.0);
            total += Math.max(0, Math.min(1, f)) * c.weight();
            weights += c.weight();
        }
        double food = fulfilment == null ? 0 : Math.max(0, Math.min(1, fulfilment.getOrDefault(NeedCategory.FOOD, 0.0)));
        double base = weights == 0 ? 0 : total / weights * MAX_SOL * (0.5 + 0.5 * food);
        double income = Math.min(MAX_INCOME_SOL, Math.max(0, incomePerCapita) * INCOME_SOL_PER_UNIT);
        return clampSol((int) Math.round(base + income + bonusBps));
    }

    /** 生活水平每天向目标移动差距的 1/5（至少 1 点）。 */
    public static int smoothSol(int current, int target) {
        int diff = target - current;
        if (diff == 0) return current;
        int step = diff / 5;
        if (step == 0) step = diff > 0 ? 1 : -1;
        return clampSol(current + step);
    }

    public static int clampSol(int v) {
        return Math.max(0, Math.min(MAX_SOL, v));
    }

    /**
     * 当日人口变化（千分之一人为单位，便于累积小数）：
     * {@code 人口 × 1000 × (SoL−5000)/5000 × growthMaxBps/10000}。
     */
    public static long growthMilli(int population, int sol, int growthMaxBps, boolean unrest) {
        if (population <= 0 || growthMaxBps <= 0) return 0;
        double rate = (clampSol(sol) - 5_000) / 5_000.0 * growthMaxBps / 10_000.0;
        if (unrest && rate > 0) rate /= 2;
        return Math.round(population * 1000.0 * rate);
    }

    /** 迁移吸引力：生活水平（已含收入加成），骚乱时 −1000。 */
    public static int attractiveness(int sol, boolean unrest) {
        return clampSol(sol) - (unrest ? 1_000 : 0);
    }
}
