package finance.futures;

import java.math.BigInteger;
import java.util.UUID;

/** Net position: positive quantity is long, negative quantity is short. Opposite trades close before reversing. */
public final class FuturesPosition {
    private final UUID ownerId;
    private final UUID contractId;
    private long signedQuantity;
    private long averageEntryPrice;
    private long settlementReferencePrice;
    private long realizedPnl;

    public FuturesPosition(UUID ownerId, UUID contractId, long signedQuantity, long averageEntryPrice,
                           long settlementReferencePrice, long realizedPnl) {
        if (ownerId == null || contractId == null || signedQuantity == Long.MIN_VALUE
                || (signedQuantity != 0 && (averageEntryPrice <= 0 || settlementReferencePrice <= 0))) {
            throw new IllegalArgumentException("invalid futures position");
        }
        this.ownerId = ownerId; this.contractId = contractId; this.signedQuantity = signedQuantity;
        this.averageEntryPrice = signedQuantity == 0 ? 0 : averageEntryPrice;
        this.settlementReferencePrice = signedQuantity == 0 ? 0 : settlementReferencePrice;
        this.realizedPnl = realizedPnl;
    }

    public UUID ownerId() { return ownerId; }
    public UUID contractId() { return contractId; }
    public long signedQuantity() { return signedQuantity; }
    public long quantity() { return Math.abs(signedQuantity); }
    public FuturesSide side() { return signedQuantity >= 0 ? FuturesSide.LONG : FuturesSide.SHORT; }
    public long averageEntryPrice() { return averageEntryPrice; }
    public long settlementReferencePrice() { return settlementReferencePrice; }
    public long realizedPnl() { return realizedPnl; }

    public Preview preview(FuturesOrderSide orderSide, long tradeQuantity, long tradePrice, long contractSize) {
        if (orderSide == null || tradeQuantity <= 0 || tradePrice <= 0 || contractSize <= 0) return null;
        long delta = orderSide == FuturesOrderSide.BUY ? tradeQuantity : -tradeQuantity;
        long old = signedQuantity;
        long next;
        try { next = Math.addExact(old, delta); } catch (ArithmeticException ex) { return null; }
        if (next == Long.MIN_VALUE) return null;
        long nextAverage = averageEntryPrice, nextReference = settlementReferencePrice, realizedDelta = 0, variationDelta = 0;
        if (old == 0 || Long.signum(old) == Long.signum(delta)) {
            nextAverage = weighted(averageEntryPrice, Math.abs(old), tradePrice, tradeQuantity);
            nextReference = weighted(settlementReferencePrice, Math.abs(old), tradePrice, tradeQuantity);
            if (nextAverage <= 0 || nextReference <= 0) return null;
        } else {
            long closing = Math.min(Math.abs(old), tradeQuantity);
            try { realizedDelta = FuturesMath.signedPnl(averageEntryPrice, tradePrice, contractSize,
                    old > 0 ? closing : -closing); } catch (ArithmeticException ex) { return null; }
            try { variationDelta = FuturesMath.signedPnl(settlementReferencePrice, tradePrice, contractSize,
                    old > 0 ? closing : -closing); } catch (ArithmeticException ex) { return null; }
            if (next == 0) { nextAverage = 0; nextReference = 0; }
            else if (Long.signum(next) != Long.signum(old)) { nextAverage = tradePrice; nextReference = tradePrice; }
        }
        long nextRealized;
        try { nextRealized = Math.addExact(realizedPnl, realizedDelta); } catch (ArithmeticException ex) { return null; }
        return new Preview(next, nextAverage, nextReference, nextRealized, realizedDelta, variationDelta);
    }

    public void apply(Preview preview) {
        if (preview == null) throw new IllegalArgumentException("preview");
        signedQuantity = preview.signedQuantity; averageEntryPrice = preview.averageEntryPrice;
        settlementReferencePrice = preview.settlementReferencePrice; realizedPnl = preview.realizedPnl;
    }

    public void setSettlementReferencePrice(long price) {
        if (signedQuantity != 0 && price > 0) settlementReferencePrice = price;
    }

    private static long weighted(long oldPrice, long oldQty, long newPrice, long newQty) {
        if (oldQty == 0) return newPrice;
        BigInteger result = BigInteger.valueOf(oldPrice).multiply(BigInteger.valueOf(oldQty))
                .add(BigInteger.valueOf(newPrice).multiply(BigInteger.valueOf(newQty)))
                .divide(BigInteger.valueOf(oldQty).add(BigInteger.valueOf(newQty)));
        return result.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? -1 : result.longValue();
    }

    public record Preview(long signedQuantity, long averageEntryPrice, long settlementReferencePrice,
                          long realizedPnl, long realizedDelta, long variationDelta) { }
}
