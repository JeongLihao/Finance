package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyType;
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
 * 金融操作中心 GUI —— 6 标签页（管理员 7 个）。
 */
public class FinanceScreen extends AbstractContainerScreen<FinanceMenu> {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 230;
    private static final int ROW_HEIGHT = 15;
    private static final int TAB_X = 10;
    private static final int TAB_Y = 24;
    private static final int TAB_H = 16;
    private static final int CONTENT_Y = 46;
    private static final int MARKET_TRADE_Y = 152;

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
    private static final String[] BASE_TABS = {"行情", "交易", "订单", "库存", "公司", "股票"};
    private static final String[] ADMIN_TABS = {"行情", "交易", "订单", "库存", "公司", "股票", "管理"};
    private String[] tabNames;
    private static final CompanyType[] COMPANY_TYPES = CompanyType.values();
    private int currentTab = 0;
    private boolean isAdmin = false;

    // ---- 行情标签（国际交易）控件 ----
    private String selectedCommodity = "iron";
    private EditBox intlQuantityBox;

    // ---- 交易标签控件 ----
    private EditBox priceBox;
    private EditBox quantityBox;

    // ---- 公司标签控件 ----
    private EditBox companyNameBox;
    private CompanyType selectedType = CompanyType.RAW_MATERIALS;

    // ---- 股票标签控件 ----
    private String selectedStock = "";
    private EditBox stockQuantityBox;

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

    // ---- 缓存 ----
    private List<FinanceMenu.MarketRow> cachedMarketData;
    private FinanceMenu.MarketRow cachedSelectedRow;
    private String lastSelectedCommodity = "";
    private String[] cachedMidPriceStr;
    private String[] cachedBidPriceStr;
    private String[] cachedAskPriceStr;
    private String[] cachedDayChangeStr;
    private String[] cachedDayVolumeStr;
    private String[] cachedMarketStockStr;
    private boolean cacheDirty = true;

    private List<Commodity> cachedCommodities;
    private boolean commodityCacheDirty = true;

    // ---- 分类商品缓存 ----
    private Map<CommodityCategory, List<Commodity>> categorizedCommodities;
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

        // 行情标签 — 国际交易数量输入框
        intlQuantityBox = new EditBox(font, leftPos + 58, topPos + MARKET_TRADE_Y + 53, 64, 16, Component.literal("数量"));
        intlQuantityBox.setMaxLength(8);
        intlQuantityBox.setValue("1");
        intlQuantityBox.setVisible(false);
        addWidget(intlQuantityBox);

        // 交易标签 — 价格/数量输入框
        priceBox = new EditBox(font, leftPos + 74, topPos + 86, 86, 16, Component.literal("价格"));
        priceBox.setMaxLength(12);
        priceBox.setValue("10");
        priceBox.setVisible(false);
        addWidget(priceBox);

        quantityBox = new EditBox(font, leftPos + 74, topPos + 112, 86, 16, Component.literal("数量"));
        quantityBox.setMaxLength(8);
        quantityBox.setValue("1");
        quantityBox.setVisible(false);
        addWidget(quantityBox);

        // 公司名称输入框
        companyNameBox = new EditBox(font, leftPos + 70, topPos + CONTENT_Y + 34, 190, 16, Component.literal("公司名称"));
        companyNameBox.setMaxLength(32);
        companyNameBox.setVisible(false);
        addWidget(companyNameBox);

        stockQuantityBox = new EditBox(font, leftPos + 54, topPos + 179, 64, 16, Component.literal("股数"));
        stockQuantityBox.setMaxLength(8);
        stockQuantityBox.setValue("1");
        stockQuantityBox.setVisible(false);
        addWidget(stockQuantityBox);

        // 管理标签输入框
        adminBasePriceBox = new EditBox(font, leftPos + 228, topPos + CONTENT_Y - 2, 60, 14, Component.literal("价格"));
        adminBasePriceBox.setMaxLength(12);
        adminBasePriceBox.setValue("10");
        adminBasePriceBox.setVisible(false);
        addWidget(adminBasePriceBox);

        int adminFormY = topPos + CONTENT_Y + 52;
        adminCommodityIdBox = new EditBox(font, leftPos + 30, adminFormY, 80, 14, Component.literal("商品ID"));
        adminCommodityIdBox.setMaxLength(32);
        adminCommodityIdBox.setVisible(false);
        addWidget(adminCommodityIdBox);

        adminItemIdBox = new EditBox(font, leftPos + 152, adminFormY, 112, 14, Component.literal("物品ID"));
        adminItemIdBox.setMaxLength(64);
        adminItemIdBox.setVisible(false);
        addWidget(adminItemIdBox);

        adminDisplayNameBox = new EditBox(font, leftPos + 298, adminFormY, 72, 14, Component.literal("显示名"));
        adminDisplayNameBox.setMaxLength(32);
        adminDisplayNameBox.setVisible(false);
        addWidget(adminDisplayNameBox);

        if (!menu.getStocks().isEmpty()) {
            selectedStock = menu.getStocks().get(0).symbol();
        }

        commodityCacheDirty = true;
        categorizedCacheDirty = true;
        updateInputVisibility();
    }

    /** 刷新市场缓存 */
    private void refreshCache() {
        cachedMarketData = menu.getMarketData();
        int size = cachedMarketData.size();
        cachedMidPriceStr = new String[size];
        cachedBidPriceStr = new String[size];
        cachedAskPriceStr = new String[size];
        cachedDayChangeStr = new String[size];
        cachedDayVolumeStr = new String[size];
        cachedMarketStockStr = new String[size];

        for (int i = 0; i < size; i++) {
            FinanceMenu.MarketRow row = cachedMarketData.get(i);
            cachedMidPriceStr[i] = Long.toString(row.midPrice());
            cachedBidPriceStr[i] = Long.toString(row.bidPrice());
            cachedAskPriceStr[i] = Long.toString(row.askPrice());
            cachedDayChangeStr[i] = FormatUtil.formatPercent(row.dayChange());
            cachedDayVolumeStr[i] = Integer.toString(row.dayVolume());
            cachedMarketStockStr[i] = Integer.toString(row.marketStock());
        }
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

    /** 构建按分类分组的商品缓存 */
    private void rebuildCategorizedCache() {
        Map<CommodityCategory, List<Commodity>> map = new LinkedHashMap<>();
        for (CommodityCategory cat : CommodityCategory.values()) {
            map.put(cat, new ArrayList<>());
        }
        for (Commodity c : CommodityRegistry.getAllCommodities()) {
            map.get(c.getCategory()).add(c);
        }
        // 按显示名排序
        for (List<Commodity> list : map.values()) {
            list.sort(Comparator.comparing(Commodity::getDisplayName));
        }
        categorizedCommodities = map;
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
        if (currentTab == 0) {
            intlQuantityBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (currentTab == 1) {
            priceBox.render(graphics, mouseX, mouseY, partialTick);
            quantityBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (currentTab == 4 && menu.getPlayerCompany() == null) {
            companyNameBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (currentTab == 5) {
            stockQuantityBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (currentTab == 6 && isAdmin && adminSubTab == 0) {
            adminBasePriceBox.render(graphics, mouseX, mouseY, partialTick);
            adminCommodityIdBox.render(graphics, mouseX, mouseY, partialTick);
            adminItemIdBox.render(graphics, mouseX, mouseY, partialTick);
            adminDisplayNameBox.render(graphics, mouseX, mouseY, partialTick);
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
        renderTabs(g, mouseX, mouseY);

        switch (currentTab) {
            case 0 -> renderMarketTab(g);
            case 1 -> renderTradeTab(g);
            case 2 -> renderOrdersTab(g);
            case 3 -> renderInventoryTab(g);
            case 4 -> renderCompanyTab(g);
            case 5 -> renderStockTab(g);
            case 6 -> { if (isAdmin) renderAdminTab(g); }
        }
    }

    // ---- 标签栏 ----

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = TAB_X;
        for (int i = 0; i < tabNames.length; i++) {
            String label = tabNames[i];
            int w = 48;
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

        int visibleRows = Math.min(size, 7);
        int tableBottom = 42 + visibleRows * ROW_HEIGHT;
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

        int rowY = headerY + 14;
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
            renderItemIcon(g, commodity, 14, y + 1);

            drawClippedString(g, commodityDisplayName(row.commodityId()), 28, y + 3, 40, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, cachedMidPriceStr[i], 74, y + 3, 46, COL_TEXT);
            drawClippedString(g, cachedBidPriceStr[i], 126, y + 3, 46, COL_GOOD);
            drawClippedString(g, cachedAskPriceStr[i], 178, y + 3, 46, COL_WARN);
            drawClippedString(g, cachedDayChangeStr[i], 230, y + 3, 46, changeColor(row.dayChange()));
            drawClippedString(g, cachedDayVolumeStr[i], 282, y + 3, 38, COL_TEXT_DIM);
            drawClippedString(g, cachedMarketStockStr[i], 326, y + 3, 42, COL_TEXT_DIM);
        }

        // ---- 国际交易区域 ----
        int tradeY = MARKET_TRADE_Y;
        drawSimpleSeparator(g, 8, tradeY - 4, imageWidth - 16);
        drawSectionTitle(g, "国际交易", 10, tradeY + 1);

        // 显示当前选中商品名（不是按钮行）
        String selName = commodityDisplayName(selectedCommodity);
        drawClippedString(g, "当前: " + selName, 82, tradeY + 1, 120, COL_ACCENT);

        // 信息行
        if (cachedSelectedRow != null) {
            drawClippedString(g, "市场收购: " + cachedSelectedRow.bidPrice(), 10, tradeY + 18, 120, COL_GOOD);
            drawClippedString(g, "市场出售: " + cachedSelectedRow.askPrice(), 140, tradeY + 18, 120, COL_WARN);
        }
        drawClippedString(g, "余额: " + menu.getBalance(), 260, tradeY + 18, 110, COL_TEXT);
        int owned = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
        drawClippedString(g, "持有: " + owned, 10, tradeY + 32, 130, COL_TEXT_DIM);

        // 数量 + 买卖按钮
        g.drawString(font, "数量:", 10, tradeY + 55, COL_TEXT_DIM, false);
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
        }
        drawClippedString(g, "余额: " + menu.getBalance(), 10, infoY + 16, 180, COL_TEXT);
        int owned = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
        drawClippedString(g, "持有: " + owned, 210, infoY + 16, 150, COL_TEXT_DIM);

        drawFilledButton(g, "提交买单", 10, 186, 84, COL_ACCENT);
        drawFilledButton(g, "提交卖单", 102, 186, 84, COL_BAD);
    }

    // ================================================================
    // 下拉菜单渲染
    // ================================================================

    /** 渲染分类下拉面板（在行情/交易标签中使用） */
    private void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (categorizedCacheDirty) rebuildCategorizedCache();

        int ddX = leftPos + 82;
        int ddY = (currentTab == 0) ? topPos + MARKET_TRADE_Y + 14 : topPos + CONTENT_Y + 14;
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
            List<Commodity> items = categorizedCommodities.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题
            if (y + 12 > 0 && y < maxY) {
                g.fill(2, y, ddW - 2, y + 12, COL_CATEGORY);
                g.drawString(font, "▸ " + cat.getDisplayName(), 6, y + 2, COL_TEXT, false);
            }
            y += 14;

            // 展开的分类显示其商品
            if (expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        boolean hovered = localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14;
                        if (hovered) {
                            g.fill(2, y, ddW - 2, y + 14, COL_DROPDOWN_HOVER);
                        }
                        renderItemIcon(g, c, 8, y + 1);
                        drawClippedString(g, c.getDisplayName(), 26, y + 3, 100, COL_TEXT);
                        drawClippedString(g, Long.toString(c.getBasePrice()), 140, y + 3, 50, COL_GOOD);
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
        int h = 0;
        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = categorizedCommodities.get(cat);
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

        drawHeader(g, "#", 12, headerY);
        drawHeader(g, "商品", 38, headerY);
        drawHeader(g, "类型", 105, headerY);
        drawHeader(g, "价格", 148, headerY);
        drawHeader(g, "数量", 224, headerY);
        drawHeader(g, "操作", 334, headerY);

        List<FinanceMenu.OrderRow> orders = menu.getPlayerOrders();
        if (orders.isEmpty()) {
            g.fill(tableX, headerY - 2, tableX + tableW, headerY + 20, 0xFFF1ECDD);
            g.fill(tableX, headerY - 2, tableX + tableW, headerY - 1, COL_PANEL_BORDER);
            g.drawString(font, "暂无挂单", 12, headerY + 4, COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 14;
        int maxRows = Math.min(orders.size(), 12);
        int tableBottom = rowY + maxRows * ROW_HEIGHT;
        g.fill(tableX, headerY - 2, tableX + tableW, tableBottom, 0xFFF1ECDD);
        g.fill(tableX, headerY - 2, tableX + tableW, headerY - 1, COL_PANEL_BORDER);
        g.fill(tableX, tableBottom, tableX + tableW, tableBottom + 1, COL_PANEL_BORDER);
        g.fill(tableX + 1, headerY + 11, tableX + tableW - 1, headerY + 12, COL_PANEL_BORDER);

        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.OrderRow row = orders.get(i);
            int y = rowY + i * ROW_HEIGHT;
            g.fill(tableX + 1, y, tableX + tableW - 1, y + ROW_HEIGHT, (i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, String.valueOf(i + 1), 12, y + 3, 20, COL_TEXT_DIM);
            drawClippedString(g, commodityDisplayName(row.commodityId()), 38, y + 3, 60, COL_TEXT);
            boolean isBuy = "BUY".equals(row.type());
            drawClippedString(g, isBuy ? "买" : "卖", 105, y + 3, 32, isBuy ? COL_GOOD : COL_BAD);
            drawClippedString(g, Long.toString(row.price()), 148, y + 3, 68, COL_TEXT);
            drawClippedString(g, Integer.toString(row.quantity()), 224, y + 3, 70, COL_TEXT);
            drawButton(g, "取消", 334, y + 1, 44, false);
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
        drawHeader(g, "商品", 14, invY + 16);
        drawHeader(g, "库存", 140, invY + 16);
        drawHeader(g, "背包", 210, invY + 16);

        drawFilledButton(g, "存入", 280, invY + 14, 44, COL_GOOD);
        drawFilledButton(g, "取出", 330, invY + 14, 44, COL_ACCENT);

        g.fill(8, invY + 27, imageWidth - 8, invY + 28, COL_PANEL_BORDER);

        Map<String, Integer> inv = menu.getPlayerInventory();
        Map<String, Integer> mcInv = menu.getMcInventory();
        Set<String> allCommodityIds = new LinkedHashSet<>();
        allCommodityIds.addAll(inv.keySet());
        allCommodityIds.addAll(mcInv.keySet());

        if (allCommodityIds.isEmpty()) {
            g.drawString(font, "暂无商品（可通过管理标签添加常用物品）", 12, invY + 32, COL_TEXT_DIM, false);
            return;
        }

        int y = invY + 30;
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
            g.fill(8, cardY, imageWidth - 8, cardY + 72, 0xFFE0E0E0);
            drawSimpleBorder(g, 8, cardY, imageWidth - 16, 72, COL_PANEL_BORDER);

            drawSectionTitle(g, "我的公司", 10, cardY + 2);
            g.drawString(font, "名称", 12, cardY + 18, COL_TEXT_DIM, false);
            drawClippedString(g, company.name(), 58, cardY + 18, 240, COL_TEXT);
            g.drawString(font, "行业", 12, cardY + 32, COL_TEXT_DIM, false);
            drawClippedString(g, company.type(), 58, cardY + 32, 180, COL_ACCENT);
            g.drawString(font, "现金", 12, cardY + 46, COL_TEXT_DIM, false);
            drawClippedString(g, Long.toString(company.cash()), 58, cardY + 46, 100, COL_GOOD);
            g.drawString(font, "估值", 176, cardY + 46, COL_TEXT_DIM, false);
            drawClippedString(g, Long.toString(company.totalValue()), 222, cardY + 46, 120, COL_WARN);
            renderCompanyList(g, cardY + 82);
        } else {
            g.fill(8, cardY, imageWidth - 8, cardY + 96, 0xFFE0E0E0);
            drawSimpleBorder(g, 8, cardY, imageWidth - 16, 96, COL_PANEL_BORDER);

            drawSectionTitle(g, "创建公司", 10, cardY + 2);
            g.drawString(font, "费用", 12, cardY + 18, COL_TEXT_DIM, false);
            g.drawString(font, "10,000", 55, cardY + 18, COL_WARN, false);

            g.drawString(font, "名称", 12, cardY + 36, COL_TEXT_DIM, false);

            g.drawString(font, "行业", 12, cardY + 58, COL_TEXT_DIM, false);
            String typeLabel = "< " + selectedType.getDisplayName() + " >";
            drawButton(g, typeLabel, 55, cardY + 56, 120, true);

            String typeDesc = getTypeDescription(selectedType);
            drawClippedString(g, typeDesc, 55, cardY + 76, 300, COL_TEXT_DIM);

            drawFilledButton(g, "创建公司", 300, cardY + 56, 76, COL_GOOD);
            renderCompanyList(g, cardY + 106);
        }
    }

    private void renderCompanyList(GuiGraphics g, int startY) {
        List<FinanceMenu.CompanyInfo> companies = menu.getAllCompanies();
        int tableX = 8;
        int tableW = imageWidth - 16;
        int tableH = imageHeight - startY - 8;
        if (tableH < 42) return;

        g.fill(tableX, startY, tableX + tableW, startY + tableH, 0xFFE0E0E0);
        drawSimpleBorder(g, tableX, startY, tableW, tableH, COL_PANEL_BORDER);
        drawSectionTitle(g, "公司列表", 10, startY + 3);
        drawHeader(g, "名称", 14, startY + 18);
        drawHeader(g, "行业", 150, startY + 18);
        drawHeader(g, "归属", 218, startY + 18);
        drawHeader(g, "估值", 278, startY + 18);

        if (companies.isEmpty()) {
            g.drawString(font, "暂无公司", 14, startY + 34, COL_TEXT_DIM, false);
            return;
        }

        int y = startY + 32;
        int maxRows = Math.min(companies.size(), Math.max(1, (tableH - 34) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.CompanyInfo row = companies.get(i);
            int bg = (i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD;
            g.fill(tableX + 1, y, tableX + tableW - 1, y + ROW_HEIGHT, bg);
            drawClippedString(g, row.name(), 14, y + 3, 130, COL_TEXT);
            drawClippedString(g, row.type(), 150, y + 3, 62, COL_TEXT);
            drawClippedString(g, row.playerOwned() ? "玩家" : "系统", 218, y + 3, 50,
                    row.playerOwned() ? COL_GOOD : COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.totalValue()), 278, y + 3, 100, COL_WARN);
            y += ROW_HEIGHT;
        }
    }

    // ================================================================
    // 标签 5: 股票
    // ================================================================

    private void renderStockTab(GuiGraphics g) {
        int headerY = CONTENT_Y;
        drawHeader(g, "代码", 12, headerY);
        drawHeader(g, "名称", 62, headerY);
        drawHeader(g, "价格", 164, headerY);
        drawHeader(g, "涨跌", 210, headerY);
        drawHeader(g, "成交", 262, headerY);
        drawHeader(g, "流通", 314, headerY);

        List<FinanceMenu.StockRow> stocks = menu.getStocks();
        if (stocks.isEmpty()) {
            g.drawString(font, "暂无股票。系统公司初始化后会自动生成股票。", 12, headerY + 18, COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 14;
        int maxRows = Math.min(stocks.size(), 8);
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.StockRow row = stocks.get(i);
            int y = rowY + i * ROW_HEIGHT;
            boolean selected = row.symbol().equals(selectedStock);
            g.fill(8, y, imageWidth - 8, y + ROW_HEIGHT,
                    selected ? COL_ROW_SELECT : (i % 2 == 0 ? COL_ROW_EVEN : COL_ROW_ODD));
            if (selected) g.fill(8, y, 10, y + ROW_HEIGHT, COL_ACCENT);
            drawClippedString(g, displayStockSymbol(row), 12, y + 3, 46, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, row.name(), 62, y + 3, 96, COL_TEXT);
            drawClippedString(g, Long.toString(row.lastPrice()), 164, y + 3, 40, COL_TEXT);
            drawClippedString(g, FormatUtil.formatPercent(row.dayChange()), 210, y + 3, 46, changeColor(row.dayChange()));
            drawClippedString(g, Long.toString(row.dayVolume()), 262, y + 3, 46, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.availableShares()), 314, y + 3, 50, COL_TEXT_DIM);
        }

        int tradeY = 160;
        drawSimpleSeparator(g, 8, tradeY - 6, imageWidth - 16);
        drawSectionTitle(g, "股票交易", 10, tradeY);
        FinanceMenu.StockRow selected = findSelectedStock();
        if (selected != null) {
            drawClippedString(g, displayStockSymbol(selected) + " " + selected.name(), 92, tradeY, 160, COL_TEXT);
            drawClippedString(g, "现价: " + selected.lastPrice(), 260, tradeY, 90, COL_TEXT_DIM);
        }
        g.drawString(font, "股数:", 12, tradeY + 21, COL_TEXT_DIM, false);
        drawButton(g, "-", 124, tradeY + 18, 20, false);
        drawButton(g, "+", 148, tradeY + 18, 20, false);
        drawButton(g, "+10", 176, tradeY + 18, 30, false);
        drawFilledButton(g, "买入", 234, tradeY + 18, 58, COL_GOOD);
        drawFilledButton(g, "卖出", 300, tradeY + 18, 58, COL_BAD);

        int holdY = tradeY + 42;
        drawSectionTitle(g, "我的持仓", 10, holdY);
        List<FinanceMenu.StockHoldingRow> holdings = menu.getStockHoldings();
        if (holdings.isEmpty()) {
            g.drawString(font, "暂无持仓", 12, holdY + 16, COL_TEXT_DIM, false);
            return;
        }
        int y = holdY + 16;
        int maxHoldings = Math.min(holdings.size(), 3);
        for (int i = 0; i < maxHoldings; i++) {
            FinanceMenu.StockHoldingRow row = holdings.get(i);
            drawClippedString(g, displayHoldingSymbol(row.symbol()), 14, y, 60, COL_TEXT);
            drawClippedString(g, "数量 " + row.quantity(), 86, y, 92, COL_TEXT_DIM);
            drawClippedString(g, "成本 " + row.averageCost(), 190, y, 100, COL_TEXT_DIM);
            y += ROW_HEIGHT;
        }
    }

    // ================================================================
    // 标签 6: 管理（管理员专用）
    // ================================================================

    private static final String[] ADMIN_SUB_TABS = {"从手中添加", "常用物品", "已注册商品"};

    private void renderAdminTab(GuiGraphics g) {
        int y = CONTENT_Y;

        int subX = 10;
        for (int i = 0; i < ADMIN_SUB_TABS.length; i++) {
            int w = 72;
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
        }
    }

    /** 子页 0：从手中添加 + 手动添加 */
    private void renderAdminHandTab(GuiGraphics g, int y) {
        drawSectionTitle(g, "从手中添加", 10, y);
        drawFilledButton(g, "添加手持物品", 100, y - 2, 90, COL_GOOD);
        g.drawString(font, "价格:", 200, y, COL_TEXT_DIM, false);
        y += 18;

        drawSimpleSeparator(g, 8, y, imageWidth - 16);
        y += 6;

        drawSectionTitle(g, "手动添加", 10, y);
        y += 14;

        g.drawString(font, "ID:", 10, y + 2, COL_TEXT_DIM, false);
        g.drawString(font, "物品:", 120, y + 2, COL_TEXT_DIM, false);
        g.drawString(font, "名称:", 270, y + 2, COL_TEXT_DIM, false);
        y += 18;

        g.drawString(font, "价格:", 10, y + 2, COL_TEXT_DIM, false);
        g.drawString(font, "分类:", 120, y + 2, COL_TEXT_DIM, false);
        drawButton(g, "< " + adminCategory.getDisplayName() + " >", 152, y, 80, true);
        drawFilledButton(g, "添加", 244, y, 44, COL_GOOD);
    }

    /** 子页 1：常用物品分类列表（带图标） */
    private void renderAdminQuickTab(GuiGraphics g, int y) {
        drawSectionTitle(g, "常用物品（点击添加，灰色已注册）", 10, y);
        y += 14;

        int listX = 10;
        int listW = imageWidth - 20;
        int maxYY = imageHeight - 4;

        // 按 QUICK_ITEMS 中的分类分组显示
        CommodityCategory lastCat = null;
        for (int i = 0; i < QUICK_ITEMS.length; i++) {
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
        y += 14;

        drawHeader(g, "ID", 10, y);
        drawHeader(g, "显示名", 80, y);
        drawHeader(g, "物品ID", 160, y);
        drawHeader(g, "价格", 280, y);
        drawHeader(g, "分类", 326, y);
        y += 12;

        if (commodityCacheDirty) {
            cachedCommodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
            commodityCacheDirty = false;
        }
        int maxRows = Math.min(cachedCommodities.size(), Math.max(1, (imageHeight - y - 4) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            Commodity c = cachedCommodities.get(i);
            int rowY = y + i * ROW_HEIGHT;
            g.fill(8, rowY, imageWidth - 8, rowY + ROW_HEIGHT, (i % 2 == 0) ? COL_ROW_EVEN : COL_ROW_ODD);
            drawClippedString(g, c.getId(), 10, rowY + 3, 64, COL_TEXT);
            drawClippedString(g, c.getDisplayName(), 80, rowY + 3, 74, COL_TEXT);
            String itemId = c.getItemId();
            drawClippedString(g, itemId != null ? itemId : "-", 160, rowY + 3, 114, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(c.getBasePrice()), 280, rowY + 3, 40, COL_GOOD);
            drawClippedString(g, c.getCategory().getDisplayName(), 326, rowY + 3, 42, COL_TEXT_DIM);
            drawButton(g, "删除", imageWidth - 46, rowY + 1, 36, false);
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
        if (currentTab == 0) {
            intlQuantityBox.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 1) {
            priceBox.mouseClicked(mouseX, mouseY, button);
            quantityBox.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 4 && menu.getPlayerCompany() == null) {
            companyNameBox.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 5) {
            stockQuantityBox.mouseClicked(mouseX, mouseY, button);
        }
        if (currentTab == 6 && isAdmin && adminSubTab == 0) {
            adminBasePriceBox.mouseClicked(mouseX, mouseY, button);
            adminCommodityIdBox.mouseClicked(mouseX, mouseY, button);
            adminItemIdBox.mouseClicked(mouseX, mouseY, button);
            adminDisplayNameBox.mouseClicked(mouseX, mouseY, button);
        }

        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

        // 标签切换
        if (my >= TAB_Y && my <= TAB_Y + TAB_H) {
            int x = TAB_X;
            for (int i = 0; i < tabNames.length; i++) {
                int w = 48;
                if (mx >= x && mx < x + w) {
                    currentTab = i;
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
            case 6 -> isAdmin && handleAdminClick(mx, my);
            default -> false;
        };
        if (handled) return true;

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
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentTab == 0 && intlQuantityBox.isFocused() && intlQuantityBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currentTab == 1) {
            if (priceBox.isFocused() && priceBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (quantityBox.isFocused() && quantityBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (currentTab == 4 && companyNameBox.isFocused() && companyNameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currentTab == 5 && stockQuantityBox.isFocused() && stockQuantityBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (currentTab == 6 && isAdmin && adminSubTab == 0) {
            if (adminBasePriceBox.isFocused() && adminBasePriceBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (adminCommodityIdBox.isFocused() && adminCommodityIdBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (adminItemIdBox.isFocused() && adminItemIdBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (adminDisplayNameBox.isFocused() && adminDisplayNameBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (currentTab == 0 && intlQuantityBox.isFocused() && intlQuantityBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (currentTab == 1) {
            if (priceBox.isFocused() && priceBox.charTyped(codePoint, modifiers)) return true;
            if (quantityBox.isFocused() && quantityBox.charTyped(codePoint, modifiers)) return true;
        }
        if (currentTab == 4 && companyNameBox.isFocused() && companyNameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (currentTab == 5 && stockQuantityBox.isFocused() && stockQuantityBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (currentTab == 6 && isAdmin && adminSubTab == 0) {
            if (adminBasePriceBox.isFocused() && adminBasePriceBox.charTyped(codePoint, modifiers)) return true;
            if (adminCommodityIdBox.isFocused() && adminCommodityIdBox.charTyped(codePoint, modifiers)) return true;
            if (adminItemIdBox.isFocused() && adminItemIdBox.charTyped(codePoint, modifiers)) return true;
            if (adminDisplayNameBox.isFocused() && adminDisplayNameBox.charTyped(codePoint, modifiers)) return true;
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

        int localMx = mx - ddX;
        int localMy = my - ddY;
        int y = 2 - dropdownScroll;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = categorizedCommodities.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题点击 → 展开/折叠
            if (localMy >= y && localMy < y + 12) {
                expandedCategory = (expandedCategory == cat) ? null : cat;
                return true;
            }
            y += 14;

            if (expandedCategory == cat) {
                for (Commodity c : items) {
                    if (localMy >= y && localMy < y + 14) {
                        selectedCommodity = c.getId();
                        if (currentTab == 1) {
                            finance.market.MarketPrice mp = NpcMarketMaker.getMarketPrice(c.getId());
                            if (mp != null) priceBox.setValue(Long.toString(mp.getMidPrice()));
                        }
                        dropdownOpen = false;
                        expandedCategory = null;
                        refreshSelectedRow();
                        return true;
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
        int rowY = headerY + 14;
        int maxRows = Math.min(cachedMarketData.size(), 7);
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 8 && mx < imageWidth - 8) {
                selectedCommodity = cachedMarketData.get(i).commodityId();
                refreshSelectedRow();
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
            int qty = parseInt(intlQuantityBox.getValue());
            if (mx >= 226 && mx < 294) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_BUY, selectedCommodity, 0, qty));
                return true;
            }
            if (mx >= 300 && mx < 368) {
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
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.P2P_BUY, selectedCommodity, price, qty));
                return true;
            }
            if (mx >= 102 && mx < 186) {
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
        int rowY = CONTENT_Y + 14;
        int maxRows = Math.min(orders.size(), 12);
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 334 && mx < 378) {
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

        // 存入按钮
        if (mx >= 280 && mx < 324 && my >= invY + 14 && my < invY + 27) {
            Commodity c = CommodityRegistry.getCommodity(selectedCommodity);
            if (c != null && c.getItemId() != null) {
                int mcAmount = menu.getMcInventory().getOrDefault(selectedCommodity, 0);
                if (mcAmount > 0) {
                    FinancePacketHandler.CHANNEL.sendToServer(
                            new InventoryActionPacket(InventoryActionPacket.ActionType.DEPOSIT, selectedCommodity, mcAmount));
                }
            }
            return true;
        }

        // 取出按钮
        if (mx >= 330 && mx < 374 && my >= invY + 14 && my < invY + 27) {
            int amount = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
            if (amount > 0) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new InventoryActionPacket(InventoryActionPacket.ActionType.WITHDRAW, selectedCommodity, amount));
            }
            return true;
        }

        // 表格行点击
        int y = invY + 30;
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
                return true;
            }
            y += ROW_HEIGHT;
        }
        return false;
    }

    // ---- 标签 4 点击: 公司 ----

    private boolean handleCompanyClick(int mx, int my) {
        if (menu.getPlayerCompany() != null) return false;

        int cardY = CONTENT_Y;

        if (mx >= 55 && mx < 175 && my >= cardY + 56 && my < cardY + 70) {
            int idx = 0;
            for (int i = 0; i < COMPANY_TYPES.length; i++) {
                if (COMPANY_TYPES[i] == selectedType) { idx = i; break; }
            }
            selectedType = COMPANY_TYPES[(idx + 1) % COMPANY_TYPES.length];
            return true;
        }

        if (mx >= 300 && mx < 376 && my >= cardY + 56 && my < cardY + 70) {
            String name = companyNameBox.getValue().trim();
            if (!name.isEmpty()) {
                FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(selectedType, name));
            }
            return true;
        }
        return false;
    }

    // ---- 标签 5 点击: 股票 ----

    private boolean handleStockClick(int mx, int my) {
        int rowY = CONTENT_Y + 14;
        int maxRows = Math.min(menu.getStocks().size(), 8);
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * ROW_HEIGHT;
            if (my >= y && my < y + ROW_HEIGHT && mx >= 8 && mx < imageWidth - 8) {
                selectedStock = menu.getStocks().get(i).symbol();
                return true;
            }
        }

        int tradeY = 160;
        if (my >= tradeY + 18 && my < tradeY + 32) {
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
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockTradePacket(StockTradePacket.ActionType.BUY, selectedStock, qty));
                return true;
            }
            if (mx >= 300 && mx < 358) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockTradePacket(StockTradePacket.ActionType.SELL, selectedStock, qty));
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
            int w = 72;
            if (mx >= subX && mx < subX + w && my >= y && my < y + 14) {
                adminSubTab = i;
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
            default -> false;
        };
    }

    private boolean handleAdminHandClick(int mx, int my, int y) {
        if (my >= y - 2 && my < y + 12 && mx >= 100 && mx < 190) {
            long basePrice = parseLong(adminBasePriceBox.getValue());
            if (basePrice <= 0) basePrice = 10;
            FinancePacketHandler.CHANNEL.sendToServer(AdminActionPacket.fromHand(basePrice));
            return true;
        }
        if (my >= y - 2 && my < y + 12 && mx >= 228 && mx < 288) {
            return false;
        }
        y += 18 + 6 + 14;

        if (my >= y && my < y + 16) {
            return false;
        }
        y += 18;

        if (my >= y && my < y + 13 && mx >= 152 && mx < 232) {
            CommodityCategory[] cats = CommodityCategory.values();
            int idx = adminCategory.ordinal();
            adminCategory = cats[(idx + 1) % cats.length];
            return true;
        }

        if (my >= y && my < y + 13 && mx >= 244 && mx < 288) {
            String id = adminCommodityIdBox.getValue().trim().toLowerCase();
            String itemId = adminItemIdBox.getValue().trim();
            String displayName = adminDisplayNameBox.getValue().trim();
            long basePrice = parseLong(adminBasePriceBox.getValue());

            if (id.isEmpty()) return true;
            if (displayName.isEmpty()) displayName = id;
            if (itemId.isEmpty()) itemId = null;

            FinancePacketHandler.CHANNEL.sendToServer(
                    new AdminActionPacket(id, itemId, displayName, basePrice, adminCategory));
            return true;
        }

        return false;
    }

    /** 子页 1 点击：常用物品分类列表 */
    private boolean handleAdminQuickClick(int mx, int my, int startY) {
        int y = startY + 14; // 跳过标题
        int listX = 10;
        int listW = imageWidth - 20;
        int maxYY = imageHeight - 4;

        CommodityCategory lastCat = null;
        for (int i = 0; i < QUICK_ITEMS.length; i++) {
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
                    FinancePacketHandler.CHANNEL.sendToServer(
                            new AdminActionPacket(id, itemId, displayName, basePrice, category));
                }
                return true;
            }
            y += 16;
        }
        return false;
    }

    private boolean handleAdminRegisteredClick(int mx, int my, int y) {
        y += 14 + 12;
        if (commodityCacheDirty) {
            cachedCommodities = new ArrayList<>(CommodityRegistry.getAllCommodities());
            commodityCacheDirty = false;
        }
        int maxRows = Math.min(cachedCommodities.size(), Math.max(1, (imageHeight - y - 4) / ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int rowY = y + i * ROW_HEIGHT;
            if (my >= rowY + 1 && my < rowY + 14 && mx >= imageWidth - 46 && mx < imageWidth - 10) {
                String commodityId = cachedCommodities.get(i).getId();
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
        boolean company = (currentTab == 4 && menu.getPlayerCompany() == null);
        companyNameBox.setVisible(company);
        stockQuantityBox.setVisible(currentTab == 5);
        boolean admin = (currentTab == 6 && isAdmin);
        boolean adminHand = admin && adminSubTab == 0;
        adminCommodityIdBox.setVisible(adminHand);
        adminItemIdBox.setVisible(adminHand);
        adminDisplayNameBox.setVisible(adminHand);
        adminBasePriceBox.setVisible(adminHand);
    }

    /** 渲染物品图标（12x12，居中在 13px 行高中） */
    private void renderItemIcon(GuiGraphics g, Commodity commodity, int x, int y) {
        if (commodity == null) return;
        String itemId = commodity.getItemId();
        if (itemId == null) return;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (item == null) return;
        g.renderItem(new ItemStack(item), x, y);
    }

    private FinanceMenu.StockRow findSelectedStock() {
        for (FinanceMenu.StockRow row : menu.getStocks()) {
            if (row.symbol().equals(selectedStock)) return row;
        }
        return menu.getStocks().isEmpty() ? null : menu.getStocks().get(0);
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
        int textEnd = x + font.width(label) + 4;
        g.fill(textEnd, y + 5, Math.min(textEnd + 40, imageWidth - 10), y + 6, COL_PANEL_BORDER);
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
