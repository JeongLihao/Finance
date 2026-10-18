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
                finance.network.FuturesResponsePacket futures) {}

    /** 页面上的状态（当前页、滚动位置、新公司行业）。 */
    static final class State {
        Page page = Page.HOME;
        int tradeScroll, investScroll, recordScroll;
        /** 各页的小标签：首页 0 今天 / 1 经济天气 / 2 全部记录；买卖 0 马上买卖 / 1 挂单 / 2 锁价合同。 */
        int homeTab, tradeTab;
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
    }

    static final int W = FinanceGui.PANEL_WIDTH, H = FinanceGui.PANEL_HEIGHT;
    static final int LEFT = 10, RIGHT = W - 10, WIDTH = RIGHT - LEFT;
    static final int TOP = FinanceGui.HEADER_H + 4, BOTTOM = H - 4;
    static final int PAD = 6;
    static final long COMPANY_COST = 1_000L;
    static final int TRADE_ROWS = 9, INVEST_ROWS = 7, ROW = 16;
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
            y += ui.wrapped(LEFT + PAD, y, WIDTH - 2 * PAD, line, TEXT, 3) + 8;
        }
        ui.wrapped(LEFT + PAD, Math.max(y, BOTTOM - 22), WIDTH - 2 * PAD,
                "利率和物价由央行每天自动调整。利率高时适合存钱，利率低时适合借钱扩大公司。", DIM, 2);
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
        if (v.playerChoosesAdvanced()) {
            boolean on = v.wantsAdvanced();
            String label = (on ? "[√]" : "[ ]") + " 显示高级功能";
            int tx = ui.textRight(RIGHT - 2, y, WIDTH - labelW - 12, label, DIM);
            ui.hotspot(tx, y - 2, RIGHT - 2 - tx, 12, () -> a.setWantsAdvanced(!on));
        }
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
        return new Goal("你已经是大亨了！", 1, Page.HOME);
    }

    // ================================================================ 买卖

    /** 背包里有的排前面（按能卖的钱从多到少），其余按名字。没有对应物品的商品不列。 */
    static List<FinanceMenu.MarketRow> tradeRows(View v) {
        Map<String, Integer> held = v.backpack();
        List<FinanceMenu.MarketRow> rows = new ArrayList<>();
        for (FinanceMenu.MarketRow row : v.market()) {
            Commodity c = CommodityRegistry.getCommodity(row.commodityId());
            if (c == null || c.getItemId() != null && !c.getItemId().isEmpty()) rows.add(row);
        }
        rows.sort(Comparator.<FinanceMenu.MarketRow>comparingLong(r -> -(long) held.getOrDefault(r.commodityId(), 0) * r.bidPrice())
                .thenComparing(r -> FinanceFormat.commodityDisplayName(r.commodityId())));
        return rows;
    }

    static void tradePage(UiBuilder ui, View v, State s, Actions a) {
        int y = subTabs(ui, TOP, List.of("马上买卖", "挂单", "锁价合同"), s.tradeTab, i -> s.tradeTab = i);
        switch (s.tradeTab) {
            case 1 -> orders(ui, v, s, a, y);
            case 2 -> locks(ui, v, a, y);
            default -> trade(ui, v, s, a, y);
        }
    }

    // ---------------------------------------------------------------- 挂单

    static void orders(UiBuilder ui, View v, State s, Actions a, int y) {
        List<FinanceMenu.MarketRow> rows = tradeRows(v);
        if (rows.isEmpty()) {
            ui.text(LEFT + PAD, y + 2, WIDTH, "市场还没有商品。", DIM);
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
        ui.text(nameX, y, haveX - nameX - 4, "东西", DIM);
        ui.text(haveX, y, 34, "背包", DIM);
        ui.text(bidX, y, 50, "卖出价", DIM);
        ui.text(askX, y, 50, "买入价", DIM);
        ui.fill(LEFT, y + 11, WIDTH, 1, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.MarketRow> rows = tradeRows(v);
        s.tradeScroll = clamp(s.tradeScroll, 0, Math.max(0, rows.size() - TRADE_ROWS));
        y += 15;
        for (int i = s.tradeScroll; i < Math.min(rows.size(), s.tradeScroll + TRADE_ROWS); i++) {
            FinanceMenu.MarketRow row = rows.get(i);
            String id = row.commodityId();
            String name = FinanceFormat.commodityDisplayName(id);
            int have = v.backpack().getOrDefault(id, 0);
            if ((i - s.tradeScroll) % 2 == 0) ui.fill(LEFT, y - 1, WIDTH, ROW - 1, CARD);
            ui.icon(LEFT + 2, y + 1, id);
            ui.text(nameX, y + 2, haveX - nameX - 4, name, TEXT);
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
        ui.text(LEFT + 2, TOP, WIDTH - 2, info.name() + " · " + info.type() + (info.isPublic() ? " · 已上市" : ""), TEXT);

        // 今天
        int y = TOP + 12;
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
        y += 58;
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
        y += 44;
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
        SimpleCenterService.Status st = v.status();
        long unlock = st == null ? 3_000 : st.stockUnlockBalance();
        boolean locked = v.balance() < unlock && v.holdings().isEmpty();
        ui.text(LEFT + 2, TOP, WIDTH - 2, locked
                        ? "钱包满 " + MoneyFormat.pounds(unlock) + " 后才能买股票（还差 " + MoneyFormat.pounds(unlock - v.balance()) + "）。银行存钱随时可以。"
                        : "买别人公司的股票：涨了卖掉赚差价，拿着每周还有分红。",
                locked ? FinanceGui.COL_WARN : DIM);

        int gap = 2;
        int buttonsX = ui.buttonsRightStart(RIGHT, gap, List.of("买10股", "全卖"));
        int holdX = buttonsX - 6 - 56;
        int divX = holdX - 6 - 60;
        int priceX = divX - 6 - 48;
        int nameX = LEFT + 4;
        int y = TOP + 14;
        ui.text(nameX, y, priceX - nameX - 4, "公司", DIM);
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
            ui.text(nameX, y + 2, priceX - nameX - 4, row.name(), TEXT);
            String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            ui.text(priceX, y + 2, 48, MoneyFormat.compact(row.lastPrice()) + arrow, FinanceGui.changeColor(row.dayChange()));
            ui.text(divX, y + 2, 60, MoneyFormat.compact(row.expectedDividendPerShare() * 100), TEXT);
            ui.text(holdX, y + 2, 56, have > 0 ? have + " 股" : "-", have > 0 ? TEXT : DIM);
            String buyBlock = locked ? "钱包满 " + MoneyFormat.pounds(unlock) + " 后解锁"
                    : v.balance() < row.lastPrice() * 10 ? "钱不够，10 股约 " + MoneyFormat.pounds(row.lastPrice() * 10) : null;
            String symbol = row.symbol();
            ui.buttonsRight(RIGHT, y, gap, List.of(
                    new Btn("买10股", () -> a.stock(true, symbol, 10), buyBlock),
                    new Btn("全卖", () -> a.stock(false, symbol, (int) Math.min(Integer.MAX_VALUE, have)),
                            have > 0 ? null : "你没有这只股票")));
            y += ROW;
        }

        int bankY = BOTTOM - 40;
        if (s.investScroll + INVEST_ROWS < stocks.size()) ui.textRight(RIGHT, bankY - 11, 100, "滚轮看更多", DIM);
        ui.card(LEFT, bankY, WIDTH, 40, CARD, CARD_BORDER);
        long bank = st == null ? 0 : st.bankBalance();
        int rate = st == null ? 0 : st.bankRateBps();
        int bx = ui.buttonsRight(RIGHT - PAD, bankY + 4, gap, List.of(
                new Btn("存10磅", () -> a.simple(SimpleActionPacket.Action.BANK_DEPOSIT, "", 1_000L),
                        v.balance() >= 1_000 ? null : "钱包不到 10 磅"),
                new Btn("全部存", () -> a.simple(SimpleActionPacket.Action.BANK_DEPOSIT, "", v.balance()),
                        v.balance() > 0 ? null : "钱包是空的"),
                new Btn("全部取", () -> a.simple(SimpleActionPacket.Action.BANK_WITHDRAW, "", bank),
                        bank > 0 ? null : "银行里没有存款")));
        int textW = bx - LEFT - PAD - 8;
        ui.text(LEFT + PAD, bankY + 6, textW, "银行存款 " + MoneyFormat.pounds(bank), TEXT);
        ui.wrapped(LEFT + PAD, bankY + 18, WIDTH - 2 * PAD, "年利率 " + String.format(java.util.Locale.ROOT, "%.2f%%", rate / 100.0)
                + "，按天计息，随时能取。最安全的投资。", DIM, 2);
    }

    // ================================================================ 小工具

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
