package finance.client;

import static org.junit.jupiter.api.Assertions.*;

import finance.client.ui.TextMeasure;
import finance.client.ui.UiBuilder;
import finance.client.ui.UiElement;
import finance.client.ui.UiLayoutCheck;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.CompanyRows;
import finance.gui.MarketRows;
import finance.gui.RecordRows;
import finance.gui.StockRows;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleCenterService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 金融中心每一页都用极端数据排一遍（天文数字的金额、32 字公司名、很长的提示），
 * 只要有文字、按钮、图标、输入框互相重叠或超出面板，测试就失败。
 */
class SimpleLayoutTest {

    static final long HUGE = 999_999_999_999L;
    static final String LONG_NAME = "超级无敌巨型钢铁联合矿业与农业与林业控股集团有限公司";

    private static final SimplePages.Actions NO_OP = new SimplePages.Actions() {
        @Override
        public void simple(SimpleActionPacket.Action action, String target, long amount, long price) {}

        @Override
        public void company(CompanyManagementAction action, long amount) {}

        @Override
        public void createCompany(CompanyType type) {}

        @Override
        public void stock(boolean buy, String symbol, int quantity) {}

        @Override
        public void status(String message) {}

        @Override
        public void show(SimplePages.Page page) {}

        @Override
        public void setWantsAdvanced(boolean on) {}

        @Override
        public void dismissSummary() {}
    };

    static CompanyRows.CompanyInfo company(boolean owned, boolean isPublic, boolean risk) {
        return new CompanyRows.CompanyInfo(
                UUID.randomUUID(),
                LONG_NAME,
                CompanyType.FOOD_PROCESSING.getDisplayName(),
                HUGE,
                HUGE,
                HUGE,
                Map.of(),
                owned,
                isPublic,
                "STABLE",
                5,
                5,
                4,
                0.5,
                HUGE,
                HUGE,
                -HUGE,
                HUGE,
                HUGE,
                HUGE,
                0,
                0,
                "",
                risk,
                0,
                0,
                0,
                "");
    }

    static SimplePages.View view(long balance, CompanyRows.CompanyInfo company, boolean manyRows, boolean status) {
        return view(balance, company, manyRows, status, List.of("all"));
    }

    static SimplePages.View view(
            long balance, CompanyRows.CompanyInfo company, boolean manyRows, boolean status, List<String> extras) {
        List<MarketRows.MarketRow> market = new ArrayList<>();
        Map<String, Integer> backpack = new LinkedHashMap<>();
        int n = manyRows ? 30 : 3;
        for (int i = 0; i < n; i++) {
            String id = "commodity_with_a_long_id_" + i;
            market.add(
                    new MarketRows.MarketRow(id, HUGE, HUGE, HUGE, i % 2 == 0 ? 0.4 : -0.4, 1, 1, HUGE, 1, List.of()));
            if (i % 3 == 0) backpack.put(id, 2_304);
        }
        List<StockRows.StockRow> stocks = new ArrayList<>();
        List<StockRows.StockHoldingRow> holdings = new ArrayList<>();
        for (int i = 0; i < (manyRows ? 20 : 2); i++) {
            stocks.add(new StockRows.StockRow(
                    "S" + i, LONG_NAME + i, HUGE, 0.3, 1, 1, 1, 1, 1, List.of(), HUGE, 0, 0, 0, 0, false, 0));
            if (i % 2 == 0) holdings.add(new StockRows.StockHoldingRow("S" + i, HUGE, 1));
        }
        List<RecordRows.TransactionRow> tx = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            tx.add(new RecordRows.TransactionRow(0, UUID.randomUUID(), "COMMODITY_SELL", HUGE, HUGE, LONG_NAME));
        // 没有任何进阶玩法时，三组都显示“怎么解锁”
        List<String> locked = extras.isEmpty() ? List.of("growth", "bet", "boss") : List.of();
        SimpleCenterService.Status st = status
                ? new SimpleCenterService.Status(
                        HUGE,
                        12_345,
                        7,
                        HUGE,
                        HUGE,
                        20_000,
                        SimpleCenterService.IpoProgress.NONE,
                        3_000,
                        "player",
                        extras,
                        locked,
                        List.of(),
                        cards(manyRows),
                        List.of(
                                "利率 99.99%：" + LONG_NAME + LONG_NAME,
                                "物价一年涨 99.99%：" + LONG_NAME + LONG_NAME + LONG_NAME,
                                "央行下次开会：" + LONG_NAME,
                                "上次开会：" + LONG_NAME,
                                "央行储备：" + LONG_NAME + LONG_NAME + LONG_NAME,
                                "流通中的钱 " + HUGE + LONG_NAME + LONG_NAME + LONG_NAME),
                        List.of(),
                        products(manyRows),
                        investHoldings(manyRows),
                        new finance.simple.SimpleInvestService.MarginInfo(
                                HUGE, HUGE, 101, 130, Map.of("S0", HUGE, "S1", HUGE), Map.of("S0", HUGE)),
                        bets(manyRows),
                        1_000,
                        boss(manyRows),
                        new finance.network.PlayerServicesResponsePacket.Cash(
                                true, HUGE, HUGE, HUGE, HUGE, HUGE, HUGE, HUGE, HUGE, HUGE),
                        List.of(
                                new SimpleCenterService.Valuation("S0", HUGE, -HUGE, 120, HUGE),
                                new SimpleCenterService.Valuation("S1", HUGE, HUGE, 40, 1)),
                        manyRows ? List.of(HUGE, 0L, HUGE / 2, HUGE, 1L, HUGE, 0L, HUGE) : List.of(),
                        manyRows
                                ? List.of(
                                        "1. " + LONG_NAME + "  " + HUGE,
                                        "2. " + LONG_NAME + "  " + HUGE + "（你）",
                                        "3. x",
                                        "4. y",
                                        "5. z")
                                : List.of(),
                        manyRows ? "战绩：99999 笔，赚了 99999 笔，累计 +" + HUGE + "，最赚一笔 +" + HUGE + "，最亏一笔 -" + HUGE : "")
                : null;
        List<String> summary = balance == HUGE
                ? List.of("收入 +" + HUGE + LONG_NAME, "卖东西 " + LONG_NAME, "分红 " + LONG_NAME, "其他收入 " + LONG_NAME)
                : List.of();
        List<MarketRows.OrderRow> orders = new ArrayList<>();
        for (int i = 0; i < (manyRows ? 8 : 1); i++) {
            orders.add(new MarketRows.OrderRow(
                    UUID.randomUUID(), market.get(0).commodityId(), i % 2 == 0 ? "SELL" : "BUY", HUGE, 99_999, true));
        }
        Map<String, Integer> stored = manyRows
                ? Map.of("a_very_long_commodity_id_one", 99_999, "a_very_long_commodity_id_two", 99_999)
                : Map.of();
        return new SimplePages.View(
                balance,
                -HUGE,
                HUGE,
                backpack,
                market,
                tx,
                company,
                stocks,
                holdings,
                st,
                true,
                true,
                summary,
                orders,
                stored,
                futures(manyRows),
                autos(manyRows),
                proposals(manyRows));
    }

    static List<finance.simple.SimpleOpportunityService.Card> cards(boolean many) {
        List<finance.simple.SimpleOpportunityService.Card> out = new ArrayList<>();
        out.add(new finance.simple.SimpleOpportunityService.Card(
                LONG_NAME + "股价只有每股价值的 5 成：发起收购" + LONG_NAME,
                "出价收购",
                "TAKEOVER",
                UUID.randomUUID().toString(),
                0,
                "",
                true));
        out.add(new finance.simple.SimpleOpportunityService.Card(
                "钱包里闲着 " + HUGE + "：放进理财" + LONG_NAME, "去理财", "", "", 0, "INVEST:1", false));
        out.add(new finance.simple.SimpleOpportunityService.Card(
                LONG_NAME + "需要 9999 个面包" + LONG_NAME, "", "", "", 0, "", false));
        if (many) {
            for (int i = 0; i < 5; i++) {
                out.add(new finance.simple.SimpleOpportunityService.Card(
                        "库存险：最多赔 " + HUGE + LONG_NAME,
                        "投保 " + HUGE,
                        "INSURE",
                        "INVENTORY_DISASTER",
                        HUGE,
                        "",
                        i % 2 == 0));
            }
        }
        return out;
    }

    static List<finance.simple.SimpleBetService.Bet> bets(boolean many) {
        List<finance.simple.SimpleBetService.Bet> out = new ArrayList<>();
        for (int i = 0; i < (many ? 12 : 1); i++) {
            out.add(new finance.simple.SimpleBetService.Bet(
                    i % 2 == 0 ? "s:S" + i + ":up" : "f:" + UUID.randomUUID(),
                    LONG_NAME,
                    i % 2 == 0,
                    HUGE,
                    i % 3 == 0 ? 0 : HUGE,
                    "999999 份 × 64 个，第 99999 天自动结算" + LONG_NAME,
                    i % 4 != 3));
        }
        return out;
    }

    static List<CompanyRows.CompanyProposalRow> proposals(boolean many) {
        List<CompanyRows.CompanyProposalRow> out = new ArrayList<>();
        for (int i = 0; i < (many ? 4 : 1); i++) {
            out.add(new CompanyRows.CompanyProposalRow(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "DIVIDEND",
                    LONG_NAME,
                    "",
                    1,
                    2,
                    3,
                    0,
                    99_999,
                    0.5,
                    HUGE,
                    HUGE,
                    false,
                    "ACTIVE",
                    "",
                    false,
                    "",
                    0,
                    0));
        }
        return out;
    }

    static List<StockRows.ConditionalStockOrderRow> autos(boolean many) {
        List<StockRows.ConditionalStockOrderRow> out = new ArrayList<>();
        for (int i = 0; i < (many ? 5 : 1); i++) {
            out.add(new StockRows.ConditionalStockOrderRow(
                    UUID.randomUUID(), "S0", i % 2 == 0 ? "TAKE_PROFIT" : "STOP_LOSS", HUGE, HUGE));
        }
        return out;
    }

    static finance.network.FuturesResponsePacket futures(boolean many) {
        List<finance.network.FuturesResponsePacket.ContractRow> contracts = new ArrayList<>();
        List<finance.network.FuturesResponsePacket.PositionRow> positions = new ArrayList<>();
        List<finance.network.FuturesResponsePacket.MmQuoteRow> quotes = new ArrayList<>();
        for (int i = 0; i < (many ? 9 : 2); i++) {
            UUID id = UUID.randomUUID();
            // 第一份合约对应第一种商品，商品详情里的押注一行和说明也要排一遍
            String commodity = i == 0 ? "commodity_with_a_long_id_0" : LONG_NAME;
            contracts.add(new finance.network.FuturesResponsePacket.ContractRow(
                    id,
                    "LONGCODE" + i,
                    commodity,
                    finance.futures.FuturesContractStatus.TRADING,
                    64,
                    99_999,
                    99_999,
                    HUGE,
                    HUGE,
                    HUGE,
                    HUGE,
                    HUGE));
            positions.add(new finance.network.FuturesResponsePacket.PositionRow(id, -9_999, HUGE, HUGE, -HUGE, HUGE));
            if (i % 2 == 0)
                quotes.add(
                        new finance.network.FuturesResponsePacket.MmQuoteRow(id, many ? 1 : HUGE, many ? 2 : HUGE, 0));
        }
        return new finance.network.FuturesResponsePacket(
                1,
                HUGE,
                HUGE,
                HUGE,
                HUGE,
                HUGE,
                HUGE,
                finance.futures.MarginRiskStatus.NORMAL,
                0,
                0,
                contracts,
                positions,
                List.of(),
                List.of(),
                quotes,
                false);
    }

    static finance.simple.SimpleBossService.BossInfo boss(boolean many) {
        List<finance.simple.SimpleBossService.Insurance> insurance = List.of(
                new finance.simple.SimpleBossService.Insurance("INVENTORY_DISASTER", LONG_NAME, HUGE, HUGE, false, 0),
                new finance.simple.SimpleBossService.Insurance(
                        "BUSINESS_INTERRUPTION", LONG_NAME, HUGE, HUGE, true, 99_999));
        List<finance.simple.SimpleBossService.Target> targets = new ArrayList<>();
        for (int i = 0; i < (many ? 6 : 1); i++)
            targets.add(new finance.simple.SimpleBossService.Target(
                    UUID.randomUUID().toString(), LONG_NAME, HUGE, 999, i % 2 == 0));
        return new finance.simple.SimpleBossService.BossInfo(
                many, 90, true, HUGE, HUGE, insurance, targets, !many, HUGE, HUGE);
    }

    static List<finance.simple.SimpleInvestService.Product> products(boolean many) {
        List<finance.simple.SimpleInvestService.Product> out = new ArrayList<>();
        for (int i = 0; i < (many ? 14 : 3); i++) {
            out.add(new finance.simple.SimpleInvestService.Product(
                    "fund:x" + i,
                    LONG_NAME,
                    "99999 天",
                    i % 2 == 0 ? -1 : 99_999,
                    i % 3 == 0 ? "低" : i % 3 == 1 ? "中" : "高",
                    HUGE));
        }
        return out;
    }

    static List<finance.simple.SimpleInvestService.Holding> investHoldings(boolean many) {
        List<finance.simple.SimpleInvestService.Holding> out = new ArrayList<>();
        for (int i = 0; i < (many ? 12 : 1); i++) {
            out.add(new finance.simple.SimpleInvestService.Holding(
                    "time:" + i, LONG_NAME, HUGE, "第 99999 天到期" + LONG_NAME, "提前取（没利息）"));
        }
        return out;
    }

    static List<SimplePages.View> views() {
        List<SimplePages.View> out = new ArrayList<>();
        for (long balance : new long[] {0, 999, HUGE}) {
            for (boolean many : new boolean[] {false, true}) {
                for (boolean status : new boolean[] {false, true}) {
                    out.add(view(balance, null, many, status));
                    out.add(view(balance, company(false, false, false), many, status));
                    out.add(view(balance, company(true, false, false), many, status));
                    out.add(view(balance, company(true, false, true), many, status));
                    out.add(view(balance, company(true, true, false), many, status));
                    if (status) out.add(view(balance, company(true, true, false), many, true, List.of()));
                }
            }
        }
        return out;
    }

    static List<UiElement> layout(SimplePages.View v, SimplePages.Page page, int scroll) {
        return layout(v, page, scroll, 0);
    }

    static List<UiElement> layout(SimplePages.View v, SimplePages.Page page, int scroll, int subTab) {
        return layout(v, page, scroll, subTab, false);
    }

    static List<UiElement> layout(SimplePages.View v, SimplePages.Page page, int scroll, int subTab, boolean shift) {
        SimplePages.State s = new SimplePages.State();
        s.shift = shift;
        s.favorites = java.util.Set.of("commodity_with_a_long_id_3");
        s.page = page;
        s.homeTab = subTab;
        s.tradeTab = subTab;
        s.investTab = subTab;
        s.companyTab = subTab;
        s.productScroll = scroll;
        s.recordScroll = scroll;
        s.tradeScroll = scroll;
        s.investScroll = scroll;
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        SimplePages.layout(ui, v, s, NO_OP);
        return ui.elements();
    }

    @Test
    void noPageEverOverlapsOrLeavesThePanel() {
        List<String> problems = new ArrayList<>();
        for (SimplePages.View v : views()) {
            for (SimplePages.Page page : SimplePages.Page.values()) {
                for (int scroll : new int[] {0, 5, 999}) {
                    for (int sub = 0; sub < 4; sub++) {
                        for (boolean shift : new boolean[] {false, true}) {
                            for (String p : UiLayoutCheck.problems(
                                    layout(v, page, scroll, sub, shift),
                                    FinanceGui.PANEL_WIDTH,
                                    FinanceGui.PANEL_HEIGHT)) {
                                problems.add(page + "/" + sub + (shift ? "/shift" : "") + ": " + p);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(
                problems.isEmpty(),
                () -> problems.size() + " 处排版问题，例如：\n"
                        + String.join("\n", problems.subList(0, Math.min(15, problems.size()))));
    }

    @Test
    void stockDetailNeverOverlaps() {
        List<String> problems = new ArrayList<>();
        for (SimplePages.View v : views()) {
            for (boolean candles : new boolean[] {false, true}) {
                if (!v.stocks().isEmpty()) {
                    SimplePages.State s = new SimplePages.State();
                    s.page = SimplePages.Page.INVEST;
                    s.candles = candles;
                    s.selectedStock = v.stocks().get(0).symbol();
                    UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
                    SimplePages.layout(ui, v, s, NO_OP);
                    for (String p :
                            UiLayoutCheck.problems(ui.elements(), FinanceGui.PANEL_WIDTH, FinanceGui.PANEL_HEIGHT))
                        problems.add("股票详情: " + p);
                }
                if (!v.market().isEmpty()) {
                    SimplePages.State s = new SimplePages.State();
                    s.page = SimplePages.Page.TRADE;
                    s.candles = candles;
                    s.selectedCommodity = v.market().get(0).commodityId();
                    UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
                    SimplePages.layout(ui, v, s, NO_OP);
                    for (String p :
                            UiLayoutCheck.problems(ui.elements(), FinanceGui.PANEL_WIDTH, FinanceGui.PANEL_HEIGHT))
                        problems.add("商品详情: " + p);
                }
            }
        }
        assertTrue(
                problems.isEmpty(),
                () -> problems.size() + " 处排版问题，例如：\n"
                        + String.join("\n", problems.subList(0, Math.min(15, problems.size()))));
    }

    @Test
    void unlockPopupFitsThePanel() {
        SimplePages.View base = view(HUGE, company(true, true, false), true, true);
        List<String> lines = new ArrayList<>();
        for (finance.simple.SimpleUnlocks.Group g : finance.simple.SimpleUnlocks.Group.values()) {
            lines.add(SimpleHomePage.UNLOCK_PREFIX + g.label + "」：" + g.intro + LONG_NAME);
        }
        lines.add("收入 +" + HUGE + LONG_NAME);
        lines.add("卖东西 " + LONG_NAME + LONG_NAME);
        lines.add("分红 " + LONG_NAME + LONG_NAME);
        SimplePages.View v = new SimplePages.View(
                base.balance(),
                base.todayProfit(),
                base.stockValue(),
                base.backpack(),
                base.market(),
                base.transactions(),
                base.company(),
                base.stocks(),
                base.holdings(),
                base.status(),
                true,
                true,
                lines,
                base.orders(),
                base.storedGoods(),
                base.futures(),
                base.autoOrders(),
                base.proposals());
        List<UiElement> home = layout(v, SimplePages.Page.HOME, 0);
        assertTrue(
                UiLayoutCheck.problems(home, FinanceGui.PANEL_WIDTH, FinanceGui.PANEL_HEIGHT)
                        .isEmpty(),
                () -> UiLayoutCheck.problems(home, FinanceGui.PANEL_WIDTH, FinanceGui.PANEL_HEIGHT)
                        .toString());
        assertTrue(home.stream().anyMatch(e -> "解锁了新玩法".equals(e.text())), "解锁时小结卡换成“解锁了新玩法”");
    }

    @Test
    void opportunityCardsJumpToTheirPage() {
        SimplePages.State s = new SimplePages.State();
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        SimplePages.layout(ui, view(999, company(true, true, false), false, true), s, NO_OP);
        UiElement go = ui.elements().stream()
                .filter(e -> e.kind() == UiElement.Kind.BUTTON && "去理财".equals(e.text()))
                .findFirst()
                .orElseThrow();
        go.action().run();
        assertEquals(1, s.investTab, "“去理财”跳到投资页的理财小标签");
        SimplePages.goTo(s, NO_OP, "STOCK:S1");
        assertEquals("S1", s.selectedStock);
        assertEquals(0, s.investTab);
        assertNull(SimplePages.actionOf("NOT_AN_ACTION"), "不认识的操作不发包");
    }

    @Test
    void searchFiltersAndFavoritesComeFirst() {
        SimplePages.View v = view(999, null, true, true);
        SimplePages.State s = new SimplePages.State();
        s.favorites = java.util.Set.of("commodity_with_a_long_id_7");
        assertEquals(
                "commodity_with_a_long_id_7",
                SimpleTradePage.tradeRows(v, s).get(0).commodityId(),
                "收藏的排最前面");
        s.search = "ID_12";
        List<MarketRows.MarketRow> found = SimpleTradePage.tradeRows(v, s);
        assertEquals(1, found.size(), "搜索不分大小写");
        assertEquals("commodity_with_a_long_id_12", found.get(0).commodityId());
        s.search = "nothing-like-this";
        assertTrue(SimpleTradePage.tradeRows(v, s).isEmpty());
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        s.page = SimplePages.Page.TRADE;
        SimplePages.layout(ui, v, s, NO_OP);
        assertTrue(
                ui.elements().stream().anyMatch(e -> e.kind() == UiElement.Kind.INPUT && "search".equals(e.key())),
                "列表下面有搜索框");
    }

    @Test
    void bigActionsNeedASecondClick() {
        SimplePages.State s = new SimplePages.State();
        int[] runs = {0};
        String[] status = {null};
        SimplePages.Actions a = new SimplePages.Actions() {
            @Override
            public void simple(SimpleActionPacket.Action action, String target, long amount, long price) {}

            @Override
            public void company(CompanyManagementAction action, long amount) {}

            @Override
            public void createCompany(CompanyType type) {}

            @Override
            public void stock(boolean buy, String symbol, int quantity) {}

            @Override
            public void status(String message) {
                status[0] = message;
            }

            @Override
            public void show(SimplePages.Page page) {}

            @Override
            public void setWantsAdvanced(boolean on) {}

            @Override
            public void dismissSummary() {}
        };
        UiBuilder.Btn small = SimplePages.confirm(s, a, "k", "全卖", () -> runs[0]++, null, false);
        small.action().run();
        assertEquals(1, runs[0], "小额直接执行");
        UiBuilder.Btn first = SimplePages.confirm(s, a, "k", "全卖", () -> runs[0]++, null, true);
        assertEquals("全卖", first.label());
        first.action().run();
        assertEquals(1, runs[0], "大额第一次只是待确认");
        assertNotNull(status[0]);
        UiBuilder.Btn second = SimplePages.confirm(s, a, "k", "全卖", () -> runs[0]++, null, true);
        assertEquals("确认", second.label());
        second.action().run();
        assertEquals(2, runs[0], "再点一次才执行");
        assertEquals(
                "全卖",
                SimplePages.confirm(s, a, "k", "全卖", () -> runs[0]++, null, true)
                        .label(),
                "执行后恢复原样");
        assertEquals(
                "背包里没有",
                SimplePages.confirm(s, a, "k", "全卖", () -> runs[0]++, "背包里没有", true)
                        .blocked());
    }

    @Test
    void shiftMultipliesQuantitiesByTen() {
        SimplePages.View v = view(HUGE / 1000, null, false, true);
        List<UiElement> normal = layout(v, SimplePages.Page.TRADE, 0, 0, false);
        List<UiElement> shifted = layout(v, SimplePages.Page.TRADE, 0, 0, true);
        assertTrue(normal.stream().anyMatch(e -> "买1组".equals(e.text())));
        assertTrue(shifted.stream().anyMatch(e -> "买10组".equals(e.text())), "按住 Shift 变成 ×10");
        assertTrue(shifted.stream().anyMatch(e -> "卖10组".equals(e.text())));
    }

    @Test
    void clippedTextKeepsTheFullTextForTheTooltip() {
        List<UiElement> home = layout(view(999, company(true, false, false), true, true), SimplePages.Page.HOME, 0);
        assertTrue(
                home.stream()
                        .anyMatch(e -> e.kind() == UiElement.Kind.TEXT
                                && e.tooltip() != null
                                && e.text().endsWith("...")),
                "被截断的长文字要留着全文给悬停提示");
    }

    @Test
    void disabledButtonsExplainThemselves() {
        List<UiElement> trade = layout(view(0, null, false, true), SimplePages.Page.TRADE, 0);
        assertTrue(trade.stream()
                .anyMatch(
                        e -> e.kind() == UiElement.Kind.BUTTON_DISABLED && e.tooltip() != null && e.action() != null));
    }

    @Test
    void checkerCatchesOverlapsAndOverflow() {
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        ui.text(10, 10, 200, "第一行文字", 0);
        ui.button(20, 12, "压在上面的按钮", () -> {}, null);
        ui.text(395, 100, 100, "超出右边", 0);
        ui.fill(0, 0, 400, 232, 0); // 背景不算
        List<String> problems = UiLayoutCheck.problems(ui.elements(), 400, 232);
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void wrapNeverExceedsWidth() {
        UiBuilder ui = new UiBuilder(TextMeasure.ESTIMATE, m -> {});
        for (String line : ui.wrap("Long English words should wrap at spaces，中文按字换行，" + LONG_NAME, 120)) {
            assertTrue(TextMeasure.ESTIMATE.width(line) <= 120, line);
        }
        assertEquals("", ui.clip("abc", 3));
        assertTrue(TextMeasure.ESTIMATE.width(ui.clip(LONG_NAME, 50)) <= 50);
    }
}
