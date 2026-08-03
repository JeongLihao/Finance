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
import finance.market.NpcMarketMaker;
import finance.network.*;
import finance.util.FormatUtil;
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
public class FinanceScreen extends AbstractContainerScreen<FinanceMenu> {

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 250;
    private static final int ROW_HEIGHT = 16;
    private static final int TAB_X = 10;
    private static final int TAB_Y = 24;
    private static final int TAB_H = 16;
    private static final int CONTENT_Y = 46;
    private static final int MARKET_TRADE_Y = 166;
    private static final int STOCK_TRADE_Y = 116;

    // ---- 色彩 ----
    private static final int COL_BG          = 0xFFE7E2D3;
    private static final int COL_TAB_BG      = 0xFFD6D0BE;
    private static final int COL_ACCENT      = 0xFF3F3F3F;
    private static final int COL_GOOD        = 0xFF2D7D32;
    private static final int COL_BAD         = 0xFF9A2F2F;
    private static final int COL_WARN        = 0xFF8A6500;
    private static final int COL_TEXT        = 0xFF202020;
    private static final int COL_TEXT_DIM    = 0xFF555555;
    private static final int COL_ROW_EVEN    = 0xFFF1ECDD;
    private static final int COL_ROW_ODD     = 0xFFE6E0CF;
    private static final int COL_ROW_SELECT  = 0xFFD5E3C7;
    private static final int COL_PANEL_BORDER = 0xFF373737;
    private static final int COL_BUTTON_BG   = 0xFFD0CAB8;
    private static final int COL_DROPDOWN_BG = 0xFFF5F0E1;
    private static final int COL_DROPDOWN_HOVER = 0xFFD5E3C7;
    private static final int COL_CATEGORY    = 0xFFB8B0A0;

    // ---- 标签 ----
    private static final String[] BASE_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒"};
    private static final String[] ADMIN_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "记录", "资产", "提醒", "仪表", "管理"};
    private String[] tabNames;
    private static final CompanyType[] COMPANY_TYPES = CompanyType.values();
    private int currentTab = 0;
    private boolean isAdmin = false;
    private String statusMessage = "";
    private String companyStrategyOverride = null;
    private int assetSortMode = 0; // 0=价值, 1=收益

    // ---- 行情标签（国际交易）控件 ----
    private String selectedCommodity = "iron";
    private EditBox intlQuantityBox;

    // ---- 交易标签控件 ----
    private EditBox priceBox;
    private EditBox quantityBox;
    private EditBox inventoryQuantityBox;

    // ---- 公司标签控件 ----
    private EditBox companyNameBox;
    private CompanyType selectedType = CompanyType.RAW_MATERIALS;

    // ---- 股票标签控件 ----
    private String selectedStock = "";
    private EditBox stockPriceBox;
    private EditBox stockQuantityBox;
    private EditBox alertPriceBox;

    // ---- IPO 控件 ----
    private EditBox ipoPriceBox;
    private EditBox ipoQuantityBox;
    private EditBox companyAmountBox;

    // ---- 管理标签控件 ----
    private EditBox adminCommodityIdBox;
    private EditBox adminItemIdBox;
    private EditBox adminDisplayNameBox;
    private EditBox adminBasePriceBox;
    private CommodityCategory adminCategory = CommodityCategory.RAW_MATERIALS;
    private int adminSubTab = 0; // 0=从手中添加, 1=常用物品, 2=已注册商品

    // ---- 商品下拉菜单（交易标签 + 行情标签） ----
    private boolean dropdownOpen = false;
    private CommodityCategory expandedCategory = null;
    private int dropdownScroll = 0;
    private int adminQuickPage = 0;
    private int adminRegisteredPage = 0;

    // ---- 缓存 ----
    private List<FinanceMenu.MarketRow> cachedMarketData;
    private FinanceMenu.MarketRow cachedSelectedRow;
    private String lastSelectedCommodity = "";
    private boolean cacheDirty = true;

    private List<Commodity> cachedCommodities;
    private boolean commodityCacheDirty = true;

    // ---- 分类商品缓存 ----
    private Map<CommodityCategory, List<Commodity>> categorizedCommodities;
    private Map<CommodityCategory, List<Commodity>> inventoryCategorizedCommodities;
    private boolean categorizedCacheDirty = true;

    public FinanceScreen(FinanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        refreshCache();

        isAdmin = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
        tabNames = isAdmin ? ADMIN_TABS : BASE_TABS;
        currentTab = 0;
        adminSubTab = 0;
        selectedCommodity = chooseInitialCommodity();
        selectedStock = "";

        // 行情标签 — 国际交易数量输入框
        intlQuantityBox = new EditBox(font, leftPos + 40, topPos + MARKET_TRADE_Y + 53, 48, 16, Component.literal("数量"));
        intlQuantityBox.setMaxLength(8);
        intlQuantityBox.setValue("1");
        intlQuantityBox.setVisible(false);
        addWidget(intlQuantityBox);

        // 交易标签 — 价格/数量输入框
        priceBox = new EditBox(font, leftPos + 58, topPos + CONTENT_Y + 24, 86, 16, Component.literal("价格"));
        priceBox.setMaxLength(12);
        priceBox.setValue("10");
        priceBox.setVisible(false);
        addWidget(priceBox);

        quantityBox = new EditBox(font, leftPos + 58, topPos + CONTENT_Y + 50, 86, 16, Component.literal("数量"));
        quantityBox.setMaxLength(8);
        quantityBox.setValue("1");
        quantityBox.setVisible(false);
        addWidget(quantityBox);

        inventoryQuantityBox = new EditBox(font, leftPos + 54, topPos + CONTENT_Y + 62, 56, 16, Component.literal("数量"));
        inventoryQuantityBox.setMaxLength(8);
        inventoryQuantityBox.setValue("64");
        inventoryQuantityBox.setVisible(false);
        addWidget(inventoryQuantityBox);

        // 公司名称输入框
        companyNameBox = new EditBox(font, leftPos + 70, topPos + CONTENT_Y + 36, 190, 16, Component.literal("公司名称"));
        companyNameBox.setMaxLength(32);
        companyNameBox.setVisible(false);
        addWidget(companyNameBox);

        stockPriceBox = new EditBox(font, leftPos + 54, topPos + STOCK_TRADE_Y + 18, 64, 16, Component.literal("价格"));
        stockPriceBox.setMaxLength(12);
        stockPriceBox.setValue("1");
        stockPriceBox.setVisible(false);
        addWidget(stockPriceBox);

        stockQuantityBox = new EditBox(font, leftPos + 54, topPos + STOCK_TRADE_Y + 38, 64, 16, Component.literal("股数"));
        stockQuantityBox.setMaxLength(8);
        stockQuantityBox.setValue("1");
        stockQuantityBox.setVisible(false);
        addWidget(stockQuantityBox);

        alertPriceBox = new EditBox(font, leftPos + 66, topPos + CONTENT_Y + 58, 70, 16, Component.literal("提醒价格"));
        alertPriceBox.setMaxLength(12);
        alertPriceBox.setValue("10");
        alertPriceBox.setVisible(false);
        addWidget(alertPriceBox);

        ipoPriceBox = new EditBox(font, leftPos + 58, topPos + CONTENT_Y + 136, 62, 14, Component.literal("发行价"));
        ipoPriceBox.setMaxLength(12);
        ipoPriceBox.setValue("10");
        ipoPriceBox.setVisible(false);
        addWidget(ipoPriceBox);

        ipoQuantityBox = new EditBox(font, leftPos + 172, topPos + CONTENT_Y + 136, 72, 14, Component.literal("发行数量"));
        ipoQuantityBox.setMaxLength(12);
        ipoQuantityBox.setValue("4000");
        ipoQuantityBox.setVisible(false);
        addWidget(ipoQuantityBox);

        companyAmountBox = new EditBox(font, leftPos + 58, topPos + CONTENT_Y + 106, 80, 14, Component.literal("金额"));
        companyAmountBox.setMaxLength(12);
        companyAmountBox.setValue("1000");
        companyAmountBox.setVisible(false);
        addWidget(companyAmountBox);

        // 管理标签输入框
        adminBasePriceBox = new EditBox(font, leftPos + 228, topPos + CONTENT_Y + 42, 60, 14, Component.literal("价格"));
        adminBasePriceBox.setMaxLength(12);
        adminBasePriceBox.setValue("10");
        adminBasePriceBox.setVisible(false);
        addWidget(adminBasePriceBox);

        int adminFormY = topPos + CONTENT_Y + 144;
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
            FinanceMenu.StockRow selected = findSelectedStock();
            if (selected != null) {
                stockPriceBox.setValue(Long.toString(selected.lastPrice()));
            }
        }

        commodityCacheDirty = true;
        categorizedCacheDirty = true;
        updateInputVisibility();
    }

    private void refreshCache() {
        cachedMarketData = menu.getMarketData();
        cacheDirty = false;
        refreshSelectedRow();
    }

    private void refreshSelectedRow() {
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
    private void rebuildCategorizedCache() {
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
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COL_BG);
        drawSimpleBorder(g, leftPos, topPos, imageWidth, imageHeight, COL_PANEL_BORDER);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 21, 0xFFF4F0E2);
        g.fill(leftPos + 1, topPos + 22, leftPos + imageWidth - 1, topPos + 42, COL_TAB_BG);
        g.fill(leftPos + 1, topPos + 42, leftPos + imageWidth - 1, topPos + 43, COL_PANEL_BORDER);
        RenderSystem.disableBlend();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xB0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (EditBox box : getVisibleEditBoxes()) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
        // 下拉菜单渲染在最上层
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            renderDropdown(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawClippedString(g, "金融中心", 10, 7, 130, COL_TEXT);
        if (!statusMessage.isEmpty()) {
            drawClippedString(g, statusMessage, 118, 7, 250, COL_ACCENT);
        }
        renderTabs(g, mouseX, mouseY);

        switch (currentTab) {
            case 0 -> renderMarketTab(g);
            case 1 -> renderTradeTab(g);
            case 2 -> renderOrdersTab(g);
            case 3 -> renderInventoryTab(g);
            case 4 -> renderCompanyTab(g);
            case 5 -> renderStockTab(g);
            case 6 -> renderTransactionTab(g);
            case 7 -> renderAssetTab(g);
            case 8 -> renderAlertTab(g);
            case 9 -> { if (isAdmin) renderDashboardTab(g); }
            case 10 -> { if (isAdmin) renderAdminTab(g); }
        }
    }

    // ---- 标签栏 ----

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = TAB_X;
        for (int i = 0; i < tabNames.length; i++) {
            String label = tabNames[i];
            int w = tabWidth();
            boolean active = (i == currentTab);
            boolean hovered = mouseX >= leftPos + x && mouseX < leftPos + x + w
                    && mouseY >= topPos + TAB_Y && mouseY < topPos + TAB_Y + TAB_H;

            int textColor = active ? COL_TEXT : (hovered ? COL_TEXT : COL_TEXT_DIM);
            if (active || hovered) {
                g.fill(x, TAB_Y - 1, x + w, TAB_Y + TAB_H, 0xFFECE6D5);
            }
            drawClippedString(g, label, x + 5, TAB_Y + 2, w - 10, textColor);
            if (active) {
                g.fill(x + 3, TAB_Y + TAB_H - 1, x + w - 3, TAB_Y + TAB_H, COL_ACCENT);
            }
            x += w + 3;
        }
    }

    private int tabWidth() {
        return isAdmin ? 32 : 40;
    }

    // ================================================================
    // 标签 0: 市场行情 + 国际交易
    // ================================================================

    private void renderMarketTab(GuiGraphics g) {
        if (cacheDirty) refreshCache();
        refreshSelectedRow();

        int tableX = 8;
        int tableW = imageWidth - 16;
        int headerY = CONTENT_Y;
        int size = cachedMarketData.size();

        int rowY = headerY + 16;
        int visibleRows = Math.min(size, Math.max(1, (MARKET_TRADE_Y - rowY - 8) / ROW_HEIGHT));
        int tableBottom = rowY + visibleRows * ROW_HEIGHT;
        g.fill(tableX, headerY - 2, tableX + tableW, tableBottom, 0xFFF1ECDD);
        g.fill(tableX, headerY - 2, tableX + tableW, headerY - 1, COL_PANEL_BORDER);
        g.fill(tableX, tableBottom, tableX + tableW, tableBottom + 1, COL_PANEL_BORDER);

        drawHeader(g, "商品", 12, headerY);
        drawHeader(g, "中间价", 74, headerY);
        drawHeader(g, "收购", 126, headerY);
        drawHeader(g, "出售", 178, headerY);
        drawHeader(g, "涨跌", 230, headerY);
        drawHeader(g, "成交", 282, headerY);
        drawHeader(g, "库存", 326, headerY);

        g.fill(tableX + 1, headerY + 11, tableX + tableW - 1, headerY + 12, COL_PANEL_BORDER);

        if (size == 0) {
            g.drawString(font, "暂无数据", 12, headerY + 16, COL_TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            FinanceMenu.MarketRow row = cachedMarketData.get(i);
            int y = rowY + i * ROW_HEIGHT;
            boolean selected = row.commodityId().equals(selectedCommodity);

            int bg = selected ? COL_ROW_SELECT : ((i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            g.fill(tableX + 1, y, tableX + tableW - 1, y + ROW_HEIGHT, bg);
            if (selected) {
                g.fill(tableX + 1, y, tableX + 3, y + ROW_HEIGHT, COL_ACCENT);
            }

            // 物品图标
            Commodity commodity = CommodityRegistry.getCommodity(row.commodityId());
            renderItemIcon(g, commodity, 14, y + 4);

            drawClippedString(g, commodityDisplayName(row.commodityId()), 30, y + 5, 40, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, Long.toString(row.midPrice()), 74, y + 5, 46, COL_TEXT);
            drawClippedString(g, Long.toString(row.bidPrice()), 126, y + 5, 46, COL_GOOD);
            drawClippedString(g, Long.toString(row.askPrice()), 178, y + 5, 46, COL_WARN);
            drawClippedString(g, FormatUtil.formatPercent(row.dayChange()), 230, y + 5, 46, changeColor(row.dayChange()));
            drawClippedString(g, Integer.toString(row.dayVolume()), 282, y + 5, 38, COL_TEXT_DIM);
            drawClippedString(g, Integer.toString(row.marketStock()), 326, y + 5, 80, COL_TEXT_DIM);
        }

        // ---- 国际交易区域 ----
        int tradeY = MARKET_TRADE_Y;
        g.drawString(font, "国际交易", 10, tradeY + 1, COL_TEXT, false);

        // 显示当前选中商品名（不是按钮行）
        String selName = commodityDisplayName(selectedCommodity);
        drawClippedString(g, "当前: " + selName, 82, tradeY + 1, 120, COL_ACCENT);

        // 信息行
        if (cachedSelectedRow != null) {
            drawClippedString(g, "市场收购: " + cachedSelectedRow.bidPrice(), 10, tradeY + 18, 120, COL_GOOD);
            drawClippedString(g, "市场出售: " + cachedSelectedRow.askPrice(), 140, tradeY + 18, 120, COL_WARN);
            drawTrendGraph(g, cachedSelectedRow.priceHistory(), 260, tradeY + 1, 108, 14, changeColor(cachedSelectedRow.dayChange()));
            drawClippedString(g, "高 " + cachedSelectedRow.dayHigh() + " 低 " + cachedSelectedRow.dayLow(),
                    10, tradeY + 44, 150, COL_TEXT_DIM);
            drawClippedString(g, "量 " + cachedSelectedRow.dayVolume(), 170, tradeY + 44, 70, COL_TEXT_DIM);
        }
        drawClippedString(g, "余额: " + menu.getBalance(), 260, tradeY + 18, 110, COL_TEXT);
        int owned = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
        drawClippedString(g, "持有: " + owned, 10, tradeY + 32, 130, COL_TEXT_DIM);
        int intlQty = Math.max(1, parseInt(intlQuantityBox.getValue()));
        if (cachedSelectedRow != null) {
            long buyTotal = safeMultiply(cachedSelectedRow.askPrice(), intlQty);
            long sellTotal = safeMultiply(cachedSelectedRow.bidPrice(), intlQty);
            drawClippedString(g, "买入总价: " + buyTotal, 130, tradeY + 32, 112, COL_GOOD);
            drawClippedString(g, "卖出总价: " + sellTotal, 250, tradeY + 32, 112, COL_WARN);
        }

        // 数量 + 买卖按钮
        g.drawString(font, "数量:", 10, tradeY + 53, COL_TEXT_DIM, false);
        drawButton(g, "-", 100, tradeY + 52, 20, false);
        drawButton(g, "+", 124, tradeY + 52, 20, false);
        drawButton(g, "-10", 152, tradeY + 52, 30, false);
        drawButton(g, "+10", 186, tradeY + 52, 30, false);

        drawFilledButton(g, "从市场买入", 226, tradeY + 52, 68, COL_GOOD);
        drawFilledButton(g, "卖给市场", 300, tradeY + 52, 68, COL_BAD);
    }

    // ================================================================
    // 标签 1: 交易下单（分类下拉选择商品）
    // ================================================================

    private void renderTradeTab(GuiGraphics g) {
        if (cacheDirty) refreshCache();
        refreshSelectedRow();

        // ---- 商品选择下拉按钮 ----
        drawSectionTitle(g, "商品选择", 10, CONTENT_Y);
        String btnLabel = commodityDisplayName(selectedCommodity) + " ▾";
        drawButton(g, btnLabel, 82, CONTENT_Y, 140, dropdownOpen);

        int cardY = CONTENT_Y + 20;
        int cardH = 66;
        g.fill(8, cardY, imageWidth - 8, cardY + cardH, 0xFFF1ECDD);
        g.fill(8, cardY, imageWidth - 8, cardY + 1, COL_PANEL_BORDER);
        g.fill(8, cardY + cardH, imageWidth - 8, cardY + cardH + 1, COL_PANEL_BORDER);

        g.drawString(font, "价格:", 12, cardY + 6, COL_TEXT_DIM, false);
        g.drawString(font, "数量:", 12, cardY + 30, COL_TEXT_DIM, false);

        drawButton(g, "-", 170, cardY + 4, 20, false);
        drawButton(g, "+", 194, cardY + 4, 20, false);
        drawButton(g, "-10", 222, cardY + 4, 30, false);
        drawButton(g, "+10", 256, cardY + 4, 30, false);

        drawButton(g, "-", 170, cardY + 30, 20, false);
        drawButton(g, "+", 194, cardY + 30, 20, false);
        drawButton(g, "-10", 222, cardY + 30, 30, false);
        drawButton(g, "+10", 256, cardY + 30, 30, false);

        int infoY = cardY + cardH + 6;
        if (cachedSelectedRow != null) {
            drawClippedString(g, "市场收购: " + cachedSelectedRow.bidPrice(), 10, infoY, 126, COL_GOOD);
            drawClippedString(g, "市场出售: " + cachedSelectedRow.askPrice(), 146, infoY, 126, COL_WARN);
        } else {
            drawClippedString(g, "该商品未接入国际市场，仅支持玩家间交易", 10, infoY, 260, COL_WARN);
        }
        drawClippedString(g, "余额: " + menu.getBalance(), 10, infoY + 16, 180, COL_TEXT);
        int owned = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
        drawClippedString(g, "持有: " + owned, 210, infoY + 16, 150, COL_TEXT_DIM);
        long orderTotal = safeMultiply(parseLong(priceBox.getValue()), Math.max(1, parseInt(quantityBox.getValue())));
        drawClippedString(g, "订单总价: " + orderTotal, 210, infoY + 30, 150, COL_ACCENT);

        drawFilledButton(g, "提交买单", 10, 186, 84, COL_ACCENT);
        drawFilledButton(g, "提交卖单", 102, 186, 84, COL_BAD);
    }

    // ================================================================
    // 下拉菜单渲染
    // ================================================================

    /** 渲染分类下拉面板（在行情/交易标签中使用） */
    private void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (categorizedCacheDirty) rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap =
                (currentTab == 1) ? inventoryCategorizedCommodities : categorizedCommodities;

        int localX = 82;
        int localY = (currentTab == 0) ? MARKET_TRADE_Y + 14 : CONTENT_Y + 14;
        int ddX = leftPos + localX;
        int ddY = topPos + localY;
        int ddW = 200;
        int ddH = 120;

        // 背景
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, COL_DROPDOWN_BG);
        drawSimpleBorder(g, ddX, ddY, ddW, ddH, COL_PANEL_BORDER);

        int localMx = mouseX - ddX;
        int localMy = mouseY - ddY;
        int y = 2 - dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题
            if (y + 12 > 0 && y < maxY) {
                g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 12, COL_CATEGORY);
                g.drawString(font, "▸ " + cat.getDisplayName(), ddX + 6, ddY + y + 2, COL_TEXT, false);
            }
            y += 14;

            // 展开的分类显示其商品
            if (expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        boolean hovered = localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14;
                        if (hovered) {
                            g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 14, COL_DROPDOWN_HOVER);
                        }
                        renderItemIcon(g, c, ddX + 8, ddY + y + 1);
                        drawClippedString(g, c.getDisplayName(), ddX + 26, ddY + y + 3, 100, COL_TEXT);
                        drawClippedString(g, Long.toString(c.getBasePrice()), ddX + 140, ddY + y + 3, 50, COL_GOOD);
                    }
                    y += 14;
                }
            }
        }

        // 滚动条
        int totalHeight = calcDropdownTotalHeight();
        if (totalHeight > ddH) {
            int barH = Math.max(10, ddH * ddH / totalHeight);
            int barY = (int)((long) dropdownScroll * (ddH - barH) / (totalHeight - ddH));
            g.fill(ddX + ddW - 5, ddY + barY, ddX + ddW - 2, ddY + barY + barH, COL_PANEL_BORDER);
        }
    }

    private int calcDropdownTotalHeight() {
        Map<CommodityCategory, List<Commodity>> activeMap =
                (currentTab == 1) ? inventoryCategorizedCommodities : categorizedCommodities;
        int h = 0;
        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;
            h += 14; // 分类标题
            if (expandedCategory == cat) {
                h += items.size() * 14;
            }
        }
        return h;
    }

    // ================================================================
    // 标签 2: 订单管理
    // ================================================================

    private void renderOrdersTab(GuiGraphics g) {
        int tableX = 8;
        int tableW = imageWidth - 16;
        int headerY = CONTENT_Y;

        List<FinanceMenu.OrderRow> orders = menu.getPlayerOrders();
        g.fill(tableX, headerY - 2, tableX + tableW, imageHeight - 8, 0xFFF1ECDD);
        drawSimpleBorder(g, tableX, headerY - 2, tableW, imageHeight - headerY - 6, COL_PANEL_BORDER);
        g.fill(tableX + 1, headerY - 1, tableX + tableW - 1, headerY + 13, 0xFFE0DAC9);
        drawHeader(g, "#", 12, headerY + 2);
        drawHeader(g, "商品", 34, headerY + 2);
        drawHeader(g, "方向", 92, headerY + 2);
        drawHeader(g, "单价", 130, headerY + 2);
        drawHeader(g, "数量", 178, headerY + 2);
        drawHeader(g, "归属", 226, headerY + 2);
        drawHeader(g, "操作", 286, headerY + 2);
        g.fill(tableX + 1, headerY + 13, tableX + tableW - 1, headerY + 14, COL_PANEL_BORDER);
        if (orders.isEmpty()) {
            g.drawString(font, "暂无挂单。玩家挂出的买单和卖单会显示在这里。", 12, headerY + 20, COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 16;
        int maxRows = Math.min(orders.size(), Math.max(1, (imageHeight - rowY - 10) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.OrderRow row = orders.get(i);
            int y = rowY + i * ROW_HEIGHT;
            g.fill(tableX + 1, y, tableX + tableW - 1, y + ROW_HEIGHT, (i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, String.valueOf(i + 1), 12, y + 3, 20, COL_TEXT_DIM);
            drawClippedString(g, commodityDisplayName(row.commodityId()), 34, y + 3, 54, COL_TEXT);
            boolean isBuy = "BUY".equals(row.type());
            drawClippedString(g, isBuy ? "买单" : "卖单", 92, y + 3, 34, isBuy ? COL_GOOD : COL_BAD);
            drawClippedString(g, Long.toString(row.price()), 130, y + 3, 44, COL_TEXT);
            drawClippedString(g, Integer.toString(row.quantity()), 178, y + 3, 42, COL_TEXT);
            drawClippedString(g, row.ownedByPlayer() ? "自己" : "其他", 226, y + 3, 48, row.ownedByPlayer() ? COL_ACCENT : COL_TEXT_DIM);
            drawButton(g, isBuy ? "卖出" : "买入", 286, y + 1, 38, false);
            if (row.ownedByPlayer()) {
                drawButton(g, "取消", 330, y + 1, 38, false);
            }
        }
    }

    // ================================================================
    // 标签 3: 库存与账户
    // ================================================================

    private void renderInventoryTab(GuiGraphics g) {
        int cardY = CONTENT_Y;
        int cardH = 38;
        g.fill(8, cardY, imageWidth - 8, cardY + cardH, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, cardY, imageWidth - 16, cardH, COL_PANEL_BORDER);

        drawSectionTitle(g, "账户", 10, cardY + 2);
        g.drawString(font, "可用: ", 12, cardY + 18, COL_TEXT_DIM, false);
        drawClippedString(g, Long.toString(menu.getBalance()), 46, cardY + 18, 120, COL_GOOD);
        g.drawString(font, "冻结: ", 190, cardY + 18, COL_TEXT_DIM, false);
        drawClippedString(g, Long.toString(menu.getFrozenBalance()), 224, cardY + 18, 120, COL_WARN);

        int invY = cardY + cardH + 6;
        int invH = imageHeight - invY - 4;
        g.fill(8, invY, imageWidth - 8, invY + invH, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, invY, imageWidth - 16, invH, COL_PANEL_BORDER);

        drawSectionTitle(g, "商品库存（点击选择）", 10, invY + 2);
        drawClippedString(g, "当前: " + commodityDisplayName(selectedCommodity), 122, invY + 3, 90, COL_ACCENT);
        g.drawString(font, "数量:", 12, invY + 22, COL_TEXT_DIM, false);
        drawButton(g, "-64", 116, invY + 20, 32, false);
        drawButton(g, "+64", 152, invY + 20, 32, false);
        drawFilledButton(g, "存入", 246, invY + 20, 44, COL_GOOD);
        drawFilledButton(g, "取出", 296, invY + 20, 44, COL_ACCENT);

        int headerLineY = invY + 42;
        drawHeader(g, "商品", 14, headerLineY);
        drawHeader(g, "库存", 140, headerLineY);
        drawHeader(g, "背包", 210, headerLineY);
        g.fill(8, headerLineY + 11, imageWidth - 8, headerLineY + 12, COL_PANEL_BORDER);

        Map<String, Integer> inv = menu.getPlayerInventory();
        Map<String, Integer> mcInv = menu.getMcInventory();
        Set<String> allCommodityIds = new LinkedHashSet<>();
        allCommodityIds.addAll(inv.keySet());
        allCommodityIds.addAll(mcInv.keySet());

        if (allCommodityIds.isEmpty()) {
            g.drawString(font, "暂无商品（可通过管理标签添加常用物品）", 12, invY + 32, COL_TEXT_DIM, false);
            return;
        }

        int y = headerLineY + 14;
        int maxY = invY + invH - ROW_HEIGHT;
        for (String commodityId : allCommodityIds) {
            if (y > maxY) break;
            boolean selected = commodityId.equals(selectedCommodity);
            int bg = selected ? COL_ROW_SELECT : ((y / ROW_HEIGHT) % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD);
            g.fill(9, y, imageWidth - 9, y + ROW_HEIGHT, bg);
            if (selected) {
                g.fill(9, y, 11, y + ROW_HEIGHT, COL_ACCENT);
            }
            Commodity c = CommodityRegistry.getCommodity(commodityId);
            renderItemIcon(g, c, 14, y + 1);
            drawClippedString(g, commodityDisplayName(commodityId), 28, y + 3, 106, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, String.valueOf(inv.getOrDefault(commodityId, 0)), 140, y + 3, 60, COL_TEXT);
            drawClippedString(g, String.valueOf(mcInv.getOrDefault(commodityId, 0)), 210, y + 3, 60, COL_TEXT_DIM);
            y += ROW_HEIGHT;
        }
    }

    // ================================================================
    // 标签 4: 公司管理
    // ================================================================

    private void renderCompanyTab(GuiGraphics g) {
        FinanceMenu.CompanyInfo company = menu.getPlayerCompany();
        int cardY = CONTENT_Y;
        if (company != null) {
            int cardH = imageHeight - cardY - 8;
            g.fill(8, cardY, imageWidth - 8, cardY + cardH, 0xFFE0E0E0);
            drawSimpleBorder(g, 8, cardY, imageWidth - 16, cardH, COL_PANEL_BORDER);

            drawSectionTitle(g, "我的公司", 10, cardY + 2);
            g.drawString(font, "名称", 12, cardY + 18, COL_TEXT_DIM, false);
            drawClippedString(g, company.name(), 58, cardY + 18, 240, COL_TEXT);
            g.drawString(font, "行业", 12, cardY + 32, COL_TEXT_DIM, false);
            drawClippedString(g, company.type(), 58, cardY + 32, 180, COL_ACCENT);
            g.drawString(font, "状态", 252, cardY + 32, COL_TEXT_DIM, false);
            String statusText = company.bankruptcyRisk()
                    ? "风险D" + company.bankruptcyRiskStartDay()
                    : (company.isPublic() ? "已上市" : "未上市");
            drawClippedString(g, statusText, 298, cardY + 32, 70,
                    company.bankruptcyRisk() ? COL_BAD : (company.isPublic() ? COL_GOOD : COL_WARN));
            g.drawString(font, "现金", 12, cardY + 46, COL_TEXT_DIM, false);
            drawClippedString(g, Long.toString(company.cash()), 58, cardY + 46, 100, COL_GOOD);
            g.drawString(font, "公开估值", 176, cardY + 46, COL_TEXT_DIM, false);
            drawClippedString(g, company.isPublic() ? Long.toString(company.totalValue()) : "未披露",
                    240, cardY + 46, 120, company.isPublic() ? COL_WARN : COL_TEXT_DIM);
            g.drawString(font, "策略", 12, cardY + 64, COL_TEXT_DIM, false);
            drawButton(g, "< " + displayCompanyStrategy(company) + " >", 58, cardY + 62, 118, true);
            drawClippedString(g, "出售 " + Math.round(company.autoSellRatio() * 100) + "%", 190, cardY + 65, 70, COL_TEXT_DIM);
            drawButton(g, "-10%", 262, cardY + 62, 38, false);
            drawButton(g, "+10%", 306, cardY + 62, 38, false);

            drawClippedString(g, "生产线 Lv." + company.productionLevel(), 12, cardY + 86, 80, COL_TEXT_DIM);
            drawFilledButton(g, "升级", 96, cardY + 82, 36, COL_GOOD);
            drawClippedString(g, "仓储 Lv." + company.storageLevel(), 146, cardY + 86, 70, COL_TEXT_DIM);
            drawFilledButton(g, "升级", 216, cardY + 82, 36, COL_GOOD);
            drawClippedString(g, "管理 Lv." + company.managementLevel(), 266, cardY + 86, 70, COL_TEXT_DIM);
            drawFilledButton(g, "升级", 336, cardY + 82, 36, COL_GOOD);

            g.drawString(font, "金额", 12, cardY + 110, COL_TEXT_DIM, false);
            drawFilledButton(g, "注资", 150, cardY + 106, 48, COL_GOOD);
            drawFilledButton(g, "提取", 206, cardY + 106, 48, COL_ACCENT);
            if (!company.isPublic()) {
                g.drawString(font, "发行价", 12, cardY + 138, COL_TEXT_DIM, false);
                g.drawString(font, "发行数", 126, cardY + 138, COL_TEXT_DIM, false);
                long price = Math.max(1, parseLong(ipoPriceBox.getValue()));
                long quantity = Math.max(1, parseLong(ipoQuantityBox.getValue()));
                drawClippedString(g, "募资 " + safeMultiplyLong(price, quantity), 252, cardY + 138, 70, COL_ACCENT);
                drawFilledButton(g, "提交IPO", 322, cardY + 136, 50, COL_GOOD);
            } else {
                FinanceMenu.CompanyFinancingRow financing = financingForCompany(company.companyId());
                g.drawString(font, "增发价", 12, cardY + 138, COL_TEXT_DIM, false);
                g.drawString(font, "增发数", 126, cardY + 138, COL_TEXT_DIM, false);
                if (financing == null) {
                    long price = Math.max(1, parseLong(ipoPriceBox.getValue()));
                    long quantity = Math.max(1, parseLong(ipoQuantityBox.getValue()));
                    long target = Math.max(1, parseLong(companyAmountBox.getValue()));
                    drawClippedString(g, "目标 " + target + " / 上限 " + safeMultiplyLong(price, quantity),
                            252, cardY + 124, 120, COL_TEXT_DIM);
                    drawFilledButton(g, "发起融资", 322, cardY + 136, 50, COL_GOOD);
                } else {
                    drawClippedString(g, "融资: " + financing.raisedAmount() + "/" + financing.fundingTarget()
                                    + "  股 " + financing.subscribedShares() + "/" + financing.issueQuantity(),
                            252, cardY + 124, 120, COL_ACCENT);
                    drawClippedString(g, "发行价 " + financing.issuePrice() + " 截止第 " + financing.deadlineMcDay() + " 天",
                            252, cardY + 138, 120, COL_TEXT_DIM);
                }
            }
            int reportY = cardY + 140;
            drawSectionTitle(g, "最新财报", 10, reportY);
            drawClippedString(g, "收入 " + company.reportRevenue() + " 支出 " + company.reportExpenses()
                            + " 净利 " + signedLong(company.reportNetProfit()),
                    12, reportY + 14, 188, company.reportNetProfit() >= 0 ? COL_GOOD : COL_BAD);
            drawClippedString(g, "提案文本", 198, reportY, 48, COL_TEXT_DIM);
            int proposalY = reportY + 28;
            drawSectionTitle(g, "股东提案", 10, proposalY);
            if (company.isPublic()) {
                drawFilledButton(g, "分红", 70, proposalY - 2, 38, COL_ACCENT);
                drawFilledButton(g, "增发", 112, proposalY - 2, 38, COL_GOOD);
                drawFilledButton(g, "改名", 154, proposalY - 2, 38, COL_WARN);
                drawFilledButton(g, "用途", 196, proposalY - 2, 38, COL_ACCENT);
                drawClippedString(g, "文本框=新名/用途；金额=比例/目标/预算",
                        240, proposalY, 138, COL_TEXT_DIM);
            }
            List<FinanceMenu.CompanyProposalRow> proposals = proposalsForCompany(company.companyId());
            int py = proposalY + 16;
            if (proposals.isEmpty()) {
                drawClippedString(g, company.isPublic() ? "暂无提案。" : "公司上市后可创建股东提案。",
                        12, py, 180, COL_TEXT_DIM);
            } else {
                int maxProposals = Math.min(proposals.size(), Math.max(0, (imageHeight - py - 4) / ROW_HEIGHT));
                for (int i = 0; i < maxProposals; i++) {
                    FinanceMenu.CompanyProposalRow row = proposals.get(i);
                    boolean active = "ACTIVE".equals(row.status());
                    drawClippedString(g, displayProposalType(row.type()) + " " + displayProposalValues(row),
                            12, py, 120, active ? COL_TEXT : COL_TEXT_DIM);
                    drawClippedString(g, "赞 " + row.yesVotes() + " 反 " + row.noVotes()
                                    + " 需" + Math.round(row.passRatio() * 100) + "%",
                            138, py, 92, COL_TEXT_DIM);
                    drawClippedString(g, active ? "截止" + row.endMcDay() : displayProposalStatus(row),
                            236, py, 58, active ? COL_WARN : ("PASSED".equals(row.status()) ? COL_GOOD : COL_BAD));
                    if (active && !row.playerVoted()) {
                        drawButton(g, "赞成", 300, py - 2, 36, false);
                        drawButton(g, "反对", 340, py - 2, 36, false);
                    }
                    py += ROW_HEIGHT;
                }
            }
        } else {
            g.fill(8, cardY, imageWidth - 8, cardY + 96, 0xFFE0E0E0);
            drawSimpleBorder(g, 8, cardY, imageWidth - 16, 96, COL_PANEL_BORDER);

            drawSectionTitle(g, "创建公司", 10, cardY + 2);
            g.drawString(font, "费用", 12, cardY + 18, COL_TEXT_DIM, false);
            g.drawString(font, "10,000", 55, cardY + 18, COL_WARN, false);

            g.drawString(font, "名称", 12, cardY + 36, COL_TEXT_DIM, false);

            g.drawString(font, "行业", 12, cardY + 58, COL_TEXT_DIM, false);
            String typeLabel = "< " + selectedType.getDisplayName() + " >";
            drawButton(g, typeLabel, 55, cardY + 58, 120, true);

            String typeDesc = getTypeDescription(selectedType);
            drawClippedString(g, typeDesc, 55, cardY + 76, 300, COL_TEXT_DIM);

            drawFilledButton(g, "创建公司", 300, cardY + 58, 76, COL_GOOD);
            drawClippedString(g, "创建后可在这里管理经营状态；上市后才会进入股票市场。",
                    12, cardY + 108, imageWidth - 24, COL_TEXT_DIM);
        }
    }

    // ================================================================
    // 标签 5: 股票
    // ================================================================

    private void renderStockTab(GuiGraphics g) {
        int headerY = CONTENT_Y;
        drawHeader(g, "代码", 12, headerY);
        drawHeader(g, "名称", 62, headerY);
        drawHeader(g, "价格", 150, headerY);
        drawHeader(g, "估值", 196, headerY);
        drawHeader(g, "涨跌", 240, headerY);
        drawHeader(g, "成交", 292, headerY);
        drawHeader(g, "流通", 334, headerY);

        List<FinanceMenu.StockRow> stocks = menu.getStocks();
        if (stocks.isEmpty()) {
            g.drawString(font, "暂无股票。系统公司初始化后会自动生成股票。", 12, headerY + 18, COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 16;
        int maxRows = Math.min(stocks.size(), Math.max(1, (STOCK_TRADE_Y - rowY - 10) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.StockRow row = stocks.get(i);
            int y = rowY + i * ROW_HEIGHT;
            boolean selected = row.symbol().equals(selectedStock);
            g.fill(8, y, imageWidth - 8, y + ROW_HEIGHT,
                    selected ? COL_ROW_SELECT : (i % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD));
            if (selected) g.fill(8, y, 10, y + ROW_HEIGHT, COL_ACCENT);
            drawClippedString(g, displayStockSymbol(row), 12, y + 5, 46, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, row.name(), 62, y + 5, 82, COL_TEXT);
            drawClippedString(g, Long.toString(row.lastPrice()), 150, y + 5, 40, COL_TEXT);
            drawClippedString(g, Long.toString(row.fairValue()), 196, y + 5, 38, COL_TEXT_DIM);
            drawClippedString(g, FormatUtil.formatPercent(row.dayChange()), 240, y + 5, 46, changeColor(row.dayChange()));
            drawClippedString(g, Long.toString(row.dayVolume()), 292, y + 5, 36, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.availableShares()), 334, y + 5, 80, COL_TEXT_DIM);
        }

        int tradeY = STOCK_TRADE_Y;
        drawSimpleSeparator(g, 8, tradeY - 6, imageWidth - 16);
        drawSectionTitle(g, "限价委托", 10, tradeY);
        FinanceMenu.StockRow selected = findSelectedStock();
        if (selected != null) {
            drawClippedString(g, displayStockSymbol(selected) + " " + selected.name(), 92, tradeY, 160, COL_TEXT);
            drawClippedString(g, "现价: " + selected.lastPrice(), 260, tradeY, 90, COL_TEXT_DIM);
            drawClippedString(g, "高 " + selected.dayHigh() + " 低 " + selected.dayLow() + " 量 " + selected.dayVolume(),
                    12, tradeY + 13, 190, COL_TEXT_DIM);
            drawTrendGraph(g, selected.priceHistory(), 258, tradeY + 14, 102, 18, changeColor(selected.dayChange()));
            long total = safeMultiply(Math.max(1, parseLong(stockPriceBox.getValue())), Math.max(1, parseInt(stockQuantityBox.getValue())));
            FinanceMenu.StockHoldingRow holding = findHolding(selected.symbol());
            drawClippedString(g, "总价: " + total, 12, tradeY + 52, 100, COL_ACCENT);
            if (holding != null) {
                drawClippedString(g, "持仓 " + holding.quantity() + "  成本 " + holding.averageCost(), 128, tradeY + 52, 160, COL_TEXT_DIM);
            } else {
                drawClippedString(g, "当前无持仓", 128, tradeY + 52, 90, COL_TEXT_DIM);
            }
            drawClippedString(g, "预息/股 " + selected.expectedDividendPerShare(),
                    288, tradeY + 52, 82, COL_WARN);
            drawClippedString(g, "上次 " + selected.lastDividendPerShare() + "/股 总 " + selected.lastDividendTotal(),
                    206, tradeY + 65, 160, COL_TEXT_DIM);
        }
        g.drawString(font, "价格:", 12, tradeY + 19, COL_TEXT_DIM, false);
        g.drawString(font, "股数:", 12, tradeY + 39, COL_TEXT_DIM, false);
        drawButton(g, "-", 124, tradeY + 36, 20, false);
        drawButton(g, "+", 148, tradeY + 36, 20, false);
        drawButton(g, "+10", 176, tradeY + 36, 30, false);
        drawFilledButton(g, "买单", 234, tradeY + 36, 58, COL_GOOD);
        drawFilledButton(g, "卖单", 300, tradeY + 36, 58, COL_BAD);
        g.drawString(font, "条件:", 12, tradeY + 66, COL_TEXT_DIM, false);
        drawFilledButton(g, "设止盈", 54, tradeY + 64, 58, COL_GOOD);
        drawFilledButton(g, "设止损", 120, tradeY + 64, 58, COL_BAD);
        drawClippedString(g, "使用上方价格/股数，触发后自动提交卖单", 188, tradeY + 66, 180, COL_TEXT_DIM);

        FinanceMenu.CompanyFinancingRow financing = selectedFinancingProject();
        if (financing != null) {
            int fy = tradeY + 78;
            drawClippedString(g, "融资 " + financing.raisedAmount() + "/" + financing.fundingTarget()
                            + " 股 " + financing.subscribedShares() + "/" + financing.issueQuantity()
                            + " 价 " + financing.issuePrice(),
                    12, fy, 230, COL_ACCENT);
            drawClippedString(g, "我已认 " + financing.playerSubscribedShares(), 246, fy, 48, COL_TEXT_DIM);
            drawFilledButton(g, "认购", 300, fy - 2, 42, COL_GOOD);
        }

        int orderY = tradeY + (financing != null ? 98 : 82);
        drawSectionTitle(g, "当前委托", 10, orderY);
        List<FinanceMenu.StockOrderRow> orders = selectedStockOrders();
        int shownOrders = 0;
        if (orders.isEmpty()) {
            g.drawString(font, "该股票暂无活跃委托", 12, orderY + 16, COL_TEXT_DIM, false);
        } else {
            int y = orderY + 16;
            int reservedForConditional = 30;
            int maxOrders = Math.min(orders.size(), Math.max(1, (imageHeight - y - reservedForConditional) / ROW_HEIGHT));
            for (int i = 0; i < maxOrders; i++) {
                FinanceMenu.StockOrderRow row = orders.get(i);
                drawClippedString(g, row.type().equals("BUY") ? "买" : "卖", 14, y, 20,
                        row.type().equals("BUY") ? COL_GOOD : COL_BAD);
                drawClippedString(g, "价 " + row.price(), 44, y, 70, COL_TEXT_DIM);
                drawClippedString(g, "量 " + row.quantity(), 124, y, 70, COL_TEXT_DIM);
                drawClippedString(g, row.ownedByPlayer() ? "我的" : "市场", 206, y, 42,
                        row.ownedByPlayer() ? COL_ACCENT : COL_TEXT_DIM);
                if (row.ownedByPlayer()) {
                    drawButton(g, "撤单", 300, y - 2, 42, false);
                }
                y += ROW_HEIGHT;
                shownOrders++;
            }
            if (orders.size() > maxOrders) {
                drawClippedString(g, "还有 " + (orders.size() - maxOrders) + " 条委托未显示", 14, y, 160, COL_TEXT_DIM);
            }
        }

        int conditionalY = orderY + 16 + Math.max(1, shownOrders) * ROW_HEIGHT + 8;
        if (conditionalY + 16 >= imageHeight - 4) {
            return;
        }
        drawSectionTitle(g, "条件委托", 10, conditionalY);
        List<FinanceMenu.ConditionalStockOrderRow> conditionalOrders = selectedConditionalStockOrders();
        if (conditionalOrders.isEmpty()) {
            g.drawString(font, "该股票暂无止盈止损委托", 12, conditionalY + 16, COL_TEXT_DIM, false);
            return;
        }
        int y = conditionalY + 16;
        int maxConditionalOrders = Math.min(conditionalOrders.size(),
                Math.max(0, (imageHeight - y - 4) / ROW_HEIGHT));
        for (int i = 0; i < maxConditionalOrders; i++) {
            FinanceMenu.ConditionalStockOrderRow row = conditionalOrders.get(i);
            boolean takeProfit = row.type().equals("TAKE_PROFIT");
            drawClippedString(g, takeProfit ? "止盈" : "止损", 14, y, 34,
                    takeProfit ? COL_GOOD : COL_BAD);
            drawClippedString(g, "触发 " + row.triggerPrice(), 54, y, 78, COL_TEXT_DIM);
            drawClippedString(g, "量 " + row.quantity(), 142, y, 58, COL_TEXT_DIM);
            drawButton(g, "取消", 300, y - 2, 42, false);
            y += ROW_HEIGHT;
        }
    }

    // ================================================================
    // 标签 6: 交易记录
    // ================================================================

    private void renderTransactionTab(GuiGraphics g) {
        int headerY = CONTENT_Y;
        drawHeader(g, "时间", 12, headerY);
        drawHeader(g, "玩家", 70, headerY);
        drawHeader(g, "类型", 126, headerY);
        drawHeader(g, "对象", 196, headerY);
        drawHeader(g, "数量", 278, headerY);
        drawHeader(g, "金额", 326, headerY);
        g.fill(8, headerY + 12, imageWidth - 8, headerY + 13, COL_PANEL_BORDER);

        List<FinanceMenu.TransactionRow> rows = menu.getTransactions();
        if (rows.isEmpty()) {
            g.drawString(font,
                    isAdmin ? "暂无交易记录。" : "暂无你的交易记录。",
                    12, headerY + 20, COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (imageHeight - rowY - 8) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.TransactionRow row = rows.get(i);
            int y = rowY + i * ROW_HEIGHT;
            g.fill(8, y - 2, imageWidth - 8, y + ROW_HEIGHT - 2,
                    i % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, formatRecordTime(row.timestamp()), 12, y + 2, 54, COL_TEXT_DIM);
            drawClippedString(g, shortUuid(row.playerId()), 70, y + 2, 52, COL_TEXT_DIM);
            drawClippedString(g, displayTransactionType(row.type()), 126, y + 2, 66, typeColor(row.type()));
            drawClippedString(g, row.objectName().isEmpty() ? "-" : row.objectName(), 196, y + 2, 78, COL_TEXT);
            drawClippedString(g, row.quantity() > 0 ? Long.toString(row.quantity()) : "-", 278, y + 2, 44, COL_TEXT_DIM);
            drawClippedString(g, row.amount() > 0 ? Long.toString(row.amount()) : "-", 326, y + 2, 64, COL_ACCENT);
        }
    }

    // ================================================================
    // 标签 7: 我的资产
    // ================================================================

    private void renderAssetTab(GuiGraphics g) {
        FinanceMenu.AssetSummary summary = menu.getAssetSummary();
        int y = CONTENT_Y;
        drawSectionTitle(g, "我的资产", 10, y);
        drawButton(g, assetSortMode == 0 ? "按价值排序" : "切到价值", 270, y - 2, 58, assetSortMode == 0);
        drawButton(g, assetSortMode == 1 ? "按收益排序" : "切到收益", 332, y - 2, 58, assetSortMode == 1);

        y += 18;
        g.fill(8, y - 4, imageWidth - 8, y + 42, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, y - 4, imageWidth - 16, 46, COL_PANEL_BORDER);
        drawClippedString(g, "总资产: " + summary.totalAsset(), 14, y, 110, COL_ACCENT);
        drawClippedString(g, "今日盈亏: " + signedLong(summary.todayProfit()), 136, y, 110,
                summary.todayProfit() >= 0 ? COL_GOOD : COL_BAD);
        drawClippedString(g, "现金 " + summary.cash() + " 冻结 " + summary.frozenCash(), 254, y, 130, COL_TEXT_DIM);
        y += 16;
        drawClippedString(g, "商品 " + summary.commodityValue() + " (" + percentOf(summary.commodityValue(), summary.totalAsset()) + ")",
                14, y, 120, COL_TEXT_DIM);
        drawClippedString(g, "股票 " + summary.stockValue() + " (" + percentOf(summary.stockValue(), summary.totalAsset()) + ")",
                146, y, 120, COL_TEXT_DIM);
        drawClippedString(g, "现金占比 " + percentOf(summary.cash() + summary.frozenCash(), summary.totalAsset()),
                278, y, 110, COL_TEXT_DIM);

        y += 26;
        drawHeader(g, "类别", 12, y);
        drawHeader(g, "资产", 54, y);
        drawHeader(g, "数量", 136, y);
        drawHeader(g, "现价", 188, y);
        drawHeader(g, "成本", 238, y);
        drawHeader(g, "价值", 288, y);
        drawHeader(g, "浮盈", 342, y);
        g.fill(8, y + 12, imageWidth - 8, y + 13, COL_PANEL_BORDER);

        List<FinanceMenu.AssetRow> rows = sortedAssetRows();
        int rowY = y + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (imageHeight - rowY - 6) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.AssetRow row = rows.get(i);
            int ry = rowY + i * ROW_HEIGHT;
            g.fill(8, ry - 2, imageWidth - 8, ry + ROW_HEIGHT - 2,
                    i % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, row.category(), 12, ry + 2, 38, COL_TEXT_DIM);
            drawClippedString(g, row.name(), 54, ry + 2, 78, COL_TEXT);
            drawClippedString(g, Long.toString(row.quantity()), 136, ry + 2, 48, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.currentPrice()), 188, ry + 2, 46, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.cost()), 238, ry + 2, 46, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.value()), 288, ry + 2, 50, COL_ACCENT);
            drawClippedString(g, signedLong(row.floatingProfit()), 342, ry + 2, 46,
                    row.floatingProfit() >= 0 ? COL_GOOD : COL_BAD);
        }
    }

    // ================================================================
    // 标签 8: 我的提醒
    // ================================================================

    private void renderAlertTab(GuiGraphics g) {
        int y = CONTENT_Y;
        drawSectionTitle(g, "我的提醒", 10, y);
        drawClippedString(g, "当前商品: " + commodityDisplayName(selectedCommodity), 14, y + 18, 130, COL_TEXT_DIM);
        FinanceMenu.StockRow stock = findSelectedStock();
        drawClippedString(g, "当前股票: " + (stock != null ? displayStockSymbol(stock) : "-"), 160, y + 18, 100, COL_TEXT_DIM);
        g.drawString(font, "目标价:", 14, y + 62, COL_TEXT_DIM, false);
        drawFilledButton(g, "商品涨到", 148, y + 58, 58, COL_GOOD);
        drawFilledButton(g, "商品跌到", 212, y + 58, 58, COL_BAD);
        drawFilledButton(g, "股票涨到", 276, y + 58, 58, COL_GOOD);
        drawFilledButton(g, "股票跌到", 340, y + 58, 50, COL_BAD);

        y += 86;
        drawHeader(g, "类型", 12, y);
        drawHeader(g, "对象", 58, y);
        drawHeader(g, "方向", 146, y);
        drawHeader(g, "目标价", 204, y);
        drawHeader(g, "操作", 316, y);
        g.fill(8, y + 12, imageWidth - 8, y + 13, COL_PANEL_BORDER);

        List<FinanceMenu.PriceAlertRow> alerts = menu.getPriceAlerts();
        if (alerts.isEmpty()) {
            g.drawString(font, "暂无提醒。设置后价格到达目标会发送游戏内消息。", 12, y + 20, COL_TEXT_DIM, false);
            return;
        }
        int rowY = y + 18;
        int maxRows = Math.min(alerts.size(), Math.max(1, (imageHeight - rowY - 6) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.PriceAlertRow row = alerts.get(i);
            int ry = rowY + i * ROW_HEIGHT;
            g.fill(8, ry - 2, imageWidth - 8, ry + ROW_HEIGHT - 2,
                    i % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, row.type().equals("COMMODITY") ? "商品" : "股票", 12, ry + 2, 40, COL_TEXT_DIM);
            drawClippedString(g, row.targetId(), 58, ry + 2, 84, COL_TEXT);
            drawClippedString(g, row.direction().equals("ABOVE") ? "涨到" : "跌到", 146, ry + 2, 54,
                    row.direction().equals("ABOVE") ? COL_GOOD : COL_BAD);
            drawClippedString(g, Long.toString(row.targetPrice()), 204, ry + 2, 80, COL_ACCENT);
            drawButton(g, "取消", 316, ry - 2, 42, false);
        }
    }

    // ================================================================
    // 标签 9: 经济仪表盘（管理员）
    // ================================================================

    private void renderDashboardTab(GuiGraphics g) {
        FinanceMenu.EconomyDashboardRow dashboard = menu.getDashboard();
        int y = CONTENT_Y;
        drawSectionTitle(g, "经济仪表盘", 10, y);
        drawClippedString(g, "只读监控，不直接修改市场。", 100, y, 180, COL_TEXT_DIM);
        y += 20;

        g.fill(8, y - 4, imageWidth - 8, imageHeight - 8, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, y - 4, imageWidth - 16, imageHeight - y - 4, COL_PANEL_BORDER);

        drawClippedString(g, "货币总量", 18, y + 6, 80, COL_TEXT_DIM);
        drawClippedString(g, Long.toString(dashboard.totalMoney()), 110, y + 6, 120, COL_ACCENT);
        drawClippedString(g, "破产风险公司", 238, y + 6, 82, COL_TEXT_DIM);
        drawClippedString(g, Integer.toString(dashboard.bankruptcyRiskCompanies()), 328, y + 6, 50,
                dashboard.bankruptcyRiskCompanies() > 0 ? COL_BAD : COL_GOOD);

        drawClippedString(g, "商品成交量", 18, y + 28, 80, COL_TEXT_DIM);
        drawClippedString(g, Long.toString(dashboard.dailyCommodityVolume()), 110, y + 28, 120, COL_TEXT);
        drawClippedString(g, "股票成交量", 238, y + 28, 80, COL_TEXT_DIM);
        drawClippedString(g, Long.toString(dashboard.dailyStockVolume()), 328, y + 28, 50, COL_TEXT);

        drawClippedString(g, "价格指数", 18, y + 50, 80, COL_TEXT_DIM);
        drawClippedString(g, String.format(Locale.ROOT, "%.1f", dashboard.priceIndex()), 110, y + 50, 80,
                dashboard.priceIndex() >= 100.0 ? COL_GOOD : COL_BAD);
        drawClippedString(g, "基准=100，按当前商品中价 / 基准价均值计算。", 190, y + 50, 180, COL_TEXT_DIM);

        drawClippedString(g, "央行最近干预", 18, y + 78, 90, COL_TEXT_DIM);
        drawClippedString(g, dashboard.centralBankSummary(), 110, y + 78, 260, COL_WARN);
        drawClippedString(g, "用途：观察货币池、成交活跃度、价格压力和系统性风险。", 18, y + 106, 330, COL_TEXT_DIM);
    }

    // ================================================================
    // 管理（管理员专用）
    // ================================================================

    private static final String[] ADMIN_SUB_TABS = {"从手中添加", "常用物品", "已注册商品", "价格历史", "分红设置"};

    private void renderAdminTab(GuiGraphics g) {
        int y = CONTENT_Y;

        int subX = 10;
        for (int i = 0; i < ADMIN_SUB_TABS.length; i++) {
            int w = adminSubTabWidth();
            boolean active = (i == adminSubTab);
            if (active) {
                g.fill(subX, y - 1, subX + w, y + 13, 0xFFECE6D5);
                g.fill(subX + 2, y + 12, subX + w - 2, y + 13, COL_ACCENT);
            }
            drawCenteredClippedString(g, ADMIN_SUB_TABS[i], subX, y + 2, w, active ? COL_TEXT : COL_TEXT_DIM);
            subX += w + 3;
        }
        y += 16;
        drawSimpleSeparator(g, 8, y, imageWidth - 16);
        y += 6;

        switch (adminSubTab) {
            case 0 -> renderAdminHandTab(g, y);
            case 1 -> renderAdminQuickTab(g, y);
            case 2 -> renderAdminRegisteredTab(g, y);
            case 3 -> renderAdminPriceHistoryTab(g, y);
            case 4 -> renderAdminDividendTab(g, y);
        }
    }

    private int adminSubTabWidth() {
        return 62;
    }

    /** 子页 0：从手中添加 + 手动添加 */
    private void renderAdminHandTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, imageWidth - 8, imageHeight - 8, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, y - 4, imageWidth - 16, imageHeight - y - 4, COL_PANEL_BORDER);
        drawSectionTitle(g, "从手中添加", 10, y);
        drawFilledButton(g, "添加手持物品", 100, y - 2, 90, COL_GOOD);
        // adminBasePriceBox 在 localY=88 (CONTENT_Y+88=46+88=134 但 localY=88)
        g.drawString(font, "价格:", 200, y + 20, COL_TEXT_DIM, false);
        y += 18;

        drawSimpleSeparator(g, 8, y, imageWidth - 16);
        y += 6;

        drawSectionTitle(g, "手动添加", 10, y);
        y += 14;

        // adminFormY = CONTENT_Y + 144 → localY = 144
        // 当前 y = 106，label 应在 y + 38 = 144
        g.drawString(font, "ID:", 10, y + 38, COL_TEXT_DIM, false);
        g.drawString(font, "物品:", 120, y + 38, COL_TEXT_DIM, false);
        g.drawString(font, "名称:", 270, y + 38, COL_TEXT_DIM, false);
        y += 56;

        g.drawString(font, "价格:", 10, y + 2, COL_TEXT_DIM, false);
        g.drawString(font, "分类:", 120, y + 2, COL_TEXT_DIM, false);
        drawButton(g, "< " + adminCategory.getDisplayName() + " >", 152, y, 80, true);
        drawFilledButton(g, "添加", 244, y, 44, COL_GOOD);
    }

    private void renderAdminPriceHistoryTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, imageWidth - 8, imageHeight - 8, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, y - 4, imageWidth - 16, imageHeight - y - 4, COL_PANEL_BORDER);
        drawSectionTitle(g, "价格历史清理", 10, y);
        drawClippedString(g, "清理只删除趋势图历史点，不会修改当前价格、高低价或持仓。", 10, y + 18, 310, COL_TEXT_DIM);
        drawFilledButton(g, "清理商品历史", 20, y + 44, 90, COL_WARN);
        drawFilledButton(g, "清理股票历史", 126, y + 44, 90, COL_WARN);
        drawFilledButton(g, "全部清理", 232, y + 44, 76, COL_BAD);
    }

    private void renderAdminDividendTab(GuiGraphics g, int y) {
        g.fill(8, y - 4, imageWidth - 8, imageHeight - 8, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, y - 4, imageWidth - 16, imageHeight - y - 4, COL_PANEL_BORDER);
        drawSectionTitle(g, "公司分红设置", 10, y);
        drawClippedString(g, "分红比例: " + Math.round(menu.getDividendRatio() * 100) + "%", 16, y + 24, 110, COL_TEXT);
        drawButton(g, "-10%", 134, y + 20, 44, false);
        drawButton(g, "+10%", 184, y + 20, 44, false);
        drawClippedString(g, "分红周期: " + menu.getDividendCycleDays() + " 天", 16, y + 50, 120, COL_TEXT);
        drawButton(g, "-1天", 134, y + 46, 44, false);
        drawButton(g, "+1天", 184, y + 46, 44, false);
        drawClippedString(g, "到期后，上市公司会按股东持股比例自动分红。", 16, y + 80, 280, COL_TEXT_DIM);
    }

    /** 子页 1：常用物品分类列表（带图标） */
    private void renderAdminQuickTab(GuiGraphics g, int y) {
        drawSectionTitle(g, "常用物品（点击添加，灰色已注册）", 10, y);
        int perPage = quickItemsPerPage();
        int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / perPage);
        adminQuickPage = Math.min(adminQuickPage, maxPage);
        drawButton(g, "上一页", imageWidth - 110, y - 2, 48, false);
        drawButton(g, "下一页", imageWidth - 56, y - 2, 48, false);
        drawClippedString(g, (adminQuickPage + 1) + "/" + (maxPage + 1), imageWidth - 158, y + 1, 42, COL_TEXT_DIM);
        y += 14;

        int listX = 10;
        int listW = imageWidth - 20;
        int maxYY = imageHeight - 4;

        int start = adminQuickPage * perPage;
        int end = Math.min(QUICK_ITEMS.length, start + perPage);
        CommodityCategory lastCat = null;
        for (int i = start; i < end; i++) {
            if (y + 14 > maxYY) break;

            CommodityCategory cat = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
            if (cat != lastCat) {
                // 分类标题
                g.fill(listX, y, listX + listW, y + 12, COL_CATEGORY);
                g.drawString(font, "▸ " + cat.getDisplayName(), listX + 4, y + 2, COL_TEXT, false);
                y += 14;
                lastCat = cat;
                if (y + 14 > maxYY) break;
            }

            boolean registered = CommodityRegistry.isRegistered(QUICK_ITEMS[i][0]);
            int rowBg = registered ? 0xFFD0D0D0 : ((i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            g.fill(listX + 2, y, listX + listW - 2, y + 14, rowBg);

            // 物品图标
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(QUICK_ITEMS[i][1]));
            if (item != null) {
                g.renderItem(new ItemStack(item), listX + 4, y + 1);
            }

            drawClippedString(g, QUICK_ITEMS[i][2], listX + 22, y + 3, 80, registered ? COL_TEXT_DIM : COL_TEXT);
            drawClippedString(g, "基础价: " + QUICK_ITEMS[i][3], listX + 110, y + 3, 80, COL_TEXT_DIM);
            if (registered) {
                drawClippedString(g, "已注册", listX + listW - 46, y + 3, 40, COL_TEXT_DIM);
            } else {
                drawFilledButton(g, "添加", listX + listW - 44, y, 36, COL_GOOD);
            }
            y += 16;
        }
    }

    /** 子页 2：已注册商品列表 */
    private void renderAdminRegisteredTab(GuiGraphics g, int y) {
        drawSectionTitle(g, "已注册商品", 10, y);
        ensureCommodityCache();
        int perPage = registeredItemsPerPage();
        int maxPage = Math.max(0, (cachedCommodities.size() - 1) / perPage);
        adminRegisteredPage = Math.min(adminRegisteredPage, maxPage);
        drawButton(g, "上一页", imageWidth - 110, y - 2, 48, false);
        drawButton(g, "下一页", imageWidth - 56, y - 2, 48, false);
        drawClippedString(g, (adminRegisteredPage + 1) + "/" + (maxPage + 1), imageWidth - 158, y + 1, 42, COL_TEXT_DIM);
        y += 14;

        drawHeader(g, "显示名", 10, y);
        drawHeader(g, "物品ID", 86, y);
        drawHeader(g, "价格", 222, y);
        drawHeader(g, "分类", 262, y);
        drawHeader(g, "操作", 328, y);
        y += 12;

        int start = adminRegisteredPage * perPage;
        int end = Math.min(cachedCommodities.size(), start + perPage);
        for (int i = start; i < end; i++) {
            Commodity c = cachedCommodities.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;
            g.fill(8, rowY, imageWidth - 8, rowY + ROW_HEIGHT, (i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, c.getDisplayName(), 10, rowY + 3, 70, COL_TEXT);
            String itemId = c.getItemId();
            drawClippedString(g, itemId != null ? itemId : c.getId(), 86, rowY + 3, 130, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(c.getBasePrice()), 222, rowY + 3, 36, COL_GOOD);
            drawClippedString(g, c.getCategory().getDisplayName(), 262, rowY + 3, 60, COL_TEXT_DIM);
            drawButton(g, "删除", 328, rowY + 1, 42, false);
        }
    }

    // ================================================================
    // 常用物品定义
    // ================================================================

    private static final String[][] QUICK_ITEMS = {
            {"gold",      "minecraft:gold_ingot",     "金锭",     "50", "RAW_MATERIALS"},
            {"diamond",   "minecraft:diamond",        "钻石",    "200", "RAW_MATERIALS"},
            {"emerald",   "minecraft:emerald",        "绿宝石",  "150", "RAW_MATERIALS"},
            {"copper",    "minecraft:copper_ingot",   "铜锭",     "6", "RAW_MATERIALS"},
            {"netherite", "minecraft:netherite_ingot","下界合金锭","500", "RAW_MATERIALS"},
            {"coal",      "minecraft:coal",           "煤炭",      "5", "RAW_MATERIALS"},
            {"leather",   "minecraft:leather",        "皮革",     "10", "RAW_MATERIALS"},
            {"redstone",  "minecraft:redstone",       "红石",     "8", "REDSTONE"},
            {"lapis",     "minecraft:lapis_lazuli",   "青金石",   "12", "MISCELLANEOUS"},
            {"bread",     "minecraft:bread",          "面包",     "15", "FOOD"},
            {"apple",     "minecraft:apple",          "苹果",     "12", "FOOD"},
            {"carrot",    "minecraft:carrot",         "胡萝卜",    "6", "FOOD"},
            {"potato",    "minecraft:potato",         "土豆",      "6", "FOOD"},
            {"oak_log",   "minecraft:oak_log",        "橡木原木",  "3", "BUILDING_BLOCKS"},
            {"sand",      "minecraft:sand",           "沙子",      "1", "BUILDING_BLOCKS"},
            {"glass",     "minecraft:glass",          "玻璃",      "4", "BUILDING_BLOCKS"},
            {"brick",     "minecraft:brick",          "砖块",      "5", "BUILDING_BLOCKS"},
    };

    // ================================================================
    // 点击处理
    // ================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditBox box : getVisibleEditBoxes()) {
            box.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

        // 标签切换
        if (my >= TAB_Y && my <= TAB_Y + TAB_H) {
            int x = TAB_X;
            for (int i = 0; i < tabNames.length; i++) {
                int w = tabWidth();
                if (mx >= x && mx < x + w) {
                    currentTab = i;
                    rememberUiState();
                    dropdownOpen = false;
                    updateInputVisibility();
                    return true;
                }
                x += w + 3;
            }
        }

        // 下拉菜单点击拦截（在其他标签逻辑之前）
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            if (handleDropdownClick(mx, my)) return true;
            // 点击下拉外部关闭
            dropdownOpen = false;
        }

        boolean handled = switch (currentTab) {
            case 0 -> handleMarketClick(mx, my);
            case 1 -> handleTradeClick(mx, my);
            case 2 -> handleOrdersClick(mx, my);
            case 3 -> handleInventoryClick(mx, my);
            case 4 -> handleCompanyClick(mx, my);
            case 5 -> handleStockClick(mx, my);
            case 7 -> handleAssetClick(mx, my);
            case 8 -> handleAlertClick(mx, my);
            case 10 -> isAdmin && handleAdminClick(mx, my);
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
        if (dropdownOpen && (currentTab == 0 || currentTab == 1)) {
            int totalHeight = calcDropdownTotalHeight();
            int maxScroll = Math.max(0, totalHeight - 116);
            dropdownScroll = Math.max(0, Math.min(maxScroll, dropdownScroll - (int)(delta * 14)));
            return true;
        }
        if (currentTab == 10 && isAdmin) {
            if (adminSubTab == 1) {
                int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / quickItemsPerPage());
                adminQuickPage = Math.max(0, Math.min(maxPage, adminQuickPage - (int) Math.signum(delta)));
                return true;
            }
            if (adminSubTab == 2) {
                ensureCommodityCache();
                int maxPage = Math.max(0, (cachedCommodities.size() - 1) / registeredItemsPerPage());
                adminRegisteredPage = Math.max(0, Math.min(maxPage, adminRegisteredPage - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    // ---- 下拉菜单点击 ----

    private boolean handleDropdownClick(int mx, int my) {
        int ddX = 82;
        int ddY = (currentTab == 0) ? MARKET_TRADE_Y + 14 : CONTENT_Y + 14;
        int ddW = 200;
        int ddH = 120;

        if (mx < ddX || mx >= ddX + ddW || my < ddY || my >= ddY + ddH) {
            return false; // 点击外部，由上层关闭
        }

        if (categorizedCacheDirty) rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap =
                (currentTab == 1) ? inventoryCategorizedCommodities : categorizedCommodities;

        int localMx = mx - ddX;
        int localMy = my - ddY;
        int y = 2 - dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题点击 → 展开/折叠（与渲染完全对称：12px 高）
            if (y + 12 > 0 && y < maxY) {
                if (localMy >= y && localMy < y + 12) {
                    expandedCategory = (expandedCategory == cat) ? null : cat;
                    return true;
                }
            }
            y += 14;

            if (expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        if (localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14) {
                            selectedCommodity = c.getId();
                            rememberUiState();
                            if (currentTab == 1) {
                                finance.market.MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(c.getId());
                                if (mp != null) priceBox.setValue(Long.toString(mp.getMidPrice()));
                            }
                            dropdownOpen = false;
                            expandedCategory = null;
                            refreshSelectedRow();
                            return true;
                        }
                    }
                    y += 14;
                }
            }
        }
        return true; // 点击在下拉区域内但未命中项目
    }

    // ---- 标签 0 点击: 行情 + 国际交易 ----

    private boolean handleMarketClick(int mx, int my) {
        int headerY = CONTENT_Y;
        int rowY = headerY + 16;
        int maxRows = Math.min(cachedMarketData.size(), Math.max(1, (MARKET_TRADE_Y - rowY - 8) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 8 && mx < imageWidth - 8) {
                selectedCommodity = cachedMarketData.get(i).commodityId();
                refreshSelectedRow();
                rememberUiState();
                return true;
            }
        }

        int tradeY = MARKET_TRADE_Y;

        // 数量加减按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            int qty = parseInt(intlQuantityBox.getValue());
            if (mx >= 100 && mx < 120) {
                intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 124 && mx < 144) {
                intlQuantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 152 && mx < 182) {
                intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 186 && mx < 216) {
                intlQuantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 国际买入/卖出按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            if (cachedSelectedRow == null) {
                setStatus("该商品未接入国际市场");
                return true;
            }
            int qty = parseInt(intlQuantityBox.getValue());
            if (mx >= 226 && mx < 294) {
                setStatus("已提交国际市场买入");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_BUY, selectedCommodity, 0, qty));
                return true;
            }
            if (mx >= 300 && mx < 368) {
                setStatus("已提交国际市场卖出");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_SELL, selectedCommodity, 0, qty));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 1 点击: 交易 ----

    private boolean handleTradeClick(int mx, int my) {
        // 下拉按钮
        if (mx >= 82 && mx < 222 && my >= CONTENT_Y && my < CONTENT_Y + 13) {
            dropdownOpen = !dropdownOpen;
            dropdownScroll = 0;
            return true;
        }

        int cardY = CONTENT_Y + 20;

        // 价格加减
        if (my >= cardY + 4 && my < cardY + 18) {
            long price = parseLong(priceBox.getValue());
            if (mx >= 170 && mx < 190) {
                priceBox.setValue(Long.toString(Math.max(1, price - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                priceBox.setValue(Long.toString(price + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                priceBox.setValue(Long.toString(Math.max(1, price - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                priceBox.setValue(Long.toString(price + 10));
                return true;
            }
        }

        // 数量加减
        if (my >= cardY + 30 && my < cardY + 44) {
            int qty = parseInt(quantityBox.getValue());
            if (mx >= 170 && mx < 190) {
                quantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                quantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                quantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                quantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 提交买单/卖单
        if (my >= 186 && my < 200) {
            long price = parseLong(priceBox.getValue());
            int qty = parseInt(quantityBox.getValue());
            if (mx >= 10 && mx < 94) {
                setStatus("已提交玩家买单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.P2P_BUY, selectedCommodity, price, qty));
                return true;
            }
            if (mx >= 102 && mx < 186) {
                setStatus("已提交玩家卖单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.P2P_SELL, selectedCommodity, price, qty));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 2 点击: 订单取消 ----

    private boolean handleOrdersClick(int mx, int my) {
        List<FinanceMenu.OrderRow> orders = menu.getPlayerOrders();
        int rowY = CONTENT_Y + 16;
        int maxRows = Math.min(orders.size(), Math.max(1, (imageHeight - rowY - 10) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 286 && mx < 324) {
                setStatus("已提交吃单请求");
                FinancePacketHandler.CHANNEL.sendToServer(new TakeOrderPacket(orders.get(i).orderId()));
                return true;
            }
            if (orders.get(i).ownedByPlayer() && my >= y && my < y + ROW_HEIGHT && mx >= 330 && mx < 368) {
                setStatus("已提交取消订单");
                FinancePacketHandler.CHANNEL.sendToServer(new CancelOrderPacket(orders.get(i).orderId()));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 3 点击: 库存 ----

    private boolean handleInventoryClick(int mx, int my) {
        int cardY = CONTENT_Y;
        int invY = cardY + 44;

        if (my >= invY + 20 && my < invY + 33) {
            int amount = parseInventoryAmount();
            if (mx >= 116 && mx < 148) {
                inventoryQuantityBox.setValue(Integer.toString(Math.max(64, amount - 64)));
                return true;
            }
            if (mx >= 152 && mx < 184) {
                inventoryQuantityBox.setValue(Integer.toString(amount + 64));
                return true;
            }
        }

        // 存入按钮
        if (mx >= 246 && mx < 290 && my >= invY + 20 && my < invY + 33) {
            Commodity c = CommodityRegistry.getCommodity(selectedCommodity);
            if (c != null && c.getItemId() != null) {
                setStatus("已提交存入请求");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new InventoryActionPacket(InventoryActionPacket.ActionType.DEPOSIT, selectedCommodity, parseInventoryAmount()));
            } else {
                setStatus("该商品没有绑定物品");
            }
            return true;
        }

        // 取出按钮
        if (mx >= 296 && mx < 340 && my >= invY + 20 && my < invY + 33) {
            setStatus("已提交取出请求");
            FinancePacketHandler.CHANNEL.sendToServer(
                    new InventoryActionPacket(InventoryActionPacket.ActionType.WITHDRAW, selectedCommodity, parseInventoryAmount()));
            return true;
        }

        // 表格行点击
        int y = invY + 56;
        int maxY = invY + (imageHeight - invY - 4) - ROW_HEIGHT;
        Map<String, Integer> inv = menu.getPlayerInventory();
        Map<String, Integer> mcInv = menu.getMcInventory();
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(inv.keySet());
        allIds.addAll(mcInv.keySet());

        for (String commodityId : allIds) {
            if (y > maxY) break;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 8 && mx < imageWidth - 8) {
                selectedCommodity = commodityId;
                // 更新数量输入框为该商品的库存数量
                int stock = inv.getOrDefault(commodityId, 0);
                inventoryQuantityBox.setValue(Integer.toString(Math.max(64, stock)));
                rememberUiState();
                return true;
            }
            y += ROW_HEIGHT;
        }
        return false;
    }

    // ---- 标签 4 点击: 公司 ----

    private boolean handleCompanyClick(int mx, int my) {
        int cardY = CONTENT_Y;
        FinanceMenu.CompanyInfo playerCompany = menu.getPlayerCompany();
        if (playerCompany != null) {
            if (mx >= 58 && mx < 176 && my >= cardY + 62 && my < cardY + 75) {
                CompanyStrategy[] strategies = CompanyStrategy.values();
                int idx = 0;
                for (int i = 0; i < strategies.length; i++) {
                    if (strategies[i].getDisplayName().equals(displayCompanyStrategy(playerCompany))) {
                        idx = i;
                        break;
                    }
                }
                CompanyStrategy next = strategies[(idx + 1) % strategies.length];
                companyStrategyOverride = next.getDisplayName();
                setStatus("已提交策略调整");
                FinancePacketHandler.CHANNEL.sendToServer(CompanyManagePacket.strategy(next));
                return true;
            }
            if (mx >= 262 && mx < 300 && my >= cardY + 62 && my < cardY + 75) {
                setStatus("已提交出售比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyManagePacket.sellRatio(playerCompany.autoSellRatio() - 0.10));
                return true;
            }
            if (mx >= 306 && mx < 344 && my >= cardY + 62 && my < cardY + 75) {
                setStatus("已提交出售比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyManagePacket.sellRatio(playerCompany.autoSellRatio() + 0.10));
                return true;
            }
            if (my >= cardY + 82 && my < cardY + 95) {
                if (mx >= 96 && mx < 132) {
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_PRODUCTION));
                    setStatus("已提交生产线升级");
                    return true;
                }
                if (mx >= 216 && mx < 252) {
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_STORAGE));
                    setStatus("已提交仓储升级");
                    return true;
                }
                if (mx >= 336 && mx < 372) {
                    FinancePacketHandler.CHANNEL.sendToServer(new CompanyManagePacket(CompanyManagementAction.UPGRADE_MANAGEMENT));
                    setStatus("已提交管理升级");
                    return true;
                }
            }
            if (my >= cardY + 106 && my < cardY + 119) {
                long amount = Math.max(1, parseLong(companyAmountBox.getValue()));
                if (mx >= 150 && mx < 198) {
                    FinancePacketHandler.CHANNEL.sendToServer(
                            CompanyManagePacket.amount(CompanyManagementAction.INVEST, amount));
                    setStatus("已提交公司注资");
                    return true;
                }
                if (mx >= 206 && mx < 254) {
                    FinancePacketHandler.CHANNEL.sendToServer(
                            CompanyManagePacket.amount(CompanyManagementAction.WITHDRAW, amount));
                    setStatus("已提交公司提取");
                    return true;
                }
            }
            if (!playerCompany.isPublic() && mx >= 322 && mx < 372 && my >= cardY + 136 && my < cardY + 149) {
                long price = Math.max(1, parseLong(ipoPriceBox.getValue()));
                long quantity = Math.max(1, parseLong(ipoQuantityBox.getValue()));
                setStatus("已提交公司IPO");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new CompanyIPOPacket(playerCompany.companyId(), price, quantity));
                return true;
            }
            if (playerCompany.isPublic() && financingForCompany(playerCompany.companyId()) == null
                    && mx >= 322 && mx < 372 && my >= cardY + 136 && my < cardY + 149) {
                long price = Math.max(1, parseLong(ipoPriceBox.getValue()));
                long quantity = Math.max(1, parseLong(ipoQuantityBox.getValue()));
                long target = Math.max(1, parseLong(companyAmountBox.getValue()));
                setStatus("已提交融资申请");
                FinancePacketHandler.CHANNEL.sendToServer(
                        CompanyFinancingPacket.start(playerCompany.companyId(), quantity, price, target));
                return true;
            }
            if (playerCompany.isPublic()) {
                int proposalY = cardY + 140 + 28;
                if (my >= proposalY - 2 && my < proposalY + 11) {
                    long amount = Math.max(1, parseLong(companyAmountBox.getValue()));
                    long price = Math.max(1, parseLong(ipoPriceBox.getValue()));
                    long quantity = Math.max(1, parseLong(ipoQuantityBox.getValue()));
                    String text = companyNameBox.getValue().trim();
                    if (mx >= 70 && mx < 108) {
                        sendProposal(playerCompany.companyId(), CompanyProposalType.DIVIDEND, "", amount, 0, 0);
                        return true;
                    }
                    if (mx >= 112 && mx < 150) {
                        sendProposal(playerCompany.companyId(), CompanyProposalType.SHARE_ISSUE, "", quantity, price, amount);
                        return true;
                    }
                    if (mx >= 154 && mx < 192) {
                        sendProposal(playerCompany.companyId(), CompanyProposalType.RENAME, text, 0, 0, 0);
                        return true;
                    }
                    if (mx >= 196 && mx < 234) {
                        sendProposal(playerCompany.companyId(), CompanyProposalType.FUND_USAGE, text, amount, 0, 0);
                        return true;
                    }
                }
                List<FinanceMenu.CompanyProposalRow> proposals = proposalsForCompany(playerCompany.companyId());
                int py = proposalY + 16;
                int maxProposals = Math.min(proposals.size(), Math.max(0, (imageHeight - py - 4) / ROW_HEIGHT));
                for (int i = 0; i < maxProposals; i++) {
                    FinanceMenu.CompanyProposalRow row = proposals.get(i);
                    if (!"ACTIVE".equals(row.status()) || row.playerVoted()) {
                        py += ROW_HEIGHT;
                        continue;
                    }
                    if (mx >= 300 && mx < 336 && my >= py - 2 && my < py + 11) {
                        FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), true));
                        setStatus("已提交赞成票");
                        return true;
                    }
                    if (mx >= 340 && mx < 376 && my >= py - 2 && my < py + 11) {
                        FinancePacketHandler.CHANNEL.sendToServer(CompanyProposalPacket.vote(row.proposalId(), false));
                        setStatus("已提交反对票");
                        return true;
                    }
                    py += ROW_HEIGHT;
                }
            }
            return false;
        }

        if (mx >= 55 && mx < 175 && my >= cardY + 58 && my < cardY + 71) {
            int idx = 0;
            for (int i = 0; i < COMPANY_TYPES.length; i++) {
                if (COMPANY_TYPES[i] == selectedType) { idx = i; break; }
            }
            selectedType = COMPANY_TYPES[(idx + 1) % COMPANY_TYPES.length];
            return true;
        }

        if (mx >= 300 && mx < 376 && my >= cardY + 58 && my < cardY + 71) {
            String name = companyNameBox.getValue().trim();
            if (!name.isEmpty()) {
                setStatus("已提交创建公司");
                FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(selectedType, name));
            } else {
                setStatus("请输入公司名称");
            }
            return true;
        }
        return false;
    }

    // ---- 标签 5 点击: 股票 ----

    private boolean handleStockClick(int mx, int my) {
        int rowY = CONTENT_Y + 16;
        int maxRows = Math.min(menu.getStocks().size(), Math.max(1, (STOCK_TRADE_Y - rowY - 10) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 8 && mx < imageWidth - 8) {
                selectedStock = menu.getStocks().get(i).symbol();
                FinanceMenu.StockRow selected = findSelectedStock();
                if (selected != null) {
                    stockPriceBox.setValue(Long.toString(selected.lastPrice()));
                }
                rememberUiState();
                return true;
            }
        }

        int tradeY = STOCK_TRADE_Y;
        if (my >= tradeY + 36 && my < tradeY + 50) {
            int qty = parseInt(stockQuantityBox.getValue());
            if (mx >= 124 && mx < 144) {
                stockQuantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 148 && mx < 168) {
                stockQuantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 176 && mx < 206) {
                stockQuantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
            if (mx >= 234 && mx < 292) {
                long price = Math.max(1, parseLong(stockPriceBox.getValue()));
                setStatus("已提交买入委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.PLACE_BUY, selectedStock, price, qty));
                return true;
            }
            if (mx >= 300 && mx < 358) {
                long price = Math.max(1, parseLong(stockPriceBox.getValue()));
                setStatus("已提交卖出委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.PLACE_SELL, selectedStock, price, qty));
                return true;
            }
        }
        if (my >= tradeY + 64 && my < tradeY + 77) {
            int qty = Math.max(1, parseInt(stockQuantityBox.getValue()));
            long triggerPrice = Math.max(1, parseLong(stockPriceBox.getValue()));
            if (mx >= 54 && mx < 112) {
                setStatus("已提交止盈委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new ConditionalStockOrderPacket(ConditionalStockOrderType.TAKE_PROFIT,
                                selectedStock, triggerPrice, qty));
                return true;
            }
            if (mx >= 120 && mx < 178) {
                setStatus("已提交止损委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new ConditionalStockOrderPacket(ConditionalStockOrderType.STOP_LOSS,
                                selectedStock, triggerPrice, qty));
                return true;
            }
        }

        FinanceMenu.CompanyFinancingRow financing = selectedFinancingProject();
        if (financing != null && mx >= 300 && mx < 342 && my >= tradeY + 76 && my < tradeY + 91) {
            long qty = Math.max(1, parseLong(stockQuantityBox.getValue()));
            setStatus("已提交增发认购");
            FinancePacketHandler.CHANNEL.sendToServer(
                    CompanyFinancingPacket.subscribe(financing.projectId(), qty));
            return true;
        }

        int orderY = tradeY + (financing != null ? 98 : 82);
        List<FinanceMenu.StockOrderRow> orders = selectedStockOrders();
        int maxOrders = Math.min(orders.size(),
                Math.max(1, (imageHeight - (orderY + 16) - 30) / ROW_HEIGHT));
        for (int i = 0; i < maxOrders; i++) {
            FinanceMenu.StockOrderRow row = orders.get(i);
            int y = orderY + 16 + i * ROW_HEIGHT;
            if (row.ownedByPlayer() && mx >= 300 && mx < 342 && my >= y - 2 && my < y + 11) {
                setStatus("已提交撤单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.CANCEL, row.orderId()));
                return true;
            }
        }
        int conditionalY = orderY + 16 + Math.max(1, maxOrders) * ROW_HEIGHT + 8;
        if (conditionalY + 16 >= imageHeight - 4) {
            return false;
        }
        List<FinanceMenu.ConditionalStockOrderRow> conditionalOrders = selectedConditionalStockOrders();
        int maxConditionalOrders = Math.min(conditionalOrders.size(),
                Math.max(0, (imageHeight - (conditionalY + 16) - 4) / ROW_HEIGHT));
        for (int i = 0; i < maxConditionalOrders; i++) {
            FinanceMenu.ConditionalStockOrderRow row = conditionalOrders.get(i);
            int y = conditionalY + 16 + i * ROW_HEIGHT;
            if (mx >= 300 && mx < 342 && my >= y - 2 && my < y + 11) {
                setStatus("已提交取消条件委托");
                FinancePacketHandler.CHANNEL.sendToServer(new ConditionalStockOrderPacket(row.orderId()));
                return true;
            }
        }
        return false;
    }

    private boolean handleAssetClick(int mx, int my) {
        int y = CONTENT_Y;
        if (my >= y - 2 && my < y + 11) {
            if (mx >= 270 && mx < 328) {
                assetSortMode = 0;
                setStatus("资产明细已按价值排序");
                return true;
            }
            if (mx >= 332 && mx < 390) {
                assetSortMode = 1;
                setStatus("资产明细已按收益排序");
                return true;
            }
        }
        return false;
    }

    private boolean handleAlertClick(int mx, int my) {
        int y = CONTENT_Y;
        long targetPrice = parseLong(alertPriceBox.getValue());
        if (my >= y + 58 && my < y + 71) {
            if (targetPrice <= 0) {
                setStatus("请输入有效提醒价格");
                return true;
            }
            if (mx >= 148 && mx < 206) {
                setStatus("已提交商品涨到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, selectedCommodity, PriceAlertDirection.ABOVE, targetPrice));
                return true;
            }
            if (mx >= 212 && mx < 270) {
                setStatus("已提交商品跌到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, selectedCommodity, PriceAlertDirection.BELOW, targetPrice));
                return true;
            }
            if (mx >= 276 && mx < 334) {
                if (selectedStock == null || selectedStock.isBlank()) {
                    setStatus("请先选择股票");
                    return true;
                }
                setStatus("已提交股票涨到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, selectedStock, PriceAlertDirection.ABOVE, targetPrice));
                return true;
            }
            if (mx >= 340 && mx < 390) {
                if (selectedStock == null || selectedStock.isBlank()) {
                    setStatus("请先选择股票");
                    return true;
                }
                setStatus("已提交股票跌到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, selectedStock, PriceAlertDirection.BELOW, targetPrice));
                return true;
            }
        }

        int listY = y + 86 + 18;
        List<FinanceMenu.PriceAlertRow> alerts = menu.getPriceAlerts();
        int maxRows = Math.min(alerts.size(), Math.max(1, (imageHeight - listY - 6) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int rowY = listY + i * ROW_HEIGHT;
            if (my >= rowY - 2 && my < rowY + 11 && mx >= 316 && mx < 358) {
                setStatus("已提交取消提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(alerts.get(i).alertId()));
                return true;
            }
        }
        return false;
    }

    // ---- 管理标签点击 ----

    private boolean handleAdminClick(int mx, int my) {
        int y = CONTENT_Y;

        int subX = 10;
        for (int i = 0; i < ADMIN_SUB_TABS.length; i++) {
            int w = adminSubTabWidth();
            if (mx >= subX && mx < subX + w && my >= y && my < y + 14) {
                adminSubTab = i;
                rememberUiState();
                updateInputVisibility();
                return true;
            }
            subX += w + 3;
        }
        y += 16 + 6;

        return switch (adminSubTab) {
            case 0 -> handleAdminHandClick(mx, my, y);
            case 1 -> handleAdminQuickClick(mx, my, y);
            case 2 -> handleAdminRegisteredClick(mx, my, y);
            case 3 -> handleAdminPriceHistoryClick(mx, my, y);
            case 4 -> handleAdminDividendClick(mx, my, y);
            default -> false;
        };
    }

    private boolean handleAdminDividendClick(int mx, int my, int y) {
        if (my >= y + 20 && my < y + 33) {
            if (mx >= 134 && mx < 178) {
                double next = Math.max(0.0, menu.getDividendRatio() - 0.10);
                setStatus("已提交分红比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendRatio(next));
                return true;
            }
            if (mx >= 184 && mx < 228) {
                double next = Math.min(1.0, menu.getDividendRatio() + 0.10);
                setStatus("已提交分红比例调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendRatio(next));
                return true;
            }
        }
        if (my >= y + 46 && my < y + 59) {
            if (mx >= 134 && mx < 178) {
                int next = Math.max(1, menu.getDividendCycleDays() - 1);
                setStatus("已提交分红周期调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendCycle(next));
                return true;
            }
            if (mx >= 184 && mx < 228) {
                int next = Math.min(365, menu.getDividendCycleDays() + 1);
                setStatus("已提交分红周期调整");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.setDividendCycle(next));
                return true;
            }
        }
        return false;
    }

    private boolean handleAdminPriceHistoryClick(int mx, int my, int y) {
        if (my >= y + 44 && my < y + 57) {
            if (mx >= 20 && mx < 110) {
                setStatus("已提交清理商品价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearCommodityPriceHistory());
                return true;
            }
            if (mx >= 126 && mx < 216) {
                setStatus("已提交清理股票价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearStockPriceHistory());
                return true;
            }
            if (mx >= 232 && mx < 308) {
                setStatus("已提交清理全部价格历史");
                FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.clearAllPriceHistory());
                return true;
            }
        }
        return false;
    }

    private boolean handleAdminHandClick(int mx, int my, int y) {
        // "添加手持物品" 按钮在 (100, y-2) 到 (190, y+11)
        if (my >= y - 2 && my < y + 11 && mx >= 100 && mx < 190) {
            long basePrice = parseLong(adminBasePriceBox.getValue());
            if (basePrice <= 0) basePrice = 10;
            setStatus("已提交添加手持物品");
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.fromHand(basePrice));
            return true;
        }
        // adminBasePriceBox 在 localY=88，此处 y=68，所以 y+20=88
        // 点击区域让 EditBox 自行处理，这里不拦截
        y += 18 + 6 + 14; // y = 106

        // "手动添加" 标题区域，不拦截
        y += 38; // y = 144，对应 label 和 EditBox 行

        // 分类选择按钮 "< RAW_MATERIALS >" 在 (152, y+18) = (152, 162)
        y += 18; // y = 162
        if (my >= y && my < y + 13 && mx >= 152 && mx < 232) {
            CommodityCategory[] cats = CommodityCategory.values();
            int idx = adminCategory.ordinal();
            adminCategory = cats[(idx + 1) % cats.length];
            return true;
        }

        // "添加" 按钮在 (244, y) = (244, 162)
        if (my >= y && my < y + 13 && mx >= 244 && mx < 288) {
            String id = adminCommodityIdBox.getValue().trim().toLowerCase();
            String itemId = adminItemIdBox.getValue().trim();
            String displayName = adminDisplayNameBox.getValue().trim();
            long basePrice = parseLong(adminBasePriceBox.getValue());

            if (id.isEmpty()) {
                setStatus("请输入商品 ID");
                return true;
            }
            if (displayName.isEmpty()) displayName = id;
            if (itemId.isEmpty()) itemId = null;

            setStatus("已提交添加商品");
            FinancePacketHandler.CHANNEL.sendToServer(
                    new AdminActionPacket(id, itemId, displayName, basePrice, adminCategory));
            return true;
        }

        return false;
    }

    /** 子页 1 点击：常用物品分类列表 */
    private boolean handleAdminQuickClick(int mx, int my, int startY) {
        if (my >= startY - 2 && my < startY + 11) {
            int maxPage = Math.max(0, (QUICK_ITEMS.length - 1) / quickItemsPerPage());
            if (mx >= imageWidth - 110 && mx < imageWidth - 62) {
                adminQuickPage = Math.max(0, adminQuickPage - 1);
                return true;
            }
            if (mx >= imageWidth - 56 && mx < imageWidth - 8) {
                adminQuickPage = Math.min(maxPage, adminQuickPage + 1);
                return true;
            }
        }
        int y = startY + 14; // 跳过标题
        int listX = 10;
        int listW = imageWidth - 20;
        int maxYY = imageHeight - 4;

        int perPage = quickItemsPerPage();
        int start = adminQuickPage * perPage;
        int end = Math.min(QUICK_ITEMS.length, start + perPage);
        CommodityCategory lastCat = null;
        for (int i = start; i < end; i++) {
            if (y + 14 > maxYY) break;

            CommodityCategory cat = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
            if (cat != lastCat) {
                y += 14; // 分类标题行
                lastCat = cat;
                if (y + 14 > maxYY) break;
            }

            boolean registered = CommodityRegistry.isRegistered(QUICK_ITEMS[i][0]);
            // 点击整行或"添加"按钮
            if (mx >= listX + 2 && mx < listX + listW - 2 && my >= y && my < y + 14) {
                if (!registered) {
                    String id = QUICK_ITEMS[i][0];
                    String itemId = QUICK_ITEMS[i][1];
                    String displayName = QUICK_ITEMS[i][2];
                    long basePrice = parseLong(QUICK_ITEMS[i][3]);
                    CommodityCategory category = CommodityCategory.valueOf(QUICK_ITEMS[i][4]);
                    setStatus("已提交添加 " + displayName);
                    FinancePacketHandler.CHANNEL.sendToServer(
                            new AdminActionPacket(id, itemId, displayName, basePrice, category));
                } else {
                    setStatus("该商品已注册");
                }
                return true;
            }
            y += 16;
        }
        return false;
    }

    private boolean handleAdminRegisteredClick(int mx, int my, int y) {
        if (my >= y - 2 && my < y + 11) {
            ensureCommodityCache();
            int maxPage = Math.max(0, (cachedCommodities.size() - 1) / registeredItemsPerPage());
            if (mx >= imageWidth - 110 && mx < imageWidth - 62) {
                adminRegisteredPage = Math.max(0, adminRegisteredPage - 1);
                return true;
            }
            if (mx >= imageWidth - 56 && mx < imageWidth - 8) {
                adminRegisteredPage = Math.min(maxPage, adminRegisteredPage + 1);
                return true;
            }
        }
        y += 14 + 12;
        ensureCommodityCache();
        int perPage = registeredItemsPerPage();
        int start = adminRegisteredPage * perPage;
        int end = Math.min(cachedCommodities.size(), start + perPage);
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * ROW_HEIGHT;
            if (my >= rowY + 1 && my < rowY + 14 && mx >= 328 && mx < 370) {
                String commodityId = cachedCommodities.get(i).getId();
                setStatus("已提交删除 " + commodityDisplayName(commodityId));
                FinancePacketHandler.CHANNEL.sendToServer(new AdminActionPacket(commodityId));
                commodityCacheDirty = true;
                return true;
            }
        }
        return false;
    }

    // ================================================================
    // 绘制辅助
    // ================================================================

    private void updateInputVisibility() {
        intlQuantityBox.setVisible(currentTab == 0);
        boolean trade = (currentTab == 1);
        priceBox.setVisible(trade);
        quantityBox.setVisible(trade);
        inventoryQuantityBox.setVisible(currentTab == 3);
        FinanceMenu.CompanyInfo playerCompany = menu.getPlayerCompany();
        boolean createCompany = (currentTab == 4 && playerCompany == null);
        boolean ipoCompany = (currentTab == 4 && playerCompany != null);
        boolean manageCompany = (currentTab == 4 && playerCompany != null);
        if (createCompany) {
            companyNameBox.setX(leftPos + 70);
            companyNameBox.setY(topPos + CONTENT_Y + 36);
        } else if (manageCompany) {
            companyNameBox.setX(leftPos + 198);
            companyNameBox.setY(topPos + CONTENT_Y + 140);
        }
        companyNameBox.setVisible(createCompany || manageCompany);
        companyAmountBox.setVisible(manageCompany);
        ipoPriceBox.setVisible(ipoCompany);
        ipoQuantityBox.setVisible(ipoCompany);
        stockPriceBox.setVisible(currentTab == 5);
        stockQuantityBox.setVisible(currentTab == 5);
        alertPriceBox.setVisible(currentTab == 8);
        boolean admin = (currentTab == 10 && isAdmin);
        boolean adminHand = admin && adminSubTab == 0;
        adminCommodityIdBox.setVisible(adminHand);
        adminItemIdBox.setVisible(adminHand);
        adminDisplayNameBox.setVisible(adminHand);
        adminBasePriceBox.setVisible(adminHand);
    }

    private List<EditBox> getVisibleEditBoxes() {
        List<EditBox> list = new ArrayList<>();
        if (currentTab == 0) list.add(intlQuantityBox);
        if (currentTab == 1) { list.add(priceBox); list.add(quantityBox); }
        if (currentTab == 3) list.add(inventoryQuantityBox);
        if (currentTab == 4 && menu.getPlayerCompany() == null) list.add(companyNameBox);
        if (currentTab == 4 && menu.getPlayerCompany() != null) list.add(companyNameBox);
        if (currentTab == 4 && menu.getPlayerCompany() != null) list.add(companyAmountBox);
        if (currentTab == 4 && menu.getPlayerCompany() != null) {
            list.add(ipoPriceBox);
            list.add(ipoQuantityBox);
        }
        if (currentTab == 5) {
            list.add(stockPriceBox);
            list.add(stockQuantityBox);
        }
        if (currentTab == 8) {
            list.add(alertPriceBox);
        }
        if (currentTab == 10 && isAdmin && adminSubTab == 0) {
            list.add(adminBasePriceBox);
            list.add(adminCommodityIdBox);
            list.add(adminItemIdBox);
            list.add(adminDisplayNameBox);
        }
        return list;
    }

    private void ensureCommodityCache() {
        if (commodityCacheDirty || cachedCommodities == null) {
            cachedCommodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
            cachedCommodities.sort(Comparator.comparing(Commodity::getDisplayName));
            commodityCacheDirty = false;
        }
    }

    private String chooseInitialCommodity() {
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

    private int quickItemsPerPage() {
        return 7;
    }

    private int registeredItemsPerPage() {
        return Math.max(1, (imageHeight - (CONTENT_Y + 22 + 26) - 4) / ROW_HEIGHT);
    }

    /** 渲染物品图标（约 10x10，避免默认 16x16 压住表格文字） */
    private void renderItemIcon(GuiGraphics g, Commodity commodity, int x, int y) {
        if (commodity == null) return;
        String itemId = commodity.getItemId();
        if (itemId == null) return;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.625F, 0.625F, 1.0F);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popPose();
    }

    private FinanceMenu.StockRow findSelectedStock() {
        for (FinanceMenu.StockRow row : menu.getStocks()) {
            if (row.symbol().equals(selectedStock)) return row;
        }
        return menu.getStocks().isEmpty() ? null : menu.getStocks().get(0);
    }

    private FinanceMenu.StockHoldingRow findHolding(String symbol) {
        for (FinanceMenu.StockHoldingRow row : menu.getStockHoldings()) {
            if (row.symbol().equals(symbol)) return row;
        }
        return null;
    }

    private List<FinanceMenu.StockOrderRow> selectedStockOrders() {
        List<FinanceMenu.StockOrderRow> rows = new ArrayList<>();
        String symbol = selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (FinanceMenu.StockOrderRow row : menu.getStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<FinanceMenu.ConditionalStockOrderRow> selectedConditionalStockOrders() {
        List<FinanceMenu.ConditionalStockOrderRow> rows = new ArrayList<>();
        String symbol = selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (FinanceMenu.ConditionalStockOrderRow row : menu.getConditionalStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private FinanceMenu.CompanyFinancingRow selectedFinancingProject() {
        String symbol = selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        for (FinanceMenu.CompanyFinancingRow row : menu.getCompanyFinancingRows()) {
            if (symbol.equals(row.symbol())) {
                return row;
            }
        }
        return null;
    }

    private FinanceMenu.CompanyFinancingRow financingForCompany(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        for (FinanceMenu.CompanyFinancingRow row : menu.getCompanyFinancingRows()) {
            if (companyId.equals(row.companyId())) {
                return row;
            }
        }
        return null;
    }

    private List<FinanceMenu.CompanyProposalRow> proposalsForCompany(UUID companyId) {
        List<FinanceMenu.CompanyProposalRow> rows = new ArrayList<>();
        if (companyId == null) {
            return rows;
        }
        for (FinanceMenu.CompanyProposalRow row : menu.getCompanyProposalRows()) {
            if (companyId.equals(row.companyId())) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing((FinanceMenu.CompanyProposalRow row) -> !"ACTIVE".equals(row.status()))
                .thenComparingLong(FinanceMenu.CompanyProposalRow::endMcDay));
        return rows;
    }

    private void sendProposal(UUID companyId, CompanyProposalType type, String text, long value1, long value2, long value3) {
        if (type == CompanyProposalType.RENAME || type == CompanyProposalType.FUND_USAGE) {
            if (text == null || text.isBlank()) {
                setStatus(type == CompanyProposalType.RENAME ? "请输入新公司名" : "请输入资金用途");
                return;
            }
        }
        setStatus("已提交股东提案");
        FinancePacketHandler.CHANNEL.sendToServer(
                CompanyProposalPacket.create(companyId, type, text, value1, value2, value3, 3, 60));
    }

    private String displayProposalType(String type) {
        return switch (type) {
            case "DIVIDEND" -> "分红";
            case "SHARE_ISSUE" -> "增发";
            case "RENAME" -> "改名";
            case "FUND_USAGE" -> "用途";
            default -> type;
        };
    }

    private String displayProposalValues(FinanceMenu.CompanyProposalRow row) {
        return switch (row.type()) {
            case "DIVIDEND" -> row.value1() + "%";
            case "SHARE_ISSUE" -> row.value1() + "股@" + row.value2();
            case "RENAME", "FUND_USAGE" -> row.textValue();
            default -> "";
        };
    }

    private String displayProposalStatus(FinanceMenu.CompanyProposalRow row) {
        if ("PASSED".equals(row.status())) {
            return "通过";
        }
        if ("FAILED".equals(row.status())) {
            return "未过";
        }
        return "投票中";
    }

    private static String displayStockSymbol(FinanceMenu.StockRow row) {
        return displaySymbol(row.symbol());
    }

    private static String displayHoldingSymbol(String symbol) {
        return displaySymbol(symbol);
    }

    private static String displaySymbol(String symbol) {
        return switch (symbol) {
            case "IRONM", "MININ", "铁锭" -> "铁锭";
            case "STONE", "ENERG", "石头" -> "石头";
            case "WHEAT", "AGRIC", "小麦" -> "小麦";
            default -> symbol;
        };
    }

    private void drawSimpleBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawSimpleSeparator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, COL_PANEL_BORDER);
    }

    private void drawSectionTitle(GuiGraphics g, String label, int x, int y) {
        g.drawString(font, label, x, y, COL_TEXT, false);
    }

    private void drawHeader(GuiGraphics g, String label, int x, int y) {
        g.drawString(font, label, x, y, COL_TEXT, false);
    }

    private void drawButton(GuiGraphics g, String label, int x, int y, int w, boolean active) {
        int borderColor = active ? 0xFFFFFFFF : COL_PANEL_BORDER;
        int bgColor = active ? 0xFFD6D6D6 : COL_BUTTON_BG;
        int textColor = active ? COL_TEXT : COL_TEXT_DIM;
        g.fill(x, y, x + w, y + 13, bgColor);
        drawSimpleBorder(g, x, y, w, 13, borderColor);
        drawCenteredClippedString(g, label, x, y + 3, w, textColor);
    }

    private void drawFilledButton(GuiGraphics g, String label, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 13, color);
        drawSimpleBorder(g, x, y, w, 13, COL_PANEL_BORDER);
        drawCenteredClippedString(g, label, x, y + 3, w, 0xFFFFFFFF);
    }

    private void drawClippedString(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        g.drawString(font, clipText(text, maxWidth), x, y, color, false);
    }

    private void drawCenteredClippedString(GuiGraphics g, String text, int x, int y, int width, int color) {
        String clipped = clipText(text, width - 6);
        int textX = x + Math.max(3, (width - font.width(clipped)) / 2);
        g.drawString(font, clipped, textX, y, color, false);
    }

    private String clipText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        if (maxWidth <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }

    private static int changeColor(double change) {
        if (change > 0) return COL_GOOD;
        if (change < 0) return COL_BAD;
        return COL_TEXT_DIM;
    }

    private void rememberUiState() {
        // 测试版优先保证跨世界状态干净，不把 GUI 选择写入静态字段。
    }

    public void setGuiStatus(String message) {
        setStatus(message);
    }

    private String displayCompanyStrategy(FinanceMenu.CompanyInfo company) {
        return companyStrategyOverride != null ? companyStrategyOverride : company.strategy();
    }

    private String formatRecordTime(long epochSeconds) {
        java.time.LocalDateTime time = java.time.LocalDateTime.ofEpochSecond(
                epochSeconds, 0, java.time.ZoneOffset.UTC);
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        String text = uuid.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private String displayTransactionType(String type) {
        return switch (type) {
            case "TRANSFER" -> "转账";
            case "MARKET_TRADE" -> "商品成交";
            case "NPC_BUY", "COMMODITY_SELL" -> "卖出商品";
            case "NPC_SELL", "COMMODITY_BUY" -> "买入商品";
            case "STOCK_BUY" -> "买入股票";
            case "STOCK_SELL" -> "卖出股票";
            case "ORDER_CANCEL" -> "商品撤单";
            case "STOCK_ORDER_CANCEL" -> "股票撤单";
            case "COMPANY_ACTION" -> "公司操作";
            case "COMPANY_CREATE" -> "创建公司";
            case "COMPANY_IPO" -> "公司IPO";
            case "DIVIDEND" -> "分红";
            case "CONDITIONAL_STOCK_TRIGGER" -> "条件触发";
            case "CONDITIONAL_STOCK_CANCEL" -> "条件取消";
            case "COMPANY_FINANCING_START" -> "发起融资";
            case "COMPANY_FINANCING_SUBSCRIBE" -> "认购增发";
            case "COMPANY_FINANCING_SUCCESS" -> "融资成功";
            case "COMPANY_FINANCING_REFUND" -> "融资退款";
            case "COMPANY_PROPOSAL_CREATE" -> "创建提案";
            case "COMPANY_PROPOSAL_VOTE" -> "股东投票";
            case "COMPANY_PROPOSAL_RESULT" -> "提案结果";
            case "COMPANY_BANKRUPTCY" -> "破产退市";
            case "COMPANY_LIQUIDATION" -> "清算分配";
            case "ADMIN_GIVE" -> "管理员";
            case "ADMIN_COMMODITY" -> "商品管理";
            default -> type;
        };
    }

    private int typeColor(String type) {
        return switch (type) {
            case "COMMODITY_BUY", "STOCK_BUY", "NPC_SELL" -> COL_GOOD;
            case "COMMODITY_SELL", "STOCK_SELL", "NPC_BUY" -> COL_BAD;
            case "ORDER_CANCEL", "STOCK_ORDER_CANCEL", "CONDITIONAL_STOCK_CANCEL" -> COL_WARN;
            case "COMPANY_BANKRUPTCY" -> COL_BAD;
            case "COMPANY_LIQUIDATION" -> COL_WARN;
            case "CONDITIONAL_STOCK_TRIGGER", "COMPANY_FINANCING_START",
                    "COMPANY_FINANCING_SUBSCRIBE", "COMPANY_FINANCING_SUCCESS",
                    "COMPANY_PROPOSAL_CREATE", "COMPANY_PROPOSAL_VOTE",
                    "COMPANY_PROPOSAL_RESULT" -> COL_ACCENT;
            case "COMPANY_FINANCING_REFUND" -> COL_WARN;
            default -> COL_TEXT_DIM;
        };
    }

    private List<FinanceMenu.AssetRow> sortedAssetRows() {
        List<FinanceMenu.AssetRow> rows = new ArrayList<>(menu.getAssetRows());
        if (assetSortMode == 1) {
            rows.sort(Comparator.comparingLong(FinanceMenu.AssetRow::floatingProfit).reversed()
                    .thenComparing(Comparator.comparingLong(FinanceMenu.AssetRow::value).reversed()));
        } else {
            rows.sort(Comparator.comparingLong(FinanceMenu.AssetRow::value).reversed()
                    .thenComparing(Comparator.comparingLong(FinanceMenu.AssetRow::floatingProfit).reversed()));
        }
        return rows;
    }

    private String signedLong(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private String percentOf(long value, long total) {
        if (total <= 0) {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", (double) value / total * 100.0);
    }

    private void drawTrendGraph(GuiGraphics g, List<Long> values, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, 0x55FFFFFF);
        drawSimpleBorder(g, x, y, w, h, 0x66555555);
        if (values == null || values.size() < 2 || w <= 2 || h <= 2) {
            return;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : values) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        if (min == Long.MAX_VALUE || max <= min) {
            int mid = y + h / 2;
            g.fill(x + 2, mid, x + w - 2, mid + 1, color);
            return;
        }

        int plotX = x + 2;
        int plotY = y + 2;
        int plotW = Math.max(1, w - 4);
        int plotH = Math.max(1, h - 4);
        int prevX = plotX;
        int prevY = scaleGraphY(values.get(0), min, max, plotY, plotH);
        for (int i = 1; i < values.size(); i++) {
            int px = plotX + Math.round((float) i / (values.size() - 1) * plotW);
            int py = scaleGraphY(values.get(i), min, max, plotY, plotH);
            drawPixelLine(g, prevX, prevY, px, py, color);
            prevX = px;
            prevY = py;
        }
    }

    private int scaleGraphY(long value, long min, long max, int y, int h) {
        double ratio = (double) (value - min) / Math.max(1.0, max - min);
        return y + h - (int) Math.round(ratio * h);
    }

    private void drawPixelLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void setStatus(String message) {
        statusMessage = message;
    }

    private int parseInventoryAmount() {
        return Math.max(64, parseInt(inventoryQuantityBox.getValue()));
    }

    private static long safeMultiply(long price, int quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        if (price > Long.MAX_VALUE / quantity) return Long.MAX_VALUE;
        return price * quantity;
    }

    private static long safeMultiplyLong(long price, long quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        if (price > Long.MAX_VALUE / quantity) return Long.MAX_VALUE;
        return price * quantity;
    }

    private static String getTypeDescription(CompanyType type) {
        return switch (type) {
            case RAW_MATERIALS -> "产出: 铁锭×100/天";
            case BUILDING_BLOCKS -> "产出: 石头×120/天";
            case FOOD -> "产出: 小麦×80/天";
        };
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 1; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 1; }
    }

    private static String commodityDisplayName(String commodityId) {
        Commodity c = CommodityRegistry.getCommodity(commodityId);
        return c != null ? c.getDisplayName() : commodityId;
    }
}
