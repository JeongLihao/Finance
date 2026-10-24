package finance.util;

/** 账户金额（单位便士）写成玩家看得懂的“12 磅 40 便士”。100 便士 = 1 磅。 */
public final class MoneyFormat {

    private MoneyFormat() {}

    public static String pounds(long pence) {
        if (pence == Long.MIN_VALUE) pence = -Long.MAX_VALUE;
        String sign = pence < 0 ? "-" : "";
        long abs = Math.abs(pence);
        long pounds = abs / 100, rest = abs % 100;
        if (pounds == 0) return sign + rest + " 便士";
        if (rest == 0) return sign + pounds + " 磅";
        return sign + pounds + " 磅 " + rest + " 便士";
    }

    /** 表格里的短写法：不到 1 磅写“13便士”，否则写“1.25磅”。 */
    public static String compact(long pence) {
        if (Math.abs(pence) < 100) return pence + "便士";
        long abs = Math.abs(pence);
        String text = abs % 100 == 0
                ? Long.toString(abs / 100)
                : String.format(java.util.Locale.ROOT, "%d.%02d", abs / 100, abs % 100);
        return (pence < 0 ? "-" : "") + text + "磅";
    }

    /** 基点写成百分比，最多两位小数、去掉末尾的 0：500 → “5%”，250 → “2.5%”，1234 → “12.34%”。 */
    public static String percentBps(long bps) {
        return scaled(bps, 2) + "%";
    }

    /** 千分之一单位（×10 的百分数）写成百分比，最多一位小数：905 → “90.5%”，900 → “90%”。 */
    public static String percentX10(long tenths) {
        return scaled(tenths, 1) + "%";
    }

    private static String scaled(long value, int decimals) {
        return java.math.BigDecimal.valueOf(value, decimals)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** 带正负号，用于“+8 磅 32 便士”这类变化量。 */
    public static String signed(long pence) {
        return (pence > 0 ? "+" : "") + pounds(pence);
    }
}
