package finance.futures;

import java.util.UUID;

public final class FuturesOrder {
    private final UUID orderId, playerId, contractId;
    private final FuturesOrderSide side;
    private final long limitPrice, sequence;
    private long remainingQuantity, reservedMargin;
    public FuturesOrder(UUID orderId, UUID playerId, UUID contractId, FuturesOrderSide side,
                        long limitPrice, long remainingQuantity, long sequence, long reservedMargin) {
        if (orderId == null || playerId == null || contractId == null || side == null || limitPrice <= 0
                || remainingQuantity <= 0 || sequence <= 0 || reservedMargin < 0) throw new IllegalArgumentException("invalid futures order");
        this.orderId=orderId;this.playerId=playerId;this.contractId=contractId;this.side=side;
        this.limitPrice=limitPrice;this.remainingQuantity=remainingQuantity;this.sequence=sequence;this.reservedMargin=reservedMargin;
    }
    public UUID orderId(){return orderId;} public UUID playerId(){return playerId;} public UUID contractId(){return contractId;}
    public FuturesOrderSide side(){return side;} public long limitPrice(){return limitPrice;}
    public long remainingQuantity(){return remainingQuantity;} public long sequence(){return sequence;}
    public long reservedMargin(){return reservedMargin;}
    long reservationFor(long quantity) {
        if(quantity<=0||quantity>remainingQuantity)return -1;
        if(quantity==remainingQuantity)return reservedMargin;
        return java.math.BigInteger.valueOf(reservedMargin).multiply(java.math.BigInteger.valueOf(quantity))
                .divide(java.math.BigInteger.valueOf(remainingQuantity)).longValue();
    }
    void fill(long quantity,long released){if(quantity<=0||quantity>remainingQuantity||released<0||released>reservedMargin)throw new IllegalArgumentException();remainingQuantity-=quantity;reservedMargin-=released;}
}
