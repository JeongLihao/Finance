package finance.client;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleBetService;
import finance.simple.SimpleBossService;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleInvestService;
import finance.simple.SimpleOpportunityService;
import finance.simple.SimpleUnlocks;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 简单模式四页的排版：只根据数据排出元素，不画、不发包（动作交给 {@link Actions}），测试里可以直接跑。
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
            List<FinanceMenu.MarketRow> market,
            List<FinanceMenu.TransactionRow> transactions,
            FinanceMenu.CompanyInfo company,
            List<FinanceMenu.StockRow> stocks,
            List<FinanceMenu.StockHoldingRow> holdings,
            SimpleCenterService.Status status,
            boolean playerChoosesAdvanced,
            boolean wantsAdvanced,
            List<String> pendingSummary,
            List<FinanceMenu.OrderRow> orders,
            Map<String, Integer> storedGoods,
            finance.network.FuturesResponsePacket futures,
            List<FinanceMenu.ConditionalStockOrderRow> autoOrders,
            List<FinanceMenu.CompanyProposalRow> proposals) {}

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
            case HOME -> home(ui, v, s, a);
            case TRADE -> tradePage(ui, v, s, a);
            case COMPANY -> company(ui, v, s, a);
            case INVEST -> invest(ui, v, s, a);
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

    // ================================================================ 首页

    static void home(UiBuilder ui, View v, State s, Actions a) {
        if (!v.pendingSummary().isEmpty()) {
            summary(ui, v, a);
            return;
        }
        int y = subTabs(ui, TOP, List.of("今天", "经济天气", "全部记录", "排行榜"), s.homeTab, i -> s.homeTab = i);
        switch (s.homeTab) {
            case 1 -> weather(ui, v, y);
            case 2 -> records(ui, v, s, y);
            case 3 -> ranks(ui, v, y);
            default -> today(ui, v, s, a, y);
        }
    }

    /** 全服身家排行前 5 名。 */
    static void ranks(UiBuilder ui, View v, int y) {
        List<String> lines = v.status() == null ? List.of() : v.status().ranks();
        y += 2;
        ui.text(LEFT + PAD, y, WIDTH - 2 * PAD, "身家 = 钱包 + 存款 + 理财 + 股票 + 押注现值", DIM);
        y += 16;
        if (lines.isEmpty()) ui.text(LEFT + PAD, y, WIDTH, v.status() == null ? "正在读取……" : "还没有人上榜。", DIM);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean me = line.endsWith("（你）");
            ui.text(
                    LEFT + PAD,
                    y,
                    WIDTH - 2 * PAD,
                    line,
                    i == 0 ? FinanceGui.COL_WARN : me ? FinanceGui.COL_GOOD : TEXT);
            y += ROW;
        }
        ui.wrapped(
                LEFT + PAD,
                Math.max(y + 4, BOTTOM - 22),
                WIDTH - 2 * PAD,
                "每分钟更新一次；每 7 个游戏日在聊天栏播报一次周榜和本周涨得最多的人。",
                DIM,
                2);
    }

    /** 解锁新玩法时弹出的介绍以这个开头（见 SimpleClientState.accept）。 */
    static final String UNLOCK_PREFIX = "解锁了「";

    /** “上次打开以来”的小结（或刚解锁的玩法介绍）：挡住首页内容，点“知道了”关掉。每条最多换两行。 */
    static void summary(UiBuilder ui, View v, Actions a) {
        int x = LEFT + 30, w = WIDTH - 60, y = TOP + 12;
        List<String> lines = v.pendingSummary();
        boolean unlock = lines.get(0).startsWith(UNLOCK_PREFIX);
        int textW = w - 2 * PAD, body = 0;
        for (String line : lines)
            body += Math.min(2, Math.max(1, ui.wrap(line, textW).size())) * UiBuilder.LINE + 3;
        int h = Math.min(BOTTOM - y, 26 + body + 20);
        ui.card(x, y, w, h, CARD, CARD_BORDER);
        ui.text(x + PAD, y + 6, textW, unlock ? "解锁了新玩法" : "上次打开以来", unlock ? FinanceGui.COL_GOOD : TEXT);
        int ly = y + 22;
        for (int i = 0; i < lines.size(); i++) {
            int room = (y + h - 20 - ly) / UiBuilder.LINE;
            if (room <= 0) break;
            ly += ui.wrapped(x + PAD, ly, textW, lines.get(i), i == 0 || unlock ? TEXT : DIM, Math.min(2, room)) + 3;
        }
        ui.buttonRight(x + w - PAD, y + h - 17, "知道了", a::dismissSummary, null);
    }

    static void weather(UiBuilder ui, View v, int y) {
        SimpleCenterService.Status st = v.status();
        List<String> lines = st == null ? List.of("正在读取……") : st.weather();
        y += 2;
        for (String line : lines) {
            int room = (BOTTOM - y) / UiBuilder.LINE;
            if (room <= 0) break;
            y += ui.wrapped(LEFT + PAD, y, WIDTH - 2 * PAD, line, TEXT, Math.min(3, room)) + 5;
        }
        if (y + 2 * UiBuilder.LINE <= BOTTOM) {
            ui.wrapped(
                    LEFT + PAD,
                    Math.max(y, BOTTOM - 22),
                    WIDTH - 2 * PAD,
                    "利率和物价由央行每天自动调整。利率高时适合存钱，利率低时适合借钱扩大公司。",
                    DIM,
                    2);
        }
    }

    static final int RECORD_ROWS = 15;

    static void records(UiBuilder ui, View v, State s, int y) {
        List<FinanceMenu.TransactionRow> rows = v.transactions();
        s.recordScroll = clamp(s.recordScroll, 0, Math.max(0, rows.size() - RECORD_ROWS));
        if (rows.isEmpty()) ui.text(LEFT + PAD, y + 2, 200, "还没有交易。", DIM);
        int amountW = 96, typeW = 70;
        for (int i = s.recordScroll; i < Math.min(rows.size(), s.recordScroll + RECORD_ROWS); i++) {
            FinanceMenu.TransactionRow r = rows.get(i);
            ui.text(LEFT + 2, y, typeW, FinanceFormat.displayTransactionType(r.type()), TEXT);
            String what = (r.objectName().isEmpty() ? "" : displayObject(r.objectName()))
                    + (r.quantity() > 1 ? " ×" + r.quantity() : "");
            ui.text(LEFT + 2 + typeW + 6, y, WIDTH - typeW - amountW - 16, what, DIM);
            ui.textRight(RIGHT - 2, y, amountW, MoneyFormat.pounds(r.amount()), TEXT);
            y += UiBuilder.LINE;
        }
        if (rows.size() > RECORD_ROWS) {
            ui.textRight(
                    RIGHT - 2,
                    BOTTOM - 9,
                    160,
                    "滚轮翻页 " + (s.recordScroll + 1) + "-" + Math.min(rows.size(), s.recordScroll + RECORD_ROWS) + " / "
                            + rows.size(),
                    DIM);
        }
    }

    static void today(UiBuilder ui, View v, State s, Actions a, int top) {
        SimpleCenterService.Status st = v.status();
        int y = top;
        ui.card(LEFT, y, WIDTH, 42, CARD, CARD_BORDER);
        int rightX = 236;
        ui.text(LEFT + PAD, y + 4, 60, "钱包", DIM);
        ui.bigText(LEFT + PAD, y + 16, rightX - LEFT - PAD - 8, MoneyFormat.pounds(v.balance()), TEXT, 2.0F);
        int colW = RIGHT - PAD - rightX;
        List<Long> worth = st == null ? List.of() : st.worth();
        if (worth.size() >= 2) {
            // 身家走势：最近几天每天一个点，最后一个是现在
            long change = worth.get(worth.size() - 1) - worth.get(0);
            int tw = ui.text(
                    rightX,
                    y + 4,
                    colW / 2,
                    "今天 " + MoneyFormat.signed(v.todayProfit()),
                    FinanceGui.changeColor(v.todayProfit()));
            ui.textRight(
                    RIGHT - PAD,
                    y + 4,
                    colW - tw - 6,
                    (worth.size() - 1) + " 天 " + MoneyFormat.signed(change),
                    FinanceGui.changeColor(change));
            ui.chart(rightX, y + 15, colW, 23, worth, FinanceGui.changeColor(change));
        } else {
            ui.text(
                    rightX,
                    y + 5,
                    colW,
                    "今天 " + MoneyFormat.signed(v.todayProfit()),
                    FinanceGui.changeColor(v.todayProfit()));
            ui.text(
                    rightX,
                    y + 17,
                    colW,
                    "银行 " + MoneyFormat.compact(st == null ? 0 : st.bankBalance()) + "  股票 "
                            + MoneyFormat.compact(v.stockValue()),
                    DIM);
            if (ownsCompany(v)) ui.text(rightX, y + 29, colW, "公司估值 " + MoneyFormat.compact(valuation(v)), DIM);
        }

        y += 46;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        Goal goal = goal(v);
        int buttonX = ui.buttonRight(RIGHT - PAD, y + 13, "去看看", () -> a.show(goal.page()), null);
        ui.text(LEFT + PAD, y + 4, 100, "下一个目标", DIM);
        ui.text(LEFT + PAD, y + 16, buttonX - LEFT - PAD - 8, goal.text(), TEXT);
        ui.progress(LEFT + PAD, y + 28, buttonX - LEFT - PAD - 8, goal.progress());

        y += 42;
        ui.text(LEFT + 2, y, 100, "今日机会", DIM);
        y += 12;
        if (st == null) {
            ui.text(LEFT + PAD, y + 2, WIDTH - PAD, "正在读取……", DIM);
            return;
        }
        for (SimpleOpportunityService.Card card : st.cards()) {
            if (y + ROW > BOTTOM) break;
            cardRow(ui, s, a, card, y);
            y += ROW;
        }
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

    record Goal(String text, double progress, Page page) {}

    /** 首页“下一个目标”：按 开公司 → 买股票 → 上市 → 升满工厂 的顺序找第一个还没做到的。 */
    static Goal goal(View v) {
        long balance = v.balance();
        SimpleCenterService.Status st = v.status();
        if (!ownsCompany(v)) {
            if (balance < COMPANY_COST) {
                return new Goal(
                        "攒到 10 磅就能开公司（还差 " + MoneyFormat.pounds(COMPANY_COST - balance) + "）",
                        balance / (double) COMPANY_COST,
                        Page.TRADE);
            }
            return new Goal("钱够了，去开一家公司吧！", 1, Page.COMPANY);
        }
        long unlock = st == null ? 3_000 : st.stockUnlockBalance();
        if (v.holdings().isEmpty() && balance < unlock) {
            return new Goal(
                    "攒到 " + MoneyFormat.pounds(unlock) + " 就能买股票拿分红（还差 " + MoneyFormat.pounds(unlock - balance) + "）",
                    balance / (double) Math.max(1, unlock),
                    Page.INVEST);
        }
        if (v.holdings().isEmpty() && locked(v, "bet")) {
            return new Goal("买一次股票，解锁「押注」：押涨跌赚翻倍", 0.5, Page.INVEST);
        }
        if (!v.company().isPublic()) {
            long valuation = valuation(v);
            long min = st == null ? 20_000 : st.ipoMinValuation();
            if (valuation < min) {
                return new Goal(
                        "公司估值到 " + MoneyFormat.pounds(min) + " 就能上市（现在 " + MoneyFormat.pounds(valuation) + "）",
                        valuation / (double) Math.max(1, min),
                        Page.COMPANY);
            }
            return new Goal(locked(v, "boss") ? "公司可以上市了！上市后解锁「大老板」" : "公司可以上市了！", 1, Page.COMPANY);
        }
        int level = st == null ? 0 : st.factoryLevel();
        if (level < SimpleCenterService.MAX_FACTORY_LEVEL) {
            return new Goal(
                    "把工厂升到满级（" + level + " / " + SimpleCenterService.MAX_FACTORY_LEVEL + "）",
                    level / (double) SimpleCenterService.MAX_FACTORY_LEVEL,
                    Page.COMPANY);
        }
        return new Goal(
                extra(v, "boss") ? "你已经是大亨了！“当老板”里会提醒你该收购谁。" : "你已经是大亨了！多买几家公司的股票吧。",
                1,
                extra(v, "boss") ? Page.COMPANY : Page.INVEST);
    }

    // ================================================================ 买卖

    /**
     * 背包里有的排前面（按能卖的钱从多到少），其余按名字。没有对应物品的商品不列；
     * 市场里没货、背包里也没有的不列——有人卖了才会出现。
     */
    static List<FinanceMenu.MarketRow> tradeRows(View v) {
        Map<String, Integer> held = v.backpack();
        List<FinanceMenu.MarketRow> rows = new ArrayList<>();
        for (FinanceMenu.MarketRow row : v.market()) {
            Commodity c = CommodityRegistry.getCommodity(row.commodityId());
            if (row.marketStock() <= 0 && held.getOrDefault(row.commodityId(), 0) <= 0) continue;
            if (c == null || c.getItemId() != null && !c.getItemId().isEmpty()) rows.add(row);
        }
        rows.sort(Comparator.<FinanceMenu.MarketRow>comparingLong(
                        r -> -(long) held.getOrDefault(r.commodityId(), 0) * r.bidPrice())
                .thenComparing(r -> FinanceFormat.commodityDisplayName(r.commodityId())));
        return rows;
    }

    /** 买卖列表：按搜索词过滤（名字或 ID 包含就算），收藏的排最前面。 */
    static List<FinanceMenu.MarketRow> tradeRows(View v, State s) {
        String q = s.search == null ? "" : s.search.trim().toLowerCase(java.util.Locale.ROOT);
        List<FinanceMenu.MarketRow> out = new ArrayList<>();
        for (FinanceMenu.MarketRow row : tradeRows(v)) {
            if (q.isEmpty()
                    || row.commodityId().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || FinanceFormat.commodityDisplayName(row.commodityId())
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains(q)) out.add(row);
        }
        out.sort(Comparator.comparing(r -> !s.favorites.contains(r.commodityId())));
        return out;
    }

    static void tradePage(UiBuilder ui, View v, State s, Actions a) {
        List<String> labels = List.of("马上买卖", "挂单");
        if (s.tradeTab >= labels.size()) s.tradeTab = 0;
        int y = subTabs(ui, TOP, labels, s.tradeTab, i -> {
            s.tradeTab = i;
            s.selectedCommodity = null;
        });
        switch (s.tradeTab) {
            case 1 -> orders(ui, v, s, a, y);
            default -> {
                FinanceMenu.MarketRow selected = s.selectedCommodity == null
                        ? null
                        : v.market().stream()
                                .filter(r -> r.commodityId().equals(s.selectedCommodity))
                                .findFirst()
                                .orElse(null);
                if (selected != null) commodityDetail(ui, v, s, a, selected, y);
                else trade(ui, v, s, a, y);
            }
        }
    }

    // ---------------------------------------------------------------- 挂单

    static void orders(UiBuilder ui, View v, State s, Actions a, int y) {
        List<FinanceMenu.MarketRow> rows = tradeRows(v);
        if (rows.isEmpty()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "市场里还没有货，背包里也没有能卖的东西。", DIM);
            return;
        }
        FinanceMenu.MarketRow row = rows.stream()
                .filter(r -> r.commodityId().equals(s.orderCommodity))
                .findFirst()
                .orElse(rows.get(0));
        if (!row.commodityId().equals(s.orderCommodity)) {
            s.orderCommodity = row.commodityId();
            s.orderPrice = 0;
        }
        String id = row.commodityId();
        String name = FinanceFormat.commodityDisplayName(id);
        long price = s.orderPrice > 0 ? s.orderPrice : Math.max(1, row.midPrice());
        long step = Math.max(1, price / 20);
        int have = v.backpack().getOrDefault(id, 0);
        int index = rows.indexOf(row);

        // 第一行：选商品 + 市场价
        int x = LEFT + 2;
        ui.text(x, y + 3, 30, "商品", DIM);
        x += 32;
        x += ui.button(
                        x,
                        y,
                        "<",
                        () -> {
                            s.orderCommodity = rows.get((index - 1 + rows.size()) % rows.size())
                                    .commodityId();
                            s.orderPrice = 0;
                        },
                        null)
                + 4;
        ui.icon(x, y + 2, id);
        x += ui.text(x + 13, y + 3, 70, name, TEXT) + 17;
        x += ui.button(
                        x,
                        y,
                        ">",
                        () -> {
                            s.orderCommodity =
                                    rows.get((index + 1) % rows.size()).commodityId();
                            s.orderPrice = 0;
                        },
                        null)
                + 8;
        ui.textRight(
                RIGHT - 2,
                y + 3,
                RIGHT - 2 - x,
                "市场收 " + MoneyFormat.compact(row.bidPrice()) + "  卖 " + MoneyFormat.compact(row.askPrice()),
                DIM);

        // 第二行：单价、数量
        y += 18;
        x = LEFT + 2;
        ui.text(x, y + 3, 30, "单价", DIM);
        x += 32;
        long finalPrice = price;
        x += ui.button(
                        x,
                        y,
                        "-",
                        () -> s.orderPrice = Math.max(1, finalPrice - step),
                        finalPrice <= 1 ? "最低 1 便士" : null)
                + 4;
        x += ui.text(x, y + 3, 60, MoneyFormat.compact(price), TEXT) + 4;
        x = Math.max(x, LEFT + 34 + 18 + 64);
        x += ui.button(x, y, "+", () -> s.orderPrice = finalPrice + step, null) + 14;
        ui.text(x, y + 3, 60, "数量 " + s.orderQty, TEXT);
        int qx = x + ui.width("数量 9999") + 6;
        ui.buttonFlow(
                qx,
                y,
                RIGHT - qx,
                2,
                List.of(
                        new Btn("1", () -> s.orderQty = 1, null),
                        new Btn("16", () -> s.orderQty = 16, null),
                        new Btn("64", () -> s.orderQty = 64, null),
                        new Btn("背包全部", () -> s.orderQty = Math.max(1, have), have > 0 ? null : "背包里没有" + name)));

        // 第三行：挂卖单 / 挂买单
        y += 18;
        int qty = s.orderQty;
        long total = finalPrice * qty;
        x = LEFT + 2;
        x += ui.button(
                        x,
                        y,
                        "挂卖单",
                        () -> a.simple(SimpleActionPacket.Action.PLACE_SELL_ORDER, id, qty, finalPrice),
                        have >= qty ? null : "背包里只有 " + have + " 个" + name)
                + 4;
        x += ui.button(
                        x,
                        y,
                        "挂买单",
                        () -> a.simple(SimpleActionPacket.Action.PLACE_BUY_ORDER, id, qty, finalPrice),
                        v.balance() >= total ? null : "钱不够，要冻结 " + MoneyFormat.pounds(total))
                + 8;
        ui.text(
                x,
                y + 3,
                RIGHT - x,
                qty + " 个，每个 " + MoneyFormat.compact(price) + "，共 " + MoneyFormat.compact(total) + "（买单先冻结这笔钱）",
                DIM);
        y += 17;
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "卖单：市场收购价涨到你定的价就自动卖出。买单：市场出售价跌到你定的价就自动买入，买到的东西放进背包。", DIM, 2) + 4;

        // 我的挂单
        List<FinanceMenu.OrderRow> mine =
                v.orders().stream().filter(FinanceMenu.OrderRow::ownedByPlayer).toList();
        ui.text(LEFT + 2, y, 120, "我的挂单（" + mine.size() + "）", DIM);
        y += 12;
        if (mine.isEmpty()) {
            ui.text(LEFT + PAD, y, WIDTH, "还没有挂单。", DIM);
            y += 13;
        }
        for (int i = 0; i < Math.min(3, mine.size()); i++) {
            FinanceMenu.OrderRow o = mine.get(i);
            int bx = ui.buttonRight(
                    RIGHT - 2,
                    y - 2,
                    "撤单",
                    () -> a.simple(
                            SimpleActionPacket.Action.CANCEL_ORDER, o.orderId().toString(), 0, 0),
                    null);
            ui.text(
                    LEFT + PAD,
                    y,
                    bx - LEFT - PAD - 6,
                    ("SELL".equals(o.type()) ? "卖 " : "买 ") + o.quantity() + " 个"
                            + FinanceFormat.commodityDisplayName(o.commodityId()) + "，每个 "
                            + MoneyFormat.pounds(o.price()),
                    TEXT);
            y += 14;
        }
        // 暂存在市场的货（撤掉的卖单、离线时成交的买单）
        List<Map.Entry<String, Integer>> stored = v.storedGoods().entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .limit(2)
                .toList();
        for (Map.Entry<String, Integer> e : stored) {
            if (y + 13 > BOTTOM) break;
            String goods = e.getKey();
            int bx = ui.buttonRight(
                    RIGHT - 2, y - 2, "取回背包", () -> a.simple(SimpleActionPacket.Action.TAKE_GOODS, goods, 0, 0), null);
            ui.text(
                    LEFT + PAD,
                    y,
                    bx - LEFT - PAD - 6,
                    "暂存在市场：" + FinanceFormat.commodityDisplayName(goods) + " " + e.getValue() + " 个",
                    DIM);
            y += 14;
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

    // ---------------------------------------------------------------- 押注（股票详情、商品详情共用）

    static final long[] BET_AMOUNTS = {1_000, 5_000, 20_000};

    /** 押注一行：投入金额 + 三个金额小按钮 + 看涨 / 看跌。返回占用高度。 */
    static int betRow(UiBuilder ui, View v, State s, Actions a, int y, String target, String blocked) {
        String poor = v.balance() < s.betStake ? "钱包不到 " + MoneyFormat.pounds(s.betStake) : null;
        String block = blocked != null ? blocked : poor;
        long stake = s.betStake;
        boolean big = stake >= BIG;
        int rx = ui.buttonsRight(
                RIGHT - 2,
                y,
                2,
                List.of(
                        confirm(
                                s,
                                a,
                                "bet-up:" + target,
                                "看涨",
                                () -> a.simple(SimpleActionPacket.Action.BET_UP, target, stake, 0),
                                block,
                                big),
                        confirm(
                                s,
                                a,
                                "bet-down:" + target,
                                "看跌",
                                () -> a.simple(SimpleActionPacket.Action.BET_DOWN, target, stake, 0),
                                block,
                                big)));
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 30, "押注", DIM) + 6;
        lx += ui.text(lx, y + 3, 70, "投入 " + MoneyFormat.compact(s.betStake), TEXT) + 6;
        List<Btn> chips = new ArrayList<>();
        for (long amount : BET_AMOUNTS)
            chips.add(new Btn(MoneyFormat.compact(amount), () -> s.betStake = amount, null));
        int chipsW = -ui.buttonsRightStart(0, 2, chips.stream().map(Btn::label).toList());
        if (lx + chipsW <= rx - 6) ui.buttonsRight(lx + chipsW, y, 2, chips);
        return UiBuilder.BUTTON_H + 4;
    }

    /** 股票押注的说明：投入多少相当于多少股、涨跌 10% 赚亏多少、跌（涨）到哪会被强制结束。 */
    static String stockBetText(View v, long price, long stake) {
        int m = v.status() == null || v.status().margin().liquidationPct() <= 0
                ? 130
                : v.status().margin().liquidationPct();
        SimpleBetService.Preview up = SimpleBetService.stockPreview(stake, price, true, m);
        SimpleBetService.Preview down = SimpleBetService.stockPreview(stake, price, false, m);
        if (up.quantity() <= 0) return "押注至少要投入 " + MoneyFormat.pounds((price + 1) / 2) + "。";
        return "投入 " + MoneyFormat.pounds(stake) + " ≈ 押 " + up.quantity() + " 股的涨跌（一半是借的）：涨 10% 看涨赚 "
                + MoneyFormat.pounds(up.swing10()) + "，看跌亏同样多。看涨跌超 " + up.stopPct() + "%、看跌涨超 "
                + down.stopPct() + "% 会被强制结束，押金自动退回。";
    }

    /** 商品押注要用的合约：这种商品最近到期、正在交易、有报价的那份；没有返回 null。 */
    static finance.network.FuturesResponsePacket.ContractRow betContract(View v, String commodity) {
        finance.network.FuturesResponsePacket data = v.futures();
        if (data == null) return null;
        finance.network.FuturesResponsePacket.ContractRow best = null;
        for (var c : data.contracts()) {
            if (!c.commodity().equals(commodity) || quote(v, c) == null) continue;
            if (c.status() != finance.futures.FuturesContractStatus.TRADING
                    && c.status() != finance.futures.FuturesContractStatus.LAST_TRADING_DAY) continue;
            if (best == null || c.maturityDay() < best.maturityDay()) best = c;
        }
        return best;
    }

    static finance.network.FuturesResponsePacket.MmQuoteRow quote(
            View v, finance.network.FuturesResponsePacket.ContractRow c) {
        return v.futures().mmQuotes().stream()
                .filter(q -> q.contractId().equals(c.id()))
                .findFirst()
                .orElse(null);
    }

    static void trade(UiBuilder ui, View v, State s, Actions a, int top) {
        // 列从右往左按按钮实际宽度排，左边剩下的给商品名；按住 Shift 时数量 ×10
        int m = mult(s);
        String sellLabel = m == 1 ? "卖1组" : "卖10组", buyOne = m == 1 ? "买1" : "买10", buyStack = m == 1 ? "买1组" : "买10组";
        int gap = 2;
        int buyStart = ui.buttonsRightStart(RIGHT, gap, List.of(buyOne, buyStack));
        int askX = buyStart - 6 - 50;
        int sellStart = ui.buttonsRightStart(askX - 6, gap, List.of(sellLabel, "全卖"));
        int bidX = sellStart - 6 - 50;
        int haveX = bidX - 4 - 34;
        int nameX = LEFT + 26;

        int y = top;
        ui.text(nameX, y, haveX - nameX - 4, "东西（点名字看走势，☆ 收藏）", DIM);
        ui.text(haveX, y, 34, "背包", DIM);
        ui.text(bidX, y, 50, "你卖得", DIM);
        ui.text(askX, y, 50, "你买付", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.MarketRow> rows = tradeRows(v, s);
        s.tradeScroll = clamp(s.tradeScroll, 0, Math.max(0, rows.size() - TRADE_ROWS));
        y += 15;
        if (rows.isEmpty()) {
            ui.text(
                    nameX,
                    y + 2,
                    WIDTH - nameX,
                    s.search.isBlank() ? "市场里还没有货。去挖点东西卖给市场吧。" : "没有找到“" + s.search.trim() + "”。",
                    DIM);
        }
        for (int i = s.tradeScroll; i < Math.min(rows.size(), s.tradeScroll + TRADE_ROWS); i++) {
            FinanceMenu.MarketRow row = rows.get(i);
            String id = row.commodityId();
            String name = FinanceFormat.commodityDisplayName(id);
            int have = v.backpack().getOrDefault(id, 0);
            if ((i - s.tradeScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            boolean fav = s.favorites.contains(id);
            int sw = ui.text(LEFT + 2, y + 2, 10, fav ? "★" : "☆", fav ? FinanceGui.COL_WARN : DIM);
            ui.hotspot(LEFT, y - 1, Math.max(sw, 8) + 3, ROW - 1, () -> a.toggleFavorite(id));
            ui.icon(LEFT + 12, y + 1, id);
            int nw = ui.text(nameX, y + 2, haveX - nameX - 4, name, FinanceGui.COL_ACCENT);
            ui.hotspot(nameX, y - 1, Math.max(nw, 20), ROW - 1, () -> s.selectedCommodity = id);
            ui.text(haveX, y + 2, 34, have > 0 ? Integer.toString(have) : "-", have > 0 ? TEXT : DIM);
            String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            int color = FinanceGui.changeColor(row.dayChange());
            ui.text(bidX, y + 2, 50, MoneyFormat.compact(row.bidPrice()) + arrow, color);
            String noItem = have > 0 ? null : "背包里没有" + name;
            int sellQty = Math.min(64 * m, have);
            ui.buttonsRight(
                    askX - 6,
                    y,
                    gap,
                    List.of(
                            new Btn(
                                    sellLabel,
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, sellQty),
                                    noItem),
                            confirm(
                                    s,
                                    a,
                                    "sellall:" + id,
                                    "全卖",
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have),
                                    noItem,
                                    (long) have * row.bidPrice() >= BIG)));
            ui.text(askX, y + 2, 50, MoneyFormat.compact(row.askPrice()) + arrow, color);
            String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
            ui.buttonsRight(
                    RIGHT,
                    y,
                    gap,
                    List.of(
                            new Btn(buyOne, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, m), poor),
                            confirm(
                                    s,
                                    a,
                                    "buystack:" + id,
                                    buyStack,
                                    () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64 * m),
                                    poor,
                                    64L * m * row.askPrice() >= BIG)));
            y += ROW;
        }
        // 最下面一行：搜索框、翻页、Shift 提示
        int footY = BOTTOM - 14;
        int lx = LEFT + 2 + ui.text(LEFT + 2, footY + 4, 30, "搜索", DIM) + 4;
        ui.input("search", lx, footY, 100);
        int pagerX = RIGHT;
        if (rows.size() > TRADE_ROWS) {
            pagerX = ui.textRight(
                            RIGHT,
                            footY + 4,
                            120,
                            "滚轮翻页 " + (s.tradeScroll + 1) + "-" + Math.min(rows.size(), s.tradeScroll + TRADE_ROWS)
                                    + " / " + rows.size(),
                            DIM)
                    - 8;
        }
        ui.text(lx + 108, footY + 4, pagerX - lx - 108, m == 1 ? "按住 Shift：数量 ×10" : "Shift：数量 ×10", DIM);
    }

    // ================================================================ 我的公司

    static void company(UiBuilder ui, View v, State s, Actions a) {
        FinanceMenu.CompanyInfo info = v.company();
        if (info == null) {
            newCompany(ui, v, s, a);
            return;
        }
        if (!info.playerOwned()) {
            ui.text(LEFT + 4, TOP + 6, WIDTH - 4, "你是「" + info.name() + "」的成员。", TEXT);
            ui.text(LEFT + 4, TOP + 20, WIDTH - 4, "公司的生产和成员在公司办公桌方块里管理。", DIM);
            return;
        }
        SimpleCenterService.Status st = v.status();
        CompanyType type = typeOf(info.type());
        List<String> labels = new ArrayList<>(List.of("经营"));
        if (bossOn(v)) labels.add("当老板");
        if (s.companyTab >= labels.size()) s.companyTab = 0;
        int top = subTabs(ui, TOP, labels, s.companyTab, i -> s.companyTab = i);
        int tabsRight = LEFT + 2 + 12;
        for (String label : labels) tabsRight += ui.width(label) + 14;
        ui.textRight(
                RIGHT - 2,
                TOP,
                RIGHT - 2 - tabsRight,
                info.name() + " · " + info.type() + (info.isPublic() ? " · 已上市" : ""),
                DIM);
        String tab = labels.get(s.companyTab);
        if (tab.equals("当老板")) {
            bossTab(ui, v, s, a, top);
            return;
        }

        // 今天
        int y = top;
        ui.card(LEFT, y, WIDTH, 52, CARD, CARD_BORDER);
        long transfer = Math.min(info.cash(), 10_000L);
        List<Btn> money = List.of(
                new Btn(
                        "转 " + MoneyFormat.compact(transfer) + " 到钱包",
                        () -> a.company(CompanyManagementAction.WITHDRAW, transfer),
                        transfer > 0 ? null : "公司账上没钱"),
                new Btn(
                        "从钱包投 10 磅",
                        () -> a.company(CompanyManagementAction.INVEST, 1_000L),
                        v.balance() >= 1_000 ? null : "钱包不到 10 磅"));
        int bx = Math.min(
                ui.buttonRight(
                        RIGHT - PAD,
                        y + 14,
                        money.get(0).label(),
                        money.get(0).action(),
                        money.get(0).blocked()),
                ui.buttonRight(
                        RIGHT - PAD,
                        y + 31,
                        money.get(1).label(),
                        money.get(1).action(),
                        money.get(1).blocked()));
        int textW = bx - LEFT - PAD - 8;
        int titleW = ui.text(LEFT + PAD, y + 4, 40, "今天", DIM);
        if (type != null)
            ui.text(LEFT + PAD + titleW + 8, y + 4, textW - titleW - 8, FinanceFormat.getTypeDescription(type), DIM);
        ui.text(LEFT + PAD, y + 16, textW, "公司账上 " + MoneyFormat.pounds(info.cash()), TEXT);
        ui.text(LEFT + PAD, y + 27, textW, "库存货值 " + MoneyFormat.pounds(info.inventoryValue()), TEXT);
        if (info.bankruptcyRisk()) {
            ui.text(LEFT + PAD, y + 38, textW, "快撑不住了：钱不够付开销，投点钱进去", FinanceGui.COL_BAD);
        } else {
            ui.text(
                    LEFT + PAD,
                    y + 38,
                    textW,
                    "上次结算利润 " + MoneyFormat.signed(info.reportNetProfit()),
                    FinanceGui.changeColor(info.reportNetProfit()));
        }

        // 工厂
        y += 56;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        int level =
                st == null ? info.productionLevel() + info.storageLevel() + info.managementLevel() : st.factoryLevel();
        boolean full = level >= SimpleCenterService.MAX_FACTORY_LEVEL;
        long cost = st == null ? 0 : st.nextUpgradeCost();
        String block = full ? "已经满级了" : v.balance() < cost ? "还差 " + MoneyFormat.pounds(cost - v.balance()) : null;
        bx = ui.buttonRight(
                RIGHT - PAD,
                y + 12,
                full ? "已满级" : "升级（" + MoneyFormat.compact(cost) + "）",
                () -> a.simple(SimpleActionPacket.Action.UPGRADE_FACTORY, "", 0),
                block);
        textW = bx - LEFT - PAD - 8;
        ui.text(LEFT + PAD, y + 4, textW, "工厂等级 " + level + " / " + SimpleCenterService.MAX_FACTORY_LEVEL, TEXT);
        ui.progress(LEFT + PAD, y + 16, textW, level / (double) SimpleCenterService.MAX_FACTORY_LEVEL);
        ui.text(LEFT + PAD, y + 25, textW, "升级：每天产得更多、开销更少。", DIM);

        // 上市
        y += 42;
        ui.card(LEFT, y, WIDTH, 48, CARD, CARD_BORDER);
        if (info.isPublic()) {
            ui.text(LEFT + PAD, y + 4, WIDTH - 2 * PAD, "公司已上市。", FinanceGui.COL_GOOD);
            ui.wrapped(LEFT + PAD, y + 17, WIDTH - 2 * PAD, "别人买你的股票时公司更值钱；你是大股东，每周也拿分红。", DIM, 2);
        } else {
            long valuation = valuation(v);
            long min = st == null ? 20_000 : st.ipoMinValuation();
            bx = ui.buttonRight(
                    RIGHT - PAD,
                    y + 14,
                    "让公司上市",
                    () -> a.simple(SimpleActionPacket.Action.QUICK_IPO, "", 0),
                    valuation >= min ? null : "估值还差 " + MoneyFormat.pounds(min - valuation));
            textW = bx - LEFT - PAD - 8;
            ui.text(
                    LEFT + PAD,
                    y + 4,
                    textW,
                    "上市：估值 " + MoneyFormat.pounds(valuation) + " / " + MoneyFormat.pounds(min),
                    TEXT);
            ui.progress(LEFT + PAD, y + 16, textW, valuation / (double) Math.max(1, min));
            ui.wrapped(LEFT + PAD, y + 25, textW, "把公司 40% 的股份卖给大家，换一大笔钱进公司账上。要付上市费，发行价按估值自动定。", DIM, 2);
        }
        loanAndDividendRow(ui, v, a, y + 52);
    }

    /** 经营页最下面一行：公司借钱；上市后还能直接调分红比例。 */
    static void loanAndDividendRow(UiBuilder ui, View v, Actions a, int y) {
        int x = LEFT
                + 2
                + ui.button(
                        LEFT + 2,
                        y,
                        "公司借 50 磅（30 天还清）",
                        () -> a.simple(SimpleActionPacket.Action.COMPANY_LOAN, "", 5_000, 0),
                        null)
                + 8;
        SimpleBossService.BossInfo b = boss(v);
        if (!b.publicCompany()) return;
        int pct = b.dividendPct();
        int bx = ui.buttonsRight(
                RIGHT - 2,
                y,
                2,
                List.of(
                        new Btn(
                                "-",
                                () -> a.simple(SimpleActionPacket.Action.SET_DIVIDEND, "", Math.max(0, pct - 10), 0),
                                pct <= 0 ? "已经是 0%" : null),
                        new Btn(
                                "+",
                                () -> a.simple(SimpleActionPacket.Action.SET_DIVIDEND, "", Math.min(100, pct + 10), 0),
                                pct >= 100 ? "已经是 100%" : null)));
        ui.textRight(bx - 6, y + 3, bx - 6 - x, "利润分给股东 " + pct + "%", TEXT);
    }

    /** “当老板”小标签：大老板玩法能用，或者还没解锁（显示怎么解锁）时出现。 */
    static boolean bossOn(View v) {
        return extra(v, "boss") || locked(v, "boss");
    }

    // ---------------------------------------------------------------- 当老板

    static SimpleBossService.BossInfo boss(View v) {
        return v.status() == null
                ? SimpleBossService.BossInfo.EMPTY
                : v.status().boss();
    }

    /**
     * 当老板：上面是“老板待办”机会卡（该收购、回购、投保、开银行时才出现，点一下就办），
     * 中间是待投票的提案，最下面一行保险状态和随时可开的股东大会。
     */
    static void bossTab(UiBuilder ui, View v, State s, Actions a, int y) {
        if (!extra(v, "boss")) {
            lockedCard(ui, y, "boss");
            return;
        }
        SimpleBossService.BossInfo b = boss(v);
        FinanceMenu.CompanyInfo info = v.company();
        if (extra(v, "wage") && b.wageActive()) {
            wageRow(ui, v, a, y);
            y += 18;
        }
        int bottomRows = (extra(v, "insurance") ? 13 : 0) + (extra(v, "shareholder") && b.publicCompany() ? 17 : 0);
        int limit = BOTTOM - bottomRows;

        // 待投票的提案
        if (extra(v, "shareholder")) {
            List<FinanceMenu.CompanyProposalRow> open = v.proposals().stream()
                    .filter(p -> "ACTIVE".equals(p.status()) && !p.playerVoted())
                    .toList();
            for (int i = 0; i < Math.min(2, open.size()) && y + 15 <= limit; i++) {
                FinanceMenu.CompanyProposalRow p = open.get(i);
                String id = p.proposalId().toString();
                int bx = ui.buttonsRight(
                        RIGHT - 2,
                        y,
                        2,
                        List.of(
                                new Btn("赞成", () -> a.simple(SimpleActionPacket.Action.VOTE_YES, id, 0, 0), null),
                                new Btn("反对", () -> a.simple(SimpleActionPacket.Action.VOTE_NO, id, 0, 0), null)));
                long votes = Math.max(1, p.yesVotes() + p.noVotes());
                String owner = info != null && info.companyId().equals(p.companyId()) ? "我的公司" : "持股公司";
                ui.text(
                        LEFT + PAD,
                        y + 3,
                        bx - LEFT - PAD - 6,
                        "投票：" + owner + "「" + p.title() + "」赞成 " + (p.yesVotes() * 100 / votes) + "%，第 " + p.endMcDay()
                                + " 天截止",
                        TEXT);
                y += ROW;
            }
        }

        // 老板待办
        List<SimpleOpportunityService.Card> todo = v.status() == null
                ? List.of()
                : v.status().cards().stream()
                        .filter(SimpleOpportunityService.Card::boss)
                        .toList();
        ui.text(LEFT + 2, y, 100, "老板待办", DIM);
        y += 12;
        if (todo.isEmpty()) {
            y += ui.wrapped(
                    LEFT + PAD,
                    y,
                    WIDTH - 2 * PAD,
                    "现在没什么急事。别家股价被低估、公司缺钱、库存没上保险、钱多到能开银行时，这里会提醒你。",
                    DIM,
                    Math.max(1, Math.min(2, (limit - y) / UiBuilder.LINE)));
        }
        for (SimpleOpportunityService.Card card : todo) {
            if (y + ROW > limit) break;
            cardRow(ui, s, a, card, y);
            y += ROW;
        }

        // 最下面：保险状态、股东大会
        int by = BOTTOM - bottomRows;
        if (extra(v, "insurance")) {
            List<String> parts = new ArrayList<>();
            for (SimpleBossService.Insurance i : b.insurance()) {
                String name =
                        i.name().contains("：") ? i.name().substring(0, i.name().indexOf('：')) : i.name();
                parts.add(name + (i.active() ? " 保到第 " + i.expiryDay() + " 天" : " 未投保"));
            }
            ui.text(
                    LEFT + 2,
                    by + 2,
                    WIDTH - 4,
                    "保险：" + (parts.isEmpty() ? "正在读取……" : String.join("  ·  ", parts)),
                    DIM);
            by += 13;
        }
        if (extra(v, "shareholder") && b.publicCompany()) {
            int lx = LEFT + 2 + ui.text(LEFT + 2, by + 3, 70, "股东大会", DIM) + 8;
            int bw = ui.buttonFlow(
                    lx,
                    by,
                    RIGHT - lx,
                    3,
                    List.of(
                            new Btn(
                                    "增发 10% 融资",
                                    () -> a.simple(SimpleActionPacket.Action.PROPOSE, "issue", 0, 0), null),
                            new Btn(
                                    "回购 5% 托股价",
                                    () -> a.simple(SimpleActionPacket.Action.PROPOSE, "buyback", 0, 0), null)));
            if (bw <= UiBuilder.BUTTON_H) {
                int used = ui.buttonsRightStart(0, 3, List.of("增发 10% 融资", "回购 5% 托股价"));
                ui.text(lx - used + 8, by + 3, RIGHT - (lx - used + 8), "投票 3 天，过半通过", DIM);
            }
        }
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

    /** 经营页最下面：工人日工资（宏观经济开启时）。 */
    static void wageRow(UiBuilder ui, View v, Actions a, int y) {
        SimpleBossService.BossInfo b = boss(v);
        if (!b.wageActive()) return;
        long wage = b.wage(), step = Math.max(1, wage / 10);
        int bx = ui.buttonsRight(
                RIGHT - 2,
                y,
                2,
                List.of(
                        new Btn(
                                "-",
                                () -> a.simple(SimpleActionPacket.Action.SET_WAGE, "", Math.max(0, wage - step), 0),
                                wage <= 0 ? "已经是 0" : null),
                        new Btn("+", () -> a.simple(SimpleActionPacket.Action.SET_WAGE, "", wage + step, 0), null)));
        ui.text(
                LEFT + 2,
                y + 3,
                bx - LEFT - 8,
                "工人日工资 " + MoneyFormat.pounds(wage) + "（低于 " + MoneyFormat.pounds(b.reservationWage()) + " 招不到人）",
                wage < b.reservationWage() ? FinanceGui.COL_BAD : TEXT);
    }

    // ---------------------------------------------------------------- 现金

    static void cashTab(UiBuilder ui, View v, Actions a, int y) {
        var c = v.status() == null ? null : v.status().cash();
        if (c == null || !c.enabled()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "服务器没有开启实体现金。", DIM);
            return;
        }
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "现金是央行发行的便士铜币、10 便士硬币和 1 磅纸币，和账户里的钱 1:1 互换。绿宝石按市场价换，价格每天在变。", DIM, 2)
                + 6;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        ui.text(
                LEFT + PAD,
                y + 5,
                WIDTH - 2 * PAD,
                "账户 " + MoneyFormat.pounds(c.balance()) + "    身上现金 " + MoneyFormat.pounds(c.cashOnHand()),
                TEXT);
        ui.buttonFlow(
                LEFT + PAD,
                y + 20,
                WIDTH - 2 * PAD,
                3,
                List.of(
                        new Btn(
                                "身上现金全部存入",
                                () -> a.simple(SimpleActionPacket.Action.CASH_DEPOSIT, "", c.cashOnHand(), 0),
                                c.cashOnHand() > 0 ? null : "身上没有现金"),
                        new Btn(
                                "取出 1 磅",
                                () -> a.simple(SimpleActionPacket.Action.CASH_WITHDRAW, "", 100, 0),
                                c.balance() >= 100 ? null : "账户不到 1 磅"),
                        new Btn(
                                "取出 10 磅",
                                () -> a.simple(SimpleActionPacket.Action.CASH_WITHDRAW, "", 1_000, 0),
                                c.balance() >= 1_000 ? null : "账户不到 10 磅")));
        y += 44;
        ui.card(LEFT, y, WIDTH, 50, CARD, CARD_BORDER);
        ui.text(
                LEFT + PAD,
                y + 5,
                WIDTH - 2 * PAD,
                "绿宝石 " + c.emeralds() + " 颗：现在卖 1 颗得 " + MoneyFormat.pounds(c.emeraldToCash()) + "，买 1 颗付 "
                        + MoneyFormat.pounds(c.cashToEmerald()),
                TEXT);
        ui.text(LEFT + PAD, y + 17, WIDTH - 2 * PAD, c.trendText(), DIM);
        ui.buttonFlow(
                LEFT + PAD,
                y + 31,
                WIDTH - 2 * PAD,
                3,
                List.of(
                        new Btn(
                                "卖 1 颗",
                                () -> a.simple(SimpleActionPacket.Action.EMERALD_SELL, "", 1, 0),
                                c.emeralds() > 0 ? null : "没有可卖的绿宝石"),
                        new Btn(
                                "卖 16 颗",
                                () -> a.simple(
                                        SimpleActionPacket.Action.EMERALD_SELL, "", Math.min(16, c.emeralds()), 0),
                                c.emeralds() > 0 ? null : "没有可卖的绿宝石"),
                        new Btn(
                                "买 1 颗",
                                () -> a.simple(SimpleActionPacket.Action.EMERALD_BUY, "", 1, 0),
                                c.cashOnHand() >= c.cashToEmerald() ? null : "身上现金不够")));
    }

    static void newCompany(UiBuilder ui, View v, State s, Actions a) {
        int y = TOP + 4;
        y += ui.wrapped(LEFT + 4, y, WIDTH - 8, "开一家公司：它每天自己生产、自己卖货，赚的钱可以转进你的钱包。", TEXT, 2) + 10;
        int labelX = LEFT + 4, fieldX = LEFT + 50;
        ui.text(labelX, y + 3, fieldX - labelX - 4, "行业", DIM);
        int bw = ui.button(
                fieldX,
                y,
                "< " + s.newCompanyType.getDisplayName() + " >",
                () -> {
                    CompanyType[] types = CompanyType.values();
                    s.newCompanyType = types[(s.newCompanyType.ordinal() + 1) % types.length];
                },
                null);
        ui.text(
                fieldX + bw + 8,
                y + 3,
                RIGHT - fieldX - bw - 8,
                FinanceFormat.getTypeDescription(s.newCompanyType),
                DIM);
        y += 22;
        ui.text(labelX, y + 4, fieldX - labelX - 4, "名字", DIM);
        ui.input("companyName", fieldX, y, 190);
        y += 24;
        boolean enough = v.balance() >= COMPANY_COST;
        ui.button(
                fieldX,
                y,
                "开公司（10 磅，全部作为公司的启动资金）",
                () -> a.createCompany(s.newCompanyType),
                enough ? null : "还差 " + MoneyFormat.pounds(COMPANY_COST - v.balance()) + "，先去“买卖”页卖点东西");
        if (!enough) {
            ui.text(
                    fieldX,
                    y + 18,
                    RIGHT - fieldX,
                    "还差 " + MoneyFormat.pounds(COMPANY_COST - v.balance()) + "，先去“买卖”页卖点东西。",
                    FinanceGui.COL_WARN);
        }
    }

    // ================================================================ 投资

    static void invest(UiBuilder ui, View v, State s, Actions a) {
        if (s.investTab > 3) s.investTab = 0;
        int y = subTabs(ui, TOP, List.of("股票", "理财", "押注", "现金"), s.investTab, i -> {
            s.investTab = i;
            s.selectedStock = null;
        });
        switch (s.investTab) {
            case 1 -> products(ui, v, s, a, y);
            case 2 -> betsTab(ui, v, a, y);
            case 3 -> cashTab(ui, v, a, y);
            default -> {
                FinanceMenu.StockRow selected = s.selectedStock == null
                        ? null
                        : v.stocks().stream()
                                .filter(r -> r.symbol().equals(s.selectedStock))
                                .findFirst()
                                .orElse(null);
                if (selected != null) stockDetail(ui, v, s, a, selected, y);
                else stockList(ui, v, s, a, y);
            }
        }
    }

    static long unlock(View v) {
        return v.status() == null ? 3_000 : v.status().stockUnlockBalance();
    }

    static boolean stocksLocked(View v) {
        return v.balance() < unlock(v) && v.holdings().isEmpty();
    }

    static void stockList(UiBuilder ui, View v, State s, Actions a, int y) {
        boolean locked = stocksLocked(v);
        int gap = 2;
        int lot = 10 * mult(s);
        String buyLabel = "买" + lot + "股";
        int buttonsX = ui.buttonsRightStart(RIGHT, gap, List.of(buyLabel, "全卖"));
        int holdX = buttonsX - 6 - 56;
        int divX = holdX - 6 - 60;
        int priceX = divX - 6 - 48;
        int nameX = LEFT + 4;
        ui.text(nameX, y, priceX - nameX - 4, "公司（点名字看详情）", DIM);
        ui.text(priceX, y, 48, "股价", DIM);
        ui.text(divX, y, 60, "每百股分红", DIM);
        ui.text(holdX, y, 56, "我持有", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.StockRow> stocks = v.stocks();
        s.investScroll = clamp(s.investScroll, 0, Math.max(0, stocks.size() - INVEST_ROWS));
        y += 15;
        if (stocks.isEmpty()) ui.text(nameX, y + 2, 200, "还没有公司上市。", DIM);
        for (int i = s.investScroll; i < Math.min(stocks.size(), s.investScroll + INVEST_ROWS); i++) {
            FinanceMenu.StockRow row = stocks.get(i);
            long have = holding(v, row.symbol());
            if ((i - s.investScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            int nw = ui.text(nameX, y + 2, priceX - nameX - 4, row.name(), FinanceGui.COL_ACCENT);
            String symbol = row.symbol();
            ui.hotspot(nameX, y - 1, Math.max(nw, 30), ROW - 1, () -> s.selectedStock = symbol);
            String arrow =
                    row.mmHalted() ? " 熔断" : row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            ui.text(
                    priceX,
                    y + 2,
                    48,
                    MoneyFormat.compact(row.lastPrice()) + arrow,
                    row.mmHalted() ? FinanceGui.COL_BAD : FinanceGui.changeColor(row.dayChange()));
            ui.text(divX, y + 2, 60, MoneyFormat.compact(row.expectedDividendPerShare() * 100), TEXT);
            ui.text(holdX, y + 2, 56, have > 0 ? have + " 股" : "-", have > 0 ? TEXT : DIM);
            long cost = row.lastPrice() * lot;
            String buyBlock = row.mmHalted()
                    ? HALTED
                    : locked
                            ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁"
                            : v.balance() < cost ? "钱不够，" + lot + " 股约 " + MoneyFormat.pounds(cost) : null;
            ui.buttonsRight(
                    RIGHT,
                    y,
                    gap,
                    List.of(
                            confirm(
                                    s,
                                    a,
                                    "stockbuy:" + symbol,
                                    buyLabel,
                                    () -> a.stock(true, symbol, lot),
                                    buyBlock,
                                    cost >= BIG),
                            confirm(
                                    s,
                                    a,
                                    "stocksell:" + symbol,
                                    "全卖",
                                    () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                                    have > 0 ? null : "你没有这只股票",
                                    have * row.lastPrice() >= BIG)));
            y += ROW;
        }
        if (s.investScroll + INVEST_ROWS < stocks.size()) ui.textRight(RIGHT, BOTTOM - 40, 100, "滚轮看更多", DIM);
        marginFooter(ui, v, a, locked);
    }

    /** 股票页底部的说明：还不能买时说差多少，能买时说怎么玩；押注没解锁时顺带说怎么解锁。 */
    static void marginFooter(UiBuilder ui, View v, Actions a, boolean locked) {
        int cardY = BOTTOM - 28;
        ui.card(LEFT, cardY, WIDTH, 28, CARD, CARD_BORDER);
        String text = locked
                ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后才能买股票（还差 " + MoneyFormat.pounds(unlock(v) - v.balance())
                        + "）。理财随时可以买。"
                : extra(v, "margin")
                        ? "买股票：涨了卖掉赚差价，拿着每周有分红。点公司名字看走势、设自动卖出，或者押注涨跌。"
                        : locked(v, "bet")
                                ? "买股票：涨了卖掉赚差价，拿着每周有分红。买过一次股票后解锁「押注」：押涨跌赚翻倍。"
                                : "买股票：涨了卖掉赚差价，拿着每周有分红。点公司名字看走势、设自动卖出。";
        ui.wrapped(LEFT + PAD, cardY + 4, WIDTH - 2 * PAD, text, DIM, 2);
    }

    static void stockDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.StockRow row, int y) {
        String symbol = row.symbol();
        long have = holding(v, symbol);
        long price = Math.max(1, row.lastPrice());
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedStock = null, null);
        String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
        int hx = ui.textRight(RIGHT - 2, y + 2, 140, "我持有 " + have + " 股", TEXT);
        ui.text(
                LEFT + bw + 10,
                y + 2,
                hx - LEFT - bw - 18,
                row.name() + "  " + MoneyFormat.compact(price) + arrow
                        + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()),
                FinanceGui.changeColor(row.dayChange()));
        y += 16;
        priceChart(
                ui,
                s,
                y,
                42,
                row.priceHistory(),
                row.hourBars(),
                FinanceGui.changeColor(row.dayChange()),
                () -> a.simple(SimpleActionPacket.Action.PRICE_ALERT, "s:" + symbol, 0, 0));
        y += 46;
        ui.text(
                LEFT + 2,
                y,
                WIDTH - 4,
                "每股分红约 " + MoneyFormat.pounds(row.expectedDividendPerShare()) + "（上次 "
                        + MoneyFormat.pounds(row.lastDividendPerShare()) + "），每周发一次",
                DIM);
        y += 13;

        // 买卖
        boolean locked = stocksLocked(v);
        String lockReason = row.mmHalted() ? HALTED : locked ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁" : null;
        // 按钮上写明大约花多少钱；按住 Shift 股数 ×10
        int small = 10 * mult(s), large = 100 * mult(s);
        long smallCost = price * small, largeCost = price * large;
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 40, "买卖", DIM) + 8;
        y += ui.buttonFlow(
                        lx,
                        y,
                        RIGHT - lx,
                        3,
                        List.of(
                                confirm(
                                        s,
                                        a,
                                        "stockbuy:" + symbol,
                                        "买" + small + "股≈" + MoneyFormat.compact(smallCost),
                                        () -> a.stock(true, symbol, small),
                                        lockReason != null ? lockReason : v.balance() < smallCost ? "钱不够" : null,
                                        smallCost >= BIG),
                                confirm(
                                        s,
                                        a,
                                        "stockbuy100:" + symbol,
                                        "买" + large + "股≈" + MoneyFormat.compact(largeCost),
                                        () -> a.stock(true, symbol, large),
                                        lockReason != null ? lockReason : v.balance() < largeCost ? "钱不够" : null,
                                        largeCost >= BIG),
                                confirm(
                                        s,
                                        a,
                                        "stocksell:" + symbol,
                                        "全卖",
                                        () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                                        row.mmHalted() ? HALTED : have > 0 ? null : "你没有这只股票",
                                        have * price >= BIG)))
                + 4;

        // 自动卖出
        long up = price + Math.max(1, price / 10), down = Math.max(1, price - Math.max(1, price / 10));
        lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 50, "自动卖出", DIM) + 8;
        String noHold = have > 0 ? null : "先买入才能设自动卖出";
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn(
                                "涨到 " + MoneyFormat.compact(up) + " 全卖",
                                () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_UP, symbol, have, up),
                                noHold),
                        new Btn(
                                "跌到 " + MoneyFormat.compact(down) + " 全卖",
                                () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_DOWN, symbol, have, down),
                                noHold)));
        y += 16;
        List<FinanceMenu.ConditionalStockOrderRow> autos =
                v.autoOrders().stream().filter(o -> o.symbol().equals(symbol)).toList();
        if (!autos.isEmpty()) {
            FinanceMenu.ConditionalStockOrderRow o = autos.get(0);
            int cx = ui.buttonRight(
                    RIGHT - 2,
                    y - 2,
                    "取消",
                    () -> a.simple(
                            SimpleActionPacket.Action.CANCEL_AUTO, o.orderId().toString(), 0, 0),
                    null);
            ui.text(
                    LEFT + 12,
                    y,
                    cx - LEFT - 18,
                    ("TAKE_PROFIT".equals(o.type()) ? "涨到 " : "跌到 ") + MoneyFormat.pounds(o.triggerPrice()) + " 时卖出 "
                            + o.quantity() + " 股" + (autos.size() > 1 ? "（还有 " + (autos.size() - 1) + " 条）" : ""),
                    TEXT);
            y += 13;
        }
        y += 3;

        // 押注：解锁了就给看涨 / 看跌，还没解锁就说怎么解锁，整个关掉就讲股价怎么来的
        if (extra(v, "margin")) {
            y += betRow(ui, v, s, a, y, "s:" + symbol, row.mmHalted() ? HALTED : null);
            ui.wrapped(
                    LEFT + 2,
                    y,
                    WIDTH - 4,
                    stockBetText(v, price, s.betStake),
                    DIM,
                    Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        String text = locked(v, "bet")
                ? "押注（" + unlockHint("bet") + "）：押股价涨还是跌，押对了赚翻倍。" + valuationText(v, row)
                : valuationText(v, row);
        ui.wrapped(LEFT + 2, y, WIDTH - 4, text, DIM, Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
    }

    /** “股价怎么来的”：家底 + 日利润 × 倍数，÷ 股数；现价比每股价值贵还是便宜。 */
    static String valuationText(View v, FinanceMenu.StockRow row) {
        SimpleCenterService.Valuation val = v.status() == null
                ? null
                : v.status().valuations().stream()
                        .filter(x -> x.symbol().equals(row.symbol()))
                        .findFirst()
                        .orElse(null);
        if (val == null || val.shares() <= 0) {
            return "股价怎么来的：公司的家底加上最近赚的钱，再除以股数。央行加息时股价通常会跌。";
        }
        long perShare = Math.max(1, row.fairValue());
        long price = Math.max(1, row.lastPrice());
        String verdict = price > perShare * 11 / 10 ? "现价偏贵" : price < perShare * 9 / 10 ? "现价偏便宜" : "现价合理";
        return "股价怎么来的：家底 " + MoneyFormat.compact(val.netAssets()) + " + 日利润 " + MoneyFormat.signed(val.dailyProfit())
                + " × " + (val.multipleX10() / 10) + " 天，÷ " + val.shares()
                + " 股 ≈ 每股 " + MoneyFormat.compact(perShare) + "，" + verdict + "。倍数看央行利率，加息时变小，股价跟着跌。";
    }

    /**
     * 价格图：右边小按钮切换“走势”（每次价格变动一个点）和“K线”（每游戏小时一根），
     * 第三个“提醒我”在价格涨或跌 10% 时提醒（alert 为 null 时不显示）。
     */
    static void priceChart(
            UiBuilder ui, State s, int y, int h, List<Long> line, List<Long> bars, int color, Runnable alert) {
        int bw = Math.max(Math.max(ui.buttonWidth("走势"), ui.buttonWidth("K线")), ui.buttonWidth("提醒我"));
        int chartW = WIDTH - 4 - bw - 4;
        if (s.candles) ui.candles(LEFT + 2, y, chartW, h, bars);
        else ui.chart(LEFT + 2, y, chartW, h, line, color);
        int bx = LEFT + 2 + chartW + 4;
        ui.button(bx, y, "走势", () -> s.candles = false, s.candles ? null : "正在看走势：每次价格变动一个点");
        ui.button(bx, y + UiBuilder.BUTTON_H + 1, "K线", () -> s.candles = true, s.candles ? "正在看 K 线：每游戏小时一根" : null);
        if (alert != null) ui.button(bx, y + 2 * (UiBuilder.BUTTON_H + 1), "提醒我", alert, null);
    }

    static void commodityDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.MarketRow row, int y) {
        String id = row.commodityId();
        String name = FinanceFormat.commodityDisplayName(id);
        int have = v.backpack().getOrDefault(id, 0);
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedCommodity = null, null);
        int sx = ui.textRight(
                RIGHT - 2,
                y + 2,
                150,
                "市场还有 " + row.marketStock() + " 个",
                row.marketStock() > 0 ? TEXT : FinanceGui.COL_BAD);
        ui.icon(LEFT + bw + 10, y + 1, id);
        ui.text(
                LEFT + bw + 24,
                y + 2,
                sx - LEFT - bw - 30,
                name + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()),
                FinanceGui.changeColor(row.dayChange()));
        y += 16;
        boolean bet = extra(v, "futures"), betLocked = !bet && locked(v, "bet");
        int chartH = bet ? 40 : betLocked ? 58 : 70;
        priceChart(
                ui,
                s,
                y,
                chartH,
                row.priceHistory(),
                row.hourBars(),
                FinanceGui.changeColor(row.dayChange()),
                () -> a.simple(SimpleActionPacket.Action.PRICE_ALERT, "c:" + id, 0, 0));
        y += chartH + 4;
        ui.text(
                LEFT + 2,
                y,
                WIDTH - 4,
                "今天最高 " + MoneyFormat.compact(row.dayHigh()) + "  最低 " + MoneyFormat.compact(row.dayLow()) + "  成交 "
                        + row.dayVolume() + " 个",
                DIM);
        y += 14;

        // 按住 Shift 数量 ×10；左边的价格后面写一组多少钱
        int m = mult(s);
        String noItem = have > 0 ? null : "背包里没有" + name;
        int sellQty = Math.min(64 * m, have);
        int lx = LEFT
                + 2
                + ui.text(
                        LEFT + 2,
                        y + 3,
                        160,
                        "你卖得 " + MoneyFormat.compact(row.bidPrice()) + "/个，一组 "
                                + MoneyFormat.compact(row.bidPrice() * 64),
                        TEXT)
                + 8;
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn(
                                m == 1 ? "卖1组" : "卖10组",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, sellQty),
                                noItem),
                        confirm(
                                s,
                                a,
                                "sellall:" + id,
                                "全卖（" + have + "）",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have),
                                noItem,
                                (long) have * row.bidPrice() >= BIG)));
        y += 17;
        String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
        String empty = row.marketStock() <= 0 ? "市场上没货了，等别人卖或央行补货" : null;
        String block = empty != null ? empty : poor;
        lx = LEFT
                + 2
                + ui.text(
                        LEFT + 2,
                        y + 3,
                        160,
                        "你买付 " + MoneyFormat.compact(row.askPrice()) + "/个，一组 "
                                + MoneyFormat.compact(row.askPrice() * 64),
                        TEXT)
                + 8;
        ui.buttonFlow(
                lx,
                y,
                RIGHT - lx,
                3,
                List.of(
                        new Btn("买" + m, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, m), block),
                        new Btn("买" + 16 * m, () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 16 * m), block),
                        confirm(
                                s,
                                a,
                                "buystack:" + id,
                                m == 1 ? "买1组" : "买10组",
                                () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64 * m),
                                block,
                                64L * m * row.askPrice() >= BIG)));
        y += 19;
        if (bet) {
            var contract = betContract(v, id);
            String betBlock = v.futures() == null ? "正在读取合约……" : contract == null ? "这种东西现在没有可押的合约，明天再来" : null;
            String text;
            if (contract == null) {
                text = "商品押注：押价格涨还是跌，到期按价格结算。" + (betBlock == null ? "" : betBlock + "。");
            } else {
                var q = quote(v, contract);
                long price = Math.max(1, (q.bid() + q.ask()) / 2);
                int bps = v.status() == null ? 1_000 : v.status().futuresMarginBps();
                SimpleBetService.Preview pv = SimpleBetService.futuresPreview(s.betStake, price, contract.size(), bps);
                if (pv.quantity() <= 0) {
                    betBlock =
                            "至少投入 " + MoneyFormat.pounds(SimpleBetService.futuresMinStake(price, contract.size(), bps));
                    text = "商品押注一份 = " + contract.size() + " 个，" + betBlock + "。";
                } else {
                    text = "投入 " + MoneyFormat.pounds(s.betStake) + " 签 " + pv.quantity() + " 份（共 "
                            + pv.quantity() * contract.size()
                            + " 个）：价格涨 10% 看涨赚 " + MoneyFormat.pounds(pv.swing10()) + "，看跌亏同样多。第 "
                            + contract.maturityDay() + " 天自动结算，押金退回钱包。";
                }
            }
            y += betRow(ui, v, s, a, y, "f:" + id, betBlock);
            ui.wrapped(LEFT + 2, y, WIDTH - 4, text, DIM, Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        if (betLocked) {
            ui.wrapped(
                    LEFT + 2,
                    y,
                    WIDTH - 4,
                    "押注（" + unlockHint("bet") + "）：押价格涨还是跌，押对了赚翻倍。",
                    DIM,
                    Math.max(1, Math.min(2, (BOTTOM - y) / UiBuilder.LINE)));
            return;
        }
        Commodity c = CommodityRegistry.getCommodity(id);
        int tier = c == null ? -1 : c.getReserveTier();
        String source = tier == 0 ? "来源：玩家卖出。央行不供应这种东西，卖完就没了。" : tier > 0 ? "来源：央行储备第 " + tier + " 档，每天只补少量货。" : "";
        ui.wrapped(
                LEFT + 2,
                y,
                WIDTH - 4,
                source + "价格跟着市场库存走：大家抢着买就涨，卖的人多就跌。",
                DIM,
                Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
    }

    /** 熔断或停牌时按钮上的说明。 */
    static final String HALTED = "熔断或停牌中：股价 5 分钟内跌超 15% 会暂停交易 1 分钟";

    static final int PRODUCT_ROWS = 8;
    static final long[] AMOUNTS = {1_000, 5_000, 10_000};

    static void products(UiBuilder ui, View v, State s, Actions a, int y) {
        SimpleCenterService.Status st = v.status();
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 30, "金额", DIM) + 6;
        lx += ui.text(lx, y + 3, 80, MoneyFormat.pounds(s.productAmount), TEXT) + 8;
        List<Btn> amounts = new ArrayList<>();
        for (long amount : AMOUNTS)
            amounts.add(new Btn(MoneyFormat.compact(amount), () -> s.productAmount = amount, null));
        amounts.add(
                new Btn("钱包全部", () -> s.productAmount = Math.max(1, v.balance()), v.balance() > 0 ? null : "钱包是空的"));
        ui.buttonFlow(lx, y, RIGHT - lx, 2, amounts);
        y += 17;

        // 三档：稳健 / 均衡 / 进取，每档一行
        int buyX = RIGHT - ui.buttonWidth("存入");
        int riskX = buyX - 6 - 24, rateX = riskX - 6 - 50, termX = rateX - 6 - 50, nameX = LEFT + 2;
        List<SimpleInvestService.Product> products = st == null ? List.of() : st.products();
        if (products.isEmpty()) {
            ui.text(nameX, y + 2, 200, st == null ? "正在读取……" : "现在没有银行营业。", DIM);
            y += ROW;
        }
        long amount = s.productAmount;
        for (SimpleInvestService.Product p : products) {
            if (y + ROW > BOTTOM - 2 * ROW) break;
            ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            ui.text(nameX, y + 2, termX - nameX - 4, p.name(), TEXT);
            ui.text(termX, y + 2, 50, p.term(), DIM);
            ui.text(
                    rateX,
                    y + 2,
                    50,
                    p.rateBps() < 0 ? "随行情" : String.format(java.util.Locale.ROOT, "年 %.2f%%", p.rateBps() / 100.0),
                    TEXT);
            int riskColor = "低".equals(p.risk())
                    ? FinanceGui.COL_GOOD
                    : "中".equals(p.risk()) ? FinanceGui.COL_WARN : FinanceGui.COL_BAD;
            ui.text(riskX, y + 2, 24, "险" + p.risk(), riskColor);
            String block = amount < p.minAmount()
                    ? "最少 " + MoneyFormat.pounds(p.minAmount())
                    : v.balance() < amount ? "钱包不到 " + MoneyFormat.pounds(amount) : null;
            String key = p.key();
            ui.button(buyX, y, "存入", () -> a.simple(SimpleActionPacket.Action.BUY_PRODUCT, key, amount, 0), block);
            y += ROW;
        }
        if (locked(v, "growth")) {
            ui.text(nameX, y + 1, WIDTH - 4, "进取 · 基金（" + unlockHint("growth") + "）：跟着股市涨跌，收益高也可能亏", DIM);
            y += 12;
        }

        // 我的理财
        y += 4;
        if (y + 13 + ROW > BOTTOM) return;
        List<SimpleInvestService.Holding> holdings = st == null ? List.of() : st.holdings();
        int labelW = ui.text(LEFT + 2, y, 60, "我的理财", DIM);
        ui.text(
                LEFT + 2 + labelW + 8,
                y,
                WIDTH - labelW - 12,
                holdings.isEmpty() ? "还没有，挑一档存进去，每天都有利息。" : "存款保本；基金随行情涨跌。",
                DIM);
        y += 13;
        int rows = Math.max(1, (BOTTOM - y) / ROW);
        s.productScroll = clamp(s.productScroll, 0, Math.max(0, holdings.size() - rows));
        for (int i = s.productScroll; i < Math.min(holdings.size(), s.productScroll + rows); i++) {
            SimpleInvestService.Holding h = holdings.get(i);
            String key = h.key();
            int bx = ui.buttonRight(
                    RIGHT - 2,
                    y,
                    h.redeemLabel(),
                    () -> a.simple(SimpleActionPacket.Action.REDEEM_PRODUCT, key, 0, 0),
                    null);
            int nw = ui.text(
                    LEFT + PAD,
                    y + 3,
                    Math.min(150, bx - LEFT - PAD - 8),
                    h.name() + " " + MoneyFormat.compact(h.amount()),
                    TEXT);
            ui.text(LEFT + PAD + nw + 6, y + 3, bx - LEFT - PAD - nw - 14, h.status(), DIM);
            y += ROW;
        }
    }

    // ---------------------------------------------------------------- 押注

    /** 我的押注：每笔一行，右边是赚了还是亏了、和“结束”按钮。押金在结束后自动退回钱包。 */
    static void betsTab(UiBuilder ui, View v, Actions a, int y) {
        if (!extra(v, "margin")
                && !extra(v, "futures")
                && (v.status() == null || v.status().bets().isEmpty())) {
            if (locked(v, "bet")) lockedCard(ui, y, "bet");
            else ui.text(LEFT + PAD, y + 2, WIDTH, "服务器没有开启押注。", DIM);
            return;
        }
        List<SimpleBetService.Bet> bets =
                v.status() == null ? List.of() : v.status().bets();
        SimpleInvestService.MarginInfo m = v.status() == null
                ? SimpleInvestService.MarginInfo.EMPTY
                : v.status().margin();
        int footer = 24;
        if (bets.isEmpty()) {
            ui.wrapped(
                    LEFT + PAD,
                    y + 2,
                    WIDTH - 2 * PAD,
                    "还没有押注。在股票详情（投资 → 点公司名字）或商品详情（买卖 → 点东西名字）里选好投入，" + "点「看涨」或「看跌」就开始。押对了赚翻倍，押错会亏，亏太多会被强制结束。",
                    DIM,
                    4);
            String stats = v.status() == null ? "" : v.status().betStats();
            if (!stats.isEmpty()) ui.text(LEFT + PAD, BOTTOM - 11, WIDTH - 2 * PAD, stats, TEXT);
            return;
        }
        int rowH = 26;
        int fit = Math.max(1, (BOTTOM - footer - y) / rowH);
        int shown = bets.size() <= fit ? bets.size() : fit - 1;
        for (SimpleBetService.Bet bet : bets.subList(0, shown)) {
            ui.card(LEFT, y, WIDTH, rowH - 2, CARD, CARD_BORDER);
            String key = bet.key();
            int bx = ui.buttonRight(
                            RIGHT - PAD,
                            y + 5,
                            "结束",
                            () -> a.simple(SimpleActionPacket.Action.BET_END, key, 0, 0),
                            null)
                    - 8;
            String result = !bet.tracked()
                    ? "现值 " + MoneyFormat.compact(bet.value())
                    : (bet.pnl() >= 0 ? "赚 " : "亏 ") + MoneyFormat.compact(Math.abs(bet.pnl()));
            int rx = ui.textRight(bx, y + 4, 110, result, bet.tracked() ? FinanceGui.changeColor(bet.pnl()) : TEXT) - 8;
            int sx = bet.tracked()
                    ? ui.textRight(bx, y + 14, 110, "投入 " + MoneyFormat.compact(bet.stake()), DIM) - 8
                    : bx;
            int tw = ui.text(
                    LEFT + PAD, y + 4, 40, bet.up() ? "看涨" : "看跌", bet.up() ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            ui.text(LEFT + PAD + tw + 6, y + 4, rx - LEFT - PAD - tw - 6, bet.name(), TEXT);
            ui.text(LEFT + PAD, y + 14, sx - LEFT - PAD, bet.note(), DIM);
            y += rowH;
        }
        if (shown < bets.size())
            ui.text(LEFT + PAD, y + 4, WIDTH - PAD, "还有 " + (bets.size() - shown) + " 笔，先结束几笔再看。", DIM);
        int fy = BOTTOM - footer + 2;
        boolean stockBets = bets.stream().anyMatch(b -> b.key().startsWith("s:"));
        String line = stockBets && m.safetyPct() >= 0
                ? "股票押注安全度 " + m.safetyPct() + "%：低于 " + m.liquidationPct() + "% 会被强制结束。结束后押金自动退回钱包。"
                : "结束后押金自动退回钱包；商品押注到期自动结算。";
        ui.text(
                LEFT + 2,
                fy,
                WIDTH - 4,
                line,
                stockBets && m.safetyPct() >= 0 && m.safetyPct() < m.liquidationPct() + 20 ? FinanceGui.COL_BAD : DIM);
        String stats = v.status() == null ? "" : v.status().betStats();
        ui.text(
                LEFT + 2,
                fy + UiBuilder.LINE,
                WIDTH - 4,
                stats.isEmpty() ? "被强制结束、到期结算时会在聊天栏提醒你，离线时上线补发。" : stats,
                DIM);
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
        FinanceMenu.CompanyInfo info = v.company();
        if (st != null && st.companyValuation() > 0) return st.companyValuation();
        return info == null ? 0 : info.cash() + info.inventoryValue();
    }

    static long holding(View v, String symbol) {
        for (FinanceMenu.StockHoldingRow h : v.holdings()) if (h.symbol().equals(symbol)) return h.quantity();
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
