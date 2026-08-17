package finance.fixedincome;

import finance.policy.MonetaryPolicyService;

public final class YieldCurveService {
    private YieldCurveService() { }
    public static int yieldBasisPoints(int termDays) {
        int premium = switch (termDays) { case 7 -> 25; case 30 -> 100; case 90 -> 200; default -> -1; };
        if (premium < 0) return -1;
        return Math.min(100_000, MonetaryPolicyService.benchmarkRateBasisPoints() + premium);
    }
}
