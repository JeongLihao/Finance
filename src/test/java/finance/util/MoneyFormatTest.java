package finance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyFormatTest {
    @Test void formatsPenceAsPoundsAndPence() {
        assertEquals("0 便士", MoneyFormat.pounds(0));
        assertEquals("40 便士", MoneyFormat.pounds(40));
        assertEquals("12 磅", MoneyFormat.pounds(1200));
        assertEquals("12 磅 40 便士", MoneyFormat.pounds(1240));
        assertEquals("-3 磅 5 便士", MoneyFormat.pounds(-305));
        assertEquals("+8 磅 32 便士", MoneyFormat.signed(832));
        assertEquals("13便士", MoneyFormat.compact(13));
        assertEquals("1.25磅", MoneyFormat.compact(125));
        assertEquals("2磅", MoneyFormat.compact(200));
    }
}
