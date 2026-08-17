package finance.futures;

import java.math.BigInteger;

/** Exact integer arithmetic for futures. Margin rounds upward; PnL is exact in smallest currency units. */
public final class FuturesMath {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private FuturesMath() { }

    public static long notional(long price, long contractSize, long quantity) {
        if (price <= 0 || contractSize <= 0 || quantity <= 0) return -1;
        return capOrInvalid(BigInteger.valueOf(price).multiply(BigInteger.valueOf(contractSize))
                .multiply(BigInteger.valueOf(quantity)));
    }

    public static long margin(long price, long contractSize, long quantity, int rateBps) {
        long notional = notional(price, contractSize, quantity);
        if (notional <= 0 || rateBps <= 0 || rateBps > 10_000) return -1;
        BigInteger[] qr = BigInteger.valueOf(notional).multiply(BigInteger.valueOf(rateBps))
                .divideAndRemainder(BigInteger.valueOf(10_000));
        return capOrInvalid(qr[0].add(qr[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE));
    }

    public static long signedPnl(long fromPrice, long toPrice, long contractSize, long signedQuantity) {
        if (fromPrice <= 0 || toPrice <= 0 || contractSize <= 0 || signedQuantity == 0) return 0;
        BigInteger value = BigInteger.valueOf(toPrice).subtract(BigInteger.valueOf(fromPrice))
                .multiply(BigInteger.valueOf(contractSize)).multiply(BigInteger.valueOf(signedQuantity));
        if (value.compareTo(MAX) > 0 || value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            throw new ArithmeticException("futures pnl overflow");
        }
        return value.longValue();
    }

    public static long averagePrice(long oldPrice, long oldQuantity, long newPrice, long addedQuantity) {
        if (oldPrice <= 0 || oldQuantity < 0 || newPrice <= 0 || addedQuantity <= 0) return -1;
        BigInteger totalQty = BigInteger.valueOf(oldQuantity).add(BigInteger.valueOf(addedQuantity));
        BigInteger cost = BigInteger.valueOf(oldPrice).multiply(BigInteger.valueOf(oldQuantity))
                .add(BigInteger.valueOf(newPrice).multiply(BigInteger.valueOf(addedQuantity)));
        return cost.divide(totalQty).min(MAX).longValue();
    }

    public static long maxOpenQuantity(long available, long price, long size, int rateBps, long hardLimit) {
        long perLot = margin(price, size, 1, rateBps);
        if (available < 0 || perLot <= 0 || hardLimit <= 0) return 0;
        return Math.min(hardLimit, available / perLot);
    }

    private static long capOrInvalid(BigInteger value) {
        return value.signum() <= 0 || value.compareTo(MAX) > 0 ? -1 : value.longValue();
    }
}
