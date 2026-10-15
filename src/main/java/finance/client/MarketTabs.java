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

/** 行情 / 交易 / 商品下拉（含行情缓存与下拉状态）：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */

public final class MarketTabs {
    private final FinanceScreen screen;

    public MarketTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    // ================================================================
    // 标签 0: 市场行情 + 国际交易
    // ================================================================

    public void renderMarketTab(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.cacheDirty) screen.refreshCache();
        screen.refreshSelectedRow();

        if (screen.commodityChartVisible) {
            screen.stockTabs.renderCandlestickView(g, MarketInstrumentType.COMMODITY, screen.selectedCommodity,
                    FinanceFormat.commodityDisplayName(screen.selectedCommodity), mouseX, mouseY);
            return;
        }

        int tableX = 8;
        int tableW = screen.imageWidth() - 16;
        int headerY = FinanceGui.CONTENT_Y;
        int size = screen.cachedMarketData.size();

        int rowY = headerY + 16;
        int visibleRows = Math.min(size, Math.max(1, (FinanceGui.MARKET_TRADE_Y - rowY - 8) / FinanceGui.ROW_HEIGHT));
        int tableBottom = rowY + visibleRows * FinanceGui.ROW_HEIGHT;
        g.fill(tableX, headerY - 2, tableX + tableW, tableBottom, 0xFFF1ECDD);
        g.fill(tableX, headerY - 2, tableX + tableW, headerY - 1, FinanceGui.COL_PANEL_BORDER);
        g.fill(tableX, tableBottom, tableX + tableW, tableBottom + 1, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawHeader(screen.font(), g, "商品", 12, headerY);
        FinanceGui.drawHeader(screen.font(), g, "中间价", 74, headerY);
        FinanceGui.drawHeader(screen.font(), g, "收购", 126, headerY);
        FinanceGui.drawHeader(screen.font(), g, "出售", 178, headerY);
        FinanceGui.drawHeader(screen.font(), g, "涨跌", 230, headerY);
        FinanceGui.drawHeader(screen.font(), g, "成交", 282, headerY);
        FinanceGui.drawHeader(screen.font(), g, "库存", 326, headerY);

        g.fill(tableX + 1, headerY + 11, tableX + tableW - 1, headerY + 12, FinanceGui.COL_PANEL_BORDER);

        if (size == 0) {
            g.drawString(screen.font(), "暂无数据", 12, headerY + 16, FinanceGui.COL_TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            FinanceMenu.MarketRow row = screen.cachedMarketData.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            boolean selected = row.commodityId().equals(screen.selectedCommodity);

            int bg = selected ? FinanceGui.COL_ROW_SELECT : ((i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            g.fill(tableX + 1, y, tableX + tableW - 1, y + FinanceGui.ROW_HEIGHT, bg);
            if (selected) {
                g.fill(tableX + 1, y, tableX + 3, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            }

            // 物品图标
            Commodity commodity = CommodityRegistry.getCommodity(row.commodityId());
            FinanceGui.renderItemIcon(g, commodity, 14, y + 4);

            FinanceGui.drawClippedString(screen.font(), g, FinanceFormat.commodityDisplayName(row.commodityId()), 30, y + 5, 40, selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.midPrice()), 74, y + 5, 46, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.bidPrice()), 126, y + 5, 46, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.askPrice()), 178, y + 5, 46, FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(screen.font(), g, FormatUtil.formatPercent(row.dayChange()), 230, y + 5, 46, FinanceGui.changeColor(row.dayChange()));
            FinanceGui.drawClippedString(screen.font(), g, Integer.toString(row.dayVolume()), 282, y + 5, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, Integer.toString(row.marketStock()), 326, y + 5, 80, FinanceGui.COL_TEXT_DIM);
        }

        // ---- 国际交易区域 ----
        int tradeY = FinanceGui.MARKET_TRADE_Y;
        g.drawString(screen.font(), "国际交易", 10, tradeY + 1, FinanceGui.COL_TEXT, false);
        FinanceGui.drawButton(screen.font(), g, "K线", 360, tradeY, 30, false);

        // 显示当前选中商品名（不是按钮行）
        String selName = FinanceFormat.commodityDisplayName(screen.selectedCommodity);
        FinanceGui.drawClippedString(screen.font(), g, "当前: " + selName, 82, tradeY + 1, 120, FinanceGui.COL_ACCENT);

        // 信息行
        if (screen.cachedSelectedRow != null) {
            FinanceGui.drawClippedString(screen.font(), g, "市场收购: " + screen.cachedSelectedRow.bidPrice(), 10, tradeY + 18, 120, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "市场出售: " + screen.cachedSelectedRow.askPrice(), 140, tradeY + 18, 120, FinanceGui.COL_WARN);
            FinanceGui.drawTrendGraph(g, screen.cachedSelectedRow.priceHistory(), 260, tradeY + 1, 108, 14, FinanceGui.changeColor(screen.cachedSelectedRow.dayChange()));
            FinanceGui.drawClippedString(screen.font(), g, "高 " + screen.cachedSelectedRow.dayHigh() + " 低 " + screen.cachedSelectedRow.dayLow(),
                    10, tradeY + 44, 150, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "量 " + screen.cachedSelectedRow.dayVolume(), 170, tradeY + 44, 70, FinanceGui.COL_TEXT_DIM);
        }
        FinanceGui.drawClippedString(screen.font(), g, "余额: " + screen.menu().getBalance(), 260, tradeY + 18, 110, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "仓储 " + screen.menu().getWarehouseUsed() + "/" + screen.menu().getWarehouseCapacity()
                + (screen.menu().isWarehouseOverCapacity() ? " 超量" : ""), 260, tradeY + 44, 120,
                screen.menu().isWarehouseOverCapacity() ? FinanceGui.COL_BAD : FinanceGui.COL_TEXT_DIM);
        int owned = screen.menu().getPlayerInventory().getOrDefault(screen.selectedCommodity, 0);
        FinanceGui.drawClippedString(screen.font(), g, "持有: " + owned, 10, tradeY + 32, 130, FinanceGui.COL_TEXT_DIM);
        int intlQty = Math.max(1, FinanceFormat.parseInt(screen.intlQuantityBox.getValue()));
        if (screen.cachedSelectedRow != null) {
            long buyTotal = FinanceFormat.safeMultiply(screen.cachedSelectedRow.askPrice(), intlQty);
            long sellTotal = FinanceFormat.safeMultiply(screen.cachedSelectedRow.bidPrice(), intlQty);
            FinanceGui.drawClippedString(screen.font(), g, "买入总价: " + buyTotal, 130, tradeY + 32, 112, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "卖出总价: " + sellTotal, 250, tradeY + 32, 112, FinanceGui.COL_WARN);
        }

        // 数量 + 买卖按钮
        g.drawString(screen.font(), "数量:", 10, tradeY + 53, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawButton(screen.font(), g, "-", 100, tradeY + 52, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 124, tradeY + 52, 20, false);
        FinanceGui.drawButton(screen.font(), g, "-10", 152, tradeY + 52, 30, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 186, tradeY + 52, 30, false);

        FinanceGui.drawFilledButton(screen.font(), g, "从市场买入", 226, tradeY + 52, 68, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "卖给市场", 300, tradeY + 52, 68, FinanceGui.COL_BAD);
    }

    // ================================================================
    // 标签 1: 交易下单（分类下拉选择商品）
    // ================================================================

    public void renderTradeTab(GuiGraphics g) {
        if (screen.cacheDirty) screen.refreshCache();
        screen.refreshSelectedRow();

        // ---- 商品选择下拉按钮 ----
        FinanceGui.drawSectionTitle(screen.font(), g, "商品选择", 10, FinanceGui.CONTENT_Y);
        String btnLabel = FinanceFormat.commodityDisplayName(screen.selectedCommodity) + " ▾";
        FinanceGui.drawButton(screen.font(), g, btnLabel, 82, FinanceGui.CONTENT_Y, 140, screen.dropdownOpen);

        int cardY = FinanceGui.CONTENT_Y + 20;
        int cardH = 66;
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + cardH, 0xFFF1ECDD);
        g.fill(8, cardY, screen.imageWidth() - 8, cardY + 1, FinanceGui.COL_PANEL_BORDER);
        g.fill(8, cardY + cardH, screen.imageWidth() - 8, cardY + cardH + 1, FinanceGui.COL_PANEL_BORDER);

        g.drawString(screen.font(), "价格:", 12, cardY + 6, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "数量:", 12, cardY + 30, FinanceGui.COL_TEXT_DIM, false);

        FinanceGui.drawButton(screen.font(), g, "-", 170, cardY + 4, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 194, cardY + 4, 20, false);
        FinanceGui.drawButton(screen.font(), g, "-10", 222, cardY + 4, 30, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 256, cardY + 4, 30, false);

        FinanceGui.drawButton(screen.font(), g, "-", 170, cardY + 30, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 194, cardY + 30, 20, false);
        FinanceGui.drawButton(screen.font(), g, "-10", 222, cardY + 30, 30, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 256, cardY + 30, 30, false);

        int infoY = cardY + cardH + 6;
        if (screen.cachedSelectedRow != null) {
            FinanceGui.drawClippedString(screen.font(), g, "市场收购: " + screen.cachedSelectedRow.bidPrice(), 10, infoY, 126, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(screen.font(), g, "市场出售: " + screen.cachedSelectedRow.askPrice(), 146, infoY, 126, FinanceGui.COL_WARN);
        } else {
            FinanceGui.drawClippedString(screen.font(), g, "该商品未接入国际市场，仅支持玩家间交易", 10, infoY, 260, FinanceGui.COL_WARN);
        }
        FinanceGui.drawClippedString(screen.font(), g, "余额: " + screen.menu().getBalance(), 10, infoY + 16, 180, FinanceGui.COL_TEXT);
        int owned = screen.menu().getPlayerInventory().getOrDefault(screen.selectedCommodity, 0);
        FinanceGui.drawClippedString(screen.font(), g, "持有: " + owned, 210, infoY + 16, 150, FinanceGui.COL_TEXT_DIM);
        long orderTotal = FinanceFormat.safeMultiply(FinanceFormat.parseLong(screen.priceBox.getValue()), Math.max(1, FinanceFormat.parseInt(screen.quantityBox.getValue())));
        FinanceGui.drawClippedString(screen.font(), g, "订单总价: " + orderTotal, 210, infoY + 30, 150, FinanceGui.COL_ACCENT);

        FinanceGui.drawFilledButton(screen.font(), g, "提交买单", 10, 186, 84, FinanceGui.COL_ACCENT);
        FinanceGui.drawFilledButton(screen.font(), g, "提交卖单", 102, 186, 84, FinanceGui.COL_BAD);
    }

    // ================================================================
    // 下拉菜单渲染
    // ================================================================

    /** 渲染分类下拉面板（在行情/交易标签中使用） */
    public void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.categorizedCacheDirty) screen.rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap =
                (screen.currentTab == 1) ? screen.inventoryCategorizedCommodities : screen.categorizedCommodities;

        int localX = 82;
        int localY = (screen.currentTab == 0) ? FinanceGui.MARKET_TRADE_Y + 14 : FinanceGui.CONTENT_Y + 14;
        int ddX = screen.leftPos() + localX;
        int ddY = screen.topPos() + localY;
        int ddW = 200;
        int ddH = 120;

        // 背景
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, FinanceGui.COL_DROPDOWN_BG);
        FinanceGui.drawSimpleBorder(g, ddX, ddY, ddW, ddH, FinanceGui.COL_PANEL_BORDER);
        g.enableScissor(screen.toScreen(ddX + 2), screen.toScreen(ddY + 2),
                screen.toScreen(ddX + ddW - 2), screen.toScreen(ddY + ddH - 2));

        int localMx = mouseX - ddX;
        int localMy = mouseY - ddY;
        int y = 2 - screen.dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题
            if (y + 12 > 0 && y < maxY) {
                g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 12, FinanceGui.COL_CATEGORY);
                g.drawString(screen.font(), "▸ " + cat.getDisplayName(), ddX + 6, ddY + y + 2, FinanceGui.COL_TEXT, false);
            }
            y += 14;

            // 展开的分类显示其商品
            if (screen.expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        boolean hovered = localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14;
                        if (hovered) {
                            g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 14, FinanceGui.COL_DROPDOWN_HOVER);
                        }
                        FinanceGui.renderItemIcon(g, c, ddX + 8, ddY + y + 1);
                        FinanceGui.drawClippedString(screen.font(), g, c.getDisplayName(), ddX + 26, ddY + y + 3, 100, FinanceGui.COL_TEXT);
                        FinanceGui.drawClippedString(screen.font(), g, Long.toString(c.getBasePrice()), ddX + 140, ddY + y + 3, 50, FinanceGui.COL_GOOD);
                    }
                    y += 14;
                }
            }
        }
        g.disableScissor();

        // 滚动条
        int totalHeight = calcDropdownTotalHeight();
        if (totalHeight > ddH) {
            int barH = Math.max(10, ddH * ddH / totalHeight);
            int barY = (int)((long) screen.dropdownScroll * (ddH - barH) / (totalHeight - ddH));
            g.fill(ddX + ddW - 5, ddY + barY, ddX + ddW - 2, ddY + barY + barH, FinanceGui.COL_PANEL_BORDER);
        }
    }
    public int calcDropdownTotalHeight() {
        Map<CommodityCategory, List<Commodity>> activeMap =
                (screen.currentTab == 1) ? screen.inventoryCategorizedCommodities : screen.categorizedCommodities;
        int h = 0;
        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;
            h += 14; // 分类标题
            if (screen.expandedCategory == cat) {
                h += items.size() * 14;
            }
        }
        return h;
    }

    // ---- 下拉菜单点击 ----

    public boolean handleDropdownClick(int mx, int my) {
        int ddX = 82;
        int ddY = (screen.currentTab == 0) ? FinanceGui.MARKET_TRADE_Y + 14 : FinanceGui.CONTENT_Y + 14;
        int ddW = 200;
        int ddH = 120;

        if (mx < ddX || mx >= ddX + ddW || my < ddY || my >= ddY + ddH) {
            return false; // 点击外部，由上层关闭
        }

        if (screen.categorizedCacheDirty) screen.rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap =
                (screen.currentTab == 1) ? screen.inventoryCategorizedCommodities : screen.categorizedCommodities;

        int localMx = mx - ddX;
        int localMy = my - ddY;
        int y = 2 - screen.dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题点击 → 展开/折叠（与渲染完全对称：12px 高）
            if (y + 12 > 0 && y < maxY) {
                if (localMy >= y && localMy < y + 12) {
                    screen.expandedCategory = (screen.expandedCategory == cat) ? null : cat;
                    return true;
                }
            }
            y += 14;

            if (screen.expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        if (localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14) {
                            screen.selectedCommodity = c.getId();
                            screen.rememberUiState();
                            if (screen.currentTab == 1) {
                                finance.market.MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(c.getId());
                                if (mp != null) screen.priceBox.setValue(Long.toString(mp.getMidPrice()));
                            }
                            screen.dropdownOpen = false;
                            screen.expandedCategory = null;
                            screen.refreshSelectedRow();
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

    public boolean handleMarketClick(int mx, int my) {
        if (screen.commodityChartVisible) {
            return screen.stockTabs.handleCandlestickControls(mx, my, MarketInstrumentType.COMMODITY, screen.selectedCommodity);
        }
        int headerY = FinanceGui.CONTENT_Y;
        int rowY = headerY + 16;
        int maxRows = Math.min(screen.cachedMarketData.size(), Math.max(1, (FinanceGui.MARKET_TRADE_Y - rowY - 8) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            if (my >= y && my < y + FinanceGui.ROW_HEIGHT && mx >= 8 && mx < screen.imageWidth() - 8) {
                screen.selectedCommodity = screen.cachedMarketData.get(i).commodityId();
                screen.refreshSelectedRow();
                screen.rememberUiState();
                return true;
            }
        }

        int tradeY = FinanceGui.MARKET_TRADE_Y;

        if (mx >= 360 && mx < 390 && my >= tradeY && my < tradeY + 14) {
            screen.commodityChartVisible = true;
            screen.stockTabs.requestCandlesticks(MarketInstrumentType.COMMODITY, screen.selectedCommodity);
            screen.updateInputVisibility();
            return true;
        }

        // 数量加减按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            int qty = FinanceFormat.parseInt(screen.intlQuantityBox.getValue());
            if (mx >= 100 && mx < 120) {
                screen.intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 124 && mx < 144) {
                screen.intlQuantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 152 && mx < 182) {
                screen.intlQuantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 186 && mx < 216) {
                screen.intlQuantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 国际买入/卖出按钮
        if (my >= tradeY + 52 && my < tradeY + 66) {
            if (screen.cachedSelectedRow == null) {
                screen.setStatus("该商品未接入国际市场");
                return true;
            }
            int qty = FinanceFormat.parseInt(screen.intlQuantityBox.getValue());
            if (mx >= 226 && mx < 294) {
                screen.setStatus("已提交国际市场买入");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_BUY, screen.selectedCommodity, 0, qty));
                return true;
            }
            if (mx >= 300 && mx < 368) {
                screen.setStatus("已提交国际市场卖出");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.INTL_SELL, screen.selectedCommodity, 0, qty));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 1 点击: 交易 ----

    public boolean handleTradeClick(int mx, int my) {
        // 下拉按钮
        if (mx >= 82 && mx < 222 && my >= FinanceGui.CONTENT_Y && my < FinanceGui.CONTENT_Y + 13) {
            screen.dropdownOpen = !screen.dropdownOpen;
            screen.dropdownScroll = 0;
            return true;
        }

        int cardY = FinanceGui.CONTENT_Y + 20;

        // 价格加减
        if (my >= cardY + 4 && my < cardY + 18) {
            long price = FinanceFormat.parseLong(screen.priceBox.getValue());
            if (mx >= 170 && mx < 190) {
                screen.priceBox.setValue(Long.toString(Math.max(1, price - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                screen.priceBox.setValue(Long.toString(price + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                screen.priceBox.setValue(Long.toString(Math.max(1, price - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                screen.priceBox.setValue(Long.toString(price + 10));
                return true;
            }
        }

        // 数量加减
        if (my >= cardY + 30 && my < cardY + 44) {
            int qty = FinanceFormat.parseInt(screen.quantityBox.getValue());
            if (mx >= 170 && mx < 190) {
                screen.quantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 194 && mx < 214) {
                screen.quantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 222 && mx < 252) {
                screen.quantityBox.setValue(Integer.toString(Math.max(1, qty - 10)));
                return true;
            }
            if (mx >= 256 && mx < 286) {
                screen.quantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
        }

        // 提交买单/卖单
        if (my >= 186 && my < 200) {
            long price = FinanceFormat.parseLong(screen.priceBox.getValue());
            int qty = FinanceFormat.parseInt(screen.quantityBox.getValue());
            if (mx >= 10 && mx < 94) {
                screen.setStatus("已提交玩家买单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.P2P_BUY, screen.selectedCommodity, price, qty));
                return true;
            }
            if (mx >= 102 && mx < 186) {
                screen.setStatus("已提交玩家卖单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new TradeActionPacket(TradeActionPacket.ActionType.P2P_SELL, screen.selectedCommodity, price, qty));
                return true;
            }
        }
        return false;
    }
}
