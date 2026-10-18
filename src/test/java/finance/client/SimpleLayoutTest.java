package finance.client;

import finance.client.ui.TextMeasure;
import finance.client.ui.UiBuilder;
import finance.client.ui.UiElement;
import finance.client.ui.UiLayoutCheck;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleCenterService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 金融中心每一页都用极端数据排一遍（天文数字的金额、32 字公司名、很长的提示），
 * 只要有文字、按钮、图标、输入框互相重叠或超出面板，测试就失败。
 */
class SimpleLayoutTest {

    static final long HUGE = 999_999_999_999L;
    static final String LONG_NAME = "超级无敌巨型钢铁联合矿业与农业与林业控股集团有限公司";

    private static final SimplePages.Actions NO_OP = new SimplePages.Actions() {
        @Override public void simple(SimpleActionPacket.Action action, String target, long amount) {}
        @Override public void company(CompanyManagementAction action, long amount) {}
        @Override public void createCompany(CompanyType type) {}
        @Override public void stock(boolean buy, String symbol, int quantity) {}
        @Override public void status(String message) {}
        @Override public void show(SimplePages.Page page) {}
        @Override public void setWantsAdvanced(boolean on) {}
        @Override public void dismissSummary() {}
    };

    static FinanceMenu.CompanyInfo company(boolean owned, boolean isPublic, boolean risk) {
        return new FinanceMenu.CompanyInfo(UUID.randomUUID(), LONG_NAME, CompanyType.FOOD_PROCESSING.getDisplayName(),
                HUGE, HUGE, HUGE, Map.of(), owned, isPublic, "STABLE", 5, 5, 4, 0.5,
                HUGE, HUGE, -HUGE, HUGE, HUGE, HUGE, 0, 0, "", risk, 0, 0, 0, "");
    }

    static SimplePages.View view(long balance, FinanceMenu.CompanyInfo company, boolean manyRows, boolean status) {
        List<FinanceMenu.MarketRow> market = new ArrayList<>();
        Map<String, Integer> backpack = new LinkedHashMap<>();
        int n = manyRows ? 30 : 3;
        for (int i = 0; i < n; i++) {
            String id = "commodity_with_a_long_id_" + i;
            market.add(new FinanceMenu.MarketRow(id, HUGE, HUGE, HUGE, i % 2 == 0 ? 0.4 : -0.4, 1, 1, HUGE, 1, List.of()));
            if (i % 3 == 0) backpack.put(id, 2_304);
        }
        List<FinanceMenu.StockRow> stocks = new ArrayList<>();
        List<FinanceMenu.StockHoldingRow> holdings = new ArrayList<>();
        for (int i = 0; i < (manyRows ? 20 : 2); i++) {
            stocks.add(new FinanceMenu.StockRow("S" + i, LONG_NAME + i, HUGE, 0.3, 1, 1, 1, 1, 1, List.of(),
                    HUGE, 0, 0, 0, 0, false, 0));
            if (i % 2 == 0) holdings.add(new FinanceMenu.StockHoldingRow("S" + i, HUGE, 1));
        }
        List<FinanceMenu.TransactionRow> tx = new ArrayList<>();
        for (int i = 0; i < 5; i++) tx.add(new FinanceMenu.TransactionRow(0, UUID.randomUUID(), "COMMODITY_SELL", HUGE, HUGE, LONG_NAME));
        SimpleCenterService.Status st = status ? new SimpleCenterService.Status(HUGE, 12_345, 7, HUGE, HUGE, 20_000, 3_000, "player",
                List.of(LONG_NAME + "需要 9999 个面包，送去能拿 " + HUGE + " 便士（去村里的贸易站交货）",
                        "背包里的 2304 个" + LONG_NAME + "现在能卖很多钱", "第三条机会" + LONG_NAME),
                List.of("利率 99.99%：" + LONG_NAME + LONG_NAME, "物价一年涨 99.99%：" + LONG_NAME + LONG_NAME + LONG_NAME,
                        "央行下次开会：" + LONG_NAME, "上次开会：" + LONG_NAME), List.of()) : null;
        List<String> summary = balance == HUGE ? List.of("收入 +" + HUGE + LONG_NAME, "卖东西 " + LONG_NAME, "分红 " + LONG_NAME,
                "其他收入 " + LONG_NAME) : List.of();
        return new SimplePages.View(balance, -HUGE, HUGE, backpack, market, tx, company, stocks, holdings, st, true, true, summary);
    }

    static List<SimplePages.View> views() {
        List<SimplePages.View> out = new ArrayList<>();
        for (long balance : new long[]{0, 999, HUGE}) {
            for (boolean many : new boolean[]{false, true}) {
                for (boolean status : new boolean[]{false, true}) {
                    out.add(view(balance, null, many, status));
                    out.add(view(balance, company(false, false, false), many, status));
                    out.add(view(balance, company(true, false, false), many, status));
                    out.add(view(balance, company(true, false, true), many, status));
                    out.add(view(balance, company(true, true, false), many, status));
                }
            }
        }
        return out;
    }

    static List<UiElement> layout(SimplePages.View v, SimplePages.Page page, int scroll) {
        return layout(v, page, scroll, 0);
    }

    static List<UiElement> layout(SimplePages.View v, SimplePages.Page page, int scroll, int subTab) {
        SimplePages.State s = new SimplePages.State();
        s.page = page;
        s.homeTab = subTab;
        s.recordScroll = scroll;
        s.tradeScroll = scroll;
        s.investScroll = scroll;
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        SimplePages.layout(ui, v, s, NO_OP);
        return ui.elements();
    }

    @Test void noPageEverOverlapsOrLeavesThePanel() {
        List<String> problems = new ArrayList<>();
        for (SimplePages.View v : views()) {
            for (SimplePages.Page page : SimplePages.Page.values()) {
                for (int scroll : new int[]{0, 5, 999}) {
                    for (int sub = 0; sub < 3; sub++) {
                        for (String p : UiLayoutCheck.problems(layout(v, page, scroll, sub), FinanceGui.PANEL_WIDTH, FinanceGui.PANEL_HEIGHT)) {
                            problems.add(page + "/" + sub + ": " + p);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> problems.size() + " 处排版问题，例如：\n" + String.join("\n", problems.subList(0, Math.min(15, problems.size()))));
    }

    @Test void clippedTextKeepsTheFullTextForTheTooltip() {
        List<UiElement> home = layout(view(HUGE, company(true, false, false), true, true), SimplePages.Page.HOME, 0);
        assertTrue(home.stream().anyMatch(e -> e.kind() == UiElement.Kind.TEXT && e.tooltip() != null && e.text().endsWith("...")),
                "被截断的长文字要留着全文给悬停提示");
    }

    @Test void disabledButtonsExplainThemselves() {
        List<UiElement> trade = layout(view(0, null, false, true), SimplePages.Page.TRADE, 0);
        assertTrue(trade.stream().anyMatch(e -> e.kind() == UiElement.Kind.BUTTON_DISABLED && e.tooltip() != null && e.action() != null));
    }

    @Test void checkerCatchesOverlapsAndOverflow() {
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        ui.text(10, 10, 200, "第一行文字", 0);
        ui.button(20, 12, "压在上面的按钮", () -> {}, null);
        ui.text(395, 100, 100, "超出右边", 0);
        ui.fill(0, 0, 400, 232, 0); // 背景不算
        List<String> problems = UiLayoutCheck.problems(ui.elements(), 400, 232);
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test void wrapNeverExceedsWidth() {
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        for (String line : ui.wrap("Long English words should wrap at spaces，中文按字换行，" + LONG_NAME, 120)) {
            assertTrue(TextMeasure.ESTIMATE.width(line) <= 120, line);
        }
        assertEquals("", ui.clip("abc", 3));
        assertTrue(TextMeasure.ESTIMATE.width(ui.clip(LONG_NAME, 50)) <= 50);
    }
}
