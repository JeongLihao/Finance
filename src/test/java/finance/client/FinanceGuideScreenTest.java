package finance.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceGuideScreenTest {
    @Test
    void panelFitsMinecraftMinimumGuiArea() {
        assertTrue(FinanceGuideScreen.PANEL_WIDTH <= 320);
        assertTrue(FinanceGuideScreen.PANEL_HEIGHT <= 240);
    }

    @Test
    void pageTextSplitsIntoTitleAndBody() {
        assertArrayEquals(new String[]{"怎样开始？", "第一段\n\n第二段"},
                FinanceGuideScreen.splitPage("怎样开始？\n\n第一段\n\n第二段"));
        assertArrayEquals(new String[]{"只有标题", ""}, FinanceGuideScreen.splitPage("只有标题"));
        assertArrayEquals(new String[]{"标题", "正文"}, FinanceGuideScreen.splitPage("标题\r\n正文\n"));
        assertArrayEquals(new String[]{"", ""}, FinanceGuideScreen.splitPage(null));
    }

    @Test
    void pageCountStopsAtFirstMissingKeyAndIsBounded() {
        Set<String> keys = Set.of("finance.guide.page.1", "finance.guide.page.2", "finance.guide.page.4");
        assertEquals(2, FinanceGuideScreen.pageCount(keys::contains));
        assertEquals(0, FinanceGuideScreen.pageCount(key -> false));
        assertEquals(FinanceGuideScreen.MAX_PAGES, FinanceGuideScreen.pageCount(key -> true));
    }

    @Test
    void clampHandlesEmptyRanges() {
        assertEquals(0, FinanceGuideScreen.clamp(5, 0, -1));
        assertEquals(12, FinanceGuideScreen.clamp(99, 0, 12));
        assertEquals(0, FinanceGuideScreen.clamp(-3, 0, 12));
    }

    @Test
    void everyHandbookPageHasTitleAndBodyInBothLanguages() throws Exception {
        JsonObject zh = lang("zh_cn"), en = lang("en_us");
        int zhCount = FinanceGuideScreen.pageCount(zh::has);
        assertEquals(zhCount, FinanceGuideScreen.pageCount(en::has));
        assertTrue(zhCount >= 13, "handbook pages: " + zhCount);
        for (JsonObject lang : new JsonObject[]{zh, en}) {
            for (int i = 1; i <= zhCount; i++) {
                String[] page = FinanceGuideScreen.splitPage(lang.get(FinanceGuideScreen.PAGE_KEY + i).getAsString());
                assertFalse(page[0].isBlank(), "title of page " + i);
                assertFalse(page[1].isBlank(), "body of page " + i);
            }
        }
    }

    private JsonObject lang(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("assets/finance/lang/" + name + ".json")) {
            assertNotNull(in, name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
