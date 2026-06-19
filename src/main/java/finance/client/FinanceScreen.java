package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.company.CompanyType;
import finance.gui.FinanceMenu;
import finance.network.*;
import finance.util.FormatUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;

/**
 * 金融操作中心 —— 贴近 Minecraft 原生容器风格的 5 标签页 GUI。
 */
public class FinanceScreen extends AbstractContainerScreen<FinanceMenu> {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 264;
    private static final int ROW_HEIGHT = 15;
    private static final int TAB_X = 10;
    private static final int TAB_Y = 24;
    private static final int TAB_H = 16;
    private static final int CONTENT_Y = 46;
    private static final int MARKET_TRADE_Y = 176;

    // ---- Minecraft 风格色彩 ----
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

    // ---- 标签 ----
    private static final String[] TAB_NAMES = {"行情", "交易", "订单", "库存", "公司", "股票"};
    private static final CompanyType[] COMPANY_TYPES = CompanyType.values();
    private int currentTab = 0;

    // ---- 行情标签（国际交易）控件 ----
    private String selectedCommodity = "iron";
    private EditBox intlQuantityBox;

    // ---- 交易标签控件 ----
    private EditBox priceBox;
    private EditBox quantityBox;

    // ---- 公司标签控件 ----
    private EditBox companyNameBox;
    private CompanyType selectedType = CompanyType.MINING;

    // ---- 股票标签控件 ----
    private String selectedStock = "";
    private EditBox stockQuantityBox;

    // ---- 缓存（避免每帧重复计算） ----
    private List<FinanceMenu.MarketRow> cachedMarketData;
    private FinanceMenu.MarketRow cachedSelectedRow;
    private String lastSelectedCommodity = "";
    private int[] cachedButtonX;   // 商品按钮起始 X
    private int[] cachedButtonW;   // 商品按钮宽度
    private String[] cachedMidPriceStr;  // 格式化后的价格字符串
    private String[] cachedBidPriceStr;
    private String[] cachedAskPriceStr;
    private String[] cachedDayChangeStr;
    private String[] cachedDayVolumeStr;
    private String[] cachedMarketStockStr;
    private boolean cacheDirty = true;

    private int scrollOffset = 0;
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

        stockQuantityBox = new EditBox(font, leftPos + 54, topPos + 202, 64, 16, Component.literal("股数"));
        stockQuantityBox.setMaxLength(8);
        stockQuantityBox.setValue("1");
        stockQuantityBox.setVisible(false);
        addWidget(stockQuantityBox);

        if (!menu.getStocks().isEmpty()) {
            selectedStock = menu.getStocks().get(0).symbol();
        }

        scrollOffset = 0;
        updateInputVisibility();
    }

    /** 刷新缓存数据（只在 init 或数据变化时调用） */
    private void refreshCache() {
        cachedMarketData = menu.getMarketData();
        int size = cachedMarketData.size();
        cachedButtonX = new int[size];
        cachedButtonW = new int[size];
        cachedMidPriceStr = new String[size];
        cachedBidPriceStr = new String[size];
        cachedAskPriceStr = new String[size];
        cachedDayChangeStr = new String[size];
        cachedDayVolumeStr = new String[size];
        cachedMarketStockStr = new String[size];

        int btnX = 82;
        for (int i = 0; i < size; i++) {
            FinanceMenu.MarketRow row = cachedMarketData.get(i);
            int w = Math.max(36, Math.min(74, font.width(row.commodityId()) + 12));
            cachedButtonX[i] = btnX;
            cachedButtonW[i] = w;
            btnX += w + 3;

            // 预格式化数字字符串
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

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();

        // 主面板背景
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COL_BG);
        drawSimpleBorder(g, leftPos, topPos, imageWidth, imageHeight, COL_PANEL_BORDER);
        // 标题栏和标签栏背景
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 21, 0xFFF4F0E2);
        g.fill(leftPos + 1, topPos + 22, leftPos + imageWidth - 1, topPos + 42, COL_TAB_BG);
        g.fill(leftPos + 1, topPos + 42, leftPos + imageWidth - 1, topPos + 43, COL_PANEL_BORDER);

        RenderSystem.disableBlend();
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
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 标题
            drawClippedString(g, "金融中心", 10, 7, 130, COL_TEXT);
        renderTabs(g, mouseX, mouseY);

        switch (currentTab) {
            case 0 -> renderMarketTab(g);
            case 1 -> renderTradeTab(g);
            case 2 -> renderOrdersTab(g);
            case 3 -> renderInventoryTab(g);
            case 4 -> renderCompanyTab(g);
            case 5 -> renderStockTab(g);
        }
    }

    // ---- 标签栏 ----

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = TAB_X;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            String label = TAB_NAMES[i];
            int w = 48;
            int tabX = x;
            boolean active = (i == currentTab);
            boolean hovered = mouseX >= leftPos + tabX && mouseX < leftPos + tabX + w
                    && mouseY >= topPos + TAB_Y && mouseY < topPos + TAB_Y + TAB_H;

            // 标签文字
            int textColor = active ? COL_TEXT : (hovered ? COL_TEXT : COL_TEXT_DIM);
            if (active || hovered) {
                g.fill(tabX, TAB_Y - 1, tabX + w, TAB_Y + TAB_H, 0xFFECE6D5);
            }
            drawClippedString(g, label, tabX + 5, TAB_Y + 2, w - 10, textColor);

            // 活跃标签底部指示线
            if (active) {
                g.fill(tabX + 3, TAB_Y + TAB_H - 1, tabX + w - 3, TAB_Y + TAB_H, COL_ACCENT);
            }

            x += w + 3;
        }
    }

    // ---- 标签 0: 市场行情 ----

    private void renderMarketTab(GuiGraphics g) {
        if (cacheDirty) refreshCache();
        refreshSelectedRow();

        int tableX = 8;
        int tableW = imageWidth - 16;
        int headerY = CONTENT_Y;
        int size = cachedMarketData.size();

        // 表格卡片背景
        int visibleRows = Math.min(size, 7);
        int tableBottom = 42 + visibleRows * ROW_HEIGHT;
        g.fill(tableX, headerY - 2, tableX + tableW, tableBottom, 0xFFF1ECDD);
        g.fill(tableX, headerY - 2, tableX + tableW, headerY - 1, COL_PANEL_BORDER);
        g.fill(tableX, tableBottom, tableX + tableW, tableBottom + 1, COL_PANEL_BORDER);

        drawHeader(g, "商品", 12, headerY);
        drawHeader(g, "中间价", 80, headerY);
        drawHeader(g, "收购", 138, headerY);
        drawHeader(g, "出售", 196, headerY);
        drawHeader(g, "涨跌", 254, headerY);
        drawHeader(g, "成交", 306, headerY);
        drawHeader(g, "库存", 358, headerY);

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

            int textColor = selected ? 0xFFFFFFFF : COL_TEXT;
            drawClippedString(g, row.commodityId(), 12, y + 3, 62, selected ? COL_ACCENT : textColor);
            drawClippedString(g, cachedMidPriceStr[i], 80, y + 3, 52, textColor);
            drawClippedString(g, cachedBidPriceStr[i], 138, y + 3, 52, COL_GOOD);
            drawClippedString(g, cachedAskPriceStr[i], 196, y + 3, 52, COL_WARN);
            drawClippedString(g, cachedDayChangeStr[i], 254, y + 3, 46, changeColor(row.dayChange()));
            drawClippedString(g, cachedDayVolumeStr[i], 306, y + 3, 46, COL_TEXT_DIM);
            drawClippedString(g, cachedMarketStockStr[i], 358, y + 3, 46, COL_TEXT_DIM);
        }

        // ---- 国际交易区域 ----
        int tradeY = MARKET_TRADE_Y;
        drawSimpleSeparator(g, 8, tradeY - 4, imageWidth - 16);
        drawSectionTitle(g, "国际交易", 10, tradeY + 1);

        // 商品选择按钮（使用缓存的 X 和 W）
        for (int i = 0; i < size; i++) {
            boolean sel = cachedMarketData.get(i).commodityId().equals(selectedCommodity);
            drawButton(g, cachedMarketData.get(i).commodityId(), cachedButtonX[i], tradeY, cachedButtonW[i], sel);
        }

        // 信息行
        if (cachedSelectedRow != null) {
            drawClippedString(g, "市场收购: " + cachedSelectedRow.bidPrice(), 10, tradeY + 20, 120, COL_GOOD);
            drawClippedString(g, "市场出售: " + cachedSelectedRow.askPrice(), 140, tradeY + 20, 120, COL_WARN);
        }
        drawClippedString(g, "余额: " + menu.getBalance(), 270, tradeY + 20, 135, COL_TEXT);
        int owned = menu.getPlayerInventory().getOrDefault(selectedCommodity, 0);
        drawClippedString(g, "持有: " + owned, 10, tradeY + 36, 130, COL_TEXT_DIM);

        g.drawString(font, "数量:", 10, tradeY + 55, COL_TEXT_DIM, false);
        drawButton(g, "-", 132, tradeY + 52, 20, false);
        drawButton(g, "+", 156, tradeY + 52, 20, false);
        drawButton(g, "-10", 184, tradeY + 52, 30, false);
        drawButton(g, "+10", 218, tradeY + 52, 30, false);

        drawFilledButton(g, "从市场买入", 268, tradeY + 52, 68, COL_GOOD);
        drawFilledButton(g, "卖给市场", 342, tradeY + 52, 64, COL_BAD);
    }

    // ---- 标签 1: 交易下单 ----

    private void renderTradeTab(GuiGraphics g) {
        if (cacheDirty) refreshCache();
        refreshSelectedRow();

        drawSectionTitle(g, "商品选择", 10, CONTENT_Y);
        drawCommodityButtons(g, 82, CONTENT_Y + 14);

        int cardY = CONTENT_Y + 34;
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

    // ---- 标签 2: 订单管理 ----

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

            drawClippedString(g, String.valueOf(row.index()), 12, y + 3, 20, COL_TEXT_DIM);
            drawClippedString(g, row.commodityId(), 38, y + 3, 60, COL_TEXT);
            boolean isBuy = "BUY".equals(row.type());
            drawClippedString(g, isBuy ? "买" : "卖", 105, y + 3, 32, isBuy ? COL_GOOD : COL_BAD);
            drawClippedString(g, Long.toString(row.price()), 148, y + 3, 68, COL_TEXT);
            drawClippedString(g, Integer.toString(row.quantity()), 224, y + 3, 70, COL_TEXT);
            drawButton(g, "取消", 334, y + 1, 44, false);
        }
    }

    // ---- 标签 3: 库存与账户 ----

    private void renderInventoryTab(GuiGraphics g) {
        // 账户卡片
        int cardY = CONTENT_Y;
        int cardH = 38;
        g.fill(8, cardY, imageWidth - 8, cardY + cardH, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, cardY, imageWidth - 16, cardH, COL_PANEL_BORDER);

        drawSectionTitle(g, "账户", 10, cardY + 2);
        g.drawString(font, "可用: ", 12, cardY + 18, COL_TEXT_DIM, false);
        drawClippedString(g, Long.toString(menu.getBalance()), 46, cardY + 18, 120, COL_GOOD);
        g.drawString(font, "冻结: ", 190, cardY + 18, COL_TEXT_DIM, false);
        drawClippedString(g, Long.toString(menu.getFrozenBalance()), 224, cardY + 18, 120, COL_WARN);

        // 库存卡片
        int invY = cardY + cardH + 6;
        int invH = imageHeight - invY - 4;
        g.fill(8, invY, imageWidth - 8, invY + invH, 0xFFF1ECDD);
        drawSimpleBorder(g, 8, invY, imageWidth - 16, invH, COL_PANEL_BORDER);

        drawSectionTitle(g, "商品库存", 10, invY + 2);
        Map<String, Integer> inv = menu.getPlayerInventory();
        if (inv.isEmpty()) {
            g.drawString(font, "暂无商品", 12, invY + 18, COL_TEXT_DIM, false);
            return;
        }
        int y = invY + 18;
        int maxY = invY + invH - ROW_HEIGHT;
        for (Map.Entry<String, Integer> entry : inv.entrySet()) {
            if (y > maxY) break;
            drawClippedString(g, entry.getKey(), 16, y, 120, COL_TEXT);
            drawClippedString(g, String.valueOf(entry.getValue()), 150, y, 120, COL_ACCENT);
            y += ROW_HEIGHT;
        }
    }

    // ---- 标签 4: 公司管理 ----

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

    private void renderStockTab(GuiGraphics g) {
        int headerY = CONTENT_Y;
        drawHeader(g, "代码", 12, headerY);
        drawHeader(g, "名称", 62, headerY);
        drawHeader(g, "价格", 170, headerY);
        drawHeader(g, "涨跌", 222, headerY);
        drawHeader(g, "成交", 274, headerY);
        drawHeader(g, "流通", 334, headerY);

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
            drawClippedString(g, displayStockSymbol(row), 12, y + 3, 46, selected ? COL_ACCENT : COL_TEXT);
            drawClippedString(g, row.name(), 62, y + 3, 100, COL_TEXT);
            drawClippedString(g, Long.toString(row.lastPrice()), 170, y + 3, 46, COL_TEXT);
            drawClippedString(g, FormatUtil.formatPercent(row.dayChange()), 222, y + 3, 46, changeColor(row.dayChange()));
            drawClippedString(g, Long.toString(row.dayVolume()), 274, y + 3, 54, COL_TEXT_DIM);
            drawClippedString(g, Long.toString(row.availableShares()), 334, y + 3, 64, COL_TEXT_DIM);
        }

        int tradeY = 184;
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

    private FinanceMenu.StockRow findSelectedStock() {
        for (FinanceMenu.StockRow row : menu.getStocks()) {
            if (row.symbol().equals(selectedStock)) {
                return row;
            }
        }
        return menu.getStocks().isEmpty() ? null : menu.getStocks().get(0);
    }

    private String displayStockSymbol(FinanceMenu.StockRow row) {
        return switch (row.symbol()) {
            case "IRONM", "MININ", "铁矿" -> "铁矿";
            case "COALE", "ENERG", "煤能" -> "煤能";
            case "WHEAT", "AGRIC", "麦田" -> "麦田";
            case "STEEL", "MANUF", "钢铁" -> "钢铁";
            default -> row.symbol();
        };
    }

    private String displayHoldingSymbol(String symbol) {
        return switch (symbol) {
            case "IRONM", "MININ", "铁矿" -> "铁矿";
            case "COALE", "ENERG", "煤能" -> "煤能";
            case "WHEAT", "AGRIC", "麦田" -> "麦田";
            case "STEEL", "MANUF", "钢铁" -> "钢铁";
            default -> symbol;
        };
    }

    // ================================================================
    // 交互
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

        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;

        // 标签切换（从右往左布局，需要重新计算）
        if (my >= TAB_Y && my <= TAB_Y + TAB_H) {
            int x = TAB_X;
            for (int i = 0; i < TAB_NAMES.length; i++) {
                int w = 48;
                if (mx >= x && mx < x + w) {
                    currentTab = i;
                    scrollOffset = 0;
                    updateInputVisibility();
                    return true;
                }
                x += w + 3;
            }
        }

        boolean handled = switch (currentTab) {
            case 0 -> handleMarketClick(mx, my);
            case 1 -> handleTradeClick(mx, my);
            case 2 -> handleOrdersClick(mx, my);
            case 4 -> handleCompanyClick(mx, my);
            case 5 -> handleStockClick(mx, my);
            default -> false;
        };
        if (handled) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
        return super.charTyped(codePoint, modifiers);
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

        // 商品选择按钮（使用缓存位置）
        for (int i = 0; i < cachedMarketData.size(); i++) {
            if (mx >= cachedButtonX[i] && mx < cachedButtonX[i] + cachedButtonW[i] && my >= tradeY && my < tradeY + 14) {
                selectedCommodity = cachedMarketData.get(i).commodityId();
                refreshSelectedRow();
                return true;
            }
        }

        // 数量加减按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            int qty = parseInt(intlQuantityBox.getValue());
            if (mx >= 132 && mx < 152) {
                intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 156 && mx < 176) {
                intlQuantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 184 && mx < 214) {
                intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 218 && mx < 248) {
                intlQuantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 国际买入/卖出按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            int qty = parseInt(intlQuantityBox.getValue());
            if (mx >= 268 && mx < 336) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_BUY, selectedCommodity, 0, qty));
                return true;
            }
            if (mx >= 342 && mx < 406) {
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_SELL, selectedCommodity, 0, qty));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 1 点击: 交易 ----

    private boolean handleTradeClick(int mx, int my) {
        // 商品选择按钮（使用缓存）
        int x = 82;
        for (int i = 0; i < cachedMarketData.size(); i++) {
            FinanceMenu.MarketRow row = cachedMarketData.get(i);
            if (mx >= x && mx < x + cachedButtonW[i] && my >= CONTENT_Y + 14 && my < CONTENT_Y + 28) {
                selectedCommodity = row.commodityId();
                priceBox.setValue(cachedMidPriceStr[i]);
                refreshSelectedRow();
                return true;
            }
            x += cachedButtonW[i] + 3;
        }

        int cardY = CONTENT_Y + 34;

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

        // 操作按钮
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
                FinancePacketHandler.CHANNEL.sendToServer(new CancelOrderPacket(orders.get(i).index()));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 4 点击: 公司 ----

    private boolean handleCompanyClick(int mx, int my) {
        if (menu.getPlayerCompany() != null) return false;

        int cardY = CONTENT_Y;

        // 行业选择按钮
        if (mx >= 55 && mx < 175 && my >= cardY + 56 && my < cardY + 70) {
            int idx = 0;
            for (int i = 0; i < COMPANY_TYPES.length; i++) {
                if (COMPANY_TYPES[i] == selectedType) { idx = i; break; }
            }
            selectedType = COMPANY_TYPES[(idx + 1) % COMPANY_TYPES.length];
            return true;
        }

        // 创建按钮
        if (mx >= 300 && mx < 376 && my >= cardY + 56 && my < cardY + 70) {
            String name = companyNameBox.getValue().trim();
            if (!name.isEmpty()) {
                FinancePacketHandler.CHANNEL.sendToServer(new CreateCompanyPacket(selectedType, name));
            }
            return true;
        }
        return false;
    }

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

        int tradeY = 184;
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

    /** 区域标题（带装饰线） */
    private void drawSectionTitle(GuiGraphics g, String label, int x, int y) {
        g.drawString(font, label, x, y, COL_TEXT, false);
        int textEnd = x + font.width(label) + 4;
        g.fill(textEnd, y + 5, Math.min(textEnd + 40, imageWidth - 10), y + 6, COL_PANEL_BORDER);
    }

    /** 表头 */
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
        g.fill(x, y, x + w, y + 13, 0xFFD6D6D6);
        drawSimpleBorder(g, x, y, w, 13, COL_PANEL_BORDER);
        drawCenteredClippedString(g, label, x, y + 3, w, COL_TEXT);
    }

    private void drawCommodityButtons(GuiGraphics g, int startX, int y) {
        if (cacheDirty) refreshCache();
        int x = startX;
        for (int i = 0; i < cachedMarketData.size(); i++) {
            FinanceMenu.MarketRow row = cachedMarketData.get(i);
            boolean selected = row.commodityId().equals(selectedCommodity);
            drawButton(g, row.commodityId(), x, y, cachedButtonW[i], selected);
            x += cachedButtonW[i] + 3;
        }
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
            case MINING -> "产出: 铁×100 煤×50/天";
            case AGRICULTURE -> "产出: 小麦×80/天";
            case ENERGY -> "产出: 煤×120/天";
            case MANUFACTURING -> "产出: 钢材×40 消耗: 铁×80/天";
            case LOGISTICS -> "暂未开放生产，适合后续运输系统";
            case BANKING -> "暂未开放生产，适合后续贷款/股票系统";
        };
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 1; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 1; }
    }
}
