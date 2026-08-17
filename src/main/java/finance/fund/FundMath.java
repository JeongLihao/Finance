package finance.fund;

import java.math.BigInteger;

public final class FundMath {
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private FundMath() { }
    public static long ratioFloor(long value, long numerator, long denominator) {
        if (value < 0 || numerator < 0 || denominator <= 0) return -1;
        return cap(BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator)));
    }
    public static long feeFloor(long amount, int basisPoints) { return ratioFloor(amount, basisPoints, 10_000); }
    public static long cap(BigInteger value) { return value.max(BigInteger.ZERO).min(MAX).longValue(); }
    public static long saturatedAddSigned(long a, long b) { return BigInteger.valueOf(a).add(BigInteger.valueOf(b)).max(MIN).min(MAX).longValue(); }
}
