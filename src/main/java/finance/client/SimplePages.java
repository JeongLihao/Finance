package finance.client;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleInvestService;
import finance.simple.SimpleBossService;
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
        HOME("首页"), TRADE("买卖"), COMPANY("我的公司"), INVEST("投资");
        final String label;
        Page(String label) { this.label = label; }
    }

    /** 排版需要的全部数据（从金融中心快照和首页数据里取）。 */
    record View(long balance, long todayProfit, long stockValue, Map<String, Integer> backpack,
                List<FinanceMenu.MarketRow> market, List<FinanceMenu.TransactionRow> transactions,
                FinanceMenu.CompanyInfo company, List<FinanceMenu.StockRow> stocks,
                List<FinanceMenu.StockHoldingRow> holdings, SimpleCenterService.Status status,
                boolean playerChoosesAdvanced, boolean wantsAdvanced, List<String> pendingSummary,
                List<FinanceMenu.OrderRow> orders, Map<String, Integer> storedGoods,
                finance.network.FuturesResponsePacket futures, List<FinanceMenu.ConditionalStockOrderRow> autoOrders,
                List<FinanceMenu.CompanyProposalRow> proposals) {}

    /** 页面上的状态（当前页、滚动位置、新公司行业）。 */
    static final class State {
        Page page = Page.HOME;
        int tradeScroll, investScroll, recordScroll;
        /** 各页的小标签：首页 0 今天 / 1 经济天气 / 2 全部记录；买卖 0 马上买卖 / 1 挂单 / 2 锁价合同（打开 futures 时）。 */
        int homeTab, tradeTab, investTab, companyTab, productScroll;
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

        default void leverRepay() { simple(SimpleActionPacket.Action.LEVER_REPAY, "", 0, 0); }

        default void withdrawCollateral() { simple(SimpleActionPacket.Action.COLLATERAL_WITHDRAW, "", 0, 0); }
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
        int y = subTabs(ui, TOP, List.of("今天", "经济天气", "全部记录"), s.homeTab, i -> s.homeTab = i);
        switch (s.homeTab) {
            case 1 -> weather(ui, v, y);
            case 2 -> records(ui, v, s, y);
            default -> today(ui, v, a, y);
        }
    }

    /** “上次打开以来”的小结卡片：挡住首页内容，点“知道了”关掉。 */
    static void summary(UiBuilder ui, View v, Actions a) {
        int x = LEFT + 40, w = WIDTH - 80, y = TOP + 24;
        List<String> lines = v.pendingSummary();
        int h = 34 + lines.size() * UiBuilder.LINE + 20;
        ui.card(x, y, w, h, CARD, CARD_BORDER);
        ui.text(x + PAD, y + 6, w - 2 * PAD, "上次打开以来", TEXT);
        for (int i = 0; i < lines.size(); i++) {
            ui.text(x + PAD, y + 22 + i * UiBuilder.LINE, w - 2 * PAD, lines.get(i), i == 0 ? TEXT : DIM);
        }
        ui.buttonRight(x + w - PAD, y + h - 18, "知道了", a::dismissSummary, null);
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
            ui.wrapped(LEFT + PAD, Math.max(y, BOTTOM - 22), WIDTH - 2 * PAD,
                    "利率和物价由央行每天自动调整。利率高时适合存钱，利率低时适合借钱扩大公司。", DIM, 2);
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
            String what = (r.objectName().isEmpty() ? "" : displayObject(r.objectName())) + (r.quantity() > 1 ? " ×" + r.quantity() : "");
            ui.text(LEFT + 2 + typeW + 6, y, WIDTH - typeW - amountW - 16, what, DIM);
            ui.textRight(RIGHT - 2, y, amountW, MoneyFormat.pounds(r.amount()), TEXT);
            y += UiBuilder.LINE;
        }
        if (rows.size() > RECORD_ROWS) {
            ui.textRight(RIGHT - 2, BOTTOM - 9, 160, "滚轮翻页 " + (s.recordScroll + 1) + "-"
                    + Math.min(rows.size(), s.recordScroll + RECORD_ROWS) + " / " + rows.size(), DIM);
        }
    }

    static void today(UiBuilder ui, View v, Actions a, int top) {
        SimpleCenterService.Status st = v.status();
        int y = top;
        ui.card(LEFT, y, WIDTH, 42, CARD, CARD_BORDER);
        int rightX = 236;
        ui.text(LEFT + PAD, y + 4, 60, "钱包", DIM);
        ui.bigText(LEFT + PAD, y + 16, rightX - LEFT - PAD - 8, MoneyFormat.pounds(v.balance()), TEXT, 2.0F);
        int colW = RIGHT - PAD - rightX;
        ui.text(rightX, y + 5, colW, "今天 " + MoneyFormat.signed(v.todayProfit()), FinanceGui.changeColor(v.todayProfit()));
        ui.text(rightX, y + 17, colW, "银行 " + MoneyFormat.compact(st == null ? 0 : st.bankBalance())
                + "  股票 " + MoneyFormat.compact(v.stockValue()), DIM);
        if (ownsCompany(v)) ui.text(rightX, y + 29, colW, "公司估值 " + MoneyFormat.compact(valuation(v)), DIM);

        y += 46;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        Goal goal = goal(v);
        int buttonX = ui.buttonRight(RIGHT - PAD, y + 13, "去看看", () -> a.show(goal.page()), null);
        ui.text(LEFT + PAD, y + 4, 100, "下一个目标", DIM);
        ui.text(LEFT + PAD, y + 16, buttonX - LEFT - PAD - 8, goal.text(), TEXT);
        ui.progress(LEFT + PAD, y + 28, buttonX - LEFT - PAD - 8, goal.progress());

        y += 42;
        ui.text(LEFT + 2, y, 100, "今日机会", DIM);
        List<String> chances = st == null ? List.of("正在读取……") : st.opportunities();
        for (int i = 0; i < Math.min(3, chances.size()); i++) {
            ui.text(LEFT + PAD, y + 12 + i * UiBuilder.LINE, WIDTH - PAD, "· " + chances.get(i), TEXT);
        }

        y += 47;
        int labelW = ui.text(LEFT + 2, y, 60, "最近", DIM);
        List<FinanceMenu.TransactionRow> rows = v.transactions();
        if (rows.isEmpty()) ui.text(LEFT + PAD, y + 12, 200, "还没有交易。", DIM);
        for (int i = 0; i < Math.min(2, rows.size()); i++) {
            FinanceMenu.TransactionRow r = rows.get(i);
            String line = FinanceFormat.displayTransactionType(r.type())
                    + (r.objectName().isEmpty() ? "" : " " + displayObject(r.objectName()))
                    + (r.quantity() > 1 ? " ×" + r.quantity() : "") + "  " + MoneyFormat.pounds(r.amount());
            ui.text(LEFT + PAD, y + 12 + i * UiBuilder.LINE, WIDTH - PAD, line, TEXT);
        }
    }

    record Goal(String text, double progress, Page page) {}

    /** 首页“下一个目标”：按 开公司 → 买股票 → 上市 → 升满工厂 的顺序找第一个还没做到的。 */
    static Goal goal(View v) {
        long balance = v.balance();
        SimpleCenterService.Status st = v.status();
        if (!ownsCompany(v)) {
            if (balance < COMPANY_COST) {
                return new Goal("攒到 10 磅就能开公司（还差 " + MoneyFormat.pounds(COMPANY_COST - balance) + "）",
                        balance / (double) COMPANY_COST, Page.TRADE);
            }
            return new Goal("钱够了，去开一家公司吧！", 1, Page.COMPANY);
        }
        long unlock = st == null ? 3_000 : st.stockUnlockBalance();
        if (v.holdings().isEmpty() && balance < unlock) {
            return new Goal("攒到 " + MoneyFormat.pounds(unlock) + " 就能买股票拿分红（还差 "
                    + MoneyFormat.pounds(unlock - balance) + "）", balance / (double) Math.max(1, unlock), Page.INVEST);
        }
        if (!v.company().isPublic()) {
            long valuation = valuation(v);
            long min = st == null ? 20_000 : st.ipoMinValuation();
            if (valuation < min) {
                return new Goal("公司估值到 " + MoneyFormat.pounds(min) + " 就能上市（现在 " + MoneyFormat.pounds(valuation) + "）",
                        valuation / (double) Math.max(1, min), Page.COMPANY);
            }
            return new Goal("公司可以上市了！", 1, Page.COMPANY);
        }
        int level = st == null ? 0 : st.factoryLevel();
        if (level < SimpleCenterService.MAX_FACTORY_LEVEL) {
            return new Goal("把工厂升到满级（" + level + " / " + SimpleCenterService.MAX_FACTORY_LEVEL + "）",
                    level / (double) SimpleCenterService.MAX_FACTORY_LEVEL, Page.COMPANY);
        }
        return new Goal(extra(v, "takeover") ? "你已经是大亨了！试试“当老板”里的收购。" : "你已经是大亨了！多买几家公司的股票吧。", 1,
                extra(v, "takeover") ? Page.COMPANY : Page.INVEST);
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
        rows.sort(Comparator.<FinanceMenu.MarketRow>comparingLong(r -> -(long) held.getOrDefault(r.commodityId(), 0) * r.bidPrice())
                .thenComparing(r -> FinanceFormat.commodityDisplayName(r.commodityId())));
        return rows;
    }

    static void tradePage(UiBuilder ui, View v, State s, Actions a) {
        boolean futures = extra(v, "futures");
        List<String> labels = futures ? List.of("马上买卖", "挂单", "锁价合同") : List.of("马上买卖", "挂单");
        if (s.tradeTab >= labels.size()) s.tradeTab = 0;
        int y = subTabs(ui, TOP, labels, s.tradeTab, i -> { s.tradeTab = i; s.selectedCommodity = null; });
        switch (s.tradeTab) {
            case 1 -> orders(ui, v, s, a, y);
            case 2 -> locks(ui, v, a, y);
            default -> {
                FinanceMenu.MarketRow selected = s.selectedCommodity == null ? null
                        : v.market().stream().filter(r -> r.commodityId().equals(s.selectedCommodity)).findFirst().orElse(null);
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
        FinanceMenu.MarketRow row = rows.stream().filter(r -> r.commodityId().equals(s.orderCommodity)).findFirst().orElse(rows.get(0));
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
        x += ui.button(x, y, "<", () -> { s.orderCommodity = rows.get((index - 1 + rows.size()) % rows.size()).commodityId(); s.orderPrice = 0; }, null) + 4;
        ui.icon(x, y + 2, id);
        x += ui.text(x + 13, y + 3, 70, name, TEXT) + 17;
        x += ui.button(x, y, ">", () -> { s.orderCommodity = rows.get((index + 1) % rows.size()).commodityId(); s.orderPrice = 0; }, null) + 8;
        ui.textRight(RIGHT - 2, y + 3, RIGHT - 2 - x, "市场收 " + MoneyFormat.compact(row.bidPrice()) + "  卖 " + MoneyFormat.compact(row.askPrice()), DIM);

        // 第二行：单价、数量
        y += 18;
        x = LEFT + 2;
        ui.text(x, y + 3, 30, "单价", DIM);
        x += 32;
        long finalPrice = price;
        x += ui.button(x, y, "-", () -> s.orderPrice = Math.max(1, finalPrice - step), finalPrice <= 1 ? "最低 1 便士" : null) + 4;
        x += ui.text(x, y + 3, 60, MoneyFormat.compact(price), TEXT) + 4;
        x = Math.max(x, LEFT + 34 + 18 + 64);
        x += ui.button(x, y, "+", () -> s.orderPrice = finalPrice + step, null) + 14;
        ui.text(x, y + 3, 60, "数量 " + s.orderQty, TEXT);
        int qx = x + ui.width("数量 9999") + 6;
        ui.buttonFlow(qx, y, RIGHT - qx, 2, List.of(
                new Btn("1", () -> s.orderQty = 1, null),
                new Btn("16", () -> s.orderQty = 16, null),
                new Btn("64", () -> s.orderQty = 64, null),
                new Btn("背包全部", () -> s.orderQty = Math.max(1, have), have > 0 ? null : "背包里没有" + name)));

        // 第三行：挂卖单 / 挂买单
        y += 18;
        int qty = s.orderQty;
        long total = finalPrice * qty;
        x = LEFT + 2;
        x += ui.button(x, y, "挂卖单", () -> a.simple(SimpleActionPacket.Action.PLACE_SELL_ORDER, id, qty, finalPrice),
                have >= qty ? null : "背包里只有 " + have + " 个" + name) + 4;
        x += ui.button(x, y, "挂买单", () -> a.simple(SimpleActionPacket.Action.PLACE_BUY_ORDER, id, qty, finalPrice),
                v.balance() >= total ? null : "钱不够，要冻结 " + MoneyFormat.pounds(total)) + 8;
        ui.text(x, y + 3, RIGHT - x, qty + " 个，每个 " + MoneyFormat.compact(price) + "，共 " + MoneyFormat.compact(total)
                + "（买单先冻结这笔钱）", DIM);
        y += 17;
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "卖单：市场收购价涨到你定的价就自动卖出。买单：市场出售价跌到你定的价就自动买入，买到的东西放进背包。", DIM, 2) + 4;

        // 我的挂单
        List<FinanceMenu.OrderRow> mine = v.orders().stream().filter(FinanceMenu.OrderRow::ownedByPlayer).toList();
        ui.text(LEFT + 2, y, 120, "我的挂单（" + mine.size() + "）", DIM);
        y += 12;
        if (mine.isEmpty()) {
            ui.text(LEFT + PAD, y, WIDTH, "还没有挂单。", DIM);
            y += 13;
        }
        for (int i = 0; i < Math.min(3, mine.size()); i++) {
            FinanceMenu.OrderRow o = mine.get(i);
            int bx = ui.buttonRight(RIGHT - 2, y - 2, "撤单", () -> a.simple(SimpleActionPacket.Action.CANCEL_ORDER, o.orderId().toString(), 0, 0), null);
            ui.text(LEFT + PAD, y, bx - LEFT - PAD - 6, ("SELL".equals(o.type()) ? "卖 " : "买 ") + o.quantity() + " 个"
                    + FinanceFormat.commodityDisplayName(o.commodityId()) + "，每个 " + MoneyFormat.pounds(o.price()), TEXT);
            y += 14;
        }
        // 暂存在市场的货（撤掉的卖单、离线时成交的买单）
        List<Map.Entry<String, Integer>> stored = v.storedGoods().entrySet().stream().filter(e -> e.getValue() > 0).limit(2).toList();
        for (Map.Entry<String, Integer> e : stored) {
            if (y + 13 > BOTTOM) break;
            String goods = e.getKey();
            int bx = ui.buttonRight(RIGHT - 2, y - 2, "取回背包", () -> a.simple(SimpleActionPacket.Action.TAKE_GOODS, goods, 0, 0), null);
            ui.text(LEFT + PAD, y, bx - LEFT - PAD - 6, "暂存在市场：" + FinanceFormat.commodityDisplayName(goods) + " " + e.getValue() + " 个", DIM);
            y += 14;
        }
    }

    // ---------------------------------------------------------------- 锁价合同

    static void locks(UiBuilder ui, View v, Actions a, int y) {
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "锁价合同：现在就按今天的价格约好以后买卖。担心降价就“锁卖价”，担心涨价就“锁买价”；到期按差价结算，要先交一笔保证金。", DIM, 2) + 4;
        finance.network.FuturesResponsePacket data = v.futures();
        if (data == null) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "正在读取合同……", DIM);
            return;
        }
        int gap = 2;
        int buttonsX = ui.buttonsRightStart(RIGHT, gap, List.of("锁卖价", "锁买价", "平掉"));
        int posX = buttonsX - 6 - 80, priceX = posX - 6 - 74, dueX = priceX - 6 - 46, nameX = LEFT + 2;
        ui.text(nameX, y, dueX - nameX - 4, "东西", DIM);
        ui.text(dueX, y, 46, "到期", DIM);
        ui.text(priceX, y, 74, "约定价 卖/买", DIM);
        ui.text(posX, y, 80, "我的合同", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);
        y += 15;
        List<finance.network.FuturesResponsePacket.ContractRow> contracts = data.contracts().stream()
                .filter(c -> c.status() == finance.futures.FuturesContractStatus.TRADING
                        || c.status() == finance.futures.FuturesContractStatus.LAST_TRADING_DAY).toList();
        if (contracts.isEmpty()) ui.text(nameX, y + 2, WIDTH, "现在没有可以签的合同，明天再来看。", DIM);
        int shown = 0;
        long size = 0;
        for (finance.network.FuturesResponsePacket.ContractRow c : contracts) {
            if (shown++ >= 5) break;
            size = c.size();
            var quote = data.mmQuotes().stream().filter(q -> q.contractId().equals(c.id())).findFirst().orElse(null);
            var pos = data.positions().stream().filter(p -> p.contractId().equals(c.id())).findFirst().orElse(null);
            long held = pos == null ? 0 : pos.signedQuantity();
            ui.text(nameX, y + 2, dueX - nameX - 4, FinanceFormat.commodityDisplayName(c.commodity()), TEXT);
            ui.text(dueX, y + 2, 46, "第" + c.maturityDay() + "天", DIM);
            ui.text(priceX, y + 2, 74, quote == null ? "暂停" : MoneyFormat.compact(quote.bid()) + "/" + MoneyFormat.compact(quote.ask()), TEXT);
            String mine = held == 0 ? "-" : (held < 0 ? "卖" : "买") + Math.abs(held) + "份 " + MoneyFormat.compact(pos.unrealized());
            ui.text(posX, y + 2, 80, mine, held == 0 ? DIM : FinanceGui.changeColor(pos.unrealized()));
            String paused = quote == null ? "市场暂时不报价" : null;
            String id = c.id().toString();
            ui.buttonsRight(RIGHT, y, gap, List.of(
                    new Btn("锁卖价", () -> a.simple(SimpleActionPacket.Action.LOCK_SELL, id, 1, 0), paused),
                    new Btn("锁买价", () -> a.simple(SimpleActionPacket.Action.LOCK_BUY, id, 1, 0), paused),
                    new Btn("平掉", () -> a.simple(SimpleActionPacket.Action.CLOSE_LOCK, id, 0, 0),
                            held == 0 ? "你没有这份合同" : paused)));
            y += ROW;
        }
        int cardY = BOTTOM - 36;
        ui.card(LEFT, cardY, WIDTH, 36, CARD, CARD_BORDER);
        int bx = ui.buttonRight(RIGHT - PAD, cardY + 4, "取回保证金", () -> a.simple(SimpleActionPacket.Action.WITHDRAW_MARGIN, "", 0, 0),
                data.withdrawable() > 0 ? null : "没有可以取回的保证金");
        ui.text(LEFT + PAD, cardY + 6, bx - LEFT - PAD - 6, "保证金 " + MoneyFormat.pounds(data.marginCash()) + "，可取回 "
                + MoneyFormat.pounds(data.withdrawable()), TEXT);
        ui.text(LEFT + PAD, cardY + 20, WIDTH - 2 * PAD, "每次点击签 1 份" + (size > 0 ? "（1 份 = " + size + " 个）" : "")
                + "。价格跌了锁卖价的赚差价，涨了锁买价的赚差价。", DIM);
    }

    static void trade(UiBuilder ui, View v, State s, Actions a, int top) {
        // 列从右往左按按钮实际宽度排，左边剩下的给商品名
        int gap = 2;
        int buyStart = ui.buttonsRightStart(RIGHT, gap, List.of("买1", "买1组"));
        int askX = buyStart - 6 - 50;
        int sellStart = ui.buttonsRightStart(askX - 6, gap, List.of("卖1组", "全卖"));
        int bidX = sellStart - 6 - 50;
        int haveX = bidX - 4 - 34;
        int nameX = LEFT + 16;

        int y = top;
        ui.text(nameX, y, haveX - nameX - 4, "东西（点名字看走势）", DIM);
        ui.text(haveX, y, 34, "背包", DIM);
        ui.text(bidX, y, 50, "你卖得", DIM);
        ui.text(askX, y, 50, "你买付", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.MarketRow> rows = tradeRows(v);
        s.tradeScroll = clamp(s.tradeScroll, 0, Math.max(0, rows.size() - TRADE_ROWS));
        y += 15;
        if (rows.isEmpty()) ui.text(nameX, y + 2, WIDTH - nameX, "市场里还没有货。去挖点东西卖给市场吧。", DIM);
        for (int i = s.tradeScroll; i < Math.min(rows.size(), s.tradeScroll + TRADE_ROWS); i++) {
            FinanceMenu.MarketRow row = rows.get(i);
            String id = row.commodityId();
            String name = FinanceFormat.commodityDisplayName(id);
            int have = v.backpack().getOrDefault(id, 0);
            if ((i - s.tradeScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            ui.icon(LEFT + 2, y + 1, id);
            int nw = ui.text(nameX, y + 2, haveX - nameX - 4, name, FinanceGui.COL_ACCENT);
            ui.hotspot(nameX, y - 1, Math.max(nw, 20), ROW - 1, () -> s.selectedCommodity = id);
            ui.text(haveX, y + 2, 34, have > 0 ? Integer.toString(have) : "-", have > 0 ? TEXT : DIM);
            String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            int color = FinanceGui.changeColor(row.dayChange());
            ui.text(bidX, y + 2, 50, MoneyFormat.compact(row.bidPrice()) + arrow, color);
            String noItem = have > 0 ? null : "背包里没有" + name;
            ui.buttonsRight(askX - 6, y, gap, List.of(
                    new Btn("卖1组", () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, Math.min(64, have)), noItem),
                    new Btn("全卖", () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have), noItem)));
            ui.text(askX, y + 2, 50, MoneyFormat.compact(row.askPrice()) + arrow, color);
            String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
            ui.buttonsRight(RIGHT, y, gap, List.of(
                    new Btn("买1", () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 1), poor),
                    new Btn("买1组", () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64), poor)));
            y += ROW;
        }
        int footY = BOTTOM - 10;
        int pagerX = RIGHT;
        if (rows.size() > TRADE_ROWS) {
            pagerX = ui.textRight(RIGHT, footY, 120, "滚轮翻页 " + (s.tradeScroll + 1) + "-"
                    + Math.min(rows.size(), s.tradeScroll + TRADE_ROWS) + " / " + rows.size(), DIM) - 8;
        }
        ui.text(LEFT + 2, footY, pagerX - LEFT - 2, "直接卖背包里的东西，买的东西直接进背包。", DIM);
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
        if (extra(v, "insurance")) labels.add("保险");
        if (s.companyTab >= labels.size()) s.companyTab = 0;
        int top = subTabs(ui, TOP, labels, s.companyTab, i -> s.companyTab = i);
        int tabsRight = LEFT + 2 + 12;
        for (String label : labels) tabsRight += ui.width(label) + 14;
        ui.textRight(RIGHT - 2, TOP, RIGHT - 2 - tabsRight, info.name() + " · " + info.type() + (info.isPublic() ? " · 已上市" : ""), DIM);
        String tab = labels.get(s.companyTab);
        if (tab.equals("当老板")) {
            bossTab(ui, v, a, top);
            return;
        }
        if (tab.equals("保险")) {
            insuranceTab(ui, v, a, top);
            return;
        }

        // 今天
        int y = top;
        ui.card(LEFT, y, WIDTH, 52, CARD, CARD_BORDER);
        long transfer = Math.min(info.cash(), 10_000L);
        List<Btn> money = List.of(
                new Btn("转 " + MoneyFormat.compact(transfer) + " 到钱包", () -> a.company(CompanyManagementAction.WITHDRAW, transfer),
                        transfer > 0 ? null : "公司账上没钱"),
                new Btn("从钱包投 10 磅", () -> a.company(CompanyManagementAction.INVEST, 1_000L),
                        v.balance() >= 1_000 ? null : "钱包不到 10 磅"));
        int bx = Math.min(ui.buttonRight(RIGHT - PAD, y + 14, money.get(0).label(), money.get(0).action(), money.get(0).blocked()),
                ui.buttonRight(RIGHT - PAD, y + 31, money.get(1).label(), money.get(1).action(), money.get(1).blocked()));
        int textW = bx - LEFT - PAD - 8;
        int titleW = ui.text(LEFT + PAD, y + 4, 40, "今天", DIM);
        if (type != null) ui.text(LEFT + PAD + titleW + 8, y + 4, textW - titleW - 8, FinanceFormat.getTypeDescription(type), DIM);
        ui.text(LEFT + PAD, y + 16, textW, "公司账上 " + MoneyFormat.pounds(info.cash()), TEXT);
        ui.text(LEFT + PAD, y + 27, textW, "库存货值 " + MoneyFormat.pounds(info.inventoryValue()), TEXT);
        if (info.bankruptcyRisk()) {
            ui.text(LEFT + PAD, y + 38, textW, "快撑不住了：钱不够付开销，投点钱进去", FinanceGui.COL_BAD);
        } else {
            ui.text(LEFT + PAD, y + 38, textW, "上次结算利润 " + MoneyFormat.signed(info.reportNetProfit()),
                    FinanceGui.changeColor(info.reportNetProfit()));
        }

        // 工厂
        y += 56;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        int level = st == null ? info.productionLevel() + info.storageLevel() + info.managementLevel() : st.factoryLevel();
        boolean full = level >= SimpleCenterService.MAX_FACTORY_LEVEL;
        long cost = st == null ? 0 : st.nextUpgradeCost();
        String block = full ? "已经满级了" : v.balance() < cost ? "还差 " + MoneyFormat.pounds(cost - v.balance()) : null;
        bx = ui.buttonRight(RIGHT - PAD, y + 12, full ? "已满级" : "升级（" + MoneyFormat.compact(cost) + "）",
                () -> a.simple(SimpleActionPacket.Action.UPGRADE_FACTORY, "", 0), block);
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
            bx = ui.buttonRight(RIGHT - PAD, y + 14, "让公司上市", () -> a.simple(SimpleActionPacket.Action.QUICK_IPO, "", 0),
                    valuation >= min ? null : "估值还差 " + MoneyFormat.pounds(min - valuation));
            textW = bx - LEFT - PAD - 8;
            ui.text(LEFT + PAD, y + 4, textW, "上市：估值 " + MoneyFormat.pounds(valuation) + " / " + MoneyFormat.pounds(min), TEXT);
            ui.progress(LEFT + PAD, y + 16, textW, valuation / (double) Math.max(1, min));
            ui.wrapped(LEFT + PAD, y + 25, textW, "把公司 40% 的股份卖给大家，换一大笔钱进公司账上。要付上市费，发行价按估值自动定。", DIM, 2);
        }
        loanAndDividendRow(ui, v, a, y + 52);
    }

    /** 经营页最下面一行：公司借钱；上市后还能直接调分红比例。 */
    static void loanAndDividendRow(UiBuilder ui, View v, Actions a, int y) {
        int x = LEFT + 2 + ui.button(LEFT + 2, y, "公司借 50 磅（30 天还清）",
                () -> a.simple(SimpleActionPacket.Action.COMPANY_LOAN, "", 5_000, 0), null) + 8;
        SimpleBossService.BossInfo b = boss(v);
        if (!b.publicCompany()) return;
        int pct = b.dividendPct();
        int bx = ui.buttonsRight(RIGHT - 2, y, 2, List.of(
                new Btn("-", () -> a.simple(SimpleActionPacket.Action.SET_DIVIDEND, "", Math.max(0, pct - 10), 0), pct <= 0 ? "已经是 0%" : null),
                new Btn("+", () -> a.simple(SimpleActionPacket.Action.SET_DIVIDEND, "", Math.min(100, pct + 10), 0), pct >= 100 ? "已经是 100%" : null)));
        ui.textRight(bx - 6, y + 3, bx - 6 - x, "利润分给股东 " + pct + "%", TEXT);
    }

    /** “当老板”小标签：股东提案、收购、开银行、工资里至少开了一个才显示。 */
    static boolean bossOn(View v) {
        return extra(v, "shareholder") || extra(v, "takeover") || extra(v, "bank") || extra(v, "wage");
    }

    // ---------------------------------------------------------------- 当老板

    static SimpleBossService.BossInfo boss(View v) {
        return v.status() == null ? SimpleBossService.BossInfo.EMPTY : v.status().boss();
    }

    static void bossTab(UiBuilder ui, View v, Actions a, int y) {
        SimpleBossService.BossInfo b = boss(v);
        FinanceMenu.CompanyInfo info = v.company();

        if (extra(v, "wage") && b.wageActive()) {
            wageRow(ui, v, a, y);
            y += 20;
        }
        if (extra(v, "shareholder")) y = shareholders(ui, v, a, b, info, y);
        if (extra(v, "takeover")) y = takeovers(ui, a, b, y);
        if (extra(v, "bank")) bank(ui, v, a, b, y);
    }

    static int shareholders(UiBuilder ui, View v, Actions a, SimpleBossService.BossInfo b, FinanceMenu.CompanyInfo info, int y) {
        ui.text(LEFT + 2, y, 120, "股东大会", DIM);
        y += 12;
        if (b.publicCompany()) {
            long nextPct = Math.min(100, b.dividendPct() + 10);
            y += ui.buttonFlow(LEFT + 2, y, WIDTH - 4, 3, List.of(
                    new Btn("提议分红提到 " + nextPct + "%", () -> a.simple(SimpleActionPacket.Action.PROPOSE, "dividend", 0, 0),
                            b.dividendPct() >= 100 ? "分红已经是 100%" : null),
                    new Btn("提议增发 10% 融资", () -> a.simple(SimpleActionPacket.Action.PROPOSE, "issue", 0, 0), null),
                    new Btn("提议回购 5%", () -> a.simple(SimpleActionPacket.Action.PROPOSE, "buyback", 0, 0), null))) + 3;
        } else {
            ui.text(LEFT + PAD, y + 2, WIDTH - PAD, "公司上市后可以开股东大会：调分红、增发融资、回购股份。", DIM);
            y += 14;
        }
        List<FinanceMenu.CompanyProposalRow> open = v.proposals().stream()
                .filter(p -> "ACTIVE".equals(p.status()) && !p.playerVoted()).toList();
        for (int i = 0; i < Math.min(2, open.size()); i++) {
            FinanceMenu.CompanyProposalRow p = open.get(i);
            String id = p.proposalId().toString();
            int bx = ui.buttonsRight(RIGHT - 2, y - 2, 2, List.of(
                    new Btn("赞成", () -> a.simple(SimpleActionPacket.Action.VOTE_YES, id, 0, 0), null),
                    new Btn("反对", () -> a.simple(SimpleActionPacket.Action.VOTE_NO, id, 0, 0), null)));
            long votes = Math.max(1, p.yesVotes() + p.noVotes());
            String owner = info != null && info.companyId().equals(p.companyId()) ? "我的公司" : "持股公司";
            ui.text(LEFT + PAD, y, bx - LEFT - PAD - 6, owner + "「" + p.title() + "」赞成 " + (p.yesVotes() * 100 / votes)
                    + "%，第 " + p.endMcDay() + " 天截止", TEXT);
            y += 15;
        }
        return y + 2;
    }

    static int takeovers(UiBuilder ui, Actions a, SimpleBossService.BossInfo b, int y) {
        ui.text(LEFT + 2, y, WIDTH - 4, "收购别家公司（按现价高 10% 出价，想买到 51%）", DIM);
        y += 12;
        if (b.targets().isEmpty()) {
            ui.text(LEFT + PAD, y, WIDTH - PAD, "还没有别的公司上市。", DIM);
            y += 13;
        }
        for (int i = 0; i < Math.min(3, b.targets().size()); i++) {
            SimpleBossService.Target t = b.targets().get(i);
            String id = t.companyId();
            int bx = t.squeezable()
                    ? ui.buttonRight(RIGHT - 2, y - 2, "买下剩余股份", () -> a.simple(SimpleActionPacket.Action.SQUEEZE, id, 0, 0), null)
                    : ui.buttonRight(RIGHT - 2, y - 2, "出价收购", () -> a.simple(SimpleActionPacket.Action.TAKEOVER, id, 0, 0), null);
            ui.text(LEFT + PAD, y, bx - LEFT - PAD - 6, t.name() + "  每股 " + MoneyFormat.compact(t.sharePrice())
                    + "  我持有 " + (t.myPctX10() / 10.0) + "%", TEXT);
            y += 15;
        }
        return y + 2;
    }

    static void bank(UiBuilder ui, View v, Actions a, SimpleBossService.BossInfo b, int y) {
        if (y + 13 <= BOTTOM) {
            if (b.ownsBank()) {
                ui.text(LEFT + 2, y + 2, WIDTH - 4, "你已经有自己的银行了。", DIM);
            } else {
                long need = b.bankMinCapital() + b.bankCharterFee();
                int bx = ui.button(LEFT + 2, y, "开一家银行（资本 " + MoneyFormat.compact(b.bankMinCapital()) + " + 牌照 "
                                + MoneyFormat.compact(b.bankCharterFee()) + "）",
                        () -> a.simple(SimpleActionPacket.Action.CHARTER_BANK, "", b.bankMinCapital(), 0),
                        v.balance() >= need ? null : "要 " + MoneyFormat.pounds(need));
                ui.text(LEFT + 2 + bx + 6, y + 3, RIGHT - LEFT - bx - 10, "别人存钱给你，你放贷赚利差", DIM);
            }
        }
    }

    static void insuranceTab(UiBuilder ui, View v, Actions a, int y) {
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "给公司上保险：出事时保险公司按保额赔钱。每次保 7 天，到期再续。", DIM, 2) + 6;
        List<SimpleBossService.Insurance> list = boss(v).insurance();
        if (list.isEmpty()) ui.text(LEFT + PAD, y, WIDTH, "正在读取……", DIM);
        for (SimpleBossService.Insurance i : list) {
            ui.card(LEFT, y, WIDTH, 36, CARD, CARD_BORDER);
            String product = i.product();
            int bx;
            if (i.active()) {
                bx = ui.textRight(RIGHT - PAD, y + 13, 120, "已在保，第 " + i.expiryDay() + " 天到期", FinanceGui.COL_GOOD) - 6;
            } else {
                bx = ui.buttonRight(RIGHT - PAD, y + 11, "投保", () -> a.simple(SimpleActionPacket.Action.INSURE, product, 0, 0),
                        i.premium() < 0 ? "公司还没有足够的经营记录" : v.balance() < i.premium() ? "保费要 " + MoneyFormat.pounds(i.premium()) : null) - 6;
            }
            ui.text(LEFT + PAD, y + 5, bx - LEFT - PAD, i.name(), TEXT);
            ui.text(LEFT + PAD, y + 19, bx - LEFT - PAD, "最多赔 " + MoneyFormat.pounds(i.coverage())
                    + (i.premium() >= 0 ? "，保费 " + MoneyFormat.pounds(i.premium()) : ""), DIM);
            y += 40;
        }
    }

    /** 经营页最下面：工人日工资（宏观经济开启时）。 */
    static void wageRow(UiBuilder ui, View v, Actions a, int y) {
        SimpleBossService.BossInfo b = boss(v);
        if (!b.wageActive()) return;
        long wage = b.wage(), step = Math.max(1, wage / 10);
        int bx = ui.buttonsRight(RIGHT - 2, y, 2, List.of(
                new Btn("-", () -> a.simple(SimpleActionPacket.Action.SET_WAGE, "", Math.max(0, wage - step), 0), wage <= 0 ? "已经是 0" : null),
                new Btn("+", () -> a.simple(SimpleActionPacket.Action.SET_WAGE, "", wage + step, 0), null)));
        ui.text(LEFT + 2, y + 3, bx - LEFT - 8, "工人日工资 " + MoneyFormat.pounds(wage) + "（低于 "
                + MoneyFormat.pounds(b.reservationWage()) + " 招不到人）", wage < b.reservationWage() ? FinanceGui.COL_BAD : TEXT);
    }

    // ---------------------------------------------------------------- 现金

    static void cashTab(UiBuilder ui, View v, Actions a, int y) {
        var c = v.status() == null ? null : v.status().cash();
        if (c == null || !c.enabled()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "服务器没有开启实体现金。", DIM);
            return;
        }
        y += ui.wrapped(LEFT + 2, y, WIDTH - 4, "现金是央行发行的便士铜币、10 便士硬币和 1 磅纸币，和账户里的钱 1:1 互换。绿宝石按市场价换，价格每天在变。", DIM, 2) + 6;
        ui.card(LEFT, y, WIDTH, 38, CARD, CARD_BORDER);
        ui.text(LEFT + PAD, y + 5, WIDTH - 2 * PAD, "账户 " + MoneyFormat.pounds(c.balance()) + "    身上现金 " + MoneyFormat.pounds(c.cashOnHand()), TEXT);
        ui.buttonFlow(LEFT + PAD, y + 20, WIDTH - 2 * PAD, 3, List.of(
                new Btn("身上现金全部存入", () -> a.simple(SimpleActionPacket.Action.CASH_DEPOSIT, "", c.cashOnHand(), 0),
                        c.cashOnHand() > 0 ? null : "身上没有现金"),
                new Btn("取出 1 磅", () -> a.simple(SimpleActionPacket.Action.CASH_WITHDRAW, "", 100, 0), c.balance() >= 100 ? null : "账户不到 1 磅"),
                new Btn("取出 10 磅", () -> a.simple(SimpleActionPacket.Action.CASH_WITHDRAW, "", 1_000, 0), c.balance() >= 1_000 ? null : "账户不到 10 磅")));
        y += 44;
        ui.card(LEFT, y, WIDTH, 50, CARD, CARD_BORDER);
        ui.text(LEFT + PAD, y + 5, WIDTH - 2 * PAD, "绿宝石 " + c.emeralds() + " 颗：现在卖 1 颗得 " + MoneyFormat.pounds(c.emeraldToCash())
                + "，买 1 颗付 " + MoneyFormat.pounds(c.cashToEmerald()), TEXT);
        ui.text(LEFT + PAD, y + 17, WIDTH - 2 * PAD, "今天还能卖 " + c.sellLeft() + " 颗、买 " + c.buyLeft() + " 颗（现金从背包里扣、放进背包）", DIM);
        ui.buttonFlow(LEFT + PAD, y + 31, WIDTH - 2 * PAD, 3, List.of(
                new Btn("卖 1 颗", () -> a.simple(SimpleActionPacket.Action.EMERALD_SELL, "", 1, 0), c.emeralds() > 0 && c.sellLeft() > 0 ? null : "没有可卖的绿宝石"),
                new Btn("卖 16 颗", () -> a.simple(SimpleActionPacket.Action.EMERALD_SELL, "", Math.min(16, c.emeralds()), 0),
                        c.emeralds() > 0 && c.sellLeft() > 0 ? null : "没有可卖的绿宝石"),
                new Btn("买 1 颗", () -> a.simple(SimpleActionPacket.Action.EMERALD_BUY, "", 1, 0),
                        c.cashOnHand() >= c.cashToEmerald() && c.buyLeft() > 0 ? null : "身上现金不够或今天买满了")));
    }

    static void newCompany(UiBuilder ui, View v, State s, Actions a) {
        int y = TOP + 4;
        y += ui.wrapped(LEFT + 4, y, WIDTH - 8, "开一家公司：它每天自己生产、自己卖货，赚的钱可以转进你的钱包。", TEXT, 2) + 10;
        int labelX = LEFT + 4, fieldX = LEFT + 50;
        ui.text(labelX, y + 3, fieldX - labelX - 4, "行业", DIM);
        int bw = ui.button(fieldX, y, "< " + s.newCompanyType.getDisplayName() + " >", () -> {
            CompanyType[] types = CompanyType.values();
            s.newCompanyType = types[(s.newCompanyType.ordinal() + 1) % types.length];
        }, null);
        ui.text(fieldX + bw + 8, y + 3, RIGHT - fieldX - bw - 8, FinanceFormat.getTypeDescription(s.newCompanyType), DIM);
        y += 22;
        ui.text(labelX, y + 4, fieldX - labelX - 4, "名字", DIM);
        ui.input("companyName", fieldX, y, 190);
        y += 24;
        boolean enough = v.balance() >= COMPANY_COST;
        ui.button(fieldX, y, "开公司（10 磅，全部作为公司的启动资金）", () -> a.createCompany(s.newCompanyType),
                enough ? null : "还差 " + MoneyFormat.pounds(COMPANY_COST - v.balance()) + "，先去“买卖”页卖点东西");
        if (!enough) {
            ui.text(fieldX, y + 18, RIGHT - fieldX, "还差 " + MoneyFormat.pounds(COMPANY_COST - v.balance()) + "，先去“买卖”页卖点东西。",
                    FinanceGui.COL_WARN);
        }
    }

    // ================================================================ 投资

    static void invest(UiBuilder ui, View v, State s, Actions a) {
        int y = subTabs(ui, TOP, List.of("股票", "存钱", "我的存款", "现金"), s.investTab, i -> { s.investTab = i; s.selectedStock = null; });
        switch (s.investTab) {
            case 1 -> products(ui, v, s, a, y);
            case 2 -> myProducts(ui, v, a, y);
            case 3 -> cashTab(ui, v, a, y);
            default -> {
                FinanceMenu.StockRow selected = s.selectedStock == null ? null
                        : v.stocks().stream().filter(r -> r.symbol().equals(s.selectedStock)).findFirst().orElse(null);
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
        int buttonsX = ui.buttonsRightStart(RIGHT, gap, List.of("买10股", "全卖"));
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
            String arrow = row.mmHalted() ? " 熔断" : row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            ui.text(priceX, y + 2, 48, MoneyFormat.compact(row.lastPrice()) + arrow, row.mmHalted() ? FinanceGui.COL_BAD : FinanceGui.changeColor(row.dayChange()));
            ui.text(divX, y + 2, 60, MoneyFormat.compact(row.expectedDividendPerShare() * 100), TEXT);
            ui.text(holdX, y + 2, 56, have > 0 ? have + " 股" : "-", have > 0 ? TEXT : DIM);
            String buyBlock = row.mmHalted() ? HALTED : locked ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁"
                    : v.balance() < row.lastPrice() * 10 ? "钱不够，10 股约 " + MoneyFormat.pounds(row.lastPrice() * 10) : null;
            ui.buttonsRight(RIGHT, y, gap, List.of(
                    new Btn("买10股", () -> a.stock(true, symbol, 10), buyBlock),
                    new Btn("全卖", () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                            have > 0 ? null : "你没有这只股票")));
            y += ROW;
        }
        if (s.investScroll + INVEST_ROWS < stocks.size()) ui.textRight(RIGHT, BOTTOM - 40, 100, "滚轮看更多", DIM);
        marginFooter(ui, v, a, locked);
    }

    /** 股票页底部：加倍买 / 借股卖出的账户情况；没有时显示解锁提示或玩法说明。 */
    static void marginFooter(UiBuilder ui, View v, Actions a, boolean locked) {
        int cardY = BOTTOM - 28;
        ui.card(LEFT, cardY, WIDTH, 28, CARD, CARD_BORDER);
        SimpleInvestService.MarginInfo m = v.status() == null ? SimpleInvestService.MarginInfo.EMPTY : v.status().margin();
        boolean active = m.collateral() > 0 || m.debt() > 0 || !m.levered().isEmpty() || !m.shorts().isEmpty();
        if (!active) {
            ui.wrapped(LEFT + PAD, cardY + 4, WIDTH - 2 * PAD, locked
                    ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后才能买股票（还差 " + MoneyFormat.pounds(unlock(v) - v.balance()) + "）。理财随时可以买。"
                    : "买股票：涨了卖掉赚差价，拿着每周有分红。点公司名字看走势、设自动卖出。", DIM, 2);
            return;
        }
        int bx = ui.buttonsRight(RIGHT - PAD, cardY + 8, 2, List.of(
                new Btn("还清借款", a::leverRepay, m.debt() > 0 ? (v.balance() >= m.debt() ? null : "钱包不够还 " + MoneyFormat.pounds(m.debt())) : "没有借款"),
                new Btn("取回保证金", a::withdrawCollateral, m.collateral() > 0 ? null : "没有保证金")));
        String safety = m.safetyPct() < 0 ? "安全" : "安全度 " + m.safetyPct() + "%（低于 " + m.liquidationPct() + "% 会被强制卖出）";
        ui.text(LEFT + PAD, cardY + 4, bx - LEFT - PAD - 6, "保证金 " + MoneyFormat.compact(m.collateral()) + "  借款 " + MoneyFormat.compact(m.debt()), TEXT);
        ui.text(LEFT + PAD, cardY + 15, bx - LEFT - PAD - 6, safety,
                m.safetyPct() >= 0 && m.safetyPct() < m.liquidationPct() + 20 ? FinanceGui.COL_BAD : DIM);
    }

    static void stockDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.StockRow row, int y) {
        String symbol = row.symbol();
        long have = holding(v, symbol);
        long price = Math.max(1, row.lastPrice());
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedStock = null, null);
        String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
        int hx = ui.textRight(RIGHT - 2, y + 2, 140, "我持有 " + have + " 股", TEXT);
        ui.text(LEFT + bw + 10, y + 2, hx - LEFT - bw - 18, row.name() + "  " + MoneyFormat.compact(price) + arrow
                + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()), FinanceGui.changeColor(row.dayChange()));
        y += 16;
        priceChart(ui, s, y, 42, row.priceHistory(), row.hourBars(), FinanceGui.changeColor(row.dayChange()));
        y += 46;
        ui.text(LEFT + 2, y, WIDTH - 4, "每股分红约 " + MoneyFormat.pounds(row.expectedDividendPerShare()) + "（上次 "
                + MoneyFormat.pounds(row.lastDividendPerShare()) + "），每周发一次", DIM);
        y += 13;

        // 买卖
        boolean locked = stocksLocked(v);
        String lockReason = row.mmHalted() ? HALTED : locked ? "钱包满 " + MoneyFormat.pounds(unlock(v)) + " 后解锁" : null;
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 40, "买卖", DIM) + 8;
        ui.buttonFlow(lx, y, RIGHT - lx, 3, List.of(
                new Btn("买10股", () -> a.stock(true, symbol, 10), lockReason != null ? lockReason : v.balance() < price * 10 ? "钱不够" : null),
                new Btn("买100股", () -> a.stock(true, symbol, 100), lockReason != null ? lockReason : v.balance() < price * 100 ? "钱不够" : null),
                new Btn("全卖", () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                        row.mmHalted() ? HALTED : have > 0 ? null : "你没有这只股票")));
        y += 17;

        // 自动卖出
        long up = price + Math.max(1, price / 10), down = Math.max(1, price - Math.max(1, price / 10));
        lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 50, "自动卖出", DIM) + 8;
        String noHold = have > 0 ? null : "先买入才能设自动卖出";
        ui.buttonFlow(lx, y, RIGHT - lx, 3, List.of(
                new Btn("涨到 " + MoneyFormat.compact(up) + " 全卖", () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_UP, symbol, have, up), noHold),
                new Btn("跌到 " + MoneyFormat.compact(down) + " 全卖", () -> a.simple(SimpleActionPacket.Action.AUTO_SELL_DOWN, symbol, have, down), noHold)));
        y += 16;
        List<FinanceMenu.ConditionalStockOrderRow> autos = v.autoOrders().stream().filter(o -> o.symbol().equals(symbol)).toList();
        for (int i = 0; i < Math.min(2, autos.size()); i++) {
            FinanceMenu.ConditionalStockOrderRow o = autos.get(i);
            int cx = ui.buttonRight(RIGHT - 2, y - 2, "取消", () -> a.simple(SimpleActionPacket.Action.CANCEL_AUTO, o.orderId().toString(), 0, 0), null);
            ui.text(LEFT + 12, y, cx - LEFT - 18, ("TAKE_PROFIT".equals(o.type()) ? "涨到 " : "跌到 ") + MoneyFormat.pounds(o.triggerPrice())
                    + " 时卖出 " + o.quantity() + " 股", TEXT);
            y += 13;
        }
        if (autos.size() > 2) {
            ui.text(LEFT + 12, y, 200, "还有 " + (autos.size() - 2) + " 条", DIM);
            y += 11;
        }
        y += 3;

        // 进阶（服务器打开 margin，或者已经有仓位要平）
        SimpleInvestService.MarginInfo m = v.status() == null ? SimpleInvestService.MarginInfo.EMPTY : v.status().margin();
        long shorted = m.shorts().getOrDefault(symbol, 0L), levered = m.levered().getOrDefault(symbol, 0L);
        if (!extra(v, "margin") && shorted == 0 && levered == 0) {
            ui.wrapped(LEFT + 2, y, WIDTH - 4, valuationText(v, row), DIM, Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
            return;
        }
        lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 40, "进阶", DIM) + 8;
        List<Btn> advanced = new ArrayList<>();
        if (extra(v, "margin")) advanced.add(new Btn("加倍买10股", () -> a.simple(SimpleActionPacket.Action.LEVER_BUY, symbol, 10, 0),
                lockReason != null ? lockReason : v.balance() < (price * 10 + 1) / 2 ? "首付要 " + MoneyFormat.pounds((price * 10 + 1) / 2) : null));
        if (extra(v, "margin")) advanced.add(new Btn("借股卖出10股", () -> a.simple(SimpleActionPacket.Action.SHORT_SELL, symbol, 10, 0),
                lockReason != null ? lockReason : v.balance() < (price * 10 + 1) / 2 ? "要押 " + MoneyFormat.pounds((price * 10 + 1) / 2) : null));
        if (shorted > 0) advanced.add(new Btn("买回还券", () -> a.simple(SimpleActionPacket.Action.SHORT_COVER, symbol, 0, 0), null));
        y += ui.buttonFlow(lx, y, RIGHT - lx, 3, advanced) + 4;
        String held = (levered > 0 ? "加倍买了 " + levered + " 股。" : "") + (shorted > 0 ? "借股卖出了 " + shorted + " 股。" : "");
        ui.wrapped(LEFT + 2, y, WIDTH - 4, held + "加倍买：你出一半钱、券商借一半，涨了赚双倍，跌太多会被强制卖出。借股卖出：赌它会跌，涨了就亏。",
                DIM, Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
    }

    /** “股价怎么来的”：家底 + 日利润 × 倍数，÷ 股数；现价比每股价值贵还是便宜。 */
    static String valuationText(View v, FinanceMenu.StockRow row) {
        SimpleCenterService.Valuation val = v.status() == null ? null
                : v.status().valuations().stream().filter(x -> x.symbol().equals(row.symbol())).findFirst().orElse(null);
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

    /** 价格图：右边两个小按钮切换“走势”（每次价格变动一个点）和“K线”（每游戏小时一根）。 */
    static void priceChart(UiBuilder ui, State s, int y, int h, List<Long> line, List<Long> bars, int color) {
        int bw = Math.max(ui.buttonWidth("走势"), ui.buttonWidth("K线"));
        int chartW = WIDTH - 4 - bw - 4;
        if (s.candles) ui.candles(LEFT + 2, y, chartW, h, bars);
        else ui.chart(LEFT + 2, y, chartW, h, line, color);
        int bx = LEFT + 2 + chartW + 4;
        ui.button(bx, y, "走势", () -> s.candles = false, s.candles ? null : "正在看走势：每次价格变动一个点");
        ui.button(bx, y + UiBuilder.BUTTON_H + 3, "K线", () -> s.candles = true, s.candles ? "正在看 K 线：每游戏小时一根" : null);
    }

    static void commodityDetail(UiBuilder ui, View v, State s, Actions a, FinanceMenu.MarketRow row, int y) {
        String id = row.commodityId();
        String name = FinanceFormat.commodityDisplayName(id);
        int have = v.backpack().getOrDefault(id, 0);
        int bw = ui.button(LEFT + 2, y - 1, "< 返回", () -> s.selectedCommodity = null, null);
        int sx = ui.textRight(RIGHT - 2, y + 2, 150, "市场还有 " + row.marketStock() + " 个", row.marketStock() > 0 ? TEXT : FinanceGui.COL_BAD);
        ui.icon(LEFT + bw + 10, y + 1, id);
        ui.text(LEFT + bw + 24, y + 2, sx - LEFT - bw - 30, name + String.format(java.util.Locale.ROOT, "（今天 %+.1f%%）", row.dayChange()),
                FinanceGui.changeColor(row.dayChange()));
        y += 16;
        priceChart(ui, s, y, 70, row.priceHistory(), row.hourBars(), FinanceGui.changeColor(row.dayChange()));
        y += 74;
        ui.text(LEFT + 2, y, WIDTH - 4, "今天最高 " + MoneyFormat.compact(row.dayHigh()) + "  最低 " + MoneyFormat.compact(row.dayLow())
                + "  成交 " + row.dayVolume() + " 个", DIM);
        y += 14;

        String noItem = have > 0 ? null : "背包里没有" + name;
        int lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 150, "你卖得 " + MoneyFormat.compact(row.bidPrice()) + "/个", TEXT) + 8;
        ui.buttonFlow(lx, y, RIGHT - lx, 3, List.of(
                new Btn("卖1组", () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, Math.min(64, have)), noItem),
                new Btn("全卖（" + have + "）", () -> a.simple(SimpleActionPacket.Action.QUICK_SELL, id, have), noItem)));
        y += 17;
        String poor = v.balance() < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
        String empty = row.marketStock() <= 0 ? "市场上没货了，等别人卖或央行补货" : null;
        lx = LEFT + 2 + ui.text(LEFT + 2, y + 3, 150, "你买付 " + MoneyFormat.compact(row.askPrice()) + "/个", TEXT) + 8;
        ui.buttonFlow(lx, y, RIGHT - lx, 3, List.of(
                new Btn("买1", () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 1), empty != null ? empty : poor),
                new Btn("买16", () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 16), empty != null ? empty : poor),
                new Btn("买1组", () -> a.simple(SimpleActionPacket.Action.QUICK_BUY, id, 64), empty != null ? empty : poor)));
        y += 19;
        Commodity c = CommodityRegistry.getCommodity(id);
        int tier = c == null ? -1 : c.getReserveTier();
        String source = tier == 0 ? "来源：玩家卖出。央行不供应这种东西，卖完就没了。"
                : tier > 0 ? "来源：央行储备第 " + tier + " 档，每天只补少量货。" : "";
        ui.wrapped(LEFT + 2, y, WIDTH - 4, source + "价格跟着市场库存走：大家抢着买就涨，卖的人多就跌。",
                DIM, Math.max(1, (BOTTOM - y) / UiBuilder.LINE));
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
        for (long amount : AMOUNTS) amounts.add(new Btn(MoneyFormat.compact(amount), () -> s.productAmount = amount, null));
        amounts.add(new Btn("钱包全部", () -> s.productAmount = Math.max(1, v.balance()), v.balance() > 0 ? null : "钱包是空的"));
        ui.buttonFlow(lx, y, RIGHT - lx, 2, amounts);
        y += 17;

        int buyX = RIGHT - ui.buttonWidth("买入");
        int riskX = buyX - 6 - 24, rateX = riskX - 6 - 56, termX = rateX - 6 - 56, nameX = LEFT + 2;
        ui.text(nameX, y, termX - nameX - 4, "产品", DIM);
        ui.text(termX, y, 56, "期限", DIM);
        ui.text(rateX, y, 56, "年收益", DIM);
        ui.text(riskX, y, 24, "风险", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);
        y += 15;
        List<SimpleInvestService.Product> products = st == null ? List.of() : st.products();
        if (products.isEmpty()) ui.text(nameX, y + 2, 200, st == null ? "正在读取……" : "现在没有银行营业。", DIM);
        s.productScroll = clamp(s.productScroll, 0, Math.max(0, products.size() - PRODUCT_ROWS));
        long amount = s.productAmount;
        for (int i = s.productScroll; i < Math.min(products.size(), s.productScroll + PRODUCT_ROWS); i++) {
            SimpleInvestService.Product p = products.get(i);
            if ((i - s.productScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            ui.text(nameX, y + 2, termX - nameX - 4, p.name(), TEXT);
            ui.text(termX, y + 2, 56, p.term(), DIM);
            ui.text(rateX, y + 2, 56, p.rateBps() < 0 ? "随行情" : String.format(java.util.Locale.ROOT, "%.2f%%", p.rateBps() / 100.0), TEXT);
            int riskColor = "低".equals(p.risk()) ? FinanceGui.COL_GOOD : "中".equals(p.risk()) ? FinanceGui.COL_WARN : FinanceGui.COL_BAD;
            ui.text(riskX, y + 2, 24, p.risk(), riskColor);
            String block = amount < p.minAmount() ? "最少 " + MoneyFormat.pounds(p.minAmount())
                    : v.balance() < amount ? "钱包不到 " + MoneyFormat.pounds(amount) : null;
            String key = p.key();
            ui.button(buyX, y, "买入", () -> a.simple(SimpleActionPacket.Action.BUY_PRODUCT, key, amount, 0), block);
            y += ROW;
        }
        if (s.productScroll + PRODUCT_ROWS < products.size()) ui.textRight(RIGHT, BOTTOM - 9, 100, "滚轮看更多", DIM);
        ui.text(LEFT + 2, BOTTOM - 9, WIDTH - 110, extra(v, "fund") ? "存款保本；基金随行情涨跌，可能亏。"
                : "存款保本。央行加息时存款利息跟着涨。", DIM);
    }

    static void myProducts(UiBuilder ui, View v, Actions a, int y) {
        SimpleCenterService.Status st = v.status();
        List<SimpleInvestService.Holding> holdings = st == null ? List.of() : st.holdings();
        if (holdings.isEmpty()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "还没有存款。去“存钱”里挑一个。", DIM);
            return;
        }
        for (SimpleInvestService.Holding h : holdings) {
            if (y + 24 > BOTTOM) {
                ui.text(LEFT + PAD, y, WIDTH, "还有更多……", DIM);
                break;
            }
            String key = h.key();
            int bx = ui.buttonRight(RIGHT - 2, y + 4, h.redeemLabel(), () -> a.simple(SimpleActionPacket.Action.REDEEM_PRODUCT, key, 0, 0), null);
            ui.text(LEFT + 2, y, bx - LEFT - 8, h.name() + "  " + MoneyFormat.pounds(h.amount()), TEXT);
            ui.text(LEFT + 2, y + 11, bx - LEFT - 8, h.status(), DIM);
            y += 24;
        }
    }

    // ================================================================ 小工具

    /** 服务器给普通玩家打开的进阶玩法（配置 simpleCenter.extras）。 */
    static boolean extra(View v, String name) {
        if (v.status() == null) return false;
        List<String> extras = v.status().extras();
        return extras.contains("all") || extras.contains(name);
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
        for (CompanyType t : CompanyType.values()) if (t.getDisplayName().equals(displayName) || t.name().equals(displayName)) return t;
        return null;
    }

    /** 交易记录里的对象名可能是商品 ID，能翻成中文名就翻。 */
    static String displayObject(String name) {
        Commodity c = CommodityRegistry.getCommodity(name);
        return c == null ? name : c.getDisplayName();
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
