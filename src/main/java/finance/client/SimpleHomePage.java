package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.gui.RecordRows;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleOpportunityService;
import finance.util.MoneyFormat;
import java.util.List;

/** 简单模式“首页”：今天该做什么、目标进度、钱包和身家走势、排行榜、最近记录。排版规则见 {@link SimplePages}。 */
final class SimpleHomePage {

    private SimpleHomePage() {}

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
        List<RecordRows.TransactionRow> rows = v.transactions();
        s.recordScroll = clamp(s.recordScroll, 0, Math.max(0, rows.size() - RECORD_ROWS));
        if (rows.isEmpty()) ui.text(LEFT + PAD, y + 2, 200, "还没有交易。", DIM);
        int amountW = 96, typeW = 70;
        for (int i = s.recordScroll; i < Math.min(rows.size(), s.recordScroll + RECORD_ROWS); i++) {
            RecordRows.TransactionRow r = rows.get(i);
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
}
