package finance.util;

/**
 * 数学工具类 —— 提供安全的数值计算方法。
 */
public class MathUtil {

    public static long saturatedAddNonNegative(long left, long right) {
        long safeLeft = Math.max(0, left);
        long safeRight = Math.max(0, right);
        return safeLeft > Long.MAX_VALUE - safeRight ? Long.MAX_VALUE : safeLeft + safeRight;
    }

    public static long saturatedMultiplyNonNegative(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    /**
     * 安全的价格 × 数量乘法，溢出时返回 -1。
     *
     * @param price    单价
     * @param quantity 数量
     * @return 乘积，溢出返回 -1
     */
    public static long multiplyExactOrNegative1(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException ex) {
            return -1;
        }
    }
}
