package finance.earnings;

import finance.config.FinanceConfig;
import java.util.List;

/**
 * 分析师预期 —— 纯函数，公开透明的确定性公式。
 *
 * <p>预期 = 最近 N 期季报净利润的线性外推。输入只有已发布季报，
 * 公司所有者无法操纵（历史季报只增不改）。
 */
public final class EarningsExpectation {

    private EarningsExpectation() {}

    /**
     * 计算市场预期净利润。
     *
     * @param history 按时间升序的已发布季报（不含本期）
     * @return 预期净利润；history 为空时返回 Long.MIN_VALUE 表示"无预期"
     */
    public static long expectedProfit(List<QuarterlyEarningsReport> history) {
        if (history == null || history.isEmpty()) {
            return Long.MIN_VALUE;
        }
        int window = Math.max(1, FinanceConfig.earningsExpectationQuarters());
        int from = Math.max(0, history.size() - window);
        List<QuarterlyEarningsReport> slice = history.subList(from, history.size());
        if (slice.size() == 1) {
            return slice.get(0).netProfit();
        }
        long first = slice.get(0).netProfit();
        long last = slice.get(slice.size() - 1).netProfit();
        // 线性外推：last + (last - first) / (n - 1)，防溢出用 double 中转后钳制；
        // 注意先转 double 再减：(double)(last - first) 会在 long 减法阶段先溢出
        double slope = ((double) last - (double) first) / (slice.size() - 1);
        double expected = (double) last + slope;
        if (expected >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (expected <= Long.MIN_VALUE + 1) return Long.MIN_VALUE + 1;
        return Math.round(expected);
    }

    /**
     * 预期差（bps）= (实际 − 预期) / max(|预期|, 1) × 10000，钳制 ±surpriseCapBps。
     *
     * @param expected {@link #expectedProfit} 的返回值；Long.MIN_VALUE 表示无预期
     */
    public static long surpriseBps(long actual, long expected) {
        if (expected == Long.MIN_VALUE) {
            return 0;
        }
        long denom = Math.max(1L, Math.abs(expected));
        // 用 double 中转避免 (actual - expected) * 10000 溢出
        double bps = ((double) actual - (double) expected) / (double) denom * 10000.0;
        long cap = Math.max(1, FinanceConfig.earningsSurpriseCapBps());
        if (bps >= cap) return cap;
        if (bps <= -cap) return -cap;
        return Math.round(bps);
    }

    /**
     * 环比 bps = (本期 − 上期) / max(|上期|, 1) × 10000；上期为 0 时记 0（不编造）。
     */
    public static long qoqBps(long current, long previous) {
        if (previous == 0) {
            return 0;
        }
        long denom = Math.max(1L, Math.abs(previous));
        double bps = ((double) current - (double) previous) / (double) denom * 10000.0;
        if (bps >= 100000) return 100000;
        if (bps <= -100000) return -100000;
        return Math.round(bps);
    }

    /**
     * 财报日偏移：floorMod(companyId.hashCode, quarterDays)。
     * 纯确定性，错开发布、重启不变，无需新增"上市日"字段。
     */
    public static int earningsDayOffset(java.util.UUID companyId, int quarterDays) {
        if (quarterDays <= 0) {
            return 0;
        }
        return Math.floorMod(companyId.hashCode(), quarterDays);
    }

    /**
     * 今天是不是这家公司的财报日。
     */
    public static boolean isEarningsDay(java.util.UUID companyId, long mcDay, int quarterDays) {
        if (quarterDays <= 0) {
            return false;
        }
        return Math.floorMod(mcDay, quarterDays) == earningsDayOffset(companyId, quarterDays);
    }
}
