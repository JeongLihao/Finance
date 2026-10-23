package finance.client;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.CompanyRows;
import finance.gui.MarketRows;
import finance.gui.RecordRows;
import finance.gui.StockRows;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleOpportunityService;
import finance.simple.SimpleUnlocks;
import java.util.List;
import java.util.Map;

/**
 * 简单模式四页的排版：只根据数据排出元素，不画、不发包（动作交给 {@link Actions}），测试里可以直接跑。
 * 这里放四页共用的数据（{@link View}、{@link State}）、页签、大额确认和小工具；
 * 每一页的排版在 {@link SimpleHomePage}、{@link SimpleTradePage}、{@link SimpleCompanyPage}、{@link SimpleInvestPage}。
 * 所有位置都按文字实际宽度计算，放不下的截断并悬停显示全文。
 */
final class SimplePages {

    enum Page {
        HOME("首页"),
        TRADE("买卖"),
        COMPANY("我的公司"),
        INVEST("投资");
        final String label;

        Page(String label) {
            this.label = label;
        }
    }

    /** 排版需要的全部数据（从金融中心快照和首页数据里取）。 */
    record View(
            long balance,
            long todayProfit,
            long stockValue,
            Map<String, Integer> backpack,
            List<MarketRows.MarketRow> market,
            List<RecordRows.TransactionRow> transactions,
            CompanyRows.CompanyInfo company,
            List<StockRows.StockRow> stocks,
            List<StockRows.StockHoldingRow> holdings,
            SimpleCenterService.Status status,
            boolean playerChoosesAdvanced,
            boolean wantsAdvanced,
            List<String> pendingSummary,
            List<MarketRows.OrderRow> orders,
            Map<String, Integer> storedGoods,
            finance.network.FuturesResponsePacket futures,
            List<StockRows.ConditionalStockOrderRow> autoOrders,
            List<CompanyRows.CompanyProposalRow> proposals) {}

    /** 页面上的状态（当前页、滚动位置、新公司行业）。 */
    static final class State {
        Page page = Page.HOME;
        int tradeScroll, investScroll, recordScroll;
        /**
         * 各页的小标签：首页 0 今天 / 1 经济天气 / 2 全部记录；买卖 0 马上买卖 / 1 挂单；
         * 我的公司 0 经营 / 1 当老板；投资 0 股票 / 1 理财 / 2 押注 / 3 现金。productScroll 是“我的理财”的滚动位置。
         */
        int homeTab, tradeTab, investTab, companyTab, productScroll;
        /** 押注投入多少钱（便士）。 */
        long betStake = 1_000;
        /** 买卖列表的搜索词；收藏的商品（排最前面）；是否按住 Shift（数量 ×10）。由 SimpleCenter 每帧填。 */
        String search = "";

        java.util.Set<String> favorites = java.util.Set.of();
        boolean shift;
        /** 大额操作的二次确认：第一次点击记下按钮和时间，3 秒内再点才执行。 */
        String armed;

        long armedAt;
        /** 投资页：正在看详情的股票（null 表示看列表）；理财买入金额。 */
        String selectedStock;
        /** 买卖页：正在看详情的商品（null 表示看列表）；详情图显示 K 线还是走势线。 */
        String selectedCommodity;

        boolean candles;
        long productAmount = 1_000;
        /** 挂单：选中的商品、单价（0 = 按市场价自动填）、数量。 */
        String orderCommodity = "";

        long orderPrice;
        int orderQty = 64;
        CompanyType newCompanyType = CompanyType.RAW_MATERIALS;
    }

    /** 按钮背后的动作。 */
    interface Actions {
        void simple(SimpleActionPacket.Action action, String target, long amount, long price);

        default void simple(SimpleActionPacket.Action action, String target, long amount) {
            simple(action, target, amount, 0);
        }

        void company(CompanyManagementAction action, long amount);

        void createCompany(CompanyType type);

        void stock(boolean buy, String symbol, int quantity);

        void status(String message);

        void show(Page page);

        void setWantsAdvanced(boolean on);

        void dismissSummary();

        /** 收藏或取消收藏一种商品（存在本机）。 */
        default void toggleFavorite(String commodityId) {}

        default void leverRepay() {
            simple(SimpleActionPacket.Action.LEVER_REPAY, "", 0, 0);
        }

        default void withdrawCollateral() {
            simple(SimpleActionPacket.Action.COLLATERAL_WITHDRAW, "", 0, 0);
        }
    }

    static final int W = FinanceGui.PANEL_WIDTH, H = FinanceGui.PANEL_HEIGHT;
    static final int LEFT = 10, RIGHT = W - 10, WIDTH = RIGHT - LEFT;
    static final int TOP = FinanceGui.HEADER_H + 4, BOTTOM = H - 4;
    static final int PAD = 6;
    static final long COMPANY_COST = 1_000L;
    static final int TRADE_ROWS = 9, INVEST_ROWS = 8, ROW = 16;
    static final int CARD = FinanceGui.COL_ROW_EVEN, CARD_BORDER = 0xFFB8B0A0;
    static final int TEXT = FinanceGui.COL_TEXT, DIM = FinanceGui.COL_TEXT_DIM;

    private SimplePages() {}

    static void layout(UiBuilder ui, View v, State s, Actions a) {
        tabs(ui, s, a);
        switch (s.page) {
            case HOME -> SimpleHomePage.home(ui, v, s, a);
            case TRADE -> SimpleTradePage.tradePage(ui, v, s, a);
            case COMPANY -> SimpleCompanyPage.company(ui, v, s, a);
            case INVEST -> SimpleInvestPage.invest(ui, v, s, a);
        }
    }

    // ================================================================ 页签

    static void tabs(UiBuilder ui, State s, Actions a) {
        Page[] pages = Page.values();
        int gap = 4, w = (WIDTH - (pages.length - 1) * gap) / pages.length, x = LEFT;
        for (Page p : pages) {
            if (p == s.page) {
                ui.fill(x, FinanceGui.TAB_Y - 1, w, FinanceGui.TAB_H + 1, 0xFFECE6D5);
                ui.fill(x + 3, FinanceGui.TAB_Y + FinanceGui.TAB_H - 1, w - 6, 1, FinanceGui.COL_ACCENT);
            }
            int tw = ui.width(p.label);
            ui.text(x + Math.max(2, (w - tw) / 2), FinanceGui.TAB_Y + 2, w - 4, p.label, p == s.page ? TEXT : DIM);
            ui.hotspot(x, FinanceGui.TAB_Y - 1, w, FinanceGui.TAB_H + 1, () -> a.show(p));
            x += w + gap;
        }
    }

    /** 页内的一排小标签；返回内容开始的 y。 */
    static int subTabs(UiBuilder ui, int y, List<String> labels, int selected, java.util.function.IntConsumer select) {
        int x = LEFT + 2;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int w = ui.width(label);
            boolean on = i == selected;
            ui.text(x, y, w, label, on ? TEXT : DIM);
            if (on) ui.fill(x, y + 10, w, 1, FinanceGui.COL_ACCENT);
            int index = i;
            ui.hotspot(x - 3, y - 2, w + 6, 13, () -> select.accept(index));
            x += w + 14;
        }
        ui.fill(LEFT, y + 12, WIDTH, 1, 0xFFCFC8B4);
        return y + 16;
    }

    /** 一张机会卡占一行：左边说明（放不下截断、悬停看全文），右边一个按钮。 */
    static void cardRow(UiBuilder ui, State s, Actions a, SimpleOpportunityService.Card card, int y) {
        int textRight = RIGHT - 2;
        if (!card.button().isEmpty()) {
            Runnable run = card.action().isEmpty()
                    ? () -> goTo(s, a, card.goTo())
                    : () -> {
                        SimpleActionPacket.Action action = actionOf(card.action());
                        if (action != null) a.simple(action, card.target(), card.amount(), 0);
                    };
            // 收购、买下剩余股份、开银行、发债、抵押都是大额操作，要点两次
            boolean big = List.of("TAKEOVER", "SQUEEZE", "CHARTER_BANK", "ISSUE_BOND", "PLEDGE")
                    .contains(card.action());
            Btn b = confirm(s, a, "card:" + card.action() + ":" + card.target(), card.button(), run, null, big);
            textRight = ui.buttonRight(RIGHT - 2, y, b.label(), b.action(), b.blocked()) - 6;
        }
        ui.text(LEFT + PAD, y + 3, textRight - LEFT - PAD, "· " + card.text(), TEXT);
    }

    /** 机会卡里的操作名 → 动作；不认识的（版本不一致）返回 null。 */
    static SimpleActionPacket.Action actionOf(String name) {
        for (SimpleActionPacket.Action action : SimpleActionPacket.Action.values())
            if (action.name().equals(name)) return action;
        return null;
    }

    /** 跳到机会卡指的地方：TRADE、INVEST:小标签、COMPANY:小标签、STOCK:股票代码。 */
    static void goTo(State s, Actions a, String where) {
        if (where == null || where.isEmpty()) return;
        String[] parts = where.split(":", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (parts[0]) {
            case "TRADE" -> {
                s.tradeTab = 0;
                s.selectedCommodity = null;
                a.show(Page.TRADE);
            }
            case "INVEST" -> {
                s.investTab = parseTab(arg);
                s.selectedStock = null;
                a.show(Page.INVEST);
            }
            case "COMPANY" -> {
                s.companyTab = parseTab(arg);
                a.show(Page.COMPANY);
            }
            case "STOCK" -> {
                s.investTab = 0;
                s.selectedStock = arg;
                a.show(Page.INVEST);
            }
            default -> {}
        }
    }

    private static int parseTab(String text) {
        try {
            return Math.max(0, Math.min(3, Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- 大额确认、Shift ×10

    /** 超过 50 磅的操作要点两次。 */
    static final long BIG = 5_000;

    static final long CONFIRM_MS = 3_000;

    /**
     * 大额操作的按钮：第一次点只把按钮变成“确认”并在状态栏说明，3 秒内再点才执行；
     * 不大的、被挡住的按钮原样返回。“确认”和原来的字一样宽或更窄，列表里不会挤乱。
     */
    static Btn confirm(State s, Actions a, String key, String label, Runnable action, String blocked, boolean big) {
        if (blocked != null || !big) return new Btn(label, action, blocked);
        boolean armed = key.equals(s.armed) && System.currentTimeMillis() - s.armedAt < CONFIRM_MS;
        if (armed)
            return new Btn(
                    "确认",
                    () -> {
                        s.armed = null;
                        action.run();
                    },
                    null);
        return new Btn(
                label,
                () -> {
                    s.armed = key;
                    s.armedAt = System.currentTimeMillis();
                    a.status("「" + label + "」金额较大，3 秒内再点一次“确认”才会执行。");
                },
                null);
    }

    /** 按住 Shift 时数量 ×10。 */
    static int mult(State s) {
        return s.shift ? 10 : 1;
    }

    /** 还没解锁的玩法：一张卡说明做到什么就能解锁、解锁后能玩什么。 */
    static void lockedCard(UiBuilder ui, int y, String group) {
        SimpleUnlocks.Group g = SimpleUnlocks.Group.of(group);
        if (g == null) return;
        ui.card(LEFT, y, WIDTH, 50, CARD, CARD_BORDER);
        ui.text(LEFT + PAD, y + 5, WIDTH - 2 * PAD, "「" + g.label + "」还没解锁：" + g.howTo, TEXT);
        ui.wrapped(LEFT + PAD, y + 19, WIDTH - 2 * PAD, g.intro, DIM, 2);
    }

    /** “买过一次股票后解锁”这样的一句话。 */
    static String unlockHint(String group) {
        SimpleUnlocks.Group g = SimpleUnlocks.Group.of(group);
        return g == null ? "还没解锁" : g.howTo;
    }

    // ================================================================ 小工具

    /** 这个玩家现在能用的进阶玩法：单项名（margin、takeover……）或组名（bet、growth、boss）。服务端按进度和配置算好发来。 */
    static boolean extra(View v, String name) {
        if (v.status() == null) return false;
        List<String> extras = v.status().extras();
        return extras.contains("all") || extras.contains(name);
    }

    /** 这组玩法还没解锁、做到进度就能解锁（用来显示“怎么解锁”）。 */
    static boolean locked(View v, String group) {
        return v.status() != null && v.status().locked().contains(group);
    }

    static boolean ownsCompany(View v) {
        return v.company() != null && v.company().playerOwned();
    }

    /** 估值优先用服务端算的（含库存市值），没收到时用快照里的现金 + 库存。 */
    static long valuation(View v) {
        SimpleCenterService.Status st = v.status();
        CompanyRows.CompanyInfo info = v.company();
        if (st != null && st.companyValuation() > 0) return st.companyValuation();
        return info == null ? 0 : info.cash() + info.inventoryValue();
    }

    static long holding(View v, String symbol) {
        for (StockRows.StockHoldingRow h : v.holdings()) if (h.symbol().equals(symbol)) return h.quantity();
        return 0;
    }

    static CompanyType typeOf(String displayName) {
        for (CompanyType t : CompanyType.values())
            if (t.getDisplayName().equals(displayName) || t.name().equals(displayName)) return t;
        return null;
    }

    /** 交易记录里的对象名可能是商品 ID，能翻成中文名就翻。 */
    static String displayObject(String name) {
        Commodity c = CommodityRegistry.getCommodity(name);
        return c == null ? name : c.getDisplayName();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
