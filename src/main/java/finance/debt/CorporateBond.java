package finance.debt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CorporateBond {
    private final UUID id;
    private final UUID companyId;
    private final String code;
    private final long faceValue;
    private final long totalQuantity;
    private final int couponBasisPoints;
    private final long issueDay;
    private final long subscriptionEndDay;
    private final long maturityDay;
    private final int couponIntervalDays;
    private long nextCouponDay;
    private long lastCouponDay;
    private BondStatus status;
    private long escrowCash;
    private final Map<UUID, Long> holdings = new LinkedHashMap<>();
    private final Map<UUID, Long> recoveredPrincipal = new LinkedHashMap<>();

    public CorporateBond(UUID id, UUID companyId, String code, long faceValue, long totalQuantity,
                         int couponBasisPoints, long issueDay, long subscriptionEndDay, long maturityDay,
                         int couponIntervalDays, long nextCouponDay, BondStatus status, long escrowCash) {
        this.id = id; this.companyId = companyId; this.code = code; this.faceValue = faceValue;
        this.totalQuantity = totalQuantity; this.couponBasisPoints = couponBasisPoints; this.issueDay = issueDay;
        this.subscriptionEndDay = subscriptionEndDay; this.maturityDay = maturityDay;
        this.couponIntervalDays = couponIntervalDays; this.nextCouponDay = nextCouponDay;
        this.lastCouponDay = Math.max(subscriptionEndDay, nextCouponDay - Math.max(1, couponIntervalDays));
        this.status = status; this.escrowCash = Math.max(0, escrowCash);
    }

    public UUID id() { return id; } public UUID companyId() { return companyId; } public String code() { return code; }
    public long faceValue() { return faceValue; } public long totalQuantity() { return totalQuantity; }
    public int couponBasisPoints() { return couponBasisPoints; } public long issueDay() { return issueDay; }
    public long subscriptionEndDay() { return subscriptionEndDay; } public long maturityDay() { return maturityDay; }
    public int couponIntervalDays() { return couponIntervalDays; } public long nextCouponDay() { return nextCouponDay; }
    public long lastCouponDay() { return lastCouponDay; }
    public BondStatus status() { return status; } public long escrowCash() { return escrowCash; }
    public Map<UUID, Long> holdings() { return java.util.Collections.unmodifiableMap(holdings); }
    public Map<UUID, Long> recoveredPrincipal() { return java.util.Collections.unmodifiableMap(recoveredPrincipal); }
    public long subscribedQuantity() {
        java.math.BigInteger sum = java.math.BigInteger.ZERO;
        for (long q : holdings.values()) if (q > 0) sum = sum.add(java.math.BigInteger.valueOf(q));
        return sum.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    void setStatus(BondStatus value) { status = value; }
    void setNextCouponDay(long value) { nextCouponDay = value; }
    void setLastCouponDay(long value) { lastCouponDay = value; }
    public void setLastCouponDayDirect(long value) { lastCouponDay = value; }
    boolean canCreditEscrow(long amount) { return amount > 0 && escrowCash <= Long.MAX_VALUE - amount; }
    boolean creditEscrow(long amount) { if (!canCreditEscrow(amount)) return false; escrowCash += amount; return true; }
    boolean debitEscrow(long amount) { if (amount <= 0 || escrowCash < amount) return false; escrowCash -= amount; return true; }
    public boolean canAddHolding(UUID player, long quantity) {
        if (player == null || quantity <= 0) return false;
        long old = holdings.getOrDefault(player, 0L);
        return old <= Long.MAX_VALUE - quantity;
    }
    public boolean addHolding(UUID player, long quantity) {
        if (!canAddHolding(player, quantity)) return false;
        long old = holdings.getOrDefault(player, 0L);
        holdings.put(player, old + quantity); return true;
    }
    public boolean removeHolding(UUID player, long quantity) {
        if (player == null || quantity <= 0) return false;
        long old = holdings.getOrDefault(player, 0L);
        if (old < quantity) return false;
        long next = old - quantity;
        if (next == 0) holdings.remove(player); else holdings.put(player, next);
        return true;
    }
    public void putHoldingDirect(UUID player, long quantity) { if (player != null && quantity > 0) holdings.put(player, quantity); }
    public void putRecoveredPrincipalDirect(UUID player, long amount) {
        if (player != null && amount > 0) recoveredPrincipal.put(player, amount);
    }
    long remainingPrincipalClaim(UUID player) {
        long quantity = holdings.getOrDefault(player, 0L);
        if (quantity <= 0) return 0;
        java.math.BigInteger gross = java.math.BigInteger.valueOf(faceValue).multiply(java.math.BigInteger.valueOf(quantity));
        long capped = gross.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        return Math.max(0, capped - recoveredPrincipal.getOrDefault(player, 0L));
    }
    long applyRecovery(UUID player, long amount) {
        long applied = Math.min(Math.max(0, amount), remainingPrincipalClaim(player));
        if (applied <= 0) return 0;
        recoveredPrincipal.merge(player, applied, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
        return applied;
    }
    void clearHoldings() { holdings.clear(); }
}
