package finance.policy;

import finance.config.FinanceConfig;
import finance.data.EconomySavedData;

import java.util.ArrayList;
import java.util.List;

/** Persistent benchmark rate. Existing contracts keep their locked rate. */
public final class MonetaryPolicyService {
    public static final int MAX_HISTORY = 120;
    private static int benchmarkRateBasisPoints = FinanceConfig.defaultBenchmarkRateBasisPoints();
    private static final List<PolicyRateRecord> HISTORY = new ArrayList<>();

    private MonetaryPolicyService() {
    }

    public static int benchmarkRateBasisPoints() { return benchmarkRateBasisPoints; }
    public static List<PolicyRateRecord> history() { return List.copyOf(HISTORY); }

    public static boolean setBenchmarkRate(long mcDay, int basisPoints, String reason) {
        if (mcDay < 0 || basisPoints < FinanceConfig.minBenchmarkRateBasisPoints()
                || basisPoints > FinanceConfig.maxBenchmarkRateBasisPoints()) return false;
        if (!HISTORY.isEmpty() && HISTORY.get(HISTORY.size() - 1).mcDay() == mcDay) return false;
        if (basisPoints == benchmarkRateBasisPoints) return false;
        benchmarkRateBasisPoints = basisPoints;
        HISTORY.add(new PolicyRateRecord(mcDay, basisPoints, reason));
        trim();
        EconomySavedData.markDirty();
        return true;
    }

    public static void restore(int basisPoints, List<PolicyRateRecord> history) {
        benchmarkRateBasisPoints = Math.max(FinanceConfig.minBenchmarkRateBasisPoints(),
                Math.min(FinanceConfig.maxBenchmarkRateBasisPoints(), basisPoints));
        HISTORY.clear();
        if (history != null) history.stream()
                .filter(r -> r != null && r.mcDay() >= 0
                        && r.basisPoints() >= FinanceConfig.minBenchmarkRateBasisPoints()
                        && r.basisPoints() <= FinanceConfig.maxBenchmarkRateBasisPoints())
                .sorted(java.util.Comparator.comparingLong(PolicyRateRecord::mcDay))
                .forEach(r -> { if (HISTORY.isEmpty() || r.mcDay() > HISTORY.get(HISTORY.size() - 1).mcDay()) HISTORY.add(r); });
        trim();
    }

    public static void clearDirect() {
        benchmarkRateBasisPoints = FinanceConfig.defaultBenchmarkRateBasisPoints();
        HISTORY.clear();
    }

    private static void trim() {
        if (HISTORY.size() > MAX_HISTORY) HISTORY.subList(0, HISTORY.size() - MAX_HISTORY).clear();
    }
}
