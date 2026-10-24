package finance.client;

import static finance.client.SimplePages.*;

import finance.client.ui.UiBuilder;
import finance.client.ui.UiBuilder.Btn;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.CompanyRows;
import finance.network.SimpleActionPacket;
import finance.simple.SimpleBossService;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleOpportunityService;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import java.util.List;

/** 简单模式“我的公司”页：开公司、经营、当老板、现金。排版规则见 {@link SimplePages}。 */
final class SimpleCompanyPage {

    private SimpleCompanyPage() {}

    static void company(UiBuilder ui, View v, State s, Actions a) {
        CompanyRows.CompanyInfo info = v.company();
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
        int level = st == null ? info.storageLevel() + info.managementLevel() : st.factoryLevel();
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
        ui.text(LEFT + PAD, y + 25, textW, "升级：仓储费、机器维护费更省，招工更快。", DIM);

        // 上市
        y += 42;
        ui.card(LEFT, y, WIDTH, 48, CARD, CARD_BORDER);
        if (info.isPublic()) {
            ui.text(LEFT + PAD, y + 4, WIDTH - 2 * PAD, "公司已上市。", FinanceGui.COL_GOOD);
            ui.wrapped(LEFT + PAD, y + 17, WIDTH - 2 * PAD, "别人买你的股票时公司更值钱；你是大股东，每周也拿分红。", DIM, 2);
        } else if (st != null && st.ipo().open()) {
            SimpleCenterService.IpoProgress ipo = st.ipo();
            ui.text(
                    LEFT + PAD,
                    y + 4,
                    WIDTH - 2 * PAD,
                    "上市认购中：已认 " + ipo.subscribed() + " / " + ipo.quantity() + " 股，第 " + ipo.endDay() + " 天结算",
                    TEXT);
            ui.progress(LEFT + PAD, y + 16, WIDTH - 2 * PAD, ipo.subscribed() / (double) Math.max(1, ipo.quantity()));
            ui.wrapped(
                    LEFT + PAD,
                    y + 25,
                    WIDTH - 2 * PAD,
                    "投资者每天按估值认购，钱先放在托管里。认满 " + ipo.minimum() + " 股就上市、钱进公司；不够就取消，钱退回给认购的人。",
                    DIM,
                    2);
        } else {
            long valuation = valuation(v);
            long min = st == null ? 20_000 : st.ipoMinValuation();
            bx = ui.buttonRight(
                    RIGHT - PAD,
                    y + 14,
                    "发起上市认购",
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
            ui.wrapped(LEFT + PAD, y + 25, textW, "拿出 40% 的股份让投资者认购几天，认够了才上市、钱进公司。要付上市费，发行价按估值自动定。", DIM, 2);
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
        CompanyRows.CompanyInfo info = v.company();
        if (extra(v, "wage") && b.wageActive()) {
            wageRow(ui, v, a, y);
            y += 18;
        }
        int bottomRows = (extra(v, "insurance") ? 13 : 0) + (extra(v, "shareholder") && b.publicCompany() ? 17 : 0);
        int limit = BOTTOM - bottomRows;

        // 待投票的提案
        if (extra(v, "shareholder")) {
            List<CompanyRows.CompanyProposalRow> open = v.proposals().stream()
                    .filter(p -> "ACTIVE".equals(p.status()) && !p.playerVoted())
                    .toList();
            for (int i = 0; i < Math.min(2, open.size()) && y + 15 <= limit; i++) {
                CompanyRows.CompanyProposalRow p = open.get(i);
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
}
