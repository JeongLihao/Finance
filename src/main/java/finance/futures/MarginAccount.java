package finance.futures;

import java.util.UUID;

public final class MarginAccount {
    private final UUID ownerId;
    private long cashBalance;
    private long frozenForOrders;
    private MarginRiskStatus riskStatus;
    private long lastSettlementDay;
    private long marginCallNotifiedDay = -1;

    public MarginAccount(UUID ownerId, long cashBalance, long frozenForOrders,
                         MarginRiskStatus riskStatus, long lastSettlementDay) {
        if (ownerId == null || cashBalance < 0 || frozenForOrders < 0 || frozenForOrders > cashBalance || riskStatus == null) {
            throw new IllegalArgumentException("invalid margin account");
        }
        this.ownerId = ownerId; this.cashBalance = cashBalance; this.frozenForOrders = frozenForOrders;
        this.riskStatus = riskStatus; this.lastSettlementDay = Math.max(-1, lastSettlementDay);
    }
    public MarginAccount(UUID ownerId, long cashBalance, long frozenForOrders, MarginRiskStatus riskStatus,
                         long lastSettlementDay, long marginCallNotifiedDay) {
        this(ownerId, cashBalance, frozenForOrders, riskStatus, lastSettlementDay);
        this.marginCallNotifiedDay = Math.max(-1, marginCallNotifiedDay);
    }
    public UUID ownerId() { return ownerId; }
    public long cashBalance() { return cashBalance; }
    public long frozenForOrders() { return frozenForOrders; }
    public MarginRiskStatus riskStatus() { return riskStatus; }
    public long lastSettlementDay() { return lastSettlementDay; }
    public long marginCallNotifiedDay() { return marginCallNotifiedDay; }
    boolean canCredit(long amount) { return amount > 0 && cashBalance <= Long.MAX_VALUE - amount; }
    boolean credit(long amount) { if (!canCredit(amount)) return false; cashBalance += amount; return true; }
    boolean debit(long amount) { if (amount <= 0 || cashBalance < amount || cashBalance - amount < frozenForOrders) return false; cashBalance -= amount; return true; }
    boolean freeze(long amount) { if (amount < 0 || cashBalance - frozenForOrders < amount) return false; frozenForOrders += amount; return true; }
    boolean unfreeze(long amount) { if (amount < 0 || frozenForOrders < amount) return false; frozenForOrders -= amount; return true; }
    void forceDebit(long amount) { if (amount < 0 || amount > cashBalance) throw new IllegalArgumentException(); cashBalance -= amount; if (frozenForOrders > cashBalance) frozenForOrders = cashBalance; }
    void setRiskStatus(MarginRiskStatus value) { if (value != null) riskStatus = value; }
    void setLastSettlementDay(long value) { lastSettlementDay = Math.max(lastSettlementDay, value); }
    void markMarginCallNotified(long day) { marginCallNotifiedDay = Math.max(marginCallNotifiedDay, day); }
}
