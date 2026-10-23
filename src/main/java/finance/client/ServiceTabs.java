package finance.client;

import finance.gameplay.FinanceScreenMode;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 金融服务页：画页签头、按子页签分发渲染 / 点击 / 请求数据。
 * 每个子页签自己的画面和操作在面板类里：{@link FinancialProductPanel}（指数到贷款）、{@link FuturesPanel}、
 * {@link BankPanel}、{@link FundPanel}、{@link InsurancePanel}、{@link RegionalRiskPanel}。
 * 银行柜台打开时只有“银行”“库存质押”两个页签。
 */
public final class ServiceTabs {
    /** 子页签编号（{@code FinanceScreen.financialSubTab}）。指数到贷款共用金融产品数据。 */
    static final int INDEX = 0,
            ISSUE = 1,
            BOND_MARKET = 2,
            HOLDINGS = 3,
            NOTES = 4,
            LOANS = 5,
            FUTURES = 6,
            BANK = 7,
            FUND = 8,
            INSURANCE = 9,
            REGIONAL = 10;

    private static final String[] LABELS = {"指数", "发行", "债市", "持仓", "票据", "贷款", "期货", "银行", "基金", "保险", "区域"};
    private static final int TAB_X = 10, TAB_STEP = 34, TAB_W = 32;

    private final FinanceScreen screen;
    private final FinancialProductPanel products;
    private final FuturesPanel futures;
    private final BankPanel bank;
    private final FundPanel fund;
    private final InsurancePanel insurance;
    private final RegionalRiskPanel regional;

    public ServiceTabs(FinanceScreen screen) {
        this.screen = screen;
        products = new FinancialProductPanel(screen);
        futures = new FuturesPanel(screen);
        bank = new BankPanel(screen);
        fund = new FundPanel(screen);
        insurance = new InsurancePanel(screen);
        regional = new RegionalRiskPanel(screen);
    }

    private boolean bankCounter() {
        return screen.menu().getInitialMode() == FinanceScreenMode.BANK;
    }

    public void renderFinancialProductsTab(GuiGraphics g, int mouseX, int mouseY) {
        if (bankCounter()) {
            if (screen.financialSubTab != REGIONAL) screen.financialSubTab = BANK;
            g.fill(
                    10,
                    FinanceGui.CONTENT_Y - 2,
                    72,
                    FinanceGui.CONTENT_Y + 13,
                    screen.financialSubTab == BANK ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            g.fill(
                    76,
                    FinanceGui.CONTENT_Y - 2,
                    138,
                    FinanceGui.CONTENT_Y + 13,
                    screen.financialSubTab == REGIONAL ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            FinanceGui.drawClippedString(screen.font(), g, "银行", 28, FinanceGui.CONTENT_Y + 1, 30, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, "库存质押", 82, FinanceGui.CONTENT_Y + 1, 52, FinanceGui.COL_TEXT);
            if (screen.financialSubTab == REGIONAL) regional.render(g);
            else bank.render(g);
            return;
        }
        for (int i = 0; i < LABELS.length; i++) {
            int x = TAB_X + i * TAB_STEP;
            g.fill(
                    x,
                    FinanceGui.CONTENT_Y - 2,
                    x + TAB_W,
                    FinanceGui.CONTENT_Y + 13,
                    i == screen.financialSubTab ? FinanceGui.COL_ROW_SELECT : FinanceGui.COL_BUTTON_BG);
            FinanceGui.drawClippedString(
                    screen.font(), g, LABELS[i], x + 3, FinanceGui.CONTENT_Y + 1, 26, FinanceGui.COL_TEXT);
        }
        switch (screen.financialSubTab) {
            case FUTURES -> futures.render(g, mouseX, mouseY);
            case BANK -> bank.render(g);
            case FUND -> fund.render(g);
            case INSURANCE -> insurance.render(g);
            case REGIONAL -> regional.render(g);
            default -> products.render(g);
        }
    }

    /** 向服务端请求这个子页签的数据。 */
    public void request(int subTab) {
        switch (subTab) {
            case FUTURES -> futures.request();
            case BANK -> bank.request();
            case FUND -> fund.request();
            case INSURANCE -> insurance.request();
            case REGIONAL -> regional.request();
            default -> products.request();
        }
    }

    public boolean handleFinancialProductsClick(int mx, int my) {
        boolean onTabs = my >= FinanceGui.CONTENT_Y - 2 && my <= FinanceGui.CONTENT_Y + 14;
        if (onTabs && bankCounter()) {
            if (mx >= 10 && mx <= 72) screen.financialSubTab = BANK;
            else if (mx >= 76 && mx <= 138) screen.financialSubTab = REGIONAL;
            else return false;
            screen.updateInputVisibility();
            request(screen.financialSubTab);
            return true;
        }
        if (onTabs) {
            for (int i = 0; i < LABELS.length; i++) {
                int x = TAB_X + i * TAB_STEP;
                if (mx >= x && mx <= x + TAB_W) {
                    screen.financialSubTab = i;
                    if (i != FUTURES) screen.futuresChartVisible = false;
                    screen.updateInputVisibility();
                    request(i);
                    return true;
                }
            }
        }
        // 保险、区域的操作按钮都在底部这一行
        boolean actionRow = my >= 208 && my <= 232;
        return switch (screen.financialSubTab) {
            case FUTURES -> futures.click(mx, my);
            case BANK -> bank.click(mx, my);
            case FUND -> fund.click(mx, my);
            case INSURANCE -> actionRow && insurance.click(mx, my);
            case REGIONAL -> actionRow && regional.click(mx, my);
            default -> products.click(mx, my);
        };
    }
}
