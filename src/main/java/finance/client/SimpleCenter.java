package finance.client;

import finance.client.simple.SimpleClientState;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.CompanyManagePacket;
import finance.network.CreateCompanyPacket;
import finance.network.FinancePacketHandler;
import finance.network.SimpleActionPacket;
import finance.network.StockTradePacket;
import finance.simple.SimpleCenterService;
import finance.util.MoneyFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 简单模式金融中心：首页、买卖、我的公司、投资四页。
 * 坐标都是面板内坐标（renderLabels 的坐标系），点击区域在绘制时登记，点击时按上一帧的区域判断。
 */
final class SimpleCenter {

    enum Page {
        HOME("首页"), TRADE("买卖"), COMPANY("我的公司"), INVEST("投资");
        final String label;
        Page(String label) { this.label = label; }
    }

    static final int LEFT = 10, WIDTH = FinanceGui.PANEL_WIDTH - 20, TOP = FinanceGui.HEADER_H + 4;
    static final int BOTTOM = FinanceGui.PANEL_HEIGHT - 4;
    static final long COMPANY_COST = 1_000L;
    static final int TRADE_ROWS = 10, INVEST_ROWS = 7;

    private final FinanceScreen screen;
    Page page = Page.HOME;
    int tradeScroll, investScroll;
    CompanyType newCompanyType = CompanyType.RAW_MATERIALS;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    SimpleCenter(FinanceScreen screen) {
        this.screen = screen;
    }

    // ================================================================ 入口

    void render(GuiGraphics g, int mouseX, int mouseY) {
        hits.clear();
        renderTabs(g, mouseX, mouseY);
        switch (page) {
            case HOME -> renderHome(g);
            case TRADE -> renderTrade(g);
            case COMPANY -> renderCompany(g);
            case INVEST -> renderInvest(g);
        }
        renderFloaters(g);
    }

    boolean click(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    boolean scroll(double delta) {
        int step = (int) -Math.signum(delta);
        if (page == Page.TRADE) {
            tradeScroll = clamp(tradeScroll + step, 0, Math.max(0, tradeRows().size() - TRADE_ROWS));
            return true;
        }
        if (page == Page.INVEST) {
            investScroll = clamp(investScroll + step, 0, Math.max(0, menu().getStocks().size() - INVEST_ROWS));
            return true;
        }
        return false;
    }

    void show(Page target) {
        page = target;
        screen.updateInputVisibility();
    }

    /** 简单模式只用公司名输入框，其余输入框全部隐藏（由 FinanceScreen 在简单模式下调用）。 */
    void updateInputs(EditBox nameBox) {
        boolean naming = page == Page.COMPANY && !ownsCompany();
        nameBox.setVisible(naming);
        if (naming) {
            nameBox.setX(screen.leftPos() + 60);
            nameBox.setY(screen.topPos() + 92);
        }
    }

    List<EditBox> visibleBoxes(EditBox nameBox) {
        return page == Page.COMPANY && !ownsCompany() ? List.of(nameBox) : List.of();
    }

    // ================================================================ 页签

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        Page[] pages = Page.values();
        int w = (WIDTH - (pages.length - 1) * 4) / pages.length;
        int x = LEFT;
        for (Page p : pages) {
            boolean active = p == page;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= FinanceGui.TAB_Y && mouseY < FinanceGui.TAB_Y + FinanceGui.TAB_H;
            if (active || hovered) g.fill(x, FinanceGui.TAB_Y - 1, x + w, FinanceGui.TAB_Y + FinanceGui.TAB_H, 0xFFECE6D5);
            FinanceGui.drawCenteredClippedString(font(), g, p.label, x, FinanceGui.TAB_Y + 2, w,
                    active || hovered ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            if (active) g.fill(x + 3, FinanceGui.TAB_Y + FinanceGui.TAB_H - 1, x + w - 3, FinanceGui.TAB_Y + FinanceGui.TAB_H, FinanceGui.COL_ACCENT);
            hits.add(new Hit(x, FinanceGui.TAB_Y - 1, w, FinanceGui.TAB_H + 1, () -> show(p)));
            x += w + 4;
        }
    }

    // ================================================================ 首页

    private void renderHome(GuiGraphics g) {
        FinanceMenu menu = menu();
        SimpleCenterService.Status st = SimpleClientState.status();
        long balance = menu.getBalance();

        card(g, TOP, 40);
        text(g, "钱包", LEFT + 6, TOP + 4, 80, FinanceGui.COL_TEXT_DIM);
        g.pose().pushPose();
        g.pose().translate(LEFT + 6, TOP + 16, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font(), MoneyFormat.pounds(balance), 0, 0, FinanceGui.COL_TEXT, false);
        g.pose().popPose();
        FinanceMenu.AssetSummary assets = menu.getAssetSummary();
        long today = assets == null ? 0 : assets.todayProfit();
        text(g, "今天 " + MoneyFormat.signed(today), 236, TOP + 6, 150, FinanceGui.changeColor(today));
        long bank = st == null ? 0 : st.bankBalance();
        long company = st == null ? 0 : st.companyValuation();
        long stocks = assets == null ? 0 : assets.stockValue();
        text(g, "银行 " + MoneyFormat.compact(bank) + "  股票 " + MoneyFormat.compact(stocks), 236, TOP + 18, 150, FinanceGui.COL_TEXT_DIM);
        if (ownsCompany()) text(g, "公司估值 " + MoneyFormat.compact(company), 236, TOP + 30, 150, FinanceGui.COL_TEXT_DIM);

        int goalY = TOP + 46;
        card(g, goalY, 40);
        text(g, "下一个目标", LEFT + 6, goalY + 4, 100, FinanceGui.COL_TEXT_DIM);
        Goal goal = goal(balance, st);
        text(g, goal.text(), LEFT + 6, goalY + 16, 300, FinanceGui.COL_TEXT);
        progress(g, LEFT + 6, goalY + 30, 290, goal.progress());
        button(g, "去看看", LEFT + WIDTH - 60, goalY + 13, 52, () -> show(goal.page()), null);

        int chanceY = goalY + 46;
        text(g, "今日机会", LEFT + 2, chanceY, 100, FinanceGui.COL_TEXT_DIM);
        List<String> chances = st == null ? List.of("正在读取……") : st.opportunities();
        for (int i = 0; i < chances.size(); i++) {
            text(g, "· " + chances.get(i), LEFT + 6, chanceY + 12 + i * 11, WIDTH - 8, FinanceGui.COL_TEXT);
        }

        int recentY = chanceY + 50;
        text(g, "最近", LEFT + 2, recentY, 60, FinanceGui.COL_TEXT_DIM);
        if (SimpleClientState.playerChooses()) {
            boolean on = SimpleClientState.playerWantsAdvanced();
            String label = (on ? "[√]" : "[ ]") + " 显示高级功能";
            int w = font().width(label) + 4;
            text(g, label, LEFT + WIDTH - w, recentY, w, FinanceGui.COL_TEXT_DIM);
            hits.add(new Hit(LEFT + WIDTH - w, recentY - 2, w, 12, () -> {
                SimpleClientState.setPlayerWantsAdvanced(!on);
                screen.setStatus(on ? "已隐藏高级功能。" : "已显示高级功能：右上角“高级”里是挂单、期货、保险、治理等全部原有页面。");
            }));
        }
        List<FinanceMenu.TransactionRow> rows = menu.getTransactions();
        if (rows.isEmpty()) text(g, "还没有交易。", LEFT + 6, recentY + 12, 200, FinanceGui.COL_TEXT_DIM);
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            FinanceMenu.TransactionRow r = rows.get(i);
            String line = FinanceFormat.displayTransactionType(r.type())
                    + (r.objectName().isEmpty() ? "" : " " + displayObject(r.objectName()))
                    + (r.quantity() > 1 ? " ×" + r.quantity() : "") + "  " + MoneyFormat.pounds(r.amount());
            text(g, line, LEFT + 6, recentY + 12 + i * 11, WIDTH - 8, FinanceGui.COL_TEXT);
        }
    }

    record Goal(String text, double progress, Page page) {}

    /** 首页“下一个目标”：按 开公司 → 买股票 → 上市 → 升满工厂 的顺序找第一个还没做到的。 */
    Goal goal(long balance, SimpleCenterService.Status st) {
        if (!ownsCompany()) {
            if (balance < COMPANY_COST) {
                return new Goal("攒到 10 磅就能开公司（还差 " + MoneyFormat.pounds(COMPANY_COST - balance) + "）",
                        balance / (double) COMPANY_COST, Page.TRADE);
            }
            return new Goal("钱够了，去开一家公司吧！", 1, Page.COMPANY);
        }
        long unlock = st == null ? 3_000 : st.stockUnlockBalance();
        if (menu().getStockHoldings().isEmpty() && balance < unlock) {
            return new Goal("攒到 " + MoneyFormat.pounds(unlock) + " 就能买股票拿分红（还差 "
                    + MoneyFormat.pounds(unlock - balance) + "）", balance / (double) Math.max(1, unlock), Page.INVEST);
        }
        FinanceMenu.CompanyInfo info = menu().getPlayerCompany();
        if (!info.isPublic()) {
            long valuation = valuation(info, st);
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
        return new Goal("你已经是大亨了！打开“显示高级功能”试试更多玩法。", 1, Page.HOME);
    }

    // ================================================================ 买卖

    /** 背包里有的排前面（按能卖的钱从多到少），其余按名字。没有对应物品的商品不列。 */
    List<FinanceMenu.MarketRow> tradeRows() {
        Map<String, Integer> held = menu().getMcInventory();
        List<FinanceMenu.MarketRow> rows = new ArrayList<>();
        for (FinanceMenu.MarketRow row : menu().getMarketData()) {
            Commodity c = CommodityRegistry.getCommodity(row.commodityId());
            if (c != null && c.getItemId() != null && !c.getItemId().isEmpty()) rows.add(row);
        }
        rows.sort(Comparator.<FinanceMenu.MarketRow>comparingLong(r -> -(long) held.getOrDefault(r.commodityId(), 0) * r.bidPrice())
                .thenComparing(r -> FinanceFormat.commodityDisplayName(r.commodityId())));
        return rows;
    }

    private void renderTrade(GuiGraphics g) {
        int y0 = TOP + 2;
        text(g, "东西", LEFT + 16, y0, 60, FinanceGui.COL_TEXT_DIM);
        text(g, "背包", 92, y0, 34, FinanceGui.COL_TEXT_DIM);
        text(g, "卖出价", 126, y0, 48, FinanceGui.COL_TEXT_DIM);
        text(g, "买入价", 246, y0, 52, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawSimpleSeparator(g, LEFT, y0 + 11, WIDTH);

        List<FinanceMenu.MarketRow> rows = tradeRows();
        tradeScroll = clamp(tradeScroll, 0, Math.max(0, rows.size() - TRADE_ROWS));
        Map<String, Integer> held = menu().getMcInventory();
        long balance = menu().getBalance();
        int y = y0 + 15;
        for (int i = tradeScroll; i < Math.min(rows.size(), tradeScroll + TRADE_ROWS); i++) {
            FinanceMenu.MarketRow row = rows.get(i);
            String id = row.commodityId();
            Commodity c = CommodityRegistry.getCommodity(id);
            int have = held.getOrDefault(id, 0);
            if ((i - tradeScroll) % 2 == 0) g.fill(LEFT, y - 2, LEFT + WIDTH, y + 14, FinanceGui.COL_ROW_EVEN);
            if (c != null) FinanceGui.renderItemIcon(g, c, LEFT + 2, y);
            text(g, FinanceFormat.commodityDisplayName(id), LEFT + 16, y + 2, 64, FinanceGui.COL_TEXT);
            text(g, have > 0 ? Integer.toString(have) : "-", 92, y + 2, 32, have > 0 ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            String arrow = row.dayChange() > 0.0001 ? "↑" : row.dayChange() < -0.0001 ? "↓" : "";
            text(g, MoneyFormat.compact(row.bidPrice()) + arrow, 126, y + 2, 48, FinanceGui.changeColor(row.dayChange()));
            String noItem = "背包里没有" + FinanceFormat.commodityDisplayName(id);
            button(g, "卖1组", 176, y, 32, () -> send(SimpleActionPacket.Action.QUICK_SELL, id, Math.min(64, have)),
                    have > 0 ? null : noItem);
            button(g, "全卖", 210, y, 30, () -> send(SimpleActionPacket.Action.QUICK_SELL, id, have),
                    have > 0 ? null : noItem);
            text(g, MoneyFormat.compact(row.askPrice()) + arrow, 246, y + 2, 52, FinanceGui.changeColor(row.dayChange()));
            String poor = balance < row.askPrice() ? "钱不够，一个要 " + MoneyFormat.pounds(row.askPrice()) : null;
            button(g, "买1", 300, y, 28, () -> send(SimpleActionPacket.Action.QUICK_BUY, id, 1), poor);
            button(g, "买1组", 330, y, 34, () -> send(SimpleActionPacket.Action.QUICK_BUY, id, 64), poor);
            y += 16;
        }
        if (rows.size() > TRADE_ROWS) {
            text(g, "滚轮翻页 " + (tradeScroll + 1) + "-" + Math.min(rows.size(), tradeScroll + TRADE_ROWS) + " / " + rows.size(),
                    LEFT + WIDTH - 110, BOTTOM - 10, 110, FinanceGui.COL_TEXT_DIM);
        }
        text(g, "直接卖背包里的东西，买的东西直接进背包。", LEFT + 2, BOTTOM - 10, 250, FinanceGui.COL_TEXT_DIM);
    }

    // ================================================================ 我的公司

    private void renderCompany(GuiGraphics g) {
        FinanceMenu.CompanyInfo info = menu().getPlayerCompany();
        long balance = menu().getBalance();
        if (info == null) {
            renderNewCompany(g, balance);
            return;
        }
        if (!info.playerOwned()) {
            text(g, "你是「" + info.name() + "」的成员。", LEFT + 4, TOP + 6, WIDTH, FinanceGui.COL_TEXT);
            text(g, "公司的生产和成员在公司办公桌方块里管理。", LEFT + 4, TOP + 20, WIDTH, FinanceGui.COL_TEXT_DIM);
            return;
        }
        SimpleCenterService.Status st = SimpleClientState.status();
        CompanyType type = typeOf(info.type());

        text(g, info.name() + " · " + info.type() + (info.isPublic() ? " · 已上市" : ""), LEFT + 2, TOP, WIDTH, FinanceGui.COL_TEXT);

        int y = TOP + 12;
        card(g, y, 52);
        text(g, "今天", LEFT + 6, y + 4, 60, FinanceGui.COL_TEXT_DIM);
        if (type != null) text(g, FinanceFormat.getTypeDescription(type), LEFT + 40, y + 4, 200, FinanceGui.COL_TEXT_DIM);
        text(g, "公司账上 " + MoneyFormat.pounds(info.cash()), LEFT + 6, y + 16, 180, FinanceGui.COL_TEXT);
        text(g, "库存货值 " + MoneyFormat.pounds(info.inventoryValue()), LEFT + 6, y + 27, 180, FinanceGui.COL_TEXT);
        if (info.bankruptcyRisk()) {
            text(g, "快撑不住了：钱不够付开销，投点钱进去", LEFT + 6, y + 38, 236, FinanceGui.COL_BAD);
        } else {
            text(g, "上次结算利润 " + MoneyFormat.signed(info.reportNetProfit()), LEFT + 6, y + 38, 180, FinanceGui.changeColor(info.reportNetProfit()));
        }
        long transfer = Math.min(info.cash(), 10_000L);
        button(g, "转 " + MoneyFormat.compact(transfer) + " 到钱包", 250, y + 14, 132,
                () -> sendCompany(CompanyManagementAction.WITHDRAW, transfer), transfer > 0 ? null : "公司账上没钱");
        button(g, "从钱包投 10 磅进公司", 250, y + 31, 132,
                () -> sendCompany(CompanyManagementAction.INVEST, 1_000L), balance >= 1_000 ? null : "钱包不到 10 磅");

        y += 58;
        card(g, y, 38);
        int level = st == null ? info.productionLevel() + info.storageLevel() + info.managementLevel() : st.factoryLevel();
        text(g, "工厂等级 " + level + " / " + SimpleCenterService.MAX_FACTORY_LEVEL, LEFT + 6, y + 4, 140, FinanceGui.COL_TEXT);
        progress(g, LEFT + 6, y + 16, 220, level / (double) SimpleCenterService.MAX_FACTORY_LEVEL);
        text(g, "升级：每天产得更多、开销更少。", LEFT + 6, y + 25, 220, FinanceGui.COL_TEXT_DIM);
        long cost = st == null ? 0 : st.nextUpgradeCost();
        String upgradeBlock = level >= SimpleCenterService.MAX_FACTORY_LEVEL ? "已经满级了"
                : balance < cost ? "还差 " + MoneyFormat.pounds(cost - balance) : null;
        button(g, level >= SimpleCenterService.MAX_FACTORY_LEVEL ? "已满级" : "升级（" + MoneyFormat.compact(cost) + "）",
                250, y + 12, 132, () -> send(SimpleActionPacket.Action.UPGRADE_FACTORY, "", 0), upgradeBlock);

        y += 44;
        card(g, y, 46);
        if (info.isPublic()) {
            text(g, "公司已上市。", LEFT + 6, y + 4, 200, FinanceGui.COL_GOOD);
            text(g, "别人买你的股票时公司更值钱；你是大股东，每周也拿分红。", LEFT + 6, y + 18, WIDTH - 12, FinanceGui.COL_TEXT_DIM);
        } else {
            long valuation = valuation(info, st);
            long min = st == null ? 20_000 : st.ipoMinValuation();
            text(g, "上市：估值 " + MoneyFormat.pounds(valuation) + " / " + MoneyFormat.pounds(min), LEFT + 6, y + 4, 230, FinanceGui.COL_TEXT);
            progress(g, LEFT + 6, y + 16, 220, valuation / (double) Math.max(1, min));
            text(g, "把公司 40% 的股份卖给大家，换一大笔钱进公司账上。", LEFT + 6, y + 25, 236, FinanceGui.COL_TEXT_DIM);
            text(g, "要付上市费，发行价按估值自动定。", LEFT + 6, y + 35, 236, FinanceGui.COL_TEXT_DIM);
            button(g, "让公司上市", 250, y + 14, 132, () -> send(SimpleActionPacket.Action.QUICK_IPO, "", 0),
                    valuation >= min ? null : "估值还差 " + MoneyFormat.pounds(min - valuation));
        }
    }

    private void renderNewCompany(GuiGraphics g, long balance) {
        text(g, "开一家公司：它每天自己生产、自己卖货，", LEFT + 4, TOP + 4, WIDTH, FinanceGui.COL_TEXT);
        text(g, "赚的钱可以转进你的钱包。", LEFT + 4, TOP + 16, WIDTH, FinanceGui.COL_TEXT);
        text(g, "行业", LEFT + 4, TOP + 40, 40, FinanceGui.COL_TEXT_DIM);
        button(g, "< " + newCompanyType.getDisplayName() + " >", 60, TOP + 37, 110, this::nextCompanyType, null);
        text(g, FinanceFormat.getTypeDescription(newCompanyType), 178, TOP + 40, 210, FinanceGui.COL_TEXT_DIM);
        text(g, "名字", LEFT + 4, TOP + 64, 40, FinanceGui.COL_TEXT_DIM);
        button(g, "开公司（10 磅，全部作为公司的启动资金）", 60, TOP + 88, 230, this::createCompany,
                balance >= COMPANY_COST ? null : "还差 " + MoneyFormat.pounds(COMPANY_COST - balance) + "，先去“买卖”页卖点东西");
        if (balance < COMPANY_COST) {
            text(g, "还差 " + MoneyFormat.pounds(COMPANY_COST - balance) + "，先去“买卖”页卖点东西。", 60, TOP + 106, 300, FinanceGui.COL_WARN);
        }
    }

    private void nextCompanyType() {
        CompanyType[] types = CompanyType.values();
        newCompanyType = types[(newCompanyType.ordinal() + 1) % types.length];
    }

    private void createCompany() {
        String name = screen.companyNameBox.getValue().trim();
        if (name.isEmpty()) {
            screen.setStatus("先给公司起个名字。");
            return;
        }
        if (name.length() > 32) {
            screen.setStatus("公司名字最多 32 个字。");
            return;
        }
        screen.setStatus("正在开公司……");
        FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(newCompanyType, name));
    }

    // ================================================================ 投资

    private void renderInvest(GuiGraphics g) {
        SimpleCenterService.Status st = SimpleClientState.status();
        long balance = menu().getBalance();
        long unlock = st == null ? 3_000 : st.stockUnlockBalance();
        boolean locked = balance < unlock && menu().getStockHoldings().isEmpty();
        text(g, locked ? "钱包满 " + MoneyFormat.pounds(unlock) + " 后才能买股票（还差 " + MoneyFormat.pounds(unlock - balance) + "）。银行存钱随时可以。"
                        : "买别人公司的股票：涨了卖掉赚差价，拿着每周还有分红。",
                LEFT + 2, TOP, WIDTH, locked ? FinanceGui.COL_WARN : FinanceGui.COL_TEXT_DIM);

        int y0 = TOP + 14;
        text(g, "公司", LEFT + 4, y0, 100, FinanceGui.COL_TEXT_DIM);
        text(g, "股价", 120, y0, 48, FinanceGui.COL_TEXT_DIM);
        text(g, "每百股分红", 170, y0, 64, FinanceGui.COL_TEXT_DIM);
        text(g, "我持有", 238, y0, 70, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawSimpleSeparator(g, LEFT, y0 + 11, WIDTH);

        List<FinanceMenu.StockRow> stocks = menu().getStocks();
        investScroll = clamp(investScroll, 0, Math.max(0, stocks.size() - INVEST_ROWS));
        int y = y0 + 15;
        if (stocks.isEmpty()) text(g, "还没有公司上市。", LEFT + 4, y + 2, 200, FinanceGui.COL_TEXT_DIM);
        for (int i = investScroll; i < Math.min(stocks.size(), investScroll + INVEST_ROWS); i++) {
            FinanceMenu.StockRow s = stocks.get(i);
            long have = holding(s.symbol());
            if ((i - investScroll) % 2 == 0) g.fill(LEFT, y - 2, LEFT + WIDTH, y + 14, FinanceGui.COL_ROW_EVEN);
            text(g, s.name(), LEFT + 4, y + 2, 104, FinanceGui.COL_TEXT);
            String arrow = s.dayChange() > 0.0001 ? "↑" : s.dayChange() < -0.0001 ? "↓" : "";
            text(g, MoneyFormat.compact(s.lastPrice()) + arrow, 120, y + 2, 48, FinanceGui.changeColor(s.dayChange()));
            text(g, MoneyFormat.compact(s.expectedDividendPerShare() * 100), 170, y + 2, 64, FinanceGui.COL_TEXT);
            text(g, have > 0 ? have + " 股" : "-", 238, y + 2, 70, have > 0 ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            String buyBlock = locked ? "钱包满 " + MoneyFormat.pounds(unlock) + " 后解锁"
                    : balance < s.lastPrice() * 10 ? "钱不够，10 股约 " + MoneyFormat.pounds(s.lastPrice() * 10) : null;
            button(g, "买10股", 312, y, 36, () -> {
                screen.setStatus("正在买入……");
                FinancePacketHandler.CHANNEL.sendToServer(new StockTradePacket(StockTradePacket.ActionType.BUY, s.symbol(), 10));
            }, buyBlock);
            button(g, "全卖", 350, y, 32, () -> {
                screen.setStatus("正在卖出……");
                FinancePacketHandler.CHANNEL.sendToServer(new StockTradePacket(StockTradePacket.ActionType.SELL, s.symbol(), (int) Math.min(Integer.MAX_VALUE, have)));
            }, have > 0 ? null : "你没有这只股票");
            y += 16;
        }

        int bankY = BOTTOM - 40;
        card(g, bankY, 38);
        long bank = st == null ? 0 : st.bankBalance();
        int rate = st == null ? 0 : st.bankRateBps();
        text(g, "银行存款 " + MoneyFormat.pounds(bank), LEFT + 6, bankY + 5, 180, FinanceGui.COL_TEXT);
        text(g, "年利率 " + String.format(java.util.Locale.ROOT, "%.2f%%", rate / 100.0) + "，按天计息，随时能取",
                LEFT + 6, bankY + 18, 230, FinanceGui.COL_TEXT_DIM);
        button(g, "存10磅", 250, bankY + 4, 40, () -> send(SimpleActionPacket.Action.BANK_DEPOSIT, "", 1_000L),
                balance >= 1_000 ? null : "钱包不到 10 磅");
        button(g, "全部存", 294, bankY + 4, 40, () -> send(SimpleActionPacket.Action.BANK_DEPOSIT, "", balance),
                balance > 0 ? null : "钱包是空的");
        button(g, "全部取", 338, bankY + 4, 44, () -> send(SimpleActionPacket.Action.BANK_WITHDRAW, "", bank),
                bank > 0 ? null : "银行里没有存款");
        if (page == Page.INVEST && investScroll + INVEST_ROWS < stocks.size()) {
            text(g, "滚轮看更多", LEFT + WIDTH - 60, bankY - 10, 60, FinanceGui.COL_TEXT_DIM);
        }
    }

    private long holding(String symbol) {
        for (FinanceMenu.StockHoldingRow h : menu().getStockHoldings()) if (h.symbol().equals(symbol)) return h.quantity();
        return 0;
    }

    // ================================================================ 飘字

    private void renderFloaters(GuiGraphics g) {
        long now = System.currentTimeMillis();
        int i = 0;
        for (SimpleClientState.Floater f : SimpleClientState.floaters()) {
            float t = (now - f.createdAt()) / (float) SimpleClientState.FLOAT_MILLIS;
            int alpha = Math.max(16, (int) (255 * (1 - t)));
            int color = (alpha << 24) | ((f.delta() > 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD) & 0xFFFFFF);
            String label = MoneyFormat.signed(f.delta());
            g.pose().pushPose();
            g.pose().translate(FinanceGui.PANEL_WIDTH / 2.0F, TOP + 70 - t * 30 - i * 12, 400);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            g.drawString(font(), label, -font().width(label) / 2, 0, color, false);
            g.pose().popPose();
            i++;
        }
    }

    // ================================================================ 小工具

    private FinanceMenu menu() { return screen.menu(); }
    private Font font() { return screen.font(); }

    private boolean ownsCompany() {
        FinanceMenu.CompanyInfo info = menu().getPlayerCompany();
        return info != null && info.playerOwned();
    }

    /** 估值优先用服务端算的（含库存市值），没收到时用快照里的现金 + 库存。 */
    private static long valuation(FinanceMenu.CompanyInfo info, SimpleCenterService.Status st) {
        return st != null && st.companyValuation() > 0 ? st.companyValuation() : info.cash() + info.inventoryValue();
    }

    static CompanyType typeOf(String displayName) {
        for (CompanyType t : CompanyType.values()) if (t.getDisplayName().equals(displayName) || t.name().equals(displayName)) return t;
        return null;
    }

    /** 交易记录里的对象名可能是商品 ID，能翻成中文名就翻。 */
    private static String displayObject(String name) {
        Commodity c = CommodityRegistry.getCommodity(name);
        return c == null ? name : c.getDisplayName();
    }

    private void send(SimpleActionPacket.Action action, String target, long amount) {
        screen.setStatus("处理中……");
        FinancePacketHandler.CHANNEL.sendToServer(new SimpleActionPacket(action, target, amount));
    }

    private void sendCompany(CompanyManagementAction action, long amount) {
        screen.setStatus("处理中……");
        FinancePacketHandler.CHANNEL.sendToServer(CompanyManagePacket.amount(action, amount));
    }

    private void text(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        FinanceGui.drawClippedString(font(), g, text, x, y, maxWidth, color);
    }

    private static void card(GuiGraphics g, int y, int h) {
        g.fill(LEFT, y, LEFT + WIDTH, y + h, FinanceGui.COL_ROW_EVEN);
        FinanceGui.drawSimpleBorder(g, LEFT, y, WIDTH, h, 0xFFB8B0A0);
    }

    private static void progress(GuiGraphics g, int x, int y, int w, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        g.fill(x, y, x + w, y + 5, 0xFFCFC8B4);
        g.fill(x, y, x + (int) Math.round(w * r), y + 5, FinanceGui.COL_GOOD);
    }

    /** blocked 为 null 时是可点的绿色按钮；否则是灰色按钮，点了在状态栏说明原因。 */
    private void button(GuiGraphics g, String label, int x, int y, int w, Runnable action, String blocked) {
        if (blocked == null) {
            FinanceGui.drawFilledButton(font(), g, label, x, y, w, FinanceGui.COL_GOOD);
            hits.add(new Hit(x, y, w, 13, action));
        } else {
            FinanceGui.drawButton(font(), g, label, x, y, w, false);
            hits.add(new Hit(x, y, w, 13, () -> screen.setStatus(blocked)));
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
