package finance.util;

/**
 * 数学工具类 —— 提供安全的数值计算方法。
 */
public class MathUtil {

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
