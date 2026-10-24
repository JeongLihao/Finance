package finance.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 基点、千分数写成百分比：最多两位 / 一位小数，去掉末尾的 0。 */
class MoneyFormatPercentTest {

    @Test
    void basisPointsTrimTrailingZeros() {
        assertEquals("5%", MoneyFormat.percentBps(500));
        assertEquals("2.5%", MoneyFormat.percentBps(250));
        assertEquals("12.34%", MoneyFormat.percentBps(1234));
        assertEquals("0.07%", MoneyFormat.percentBps(7));
        assertEquals("0%", MoneyFormat.percentBps(0));
        assertEquals("-1.5%", MoneyFormat.percentBps(-150));
    }

    @Test
    void tenthsTrimTrailingZeros() {
        assertEquals("90.5%", MoneyFormat.percentX10(905));
        assertEquals("90%", MoneyFormat.percentX10(900));
    }
}
