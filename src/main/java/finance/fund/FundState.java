package finance.fund;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-authoritative mutable state for one fund. Cash and securities live in the fund custody account. */
public final class FundState {
    public static final int MAX_NAV_HISTORY = 365;
    private FundStatus status = FundStatus.ACTIVE;
    private long totalShareUnits;
    private long currentNav = FundManager.INITIAL_NAV;
    private long previousNav = FundManager.INITIAL_NAV;
    private long lastNavDay = -1;
    private long lastFeeDay = -1;
    private long accruedFees;
    private long realizedIncome;
    private int constituentVersion;
    private long constituentEffectiveDay = -1;
    private long lastRebalanceDay = -1;
    private String suspensionReason = "";
    private final List<FundNavPoint> navHistory = new ArrayList<>();

    public FundStatus status() { return status; }
    public long totalShareUnits() { return totalShareUnits; }
    public long currentNav() { return currentNav; }
    public long previousNav() { return previousNav; }
    public long lastNavDay() { return lastNavDay; }
    public long lastFeeDay() { return lastFeeDay; }
    public long accruedFees() { return accruedFees; }
    public long realizedIncome() { return realizedIncome; }
    public int constituentVersion() { return constituentVersion; }
    public long constituentEffectiveDay() { return constituentEffectiveDay; }
    public long lastRebalanceDay() { return lastRebalanceDay; }
    public String suspensionReason() { return suspensionReason; }
    public List<FundNavPoint> navHistory() { return Collections.unmodifiableList(navHistory); }

    boolean canAddShares(long amount) { return amount > 0 && totalShareUnits <= Long.MAX_VALUE - amount; }
    void addShares(long amount) { totalShareUnits = Math.addExact(totalShareUnits, amount); }
    boolean removeShares(long amount) { if (amount <= 0 || amount > totalShareUnits) return false; totalShareUnits -= amount; return true; }
    void addFees(long amount) { if (amount > 0) accruedFees = Math.addExact(accruedFees, amount); }
    void addIncome(long amount) { if (amount > 0) realizedIncome = Math.addExact(realizedIncome, amount); }
    void markFeeDay(long day) { lastFeeDay = day; }
    void markRebalance(long day) { lastRebalanceDay = day; }
    void updateConstituents(long day) { constituentVersion = Math.addExact(constituentVersion, 1); constituentEffectiveDay = day; }
    void suspend(FundStatus value, String reason) { status = value; suspensionReason = reason == null ? "" : reason.substring(0, Math.min(256, reason.length())); }
    void resume() { status = FundStatus.ACTIVE; suspensionReason = ""; }
    void recordNav(FundNavPoint point) {
        if (point == null || point.mcDay() < 0 || point.nav() <= 0 || point.netAssets() < 0 || point.totalShareUnits() < 0) return;
        if (!navHistory.isEmpty() && navHistory.get(navHistory.size() - 1).mcDay() == point.mcDay()) navHistory.set(navHistory.size() - 1, point);
        else if (navHistory.isEmpty() || navHistory.get(navHistory.size() - 1).mcDay() < point.mcDay()) navHistory.add(point);
        while (navHistory.size() > MAX_NAV_HISTORY) navHistory.remove(0);
        previousNav = lastNavDay < point.mcDay() ? currentNav : previousNav;
        currentNav = point.nav(); lastNavDay = point.mcDay();
    }

    public void restore(FundStatus status, long totalShareUnits, long currentNav, long previousNav,
                        long lastNavDay, long lastFeeDay, long accruedFees, long realizedIncome,
                        int constituentVersion, long constituentEffectiveDay, long lastRebalanceDay,
                        String reason, List<FundNavPoint> history) {
        this.status = status; this.totalShareUnits = totalShareUnits; this.currentNav = currentNav;
        this.previousNav = previousNav; this.lastNavDay = lastNavDay; this.lastFeeDay = lastFeeDay;
        this.accruedFees = accruedFees; this.realizedIncome = realizedIncome;
        this.constituentVersion = constituentVersion; this.constituentEffectiveDay = constituentEffectiveDay;
        this.lastRebalanceDay = lastRebalanceDay; this.suspensionReason = reason == null ? "" : reason;
        navHistory.clear(); if (history != null) for (FundNavPoint point : history) recordNav(point);
        this.currentNav = currentNav; this.previousNav = previousNav; this.lastNavDay = lastNavDay;
    }
}
