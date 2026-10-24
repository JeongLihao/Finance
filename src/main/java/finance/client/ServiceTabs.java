package finance.client;

import finance.gameplay.FinanceScreenMode;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 金融服务页：画页签头、按子页签分发渲染和请求数据；按钮点击在画的时候登记（见 {@link ClickRegions}）。
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
            // 页签头点击范围沿用原来的闭区间（含右、下边界）
            screen.contentClicks.add(10, FinanceGui.CONTENT_Y - 2, 63, 17, () -> selectSubTab(BANK));
            screen.contentClicks.add(76, FinanceGui.CONTENT_Y - 2, 63, 17, () -> selectSubTab(REGIONAL));
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
            int index = i;
            screen.contentClicks.add(x, FinanceGui.CONTENT_Y - 2, TAB_W + 1, 17, () -> {
                if (index != FUTURES) screen.futuresChartVisible = false;
                selectSubTab(index);
            });
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

    private void selectSubTab(int index) {
        screen.financialSubTab = index;
        screen.updateInputVisibility();
        request(index);
    }
}
