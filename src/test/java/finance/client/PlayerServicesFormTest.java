package finance.client;

import static org.junit.jupiter.api.Assertions.*;

import finance.client.PlayerServicesForm.InvalidInput;
import finance.client.PlayerServicesForm.Page;
import org.junit.jupiter.api.Test;

class PlayerServicesFormTest {
    @Test
    void everyPageHasFourBoxSlotsAndAtLeastOneInput() {
        for (Page page : Page.values()) {
            String[] labels = PlayerServicesForm.labels(page, null);
            assertEquals(4, labels.length);
            assertNotNull(labels[0], page + " 至少有一个输入框");
        }
        assertEquals("代码 名称", PlayerServicesForm.labels(Page.BANK, null)[0], "没有数据时按还没有银行显示");
    }

    @Test
    void numbersAreParsedStrictly() {
        assertEquals(1_200, PlayerServicesForm.positive("1,200", "金额"));
        assertEquals(0, PlayerServicesForm.number("  ", "数量", true));
        assertThrows(InvalidInput.class, () -> PlayerServicesForm.positive("0", "金额"));
        assertThrows(InvalidInput.class, () -> PlayerServicesForm.positive("-5", "金额"));
        assertThrows(InvalidInput.class, () -> PlayerServicesForm.positive("abc", "金额"));
        assertThrows(InvalidInput.class, () -> PlayerServicesForm.number("", "数量", false));
        assertThrows(InvalidInput.class, () -> PlayerServicesForm.text(" ", "股票代码"));
        assertEquals("ABC", PlayerServicesForm.text(" ABC ", "股票代码"));
    }
}
