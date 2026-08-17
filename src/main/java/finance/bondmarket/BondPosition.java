package finance.bondmarket;

import java.util.UUID;

/** Mutable accounting metadata; authoritative quantity remains on CorporateBond. */
public final class BondPosition {
    private final UUID bondId;
    private final UUID playerId;
    private long frozenQuantity;
    private long totalCost;
    private long realizedProfit;
    private long receivedCoupon;

    public BondPosition(UUID bondId, UUID playerId, long frozenQuantity, long totalCost,
                        long realizedProfit, long receivedCoupon) {
        this.bondId = bondId;
        this.playerId = playerId;
        this.frozenQuantity = frozenQuantity;
        this.totalCost = totalCost;
        this.realizedProfit = realizedProfit;
        this.receivedCoupon = receivedCoupon;
    }

    public UUID bondId() { return bondId; }
    public UUID playerId() { return playerId; }
    public long frozenQuantity() { return frozenQuantity; }
    public long totalCost() { return totalCost; }
    public long realizedProfit() { return realizedProfit; }
    public long receivedCoupon() { return receivedCoupon; }
    void setFrozenQuantity(long value) { frozenQuantity = value; }
    void setTotalCost(long value) { totalCost = value; }
    void setRealizedProfit(long value) { realizedProfit = value; }
    void setReceivedCoupon(long value) { receivedCoupon = value; }
}
