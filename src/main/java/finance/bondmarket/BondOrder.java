package finance.bondmarket;

import java.util.UUID;

public final class BondOrder {
    private final UUID orderId;
    private final UUID playerId;
    private final UUID bondId;
    private final BondOrderSide side;
    private final long limitPricePerUnit;
    private long remainingQuantity;
    private final long createdSequence;

    public BondOrder(UUID orderId, UUID playerId, UUID bondId, BondOrderSide side,
                     long limitPricePerUnit, long remainingQuantity, long createdSequence) {
        this.orderId = orderId; this.playerId = playerId; this.bondId = bondId; this.side = side;
        this.limitPricePerUnit = limitPricePerUnit; this.remainingQuantity = remainingQuantity;
        this.createdSequence = createdSequence;
    }
    public UUID orderId() { return orderId; } public UUID playerId() { return playerId; }
    public UUID bondId() { return bondId; } public BondOrderSide side() { return side; }
    public long limitPricePerUnit() { return limitPricePerUnit; }
    public long remainingQuantity() { return remainingQuantity; }
    public long createdSequence() { return createdSequence; }
    void reduce(long quantity) { remainingQuantity -= quantity; }
}
