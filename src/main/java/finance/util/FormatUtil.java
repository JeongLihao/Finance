package finance.util;

/**
 * 格式化工具类 —— 提供统一的数据显示方法。
 */
public class FormatUtil {

    /**
     * 格式化百分比数值。
     * <ul>
     *   <li>正数: "+20%"</li>
     *   <li>负数: "-15%"</li>
     *   <li>零:   "0%"</li>
     * </ul>
     *
     * @param value 百分比数值（如 20.0 表示 20%）
     * @return 格式化后的字符串
     */
    public static String formatPercent(double value) {
        if (value > 0) {
            return "+" + (int) value + "%";
        } else if (value < 0) {
            return (int) value + "%";
        }
        return "0%";
    }
}
