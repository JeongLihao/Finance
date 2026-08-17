package finance.index;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MarketIndexState {
    public static final int MAX_HISTORY = 120;
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    private final String id;
    private BigDecimal divisor;
    private String constituentFingerprint = "";
    private final List<MarketIndexPoint> history = new ArrayList<>();

    public MarketIndexState(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public BigDecimal divisor() { return divisor; }
    public String constituentFingerprint() { return constituentFingerprint; }
    public List<MarketIndexPoint> history() { return List.copyOf(history); }
    public MarketIndexPoint latest() { return history.isEmpty() ? null : history.get(history.size() - 1); }

    public boolean close(long day, BigDecimal rawValue, String fingerprint) {
        if (day < 0 || rawValue == null || rawValue.signum() <= 0 || fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        MarketIndexPoint latest = latest();
        if (latest != null && day <= latest.mcDay()) return false;
        if (divisor == null || divisor.signum() <= 0) {
            divisor = rawValue.divide(BigDecimal.valueOf(finance.config.FinanceConfig.indexBasePoints()), MC);
        } else if (!fingerprint.equals(constituentFingerprint) && latest != null && latest.value() > 0) {
            // Structural changes (listing, delisting or share issuance) must not
            // create an artificial index jump.
            divisor = rawValue.divide(BigDecimal.valueOf(latest.value()), MC);
        }
        if (divisor.signum() <= 0) return false;
        double value = rawValue.divide(divisor, MC).doubleValue();
        if (!Double.isFinite(value) || value < 0) return false;
        constituentFingerprint = fingerprint;
        history.add(new MarketIndexPoint(day, value));
        if (history.size() > MAX_HISTORY) history.subList(0, history.size() - MAX_HISTORY).clear();
        return true;
    }

    public void restore(BigDecimal restoredDivisor, String fingerprint, List<MarketIndexPoint> points) {
        divisor = restoredDivisor != null && restoredDivisor.signum() > 0 ? restoredDivisor : null;
        constituentFingerprint = fingerprint == null ? "" : fingerprint;
        history.clear();
        if (points != null) {
            points.stream().filter(p -> p != null && p.mcDay() >= 0 && Double.isFinite(p.value()) && p.value() >= 0)
                    .sorted(java.util.Comparator.comparingLong(MarketIndexPoint::mcDay))
                    .forEach(p -> {
                        if (history.isEmpty() || p.mcDay() > latest().mcDay()) history.add(p);
                    });
        }
        if (history.size() > MAX_HISTORY) history.subList(0, history.size() - MAX_HISTORY).clear();
    }
}
