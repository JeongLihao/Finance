package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManagementAction;
import finance.company.CompanyType;
import finance.company.CompanyStrategy;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertType;
import finance.company.CompanyProposalType;
import finance.stock.ConditionalStockOrderType;
import finance.gui.FinanceMenu;
import finance.gameplay.FinanceScreenMode;
import finance.market.NpcMarketMaker;
import finance.network.*;
import finance.util.FormatUtil;
import finance.chart.MarketInstrumentType;
import finance.chart.MarketSummary;
import finance.client.chart.CandlestickChart;
import finance.client.chart.CandlestickClientCache;
import finance.client.chart.TechnicalIndicatorChart;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 金融操作中心 GUI —— 7 标签页（管理员 8 个）。
 */
/** 金融中心主界面（瘦身后）：只保留状态、生命周期、页签调度与输入分发。
 * 各页签的具体渲染 / 点击逻辑已抽取到同包的 *Tabs 类；绘制基元与主题常量在 FinanceGui，格式化辅助在 FinanceFormat。 */

public class FinanceScreen extends AbstractContainerScreen<FinanceMenu> {


    // ---- 色彩 ----

    // ---- 标签 ----
    static final String[] BASE_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒", "金融"};
    static final String[] ADMIN_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒", "金融", "仪表", "管理"};
    String[] tabNames;
    int[] allowedTabIndices = {0};
    int currentTab = 0;
    boolean isAdmin = false;
    String statusMessage = "";
    String companyStrategyOverride = null;
    int assetSortMode = 0; // 0=价值, 1=收益
    boolean commodityChartVisible;
    boolean stockChartVisible;
    boolean futuresChartVisible;
    int chartLimit = 30;
    int financialSubTab = 0;
    boolean governanceView = false;
    boolean restructuringView = false;
    boolean companyDetailsView = false;
    float layoutScale = 1.0F;


    // ---- 行情标签（国际交易）控件 ----
    String selectedCommodity = "iron";
    EditBox intlQuantityBox;

    // ---- 交易标签控件 ----
    EditBox priceBox;
    EditBox quantityBox;
    EditBox inventoryQuantityBox;

    // ---- 公司标签控件 ----
    EditBox companyNameBox;
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
    int adminSubTab = 0; // 0=从手中添加, 1=常用物品, 2=已注册商品

    // ---- 商品下拉菜单（交易标签 + 行情标签） ----
    boolean dropdownOpen = false;
    CommodityCategory expandedCategory = null;
    int dropdownScroll = 0;
    int adminQuickPage = 0;
    int adminRegisteredPage = 0;

    // ---- 缓存 ----
    List<FinanceMenu.MarketRow> cachedMarketData;
    FinanceMenu.MarketRow cachedSelectedRow;
    String lastSelectedCommodity = "";
    boolean cacheDirty = true;

    List<Commodity> cachedCommodities;
    boolean commodityCacheDirty = true;

    // ---- 分类商品缓存 ----
    Map<CommodityCategory, List<Commodity>> categorizedCommodities;
    Map<CommodityCategory, List<Commodity>> inventoryCategorizedCommodities;
    boolean categorizedCacheDirty = true;

    // ---- 抽取出的页签行为类（与 FinanceScreen 同包，通过 screen 反向引用共享状态） ----
    final MarketTabs marketTabs = new MarketTabs(this);
    final CompanyTabs companyTabs = new CompanyTabs(this);
    final StockTabs stockTabs = new StockTabs(this);
    final ServiceTabs serviceTabs = new ServiceTabs(this);
    final RecordTabs recordTabs = new RecordTabs(this);
    final AdminTabs adminTabs = new AdminTabs(this);
    final MacroTabs macroTabs = new MacroTabs(this);
    /** 宏观经济页签的索引（不在 BASE_TABS/ADMIN_TABS 数组里）。 */
    static final int MACRO_TAB = 12;

    // ---- 供同包 *Tabs 类访问的 protected 继承成员（font/menu 等声明在 MC 包内，跨包子类外不可见） ----
    Font font() { return font; }
    FinanceMenu menu() { return menu; }
    int imageWidth() { return imageWidth; }
    int imageHeight() { return imageHeight; }
    int leftPos() { return leftPos; }
    int topPos() { return topPos; }

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
        configureEntryTabs(menu.getInitialMode());
        adminSubTab = 0;
        governanceView = false;
        restructuringView = false;
        companyDetailsView = false;
        selectedCommodity = chooseInitialCommodity();
        selectedStock = "";

        // 行情标签 — 国际交易数量输入框
        intlQuantityBox = new EditBox(font, leftPos + 40, topPos + FinanceGui.MARKET_TRADE_Y + 53, 48, 16, Component.literal("数量"));
        intlQuantityBox.setMaxLength(8);
        intlQuantityBox.setValue("1");
        intlQuantityBox.setVisible(false);
        addWidget(intlQuantityBox);

        // 交易标签 — 价格/数量输入框
        priceBox = new EditBox(font, leftPos + 58, topPos + FinanceGui.CONTENT_Y + 24, 86, 16, Component.literal("价格"));
        priceBox.setMaxLength(12);
        priceBox.setValue("10");
        priceBox.setVisible(false);
        addWidget(priceBox);

        quantityBox = new EditBox(font, leftPos + 58, topPos + FinanceGui.CONTENT_Y + 50, 86, 16, Component.literal("数量"));
        quantityBox.setMaxLength(8);
        quantityBox.setValue("1");
        quantityBox.setVisible(false);
        addWidget(quantityBox);

        inventoryQuantityBox = new EditBox(font, leftPos + 54, topPos + FinanceGui.CONTENT_Y + 62, 56, 16, Component.literal("数量"));
        inventoryQuantityBox.setMaxLength(8);
        inventoryQuantityBox.setValue("64");
        inventoryQuantityBox.setVisible(false);
        addWidget(inventoryQuantityBox);

        // 公司名称输入框
        companyNameBox = new EditBox(font, leftPos + 70, topPos + FinanceGui.CONTENT_Y + 36, 190, 16, Component.literal("公司名称"));
        companyNameBox.setMaxLength(101);
        companyNameBox.setVisible(false);
        addWidget(companyNameBox);

        stockPriceBox = new EditBox(font, leftPos + 54, topPos + FinanceGui.STOCK_TRADE_Y + 18, 64, 16, Component.literal("价格"));
        stockPriceBox.setMaxLength(12);
        stockPriceBox.setValue("1");
        stockPriceBox.setVisible(false);
        addWidget(stockPriceBox);

        stockQuantityBox = new EditBox(font, leftPos + 54, topPos + FinanceGui.STOCK_TRADE_Y + 38, 64, 16, Component.literal("股数"));
        stockQuantityBox.setMaxLength(8);
        stockQuantityBox.setValue("1");
        stockQuantityBox.setVisible(false);
        addWidget(stockQuantityBox);

        alertPriceBox = new EditBox(font, leftPos + 66, topPos + FinanceGui.CONTENT_Y + 58, 70, 16, Component.literal("提醒价格"));
        alertPriceBox.setMaxLength(12);
        alertPriceBox.setValue("10");
        alertPriceBox.setVisible(false);
        addWidget(alertPriceBox);

        ipoPriceBox = new EditBox(font, leftPos + 58, topPos + FinanceGui.CONTENT_Y + 136, 62, 14, Component.literal("发行价"));
        ipoPriceBox.setMaxLength(12);
        ipoPriceBox.setValue("10");
        ipoPriceBox.setVisible(false);
        addWidget(ipoPriceBox);

        ipoQuantityBox = new EditBox(font, leftPos + 172, topPos + FinanceGui.CONTENT_Y + 136, 72, 14, Component.literal("发行数量"));
        ipoQuantityBox.setMaxLength(12);
        ipoQuantityBox.setValue("4000");
        ipoQuantityBox.setVisible(false);
        addWidget(ipoQuantityBox);

        companyAmountBox = new EditBox(font, leftPos + 58, topPos + FinanceGui.CONTENT_Y + 106, 80, 14, Component.literal("金额"));
        companyAmountBox.setMaxLength(12);
        companyAmountBox.setValue("1000");
        companyAmountBox.setVisible(false);
        addWidget(companyAmountBox);

        // 管理标签输入框
        adminBasePriceBox = new EditBox(font, leftPos + 228, topPos + FinanceGui.CONTENT_Y + 42, 60, 14, Component.literal("价格"));
        adminBasePriceBox.setMaxLength(12);
        adminBasePriceBox.setValue("10");
        adminBasePriceBox.setVisible(false);
        addWidget(adminBasePriceBox);

        int adminFormY = topPos + FinanceGui.CONTENT_Y + 98;
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
            for (FinanceMenu.StockRow row : menu.getStocks()) {
                if (row.symbol().equals(selectedStock)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedStock = menu.getStocks().get(0).symbol();
            }
            FinanceMenu.StockRow selected = this.stockTabs.findSelectedStock();
            if (selected != null) {
                stockPriceBox.setValue(Long.toString(selected.lastPrice()));
            }
        }

        commodityCacheDirty = true;
        categorizedCacheDirty = true;
        updateInputVisibility();
    }

    private void configureEntryTabs(FinanceScreenMode mode) {
        FinanceScreenMode safeMode = mode == null ? FinanceScreenMode.ADVANCED : mode;
        allowedTabIndices = switch (safeMode) {
            case MARKET -> new int[]{0, 1, 2};
            case COMPANY -> new int[]{4};
            case BANK -> new int[]{9};
            case ADVANCED -> new int[]{0, 5, 6, 7, 8, 9, MACRO_TAB};
            case ADMIN -> isAdmin ? new int[]{10, 11} : new int[]{10};
            case WAREHOUSE -> new int[]{3};
            case WALLET -> new int[]{7};
        };
        String[] labels = (isAdmin ? ADMIN_TABS : BASE_TABS);
        tabNames = new String[allowedTabIndices.length];
        for (int i = 0; i < allowedTabIndices.length; i++) {
            int index = allowedTabIndices[i];
            tabNames[i] = index == MACRO_TAB ? "宏观" : index < labels.length ? labels[index] : BASE_TABS[Math.min(index, BASE_TABS.length - 1)];
        }
        currentTab = allowedTabIndices[0];
        if (safeMode == FinanceScreenMode.BANK) financialSubTab = 7;
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
            for (FinanceMenu.MarketRow row : cachedMarketData) {
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
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 21, 0xFFF4F0E2);
        g.fill(leftPos + 1, topPos + 22, leftPos + imageWidth - 1, topPos + 42, FinanceGui.COL_TAB_BG);
        g.fill(leftPos + 1, topPos + 42, leftPos + imageWidth - 1, topPos + 43, FinanceGui.COL_PANEL_BORDER);
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
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            this.marketTabs.renderDropdown(graphics, virtualMouseX, virtualMouseY);
        }
        renderTooltip(graphics, virtualMouseX, virtualMouseY);
        graphics.pose().popPose();
    }


    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        FinanceGui.drawClippedString(font, g, "金融中心", 10, 7, 130, FinanceGui.COL_TEXT);
        if (!statusMessage.isEmpty()) {
            FinanceGui.drawClippedString(font, g, statusMessage, 118, 7, 250, FinanceGui.COL_ACCENT);
        }
        renderTabs(g, mouseX, mouseY);

        switch (currentTab) {
            case 0 -> this.marketTabs.renderMarketTab(g, mouseX, mouseY);
            case 1 -> this.marketTabs.renderTradeTab(g);
            case 2 -> this.recordTabs.renderOrdersTab(g);
            case 3 -> this.recordTabs.renderInventoryTab(g);
            case 4 -> this.companyTabs.renderCompanyTab(g);
            case 5 -> this.stockTabs.renderStockTab(g, mouseX, mouseY);
            case 6 -> this.recordTabs.renderTransactionTab(g);
            case 7 -> this.recordTabs.renderAssetTab(g);
            case 8 -> this.recordTabs.renderAlertTab(g);
            case 9 -> this.serviceTabs.renderFinancialProductsTab(g, mouseX, mouseY);
            case 10 -> { if (isAdmin) this.recordTabs.renderDashboardTab(g); }
            case 11 -> { if (isAdmin) this.adminTabs.renderAdminTab(g); }
            case MACRO_TAB -> this.macroTabs.render(g);
        }
    }


    // ---- 标签栏 ----

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = FinanceGui.TAB_X;
        for (int i = 0; i < tabNames.length; i++) {
            String label = tabNames[i];
            int w = tabWidth();
            boolean active = (allowedTabIndices[i] == currentTab);
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= FinanceGui.TAB_Y && mouseY < FinanceGui.TAB_Y + FinanceGui.TAB_H;

            int textColor = active ? FinanceGui.COL_TEXT : (hovered ? FinanceGui.COL_TEXT : FinanceGui.COL_TEXT_DIM);
            if (active || hovered) {
                g.fill(x, FinanceGui.TAB_Y - 1, x + w, FinanceGui.TAB_Y + FinanceGui.TAB_H, 0xFFECE6D5);
            }
            FinanceGui.drawClippedString(font, g, label, x + 5, FinanceGui.TAB_Y + 2, w - 10, textColor);
            if (active) {
                g.fill(x + 3, FinanceGui.TAB_Y + FinanceGui.TAB_H - 1, x + w - 3, FinanceGui.TAB_Y + FinanceGui.TAB_H, FinanceGui.COL_ACCENT);
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
        for (EditBox box : getVisibleEditBoxes()) {
            box.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

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
                    if (currentTab == MACRO_TAB) this.macroTabs.request();
                    if (currentTab == 9) { if(financialSubTab==6)this.serviceTabs.requestFutures();else if(financialSubTab==7)this.serviceTabs.requestBanks();else if(financialSubTab==8)this.serviceTabs.requestFunds();else if(financialSubTab==9)this.serviceTabs.requestInsurance();else if(financialSubTab==10)this.serviceTabs.requestRegionalRisk();else this.serviceTabs.requestFinancialProducts(); }
                    return true;
                }
                x += w + 3;
            }
        }

        // 下拉菜单点击拦截（在其他标签逻辑之前）
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            if (this.marketTabs.handleDropdownClick(mx, my)) return true;
            // 点击下拉外部关闭
            dropdownOpen = false;
        }

        boolean handled = switch (currentTab) {
            case 0 -> this.marketTabs.handleMarketClick(mx, my);
            case 1 -> this.marketTabs.handleTradeClick(mx, my);
            case 2 -> this.recordTabs.handleOrdersClick(mx, my);
            case 3 -> this.recordTabs.handleInventoryClick(mx, my);
            case 4 -> this.companyTabs.handleCompanyClick(mx, my);
            case 5 -> this.stockTabs.handleStockClick(mx, my);
            case 7 -> this.recordTabs.handleAssetClick(mx, my);
            case 8 -> this.recordTabs.handleAlertClick(mx, my);
            case 9 -> this.serviceTabs.handleFinancialProductsClick(mx, my);
            case 11 -> isAdmin && this.adminTabs.handleAdminClick(mx, my);
            case MACRO_TAB -> this.macroTabs.handleClick(mx, my);
            default -> false;
        };
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
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            int totalHeight = this.marketTabs.calcDropdownTotalHeight();
            int maxScroll = Math.max(0, totalHeight - 116);
            dropdownScroll = Math.max(0, Math.min(maxScroll, dropdownScroll - (int)(delta * 14)));
            return true;
        }
        if (currentTab == 11 && isAdmin) {
            if (adminSubTab == 1) {
                int maxPage = Math.max(0, (adminTabs.QUICK_ITEMS.length - 1) / this.adminTabs.quickItemsPerPage());
                adminQuickPage = Math.max(0, Math.min(maxPage, adminQuickPage - (int) Math.signum(delta)));
                return true;
            }
            if (adminSubTab == 2) {
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
        for (EditBox box : getVisibleEditBoxes()) {
            if (box.isFocused() && box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
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

    void updateInputVisibility() {
        intlQuantityBox.setVisible(currentTab == 0 && !commodityChartVisible);
        boolean trade = (currentTab == 1);
        priceBox.setVisible(trade);
        quantityBox.setVisible(trade);
        inventoryQuantityBox.setVisible(currentTab == 3);
        FinanceMenu.CompanyInfo playerCompany = menu.getPlayerCompany();
        boolean createCompany = (currentTab == 4 && playerCompany == null && !restructuringView);
        boolean governance = currentTab == 4 && playerCompany != null && governanceView;
        boolean companyDetails = currentTab == 4 && playerCompany != null && companyDetailsView;
        boolean ipoCompany = (currentTab == 4 && playerCompany != null && !governanceView
                && !restructuringView && !companyDetailsView);
        boolean manageCompany = ipoCompany;
        if(governance){
            companyNameBox.setX(leftPos+108);
            companyNameBox.setY(topPos+FinanceGui.CONTENT_Y+144);
        } else if (companyDetails) {
            companyNameBox.setX(leftPos + 48);
            companyNameBox.setY(topPos + FinanceGui.CONTENT_Y + 58);
        } else if (createCompany) {
            companyNameBox.setX(leftPos + 70);
            companyNameBox.setY(topPos + FinanceGui.CONTENT_Y + 36);
        } else if (manageCompany) {
            companyNameBox.setX(leftPos + 198);
            companyNameBox.setY(topPos + FinanceGui.CONTENT_Y + 140);
        }
        companyNameBox.setVisible(governance || companyDetails || createCompany);
        ipoPriceBox.setVisible(ipoCompany || companyDetails);
        ipoQuantityBox.setVisible(ipoCompany || companyDetails);
        boolean financial = currentTab == 9;
        stockPriceBox.setVisible(governance||(currentTab == 5 && !stockChartVisible)
                || (financial && !futuresChartVisible && (financialSubTab == 1 || financialSubTab == 2 || financialSubTab == 4 || financialSubTab == 5 || financialSubTab == 6 || financialSubTab == 7 || financialSubTab == 8 || financialSubTab == 9 || financialSubTab == 10)));
        stockQuantityBox.setVisible(governance||(currentTab == 5 && !stockChartVisible)
                || (financial && !futuresChartVisible && (financialSubTab == 1 || financialSubTab == 2 || financialSubTab == 5 || financialSubTab == 6 || financialSubTab == 7 || financialSubTab == 8 || financialSubTab == 9 || financialSubTab == 10)));
        if(governance){
            stockPriceBox.setX(leftPos+48);stockPriceBox.setY(topPos+FinanceGui.CONTENT_Y+164);
            stockQuantityBox.setX(leftPos+168);stockQuantityBox.setY(topPos+FinanceGui.CONTENT_Y+164);
            companyAmountBox.setX(leftPos+288);companyAmountBox.setY(topPos+FinanceGui.CONTENT_Y+164);
        } else if (financial) {
            stockPriceBox.setX(leftPos + 50); stockPriceBox.setY(topPos + 188);
            stockQuantityBox.setX(leftPos + 162); stockQuantityBox.setY(topPos + 188);
            if (financialSubTab == 8) {
                // 基金页：定投间隔输入框放到第二行，避免与"赎回"按钮重叠
                companyAmountBox.setX(leftPos + 312); companyAmountBox.setY(topPos + 214);
            } else {
                companyAmountBox.setX(leftPos + 282); companyAmountBox.setY(topPos + 188);
            }
        } else {
            stockPriceBox.setX(leftPos + 54); stockPriceBox.setY(topPos + FinanceGui.STOCK_TRADE_Y + 18);
            stockQuantityBox.setX(leftPos + 54); stockQuantityBox.setY(topPos + FinanceGui.STOCK_TRADE_Y + 38);
            companyAmountBox.setX(leftPos + 58); companyAmountBox.setY(topPos + FinanceGui.CONTENT_Y + 106);
            ipoPriceBox.setX(leftPos + 58); ipoPriceBox.setY(topPos + FinanceGui.CONTENT_Y + 136);
            ipoQuantityBox.setX(leftPos + 172); ipoQuantityBox.setY(topPos + FinanceGui.CONTENT_Y + 136);
        }
        if (companyDetails) {
            companyAmountBox.setX(leftPos + 48); companyAmountBox.setY(topPos + FinanceGui.CONTENT_Y + 80);
            ipoPriceBox.setX(leftPos + 168); ipoPriceBox.setY(topPos + FinanceGui.CONTENT_Y + 80);
            ipoQuantityBox.setX(leftPos + 288); ipoQuantityBox.setY(topPos + FinanceGui.CONTENT_Y + 80);
        }
        companyAmountBox.setVisible(governance || companyDetails || manageCompany
                || (financial && !futuresChartVisible && financialSubTab != 2 && financialSubTab != 3 && financialSubTab != 9));
        alertPriceBox.setVisible(currentTab == 8);
        boolean admin = (currentTab == 11 && isAdmin);
        boolean adminHand = admin && adminSubTab == 0;
        if (adminHand) {
            adminBasePriceBox.setX(leftPos + 228); adminBasePriceBox.setY(topPos + FinanceGui.CONTENT_Y + 42);
            adminCommodityIdBox.setX(leftPos + 28); adminCommodityIdBox.setY(topPos + FinanceGui.CONTENT_Y + 98);
            adminItemIdBox.setX(leftPos + 140); adminItemIdBox.setY(topPos + FinanceGui.CONTENT_Y + 98);
            adminDisplayNameBox.setX(leftPos + 296); adminDisplayNameBox.setY(topPos + FinanceGui.CONTENT_Y + 98);
        }
        adminCommodityIdBox.setVisible(adminHand);
        adminItemIdBox.setVisible(adminHand);
        adminDisplayNameBox.setVisible(adminHand);
        adminBasePriceBox.setVisible(adminHand);
    }

    private List<EditBox> getVisibleEditBoxes() {
        List<EditBox> list = new ArrayList<>();
        if (currentTab == 0 && !commodityChartVisible) list.add(intlQuantityBox);
        if (currentTab == 1) { list.add(priceBox); list.add(quantityBox); }
        if (currentTab == 3) list.add(inventoryQuantityBox);
        if (currentTab == 4 && !restructuringView && menu.getPlayerCompany() == null) list.add(companyNameBox);
        if (currentTab == 4 && !restructuringView && menu.getPlayerCompany() != null && companyNameBox.isVisible()) list.add(companyNameBox);
        if (currentTab == 4 && !restructuringView && menu.getPlayerCompany() != null && companyAmountBox.isVisible()) list.add(companyAmountBox);
        if (currentTab == 4 && !restructuringView && menu.getPlayerCompany() != null && ipoPriceBox.isVisible()) {
            list.add(ipoPriceBox);
            list.add(ipoQuantityBox);
        }
        if (currentTab == 5 && !stockChartVisible) {
            list.add(stockPriceBox);
            list.add(stockQuantityBox);
        }
        if (currentTab == 8) {
            list.add(alertPriceBox);
        }
        if (currentTab == 9) {
            if (stockPriceBox.isVisible()) list.add(stockPriceBox);
            if (stockQuantityBox.isVisible()) list.add(stockQuantityBox);
            if (companyAmountBox.isVisible()) list.add(companyAmountBox);
        }
        if (currentTab == 11 && isAdmin && adminSubTab == 0) {
            list.add(adminBasePriceBox);
            list.add(adminCommodityIdBox);
            list.add(adminItemIdBox);
            list.add(adminDisplayNameBox);
        }
        return list;
    }

    void ensureCommodityCache() {
        if (commodityCacheDirty || cachedCommodities == null) {
            cachedCommodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
            cachedCommodities.sort(Comparator.comparing(Commodity::getDisplayName));
            commodityCacheDirty = false;
        }
    }

    String chooseInitialCommodity() {
        for (FinanceMenu.MarketRow row : menu.getMarketData()) {
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
