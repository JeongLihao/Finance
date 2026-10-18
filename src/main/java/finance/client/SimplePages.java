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
                boolean playerChoosesAdvanced, boolean wantsAdvanced) {}

    /** 页面上的状态（当前页、滚动位置、新公司行业）。 */
    static final class State {
        Page page = Page.HOME;
        int tradeScroll, investScroll;
        CompanyType newCompanyType = CompanyType.RAW_MATERIALS;
    }

    /** 按钮背后的动作。 */
    interface Actions {
        void simple(SimpleActionPacket.Action action, String target, long amount);
        void company(CompanyManagementAction action, long amount);
        void createCompany(CompanyType type);
        void stock(boolean buy, String symbol, int quantity);
        void status(String message);
        void show(Page page);
        void setWantsAdvanced(boolean on);
    }

    static final int W = FinanceGui.PANEL_WIDTH, H = FinanceGui.PANEL_HEIGHT;
    static final int LEFT = 10, RIGHT = W - 10, WIDTH = RIGHT - LEFT;
    static final int TOP = FinanceGui.HEADER_H + 4, BOTTOM = H - 4;
    static final int PAD = 6;
    static final long COMPANY_COST = 1_000L;
    static final int TRADE_ROWS = 10, INVEST_ROWS = 7, ROW = 16;
    static final int CARD = FinanceGui.COL_ROW_EVEN, CARD_BORDER = 0xFFB8B0A0;
    static final int TEXT = FinanceGui.COL_TEXT, DIM = FinanceGui.COL_TEXT_DIM;

    private SimplePages() {}

    static void layout(UiBuilder ui, View v, State s, Actions a) {
        tabs(ui, s, a);
        switch (s.page) {
            case HOME -> home(ui, v, a);
            case TRADE -> trade(ui, v, s, a);
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

    // ================================================================ 首页

    static void home(UiBuilder ui, View v, Actions a) {
        SimpleCenterService.Status st = v.status();
        int y = TOP;
        ui.card(LEFT, y, WIDTH, 42, CARD, CARD_BORDER);
        int rightX = 236;
        ui.text(LEFT + PAD, y + 4, 60, "钱包", DIM);
        ui.bigText(LEFT + PAD, y + 16, rightX - LEFT - PAD - 8, MoneyFormat.pounds(v.balance()), TEXT, 2.0F);
        int colW = RIGHT - PAD - rightX;
        ui.text(rightX, y + 5, colW, "今天 " + MoneyFormat.signed(v.todayProfit()), FinanceGui.changeColor(v.todayProfit()));
        ui.text(rightX, y + 17, colW, "银行 " + MoneyFormat.compact(st == null ? 0 : st.bankBalance())
                + "  股票 " + MoneyFormat.compact(v.stockValue()), DIM);
        if (ownsCompany(v)) ui.text(rightX, y + 29, colW, "公司估值 " + MoneyFormat.compact(valuation(v)), DIM);

        y += 48;
        ui.card(LEFT, y, WIDTH, 40, CARD, CARD_BORDER);
        Goal goal = goal(v);
        int buttonX = ui.buttonRight(RIGHT - PAD, y + 13, "去看看", () -> a.show(goal.page()), null);
        ui.text(LEFT + PAD, y + 4, 100, "下一个目标", DIM);
        ui.text(LEFT + PAD, y + 16, buttonX - LEFT - PAD - 8, goal.text(), TEXT);
        ui.progress(LEFT + PAD, y + 29, buttonX - LEFT - PAD - 8, goal.progress());

        y += 46;
        ui.text(LEFT + 2, y, 100, "今日机会", DIM);
        List<String> chances = st == null ? List.of("正在读取……") : st.opportunities();
        for (int i = 0; i < Math.min(3, chances.size()); i++) {
            ui.text(LEFT + PAD, y + 12 + i * UiBuilder.LINE, WIDTH - PAD, "· " + chances.get(i), TEXT);
        }

        y += 50;
        int labelW = ui.text(LEFT + 2, y, 60, "最近", DIM);
        if (v.playerChoosesAdvanced()) {
            boolean on = v.wantsAdvanced();
            String label = (on ? "[√]" : "[ ]") + " 显示高级功能";
            int tx = ui.textRight(RIGHT - 2, y, WIDTH - labelW - 12, label, DIM);
            ui.hotspot(tx, y - 2, RIGHT - 2 - tx, 12, () -> a.setWantsAdvanced(!on));
        }
        List<FinanceMenu.TransactionRow> rows = v.transactions();
        if (rows.isEmpty()) ui.text(LEFT + PAD, y + 12, 200, "还没有交易。", DIM);
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
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

    static void trade(UiBuilder ui, View v, State s, Actions a) {
        // 列从右往左按按钮实际宽度排，左边剩下的给商品名
        int gap = 2;
        int buyStart = ui.buttonsRightStart(RIGHT, gap, List.of("买1", "买1组"));
        int askX = buyStart - 6 - 50;
        int sellStart = ui.buttonsRightStart(askX - 6, gap, List.of("卖1组", "全卖"));
        int bidX = sellStart - 6 - 50;
        int haveX = bidX - 4 - 34;
        int nameX = LEFT + 16;

        int y = TOP + 2;
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
