package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyType;
import finance.gameplay.FinanceScreenMode;
import finance.gui.FinanceMenu;
import finance.gui.MarketRows;
import finance.gui.StockRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 金融操作中心 GUI —— 7 标签页（管理员 8 个）。
 */
/** 金融中心主界面（瘦身后）：只保留状态、生命周期、页签调度与输入分发。
 * 各页签的具体渲染 / 点击逻辑已抽取到同包的 *Tabs 类；绘制基元与主题常量在 FinanceGui，格式化辅助在 FinanceFormat。 */
public class FinanceScreen extends AbstractContainerScreen<FinanceMenu> {

    // ---- 色彩 ----

    // ---- 标签 ----
    /** 顶层页签编号（{@code currentTab}），也是 BASE_TABS/ADMIN_TABS 的下标；仪表和管理只有管理员有。 */
    static final int TAB_MARKET = 0,
            TAB_TRADE = 1,
            TAB_ORDERS = 2,
            TAB_INVENTORY = 3,
            TAB_COMPANY = 4,
            TAB_STOCK = 5,
            TAB_RECORDS = 6,
            TAB_ASSETS = 7,
            TAB_ALERTS = 8,
            TAB_FINANCIAL = 9,
            TAB_DASHBOARD = 10,
            TAB_ADMIN = 11;
    /** 宏观经济页签的编号（不在 BASE_TABS/ADMIN_TABS 数组里）。 */
    static final int TAB_MACRO = 12;

    static final String[] BASE_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒", "金融"};
    static final String[] ADMIN_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒", "金融", "仪表", "管理"};
    String[] tabNames;
    int[] allowedTabIndices = {TAB_MARKET};
    int currentTab = TAB_MARKET;
    boolean isAdmin = false;
    String statusMessage = "";
    String companyStrategyOverride = null;
    int assetSortMode = 0; // 0=价值, 1=收益
    boolean commodityChartVisible;
    boolean stockChartVisible;
    boolean futuresChartVisible;
    int chartLimit = 30;
    int financialSubTab = ServiceTabs.INDEX;
    boolean governanceView = false;
    boolean restructuringView = false;
    boolean companyDetailsView = false;
    float layoutScale = 1.0F;
    /** 页签与初始选中项只在第一次 init 时设定。 */
    boolean entryConfigured = false;

    // ---- 行情标签（国际交易）控件 ----
    String selectedCommodity = "iron";
    EditBox intlQuantityBox;

    // ---- 交易标签控件 ----
    EditBox priceBox;
    EditBox quantityBox;
    EditBox inventoryQuantityBox;

    // ---- 公司标签控件 ----
    EditBox companyNameBox;
    /** 简单模式买卖列表上方的搜索框。 */
    EditBox searchBox;

    CompanyType selectedType = CompanyType.RAW_MATERIALS;

    // ---- 股票标签控件 ----
    String selectedStock = "";
    EditBox stockPriceBox;
    EditBox stockQuantityBox;
    EditBox alertPriceBox;

    // ---- IPO 控件 ----
    EditBox ipoPriceBox;
    EditBox ipoQuantityBox;
    EditBox companyAmountBox;

    // ---- 管理标签控件 ----
    EditBox adminCommodityIdBox;
    EditBox adminItemIdBox;
    EditBox adminDisplayNameBox;
    EditBox adminBasePriceBox;
    CommodityCategory adminCategory = CommodityCategory.RAW_MATERIALS;
    int adminSubTab = AdminTabs.HAND;

    // ---- 商品下拉菜单（交易标签 + 行情标签） ----
    boolean dropdownOpen = false;
    CommodityCategory expandedCategory = null;
    int dropdownScroll = 0;
    int adminQuickPage = 0;
    int adminRegisteredPage = 0;

    // ---- 缓存 ----
    List<MarketRows.MarketRow> cachedMarketData;
    MarketRows.MarketRow cachedSelectedRow;
    String lastSelectedCommodity = "";
    boolean cacheDirty = true;

    List<Commodity> cachedCommodities;
    boolean commodityCacheDirty = true;

    // ---- 分类商品缓存 ----
    Map<CommodityCategory, List<Commodity>> categorizedCommodities;
    Map<CommodityCategory, List<Commodity>> inventoryCategorizedCommodities;
    boolean categorizedCacheDirty = true;

    // ---- 抽取出的页签行为类（与 FinanceScreen 同包，通过 screen 反向引用共享状态） ----
    final MarketPanel marketPanel = new MarketPanel(this);
    final TradePanel tradePanel = new TradePanel(this);
    final CommodityDropdown commodityDropdown = new CommodityDropdown(this);
    final CompanyTabs companyTabs = new CompanyTabs(this);
    final StockTabs stockTabs = new StockTabs(this);
    final CandlestickView candlestickView = new CandlestickView(this);
    /** 内容区按钮画的时候登记在这里，点击时先查它（坐标系同内容区：已加上 CONTENT_SHIFT）。 */
    final ClickRegions contentClicks = new ClickRegions();

    final ServiceTabs serviceTabs = new ServiceTabs(this);
    final OrdersPanel ordersPanel = new OrdersPanel(this);
    final InventoryPanel inventoryPanel = new InventoryPanel(this);
    final TransactionsPanel transactionsPanel = new TransactionsPanel(this);
    final AssetsPanel assetsPanel = new AssetsPanel(this);
    final AlertsPanel alertsPanel = new AlertsPanel(this);
    final DashboardPanel dashboardPanel = new DashboardPanel(this);
    final AdminTabs adminTabs = new AdminTabs(this);
    final MacroTabs macroTabs = new MacroTabs(this);
    /** 简单模式四页（首页、买卖、我的公司、投资）。默认显示它；“高级”切到下面原有的全部页签。 */
    final SimpleCenter simple = new SimpleCenter(this);

    boolean advancedView = false;
    /** 标题栏右侧“更多操作”按钮。 */
    static final int MORE_BUTTON_W = 56, MORE_BUTTON_X = FinanceGui.PANEL_WIDTH - MORE_BUTTON_W - 6, MORE_BUTTON_Y = 1;
    /** 标题栏“教程”按钮（原来要按 J 打开教程中心）。 */
    static final int GUIDE_BUTTON_W = 32, GUIDE_BUTTON_X = MORE_BUTTON_X - GUIDE_BUTTON_W - 4;
    /** 金额、价格、数量类输入框的上限（输入框最多 12 位）。 */
    static final long MAX_INPUT_AMOUNT = 999_999_999_999L;
    /** 行情表滚动到的第一行。商品多于可见行数时用滚轮翻看。 */
    int marketScroll = 0;

    // ---- 供同包 *Tabs 类访问的 protected 继承成员（font/menu 等声明在 MC 包内，跨包子类外不可见） ----
    Font font() {
        return font;
    }

    FinanceMenu menu() {
        return menu;
    }

    int imageWidth() {
        return imageWidth;
    }
    /** 页签内容坐标系下的高度（内容整体上移了 CONTENT_SHIFT，见 FinanceGui）。 */
    int imageHeight() {
        return FinanceGui.LAYOUT_HEIGHT;
    }
    /** 页签内容坐标系的原点 y：输入框等绝对坐标控件用它定位。 */
    int contentTop() {
        return topPos - FinanceGui.CONTENT_SHIFT;
    }

    int leftPos() {
        return leftPos;
    }

    int topPos() {
        return topPos;
    }

    public FinanceScreen(FinanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = FinanceGui.PANEL_WIDTH;
        imageHeight = FinanceGui.PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        layoutScale = fitScale(width, height);
        leftPos = Math.round((width / layoutScale - FinanceGui.PANEL_WIDTH) / 2.0F);
        topPos = Math.round((height / layoutScale - FinanceGui.PANEL_HEIGHT) / 2.0F);
        refreshCache();

        isAdmin = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
        // init 在调整窗口大小、从“更多操作”或教程返回时也会再跑一次：只在第一次决定页签和选中项，之后保留玩家所在的位置
        if (!entryConfigured) {
            entryConfigured = true;
            configureEntryTabs(menu.getInitialMode());
            adminSubTab = AdminTabs.HAND;
            governanceView = false;
            restructuringView = false;
            companyDetailsView = false;
            selectedCommodity = chooseInitialCommodity();
            selectedStock = "";
        }

        // 行情标签 — 国际交易数量输入框
        intlQuantityBox = new EditBox(
                font, leftPos + 40, contentTop() + FinanceGui.MARKET_TRADE_Y + 53, 48, 16, Component.literal("数量"));
        intlQuantityBox.setMaxLength(8);
        intlQuantityBox.setValue("1");
        intlQuantityBox.setVisible(false);
        addWidget(intlQuantityBox);

        // 交易标签 — 价格/数量输入框
        priceBox = new EditBox(
                font, leftPos + 58, contentTop() + FinanceGui.CONTENT_Y + 24, 86, 16, Component.literal("价格"));
        priceBox.setMaxLength(12);
        priceBox.setValue("10");
        priceBox.setVisible(false);
        addWidget(priceBox);

        quantityBox = new EditBox(
                font, leftPos + 58, contentTop() + FinanceGui.CONTENT_Y + 50, 86, 16, Component.literal("数量"));
        quantityBox.setMaxLength(8);
        quantityBox.setValue("1");
        quantityBox.setVisible(false);
        addWidget(quantityBox);

        inventoryQuantityBox = new EditBox(
                font, leftPos + 54, contentTop() + FinanceGui.CONTENT_Y + 62, 56, 16, Component.literal("数量"));
        inventoryQuantityBox.setMaxLength(8);
        inventoryQuantityBox.setValue("64");
        inventoryQuantityBox.setVisible(false);
        addWidget(inventoryQuantityBox);

        // 公司名称输入框
        companyNameBox = new EditBox(
                font, leftPos + 70, contentTop() + FinanceGui.CONTENT_Y + 36, 190, 16, Component.literal("公司名称"));
        companyNameBox.setMaxLength(101);
        companyNameBox.setVisible(false);
        addWidget(companyNameBox);

        searchBox = new EditBox(font, leftPos + 10, contentTop() + 48, 120, 12, Component.literal("搜索"));
        searchBox.setMaxLength(24);
        searchBox.setVisible(false);
        searchBox.setValue(simple.state.search);
        searchBox.setResponder(text -> {
            simple.state.search = text;
            simple.state.tradeScroll = 0;
        });
        addWidget(searchBox);

        stockPriceBox = new EditBox(
                font, leftPos + 54, contentTop() + FinanceGui.STOCK_TRADE_Y + 18, 64, 16, Component.literal("价格"));
        stockPriceBox.setMaxLength(12);
        stockPriceBox.setValue("1");
        stockPriceBox.setVisible(false);
        addWidget(stockPriceBox);

        stockQuantityBox = new EditBox(
                font, leftPos + 54, contentTop() + FinanceGui.STOCK_TRADE_Y + 38, 64, 16, Component.literal("股数"));
        stockQuantityBox.setMaxLength(8);
        stockQuantityBox.setValue("1");
        stockQuantityBox.setVisible(false);
        addWidget(stockQuantityBox);

        alertPriceBox = new EditBox(
                font, leftPos + 66, contentTop() + FinanceGui.CONTENT_Y + 58, 70, 16, Component.literal("提醒价格"));
        alertPriceBox.setMaxLength(12);
        alertPriceBox.setValue("10");
        alertPriceBox.setVisible(false);
        addWidget(alertPriceBox);

        ipoPriceBox = new EditBox(
                font, leftPos + 58, contentTop() + FinanceGui.CONTENT_Y + 136, 62, 14, Component.literal("发行价"));
        ipoPriceBox.setMaxLength(12);
        ipoPriceBox.setValue("10");
        ipoPriceBox.setVisible(false);
        addWidget(ipoPriceBox);

        ipoQuantityBox = new EditBox(
                font, leftPos + 172, contentTop() + FinanceGui.CONTENT_Y + 136, 72, 14, Component.literal("发行数量"));
        ipoQuantityBox.setMaxLength(12);
        ipoQuantityBox.setValue("4000");
        ipoQuantityBox.setVisible(false);
        addWidget(ipoQuantityBox);

        companyAmountBox = new EditBox(
                font, leftPos + 58, contentTop() + FinanceGui.CONTENT_Y + 106, 80, 14, Component.literal("金额"));
        companyAmountBox.setMaxLength(12);
        companyAmountBox.setValue("1000");
        companyAmountBox.setVisible(false);
        addWidget(companyAmountBox);

        // 管理标签输入框
        adminBasePriceBox = new EditBox(
                font, leftPos + 228, contentTop() + FinanceGui.CONTENT_Y + 42, 60, 14, Component.literal("价格"));
        adminBasePriceBox.setMaxLength(12);
        adminBasePriceBox.setValue("10");
        adminBasePriceBox.setVisible(false);
        addWidget(adminBasePriceBox);

        int adminFormY = contentTop() + FinanceGui.CONTENT_Y + 98;
        adminCommodityIdBox = new EditBox(font, leftPos + 28, adminFormY, 82, 14, Component.literal("商品ID"));
        adminCommodityIdBox.setMaxLength(32);
        adminCommodityIdBox.setVisible(false);
        addWidget(adminCommodityIdBox);

        adminItemIdBox = new EditBox(font, leftPos + 140, adminFormY, 120, 14, Component.literal("物品ID"));
        adminItemIdBox.setMaxLength(64);
        adminItemIdBox.setVisible(false);
        addWidget(adminItemIdBox);

        adminDisplayNameBox = new EditBox(font, leftPos + 296, adminFormY, 74, 14, Component.literal("显示名"));
        adminDisplayNameBox.setMaxLength(32);
        adminDisplayNameBox.setVisible(false);
        addWidget(adminDisplayNameBox);

        if (!menu.getStocks().isEmpty()) {
            boolean found = false;
            for (StockRows.StockRow row : menu.getStocks()) {
                if (row.symbol().equals(selectedStock)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedStock = menu.getStocks().get(0).symbol();
            }
            StockRows.StockRow selected = this.stockTabs.findSelectedStock();
            if (selected != null) {
                stockPriceBox.setValue(Long.toString(selected.lastPrice()));
            }
        }

        commodityCacheDirty = true;
        categorizedCacheDirty = true;
        updateInputVisibility();
    }

    /** 玩家可见的页签（按使用频率排）；管理员额外有仪表和管理。 */
    static final int[] PLAYER_TAB_ORDER = {
        TAB_MARKET,
        TAB_TRADE,
        TAB_ORDERS,
        TAB_INVENTORY,
        TAB_COMPANY,
        TAB_STOCK,
        TAB_FINANCIAL,
        TAB_MACRO,
        TAB_ASSETS,
        TAB_RECORDS,
        TAB_ALERTS
    };

    /** 不论从哪个入口进来都能看到全部页签，入口只决定先停在哪一页。 */
    private void configureEntryTabs(FinanceScreenMode mode) {
        FinanceScreenMode safeMode = mode == null ? FinanceScreenMode.ADVANCED : mode;
        allowedTabIndices = isAdmin
                ? java.util.stream.IntStream.concat(
                                java.util.Arrays.stream(PLAYER_TAB_ORDER),
                                java.util.stream.IntStream.of(TAB_DASHBOARD, TAB_ADMIN))
                        .toArray()
                : PLAYER_TAB_ORDER.clone();
        String[] labels = (isAdmin ? ADMIN_TABS : BASE_TABS);
        tabNames = new String[allowedTabIndices.length];
        for (int i = 0; i < allowedTabIndices.length; i++) {
            int index = allowedTabIndices[i];
            tabNames[i] = index == TAB_MACRO
                    ? "宏观"
                    : index < labels.length ? labels[index] : BASE_TABS[Math.min(index, BASE_TABS.length - 1)];
        }
        currentTab = switch (safeMode) {
            case MARKET, ADVANCED -> TAB_MARKET;
            case COMPANY -> TAB_COMPANY;
            case BANK -> TAB_FINANCIAL;
            case ADMIN -> TAB_DASHBOARD;
            case WAREHOUSE -> TAB_INVENTORY;
            case WALLET -> TAB_ASSETS;
        };
        if (safeMode == FinanceScreenMode.BANK) financialSubTab = ServiceTabs.BANK;
        // 方块终端进来时，简单模式停在对应的那一页；央行控制台是管理员工具，直接进高级页签
        simple.setPage(
                switch (safeMode) {
                    case MARKET, WAREHOUSE -> SimplePages.Page.TRADE;
                    case COMPANY -> SimplePages.Page.COMPANY;
                    case BANK -> SimplePages.Page.INVEST;
                        // 证券终端：股票、理财、押注都在“投资”页
                    default -> menu.getSourceType() == finance.gameplay.FinanceTerminalType.SECURITIES_TERMINAL
                            ? SimplePages.Page.INVEST
                            : SimplePages.Page.HOME;
                });
        advancedView = safeMode == FinanceScreenMode.ADMIN;
    }

    /** 旧的全部页签（含仪表、管理）只给 OP；服务器把 simpleCenter.advancedMode 设成 always 时所有人都能进。 */
    boolean oldPagesAllowed() {
        return isAdmin || finance.client.simple.SimpleClientState.advancedAllowed();
    }

    /** 服务端推来了新快照（余额、库存、订单……）：刷新缓存，页签、输入框内容和选中项保持不变。 */
    void onDataRefreshed() {
        lastSelectedCommodity = "";
        refreshCache();
        commodityCacheDirty = true;
        categorizedCacheDirty = true;
        updateInputVisibility();
    }

    void refreshCache() {
        cachedMarketData = menu.getMarketData();
        cacheDirty = false;
        refreshSelectedRow();
    }

    void refreshSelectedRow() {
        if (!lastSelectedCommodity.equals(selectedCommodity)) {
            lastSelectedCommodity = selectedCommodity;
            cachedSelectedRow = null;
            for (MarketRows.MarketRow row : cachedMarketData) {
                if (row.commodityId().equals(selectedCommodity)) {
                    cachedSelectedRow = row;
                    break;
                }
            }
        }
    }

    /** 构建按分类分组的商品缓存（全部商品 + 库存商品） */
    void rebuildCategorizedCache() {
        Map<CommodityCategory, List<Commodity>> map = new LinkedHashMap<>();
        Map<CommodityCategory, List<Commodity>> invMap = new LinkedHashMap<>();
        for (CommodityCategory cat : CommodityCategory.values()) {
            map.put(cat, new ArrayList<>());
            invMap.put(cat, new ArrayList<>());
        }
        Map<String, Integer> playerInv = menu.getPlayerInventory();
        for (Commodity c : CommodityRegistry.getAllCommodities()) {
            map.get(c.getCategory()).add(c);
            if (playerInv.containsKey(c.getId())) {
                invMap.get(c.getCategory()).add(c);
            }
        }
        // 按显示名排序
        for (List<Commodity> list : map.values()) {
            list.sort(Comparator.comparing(Commodity::getDisplayName));
        }
        for (List<Commodity> list : invMap.values()) {
            list.sort(Comparator.comparing(Commodity::getDisplayName));
        }
        categorizedCommodities = map;
        inventoryCategorizedCommodities = invMap;
        categorizedCacheDirty = false;
    }

    // ================================================================
    // 渲染
    // ================================================================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, FinanceGui.COL_BG);
        FinanceGui.drawSimpleBorder(g, leftPos, topPos, imageWidth, imageHeight, FinanceGui.COL_PANEL_BORDER);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + FinanceGui.TAB_Y - 1, 0xFFF4F0E2);
        g.fill(
                leftPos + 1,
                topPos + FinanceGui.TAB_Y - 1,
                leftPos + imageWidth - 1,
                topPos + FinanceGui.HEADER_H,
                FinanceGui.COL_TAB_BG);
        g.fill(
                leftPos + 1,
                topPos + FinanceGui.HEADER_H,
                leftPos + imageWidth - 1,
                topPos + FinanceGui.HEADER_H + 1,
                FinanceGui.COL_PANEL_BORDER);
        RenderSystem.disableBlend();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xB0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int virtualMouseX = toVirtual(mouseX);
        int virtualMouseY = toVirtual(mouseY);
        graphics.pose().pushPose();
        graphics.pose().scale(layoutScale, layoutScale, 1.0F);
        renderBackground(graphics);
        super.render(graphics, virtualMouseX, virtualMouseY, partialTick);
        for (EditBox box : getVisibleEditBoxes()) {
            box.render(graphics, virtualMouseX, virtualMouseY, partialTick);
        }
        // 下拉菜单渲染在最上层
        if (advancedView && dropdownOpen && (currentTab == TAB_MARKET || currentTab == TAB_TRADE)) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, -FinanceGui.CONTENT_SHIFT, 0);
            this.commodityDropdown.renderDropdown(graphics, virtualMouseX, virtualMouseY + FinanceGui.CONTENT_SHIFT);
            graphics.pose().popPose();
        }
        renderTooltip(graphics, virtualMouseX, virtualMouseY);
        if (!advancedView) {
            // 被截断的文字、灰色按钮：悬停显示全文或原因
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 300);
            simple.renderTooltip(graphics, virtualMouseX - leftPos, virtualMouseY - topPos);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int absoluteMouseX, int absoluteMouseY) {
        // 原版传进来的是绝对坐标，而标题栏、页签和图表都按面板内坐标判断悬停
        int mouseX = absoluteMouseX - leftPos;
        int panelMouseY = absoluteMouseY - topPos;
        FinanceGui.drawClippedString(font, g, "金融中心", 10, 4, 130, FinanceGui.COL_TEXT);
        if (!statusMessage.isEmpty()) {
            FinanceGui.drawClippedString(font, g, statusMessage, 62, 4, GUIDE_BUTTON_X - 66, FinanceGui.COL_ACCENT);
        }
        if (!advancedView) {
            FinanceGui.drawButton(font, g, "教程", GUIDE_BUTTON_X, MORE_BUTTON_Y, GUIDE_BUTTON_W, false);
            if (oldPagesAllowed()) {
                FinanceGui.drawButton(
                        font, g, isAdmin ? "管理" : "高级", MORE_BUTTON_X, MORE_BUTTON_Y, MORE_BUTTON_W, false);
            }
            simple.render(g, mouseX, panelMouseY);
            return;
        }
        FinanceGui.drawButton(font, g, "简单", GUIDE_BUTTON_X, MORE_BUTTON_Y, GUIDE_BUTTON_W, false);
        FinanceGui.drawButton(font, g, "更多操作", MORE_BUTTON_X, MORE_BUTTON_Y, MORE_BUTTON_W, false);
        renderTabs(g, mouseX, panelMouseY);

        int mouseY = panelMouseY + FinanceGui.CONTENT_SHIFT;
        g.pose().pushPose();
        g.pose().translate(0, -FinanceGui.CONTENT_SHIFT, 0);
        contentClicks.clear();
        switch (currentTab) {
            case TAB_MARKET -> this.marketPanel.renderMarketTab(g, mouseX, mouseY);
            case TAB_TRADE -> this.tradePanel.renderTradeTab(g);
            case TAB_ORDERS -> this.ordersPanel.renderOrdersTab(g);
            case TAB_INVENTORY -> this.inventoryPanel.renderInventoryTab(g);
            case TAB_COMPANY -> this.companyTabs.renderCompanyTab(g);
            case TAB_STOCK -> this.stockTabs.renderStockTab(g, mouseX, mouseY);
            case TAB_RECORDS -> this.transactionsPanel.renderTransactionTab(g);
            case TAB_ASSETS -> this.assetsPanel.renderAssetTab(g);
            case TAB_ALERTS -> this.alertsPanel.renderAlertTab(g);
            case TAB_FINANCIAL -> this.serviceTabs.renderFinancialProductsTab(g, mouseX, mouseY);
            case TAB_DASHBOARD -> {
                if (isAdmin) this.dashboardPanel.renderDashboardTab(g);
            }
            case TAB_ADMIN -> {
                if (isAdmin) this.adminTabs.renderAdminTab(g);
            }
            case TAB_MACRO -> this.macroTabs.render(g);
        }
        g.pose().popPose();
    }

    // ---- 标签栏 ----

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = FinanceGui.TAB_X;
        for (int i = 0; i < tabNames.length; i++) {
            String label = tabNames[i];
            int w = tabWidth();
            boolean active = (allowedTabIndices[i] == currentTab);
            boolean hovered = mouseX >= x
                    && mouseX < x + w
                    && mouseY >= FinanceGui.TAB_Y
                    && mouseY < FinanceGui.TAB_Y + FinanceGui.TAB_H;

            int textColor = active ? FinanceGui.COL_TEXT : (hovered ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            if (active || hovered) {
                g.fill(x, FinanceGui.TAB_Y - 1, x + w, FinanceGui.TAB_Y + FinanceGui.TAB_H, 0xFFECE6D5);
            }
            // 页签多时宽度很窄：文字居中，只留 2 像素边距
            int labelX = x + Math.max(2, (w - font.width(label)) / 2);
            FinanceGui.drawClippedString(font, g, label, labelX, FinanceGui.TAB_Y + 2, w - 4, textColor);
            if (active) {
                g.fill(
                        x + 3,
                        FinanceGui.TAB_Y + FinanceGui.TAB_H - 1,
                        x + w - 3,
                        FinanceGui.TAB_Y + FinanceGui.TAB_H,
                        FinanceGui.COL_ACCENT);
            }
            x += w + 3;
        }
    }

    private int tabWidth() {
        return Math.max(24, (imageWidth - FinanceGui.TAB_X * 2 - (tabNames.length - 1) * 3) / tabNames.length);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX /= layoutScale;
        mouseY /= layoutScale;
        // 输入框不走 super.mouseClicked，需要手动设置焦点，否则点了也无法输入
        EditBox clickedBox = null;
        for (EditBox box : getVisibleEditBoxes()) {
            if (box.mouseClicked(mouseX, mouseY, button)) clickedBox = box;
        }
        setFocused(clickedBox);
        if (clickedBox != null) return true;

        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

        boolean onMore = mx >= MORE_BUTTON_X
                && mx < MORE_BUTTON_X + MORE_BUTTON_W
                && my >= MORE_BUTTON_Y
                && my < MORE_BUTTON_Y + 13;
        boolean onGuide = mx >= GUIDE_BUTTON_X
                && mx < GUIDE_BUTTON_X + GUIDE_BUTTON_W
                && my >= MORE_BUTTON_Y
                && my < MORE_BUTTON_Y + 13;
        if (!advancedView) {
            if (onGuide) {
                TutorialHubScreen.open(this);
                return true;
            }
            if (onMore && oldPagesAllowed()) {
                advancedView = true;
                dropdownOpen = false;
                updateInputVisibility();
                return true;
            }
            if (simple.click(mx, my)) return true;
            return mx >= 0 && mx < imageWidth && my >= 0 && my < imageHeight
                    || super.mouseClicked(mouseX, mouseY, button);
        }
        // 高级页签：标题栏“简单”回到四页，“更多操作”是原来只能用命令完成的玩家功能
        if (onGuide) {
            advancedView = false;
            dropdownOpen = false;
            updateInputVisibility();
            return true;
        }
        if (onMore) {
            PlayerServicesScreen.open();
            return true;
        }

        // 标签切换
        if (my >= FinanceGui.TAB_Y && my <= FinanceGui.TAB_Y + FinanceGui.TAB_H) {
            int x = FinanceGui.TAB_X;
            for (int i = 0; i < tabNames.length; i++) {
                int w = tabWidth();
                if (mx >= x && mx < x + w) {
                    currentTab = allowedTabIndices[i];
                    rememberUiState();
                    dropdownOpen = false;
                    updateInputVisibility();
                    if (currentTab == TAB_MACRO) this.macroTabs.request();
                    if (currentTab == TAB_FINANCIAL) this.serviceTabs.request(financialSubTab);
                    return true;
                }
                x += w + 3;
            }
        }

        // 下拉菜单点击拦截（在其他标签逻辑之前）
        if (dropdownOpen && (currentTab == TAB_MARKET || currentTab == TAB_TRADE)) {
            if (this.commodityDropdown.handleDropdownClick(mx, my + FinanceGui.CONTENT_SHIFT)) return true;
            // 点击下拉外部关闭
            dropdownOpen = false;
        }

        int contentY = my + FinanceGui.CONTENT_SHIFT;
        boolean handled;
        try {
            handled = contentClicks.click(mx, contentY)
                    || switch (currentTab) {
                        case TAB_STOCK -> this.stockTabs.handleStockClick(mx, contentY);
                        case TAB_FINANCIAL -> this.serviceTabs.handleFinancialProductsClick(mx, contentY);
                        case TAB_ADMIN -> isAdmin && this.adminTabs.handleAdminClick(mx, contentY);
                        case TAB_MACRO -> this.macroTabs.handleClick(mx, contentY);
                        default -> false;
                    };
        } catch (InvalidInput invalid) {
            // 提示已写入状态栏，不发包
            handled = true;
        }
        if (handled) {
            rememberUiState();
            return true;
        }

        if (mx >= 0 && mx < imageWidth && my >= 0 && my < imageHeight) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        mouseX /= layoutScale;
        mouseY /= layoutScale;
        if (!advancedView) {
            return simple.scroll(delta) || super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (dropdownOpen && (currentTab == TAB_MARKET || currentTab == TAB_TRADE)) {
            int totalHeight = this.commodityDropdown.calcDropdownTotalHeight();
            int maxScroll = Math.max(0, totalHeight - 116);
            dropdownScroll = Math.max(0, Math.min(maxScroll, dropdownScroll - (int) (delta * 14)));
            return true;
        }
        if (currentTab == TAB_MARKET && !commodityChartVisible) {
            int max = this.marketPanel.maxMarketScroll();
            if (max > 0) {
                marketScroll = Math.max(0, Math.min(max, marketScroll - (int) Math.signum(delta)));
                return true;
            }
        }
        if (currentTab == TAB_ADMIN && isAdmin) {
            if (adminSubTab == AdminTabs.QUICK) {
                int maxPage = Math.max(0, (adminTabs.QUICK_ITEMS.length - 1) / this.adminTabs.quickItemsPerPage());
                adminQuickPage = Math.max(0, Math.min(maxPage, adminQuickPage - (int) Math.signum(delta)));
                return true;
            }
            if (adminSubTab == AdminTabs.REGISTERED) {
                ensureCommodityCache();
                int maxPage = Math.max(0, (cachedCommodities.size() - 1) / this.adminTabs.registeredItemsPerPage());
                adminRegisteredPage = Math.max(0, Math.min(maxPage, adminRegisteredPage - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    int toVirtual(int coordinate) {
        return Math.round(coordinate / layoutScale);
    }

    int toScreen(int coordinate) {
        return Math.round(coordinate * layoutScale);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ScreenInput.handleTextBoxKey(getFocused(), keyCode, scanCode, modifiers)) return true;
        // 再按一次金融中心快捷键就关掉（输入框有焦点时上面已经把按键当作输入吃掉了）
        if (FinanceKeyMappings.OPEN_FINANCE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (EditBox box : getVisibleEditBoxes()) {
            if (box.isFocused() && box.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ================================================================
    // 绘制辅助
    // ================================================================

    /** 一个要显示的输入框：x 相对 leftPos，y 相对 contentTop()；moves 为 false 时位置沿用 init 里定好的。 */
    private record InputPlacement(EditBox box, boolean moves, int x, int y) {
        static InputPlacement fixed(EditBox box) {
            return new InputPlacement(box, false, 0, 0);
        }

        static InputPlacement at(EditBox box, int x, int y) {
            return new InputPlacement(box, true, x, y);
        }
    }

    /**
     * 高级界面当前要显示哪些输入框、放在哪，只在这里写一遍。
     * 显隐和摆放（{@link #updateInputVisibility}）与渲染、点击、打字用的列表（{@link #getVisibleEditBoxes}）都从这里来，
     * 列表顺序就是渲染和点击的顺序。
     */
    private List<InputPlacement> advancedInputs() {
        List<InputPlacement> out = new ArrayList<>();
        int cy = FinanceGui.CONTENT_Y;
        switch (currentTab) {
            case TAB_MARKET -> {
                if (!commodityChartVisible) out.add(InputPlacement.fixed(intlQuantityBox));
            }
            case TAB_TRADE -> {
                out.add(InputPlacement.fixed(priceBox));
                out.add(InputPlacement.fixed(quantityBox));
            }
            case TAB_INVENTORY -> out.add(InputPlacement.fixed(inventoryQuantityBox));
            case TAB_COMPANY -> {
                if (menu.getPlayerCompany() == null) {
                    if (!restructuringView) out.add(InputPlacement.at(companyNameBox, 70, cy + 36));
                } else if (governanceView) {
                    out.add(InputPlacement.at(companyNameBox, 108, cy + 144));
                    out.add(InputPlacement.at(companyAmountBox, 288, cy + 164));
                    out.add(InputPlacement.at(stockPriceBox, 48, cy + 164));
                    out.add(InputPlacement.at(stockQuantityBox, 168, cy + 164));
                } else if (companyDetailsView) {
                    out.add(InputPlacement.at(companyNameBox, 48, cy + 58));
                    out.add(InputPlacement.at(companyAmountBox, 48, cy + 80));
                    out.add(InputPlacement.at(ipoPriceBox, 168, cy + 80));
                    out.add(InputPlacement.at(ipoQuantityBox, 288, cy + 80));
                } else if (!restructuringView) {
                    out.add(InputPlacement.at(companyAmountBox, 58, cy + 106));
                    out.add(InputPlacement.at(ipoPriceBox, 58, cy + 136));
                    out.add(InputPlacement.at(ipoQuantityBox, 172, cy + 136));
                }
            }
            case TAB_STOCK -> {
                if (!stockChartVisible) {
                    out.add(InputPlacement.at(stockPriceBox, 54, FinanceGui.STOCK_TRADE_Y + 18));
                    out.add(InputPlacement.at(stockQuantityBox, 54, FinanceGui.STOCK_TRADE_Y + 38));
                }
            }
            case TAB_ALERTS -> out.add(InputPlacement.fixed(alertPriceBox));
            case TAB_FINANCIAL -> {
                if (futuresChartVisible) break;
                int sub = financialSubTab;
                if (sub != ServiceTabs.INDEX && sub != ServiceTabs.HOLDINGS) {
                    out.add(InputPlacement.at(stockPriceBox, 50, 188));
                    if (sub != ServiceTabs.NOTES) out.add(InputPlacement.at(stockQuantityBox, 162, 188));
                }
                if (sub != ServiceTabs.BOND_MARKET && sub != ServiceTabs.HOLDINGS && sub != ServiceTabs.INSURANCE) {
                    // 基金页：定投间隔输入框放到第二行，避免与"赎回"按钮重叠
                    out.add(
                            sub == ServiceTabs.FUND
                                    ? InputPlacement.at(companyAmountBox, 312, 214)
                                    : InputPlacement.at(companyAmountBox, 282, 188));
                }
            }
            case TAB_ADMIN -> {
                if (isAdmin && adminSubTab == AdminTabs.HAND) {
                    out.add(InputPlacement.at(adminBasePriceBox, 228, cy + 42));
                    out.add(InputPlacement.at(adminCommodityIdBox, 28, cy + 98));
                    out.add(InputPlacement.at(adminItemIdBox, 140, cy + 98));
                    out.add(InputPlacement.at(adminDisplayNameBox, 296, cy + 98));
                }
            }
            default -> {}
        }
        return out;
    }

    void updateInputVisibility() {
        for (EditBox box : List.of(
                intlQuantityBox,
                priceBox,
                quantityBox,
                inventoryQuantityBox,
                companyNameBox,
                stockPriceBox,
                stockQuantityBox,
                alertPriceBox,
                ipoPriceBox,
                ipoQuantityBox,
                companyAmountBox,
                adminBasePriceBox,
                adminCommodityIdBox,
                adminItemIdBox,
                adminDisplayNameBox)) {
            box.setVisible(false);
        }
        if (!advancedView) {
            simple.updateInputs(companyNameBox, searchBox);
            return;
        }
        searchBox.setVisible(false);
        for (InputPlacement p : advancedInputs()) {
            if (p.moves()) {
                p.box().setX(leftPos + p.x());
                p.box().setY(contentTop() + p.y());
            }
            p.box().setVisible(true);
        }
    }

    private List<EditBox> getVisibleEditBoxes() {
        if (!advancedView) return simple.visibleBoxes(companyNameBox, searchBox);
        List<EditBox> list = new ArrayList<>();
        for (InputPlacement p : advancedInputs()) list.add(p.box());
        return list;
    }

    /** 画一个普通按钮，并登记点击后的动作（内容区坐标）。 */
    void button(GuiGraphics g, String label, int x, int y, int w, boolean active, Runnable action) {
        FinanceGui.drawButton(font, g, label, x, y, w, active);
        contentClicks.add(x, y, w, 13, action);
    }

    /** 画一个实心按钮，并登记点击后的动作（内容区坐标）。 */
    void filledButton(GuiGraphics g, String label, int x, int y, int w, int color, Runnable action) {
        FinanceGui.drawFilledButton(font, g, label, x, y, w, color);
        contentClicks.add(x, y, w, 13, action);
    }

    /** 输入框内容不合法时抛出；由 mouseClicked 统一吞掉，提示已写入状态栏。 */
    static final class InvalidInput extends RuntimeException {
        InvalidInput() {
            super(null, null, false, false);
        }
    }

    /** 读取 min..max 的整数；不合法时提示并中止本次点击，绝不把非法值静默改成 1 或越界发给服务端。 */
    long requireLong(EditBox box, String label, long min, long max) {
        var value = ScreenInput.parseInRange(box.getValue(), min, max);
        if (value.isEmpty()) {
            setStatus(ScreenInput.rangeMessage(label, min, max));
            throw new InvalidInput();
        }
        return value.getAsLong();
    }

    int requireInt(EditBox box, String label, int min, int max) {
        return (int) requireLong(box, label, min, max);
    }

    /** 金额、价格、数量：1..MAX_INPUT_AMOUNT。 */
    long requireAmount(EditBox box, String label) {
        return requireLong(box, label, 1, MAX_INPUT_AMOUNT);
    }

    void ensureCommodityCache() {
        if (commodityCacheDirty || cachedCommodities == null) {
            cachedCommodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
            cachedCommodities.sort(Comparator.comparing(Commodity::getDisplayName));
            commodityCacheDirty = false;
        }
    }

    String chooseInitialCommodity() {
        for (MarketRows.MarketRow row : menu.getMarketData()) {
            if ("iron".equals(row.commodityId())) {
                return "iron";
            }
        }
        if (!menu.getMarketData().isEmpty()) {
            return menu.getMarketData().get(0).commodityId();
        }
        if (CommodityRegistry.getCommodity("iron") != null) {
            return "iron";
        }
        Collection<Commodity> commodities = CommodityRegistry.getAllCommodities();
        return commodities.isEmpty() ? "" : commodities.iterator().next().getId();
    }

    void rememberUiState() {
        // 测试版优先保证跨世界状态干净，不把 GUI 选择写入静态字段。
    }

    public void setGuiStatus(String message) {
        setStatus(message);
    }

    void setStatus(String message) {
        statusMessage = message;
    }

    static float fitScale(int screenWidth, int screenHeight) {
        float widthScale = Math.max(1.0F, screenWidth - 8.0F) / FinanceGui.PANEL_WIDTH;
        float heightScale = Math.max(1.0F, screenHeight - 8.0F) / FinanceGui.PANEL_HEIGHT;
        return Math.max(0.01F, Math.min(1.0F, Math.min(widthScale, heightScale)));
    }
}
