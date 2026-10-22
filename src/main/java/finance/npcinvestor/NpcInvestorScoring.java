package finance.npcinvestor;

import java.util.List;

/**
 * NPC 投资者评分（纯函数，便于单测）：公司画像 → 0–10000 的意愿分 → 目标持股基点。
 */
public final class NpcInvestorScoring {
    public static final int MAX_SCORE = 10_000;

    private NpcInvestorScoring() {}

    /**
     * 评分输入：只读的公司/股票画像。
     *
     * @param price              现价
     * @param fundamentalPerShare 每股基本面（基本面资产 / 总股本）
     * @param dayChangePercent   当日涨跌幅（%）
     * @param recentProfits      最近若干天净利润（旧 → 新）
     * @param recentDividendPerShare 最近两个分红周期内的每股分红合计；0 表示期间未分红
     * @param dividendCount      最近两个分红周期内的分红次数
     * @param bankruptcyRisk     是否处于破产风险
     */
    public record Profile(
            long price,
            long fundamentalPerShare,
            double dayChangePercent,
            List<Long> recentProfits,
            long recentDividendPerShare,
            int dividendCount,
            boolean bankruptcyRisk) {}

    /** 评分结果：分数与可展示的理由代码。 */
    public record Score(int value, NpcReason reason) {}

    public static Score score(NpcInvestorArchetype archetype, Profile p) {
        if (archetype == null || p == null || p.price() <= 0) return new Score(0, NpcReason.NO_PRICE);
        if (p.bankruptcyRisk()) return new Score(0, NpcReason.BANKRUPTCY_RISK);
        return switch (archetype) {
            case DIVIDEND -> dividend(p);
            case GROWTH -> growth(p);
            case SPECULATOR -> speculator(p);
            case VALUE -> value(p);
        };
    }

    /** 分红派：两个周期内没分红给低分；股息率 5% 视为满分，连续分红再加分。 */
    static Score dividend(Profile p) {
        if (p.dividendCount() <= 0 || p.recentDividendPerShare() <= 0) {
            return new Score(1_000, NpcReason.NO_DIVIDEND);
        }
        double yieldBps = p.recentDividendPerShare() * 10_000.0 / p.price();
        int base = clampScore(yieldBps / 500.0 * 8_000);
        int bonus = p.dividendCount() >= 2 ? 2_000 : 0;
        return new Score(clampScore(base + bonus), NpcReason.DIVIDEND_YIELD);
    }

    /** 成长派：近期利润为正且后半段高于前半段给高分；利润为负给零分。 */
    static Score growth(Profile p) {
        List<Long> profits = p.recentProfits() == null ? List.of() : p.recentProfits();
        if (profits.size() < 2) return new Score(2_000, NpcReason.INSUFFICIENT_HISTORY);
        int half = profits.size() / 2;
        double early = average(profits.subList(0, half));
        double late = average(profits.subList(half, profits.size()));
        if (late <= 0) return new Score(0, NpcReason.PROFIT_DECLINE);
        if (late <= early) return new Score(3_000, NpcReason.PROFIT_FLAT);
        double growth = early <= 0 ? 1.0 : (late - early) / Math.abs(early);
        return new Score(clampScore(4_000 + Math.min(1.0, growth) * 6_000), NpcReason.PROFIT_GROWTH);
    }

    /** 短线派：上涨动量越强分越高，下跌直接清仓意愿。5% 日涨幅视为满分。 */
    static Score speculator(Profile p) {
        if (p.dayChangePercent() <= 0) return new Score(0, NpcReason.MOMENTUM_DOWN);
        return new Score(clampScore(p.dayChangePercent() / 5.0 * MAX_SCORE), NpcReason.MOMENTUM_UP);
    }

    /** 价值派：现价相对每股基本面折价 30% 视为满分，溢价时零分。 */
    static Score value(Profile p) {
        if (p.fundamentalPerShare() <= 0) return new Score(0, NpcReason.NO_FUNDAMENTAL);
        double discount = (p.fundamentalPerShare() - p.price()) / (double) p.fundamentalPerShare();
        if (discount <= 0) return new Score(0, NpcReason.OVERVALUED);
        return new Score(clampScore(discount / 0.30 * MAX_SCORE), NpcReason.UNDERVALUED);
    }

    /** 目标持股基点 = 分数 × 单原型上限 × 原型系数。 */
    public static int targetBps(NpcInvestorArchetype archetype, int score, int maxHoldingBpsEach) {
        if (archetype == null || score <= 0 || maxHoldingBpsEach <= 0) return 0;
        double cap = maxHoldingBpsEach * archetype.holdingCapFactor();
        return (int) Math.max(0, Math.min(cap, Math.floor(cap * Math.min(score, MAX_SCORE) / MAX_SCORE)));
    }

    /** 情绪值 -100..100，由分数线性映射。 */
    public static int sentiment(int score) {
        return (int) Math.round(Math.max(0, Math.min(MAX_SCORE, score)) / 50.0) - 100;
    }

    private static double average(List<Long> values) {
        if (values.isEmpty()) return 0;
        double total = 0;
        for (long v : values) total += v;
        return total / values.size();
    }

    private static int clampScore(double v) {
        if (Double.isNaN(v)) return 0;
        return (int) Math.max(0, Math.min(MAX_SCORE, Math.round(v)));
    }
}
