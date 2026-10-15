package finance.fixedincome;

import finance.policy.MonetaryPolicyService;

public final class YieldCurveService {
    private YieldCurveService() { }
    public static int yieldBasisPoints(int termDays) {
        int premium = switch (termDays) { case 7 -> 25; case 30 -> 100; case 90 -> 200; default -> -1; };
        if (premium < 0) return -1;
        return Math.min(100_000, MonetaryPolicyService.benchmarkRateBasisPoints() + premium);
    }

    /** 国债收益率（基点）= 基准利率 ＋ 期限溢价；期限越长溢价越高。未知期限返回 -1。 */
    public static int treasuryYieldBasisPoints(int termDays) {
        int premium = switch (termDays) { case 30 -> 150; case 90 -> 250; case 180 -> 350; default -> -1; };
        if (premium < 0) return -1;
        return Math.min(100_000, MonetaryPolicyService.benchmarkRateBasisPoints() + premium);
    }
}
