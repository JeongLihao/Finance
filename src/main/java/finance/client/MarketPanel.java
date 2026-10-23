package finance.client;

import finance.chart.MarketInstrumentType;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.gui.MarketRows;
import finance.network.*;
import finance.util.FormatUtil;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 行情页：商品行情表（可滚动），选中商品后看 K 线，或直接和国际市场买卖。原在 MarketTabs。 */
final class MarketPanel {
    private final FinanceScreen screen;

    MarketPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderMarketTab(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.cacheDirty) screen.refreshCache();
        screen.refreshSelectedRow();

        if (screen.commodityChartVisible) {
            screen.candlestickView.renderCandlestickView(
                    g,
                    MarketInstrumentType.COMMODITY,
                    screen.selectedCommodity,
                    FinanceFormat.commodityDisplayName(screen.selectedCommodity),
                    mouseX,
                    mouseY);
            return;
        }

        int tableX = 8;
        int tableW = screen.imageWidth() - 16;
        int headerY = FinanceGui.CONTENT_Y;
        int size = screen.cachedMarketData.size();

        int rowY = headerY + 16;
        int visibleRows = Math.min(size, marketCapacity());
        int scroll = Math.max(0, Math.min(screen.marketScroll, Math.max(0, size - visibleRows)));
        screen.marketScroll = scroll;
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
        if (size > visibleRows) {
            // 商品多于可见行：右上角提示可滚动
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    (scroll > 0 ? "▲" : "") + (scroll + visibleRows < size ? "▼" : "") + "滚轮",
                    352,
                    headerY,
                    40,
                    FinanceGui.COL_TEXT_DIM);
        }

        g.fill(tableX + 1, headerY + 11, tableX + tableW - 1, headerY + 12, FinanceGui.COL_PANEL_BORDER);

        if (size == 0) {
            g.drawString(screen.font(), "暂无数据", 12, headerY + 16, FinanceGui.COL_TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            MarketRows.MarketRow row = screen.cachedMarketData.get(i + scroll);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            boolean selected = row.commodityId().equals(screen.selectedCommodity);
            screen.contentClicks.add(tableX, y, tableW, FinanceGui.ROW_HEIGHT, () -> {
                screen.selectedCommodity = row.commodityId();
                screen.refreshSelectedRow();
                screen.rememberUiState();
            });

            int bg = selected
                    ? FinanceGui.COL_ROW_SELECT
                    : ((i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            g.fill(tableX + 1, y, tableX + tableW - 1, y + FinanceGui.ROW_HEIGHT, bg);
            if (selected) {
                g.fill(tableX + 1, y, tableX + 3, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            }

            // 物品图标
            Commodity commodity = CommodityRegistry.getCommodity(row.commodityId());
            FinanceGui.renderItemIcon(g, commodity, 14, y + 4);

            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.commodityDisplayName(row.commodityId()),
                    30,
                    y + 5,
                    40,
                    selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.midPrice()), 74, y + 5, 46, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.bidPrice()), 126, y + 5, 46, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.askPrice()), 178, y + 5, 46, FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FormatUtil.formatPercent(row.dayChange()),
                    230,
                    y + 5,
                    46,
                    FinanceGui.changeColor(row.dayChange()));
            FinanceGui.drawClippedString(
                    screen.font(), g, Integer.toString(row.dayVolume()), 282, y + 5, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Integer.toString(row.marketStock()), 326, y + 5, 62, FinanceGui.COL_TEXT_DIM);
        }

        // ---- 国际交易区域 ----
        int tradeY = FinanceGui.MARKET_TRADE_Y;
        g.drawString(screen.font(), "国际交易", 10, tradeY + 1, FinanceGui.COL_TEXT, false);
        screen.button(g, "K线", 360, tradeY, 30, false, () -> {
            screen.commodityChartVisible = true;
            screen.candlestickView.requestCandlesticks(MarketInstrumentType.COMMODITY, screen.selectedCommodity);
            screen.updateInputVisibility();
        });

        // 显示当前选中商品名（不是按钮行）
        String selName = FinanceFormat.commodityDisplayName(screen.selectedCommodity);
        FinanceGui.drawClippedString(screen.font(), g, "当前: " + selName, 82, tradeY + 1, 120, FinanceGui.COL_ACCENT);

        // 信息行
        if (screen.cachedSelectedRow != null) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "市场收购: " + screen.cachedSelectedRow.bidPrice(),
                    10,
                    tradeY + 18,
                    120,
                    FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "市场出售: " + screen.cachedSelectedRow.askPrice(),
                    140,
                    tradeY + 18,
                    120,
                    FinanceGui.COL_WARN);
            FinanceGui.drawTrendGraph(
                    g,
                    screen.cachedSelectedRow.priceHistory(),
                    260,
                    tradeY + 1,
                    108,
                    14,
                    FinanceGui.changeColor(screen.cachedSelectedRow.dayChange()));
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "高 " + screen.cachedSelectedRow.dayHigh() + " 低 " + screen.cachedSelectedRow.dayLow(),
                    10,
                    tradeY + 44,
                    150,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "量 " + screen.cachedSelectedRow.dayVolume(),
                    170,
                    tradeY + 44,
                    70,
                    FinanceGui.COL_TEXT_DIM);
        }
        FinanceGui.drawClippedString(
                screen.font(), g, "余额: " + screen.menu().getBalance(), 260, tradeY + 18, 110, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "仓储 " + screen.menu().getWarehouseUsed() + "/" + screen.menu().getWarehouseCapacity()
                        + (screen.menu().isWarehouseOverCapacity() ? " 超量" : ""),
                260,
                tradeY + 44,
                120,
                screen.menu().isWarehouseOverCapacity() ? FinanceGui.COL_BAD : FinanceGui.COL_TEXT_DIM);
        int owned = screen.menu().getPlayerInventory().getOrDefault(screen.selectedCommodity, 0);
        FinanceGui.drawClippedString(screen.font(), g, "持有: " + owned, 10, tradeY + 32, 130, FinanceGui.COL_TEXT_DIM);
        int intlQty = Math.max(1, FinanceFormat.parseInt(screen.intlQuantityBox.getValue()));
        if (screen.cachedSelectedRow != null) {
            long buyTotal = FinanceFormat.safeMultiply(screen.cachedSelectedRow.askPrice(), intlQty);
            long sellTotal = FinanceFormat.safeMultiply(screen.cachedSelectedRow.bidPrice(), intlQty);
            FinanceGui.drawClippedString(
                    screen.font(), g, "买入总价: " + buyTotal, 130, tradeY + 32, 112, FinanceGui.COL_GOOD);
            FinanceGui.drawClippedString(
                    screen.font(), g, "卖出总价: " + sellTotal, 250, tradeY + 32, 112, FinanceGui.COL_WARN);
        }

        // 数量 + 买卖按钮
        g.drawString(screen.font(), "数量:", 10, tradeY + 53, FinanceGui.COL_TEXT_DIM, false);
        screen.button(g, "-", 100, tradeY + 52, 20, false, () -> addQuantity(-1));
        screen.button(g, "+", 124, tradeY + 52, 20, false, () -> addQuantity(1));
        screen.button(g, "-10", 152, tradeY + 52, 30, false, () -> addQuantity(-10));
        screen.button(g, "+10", 186, tradeY + 52, 30, false, () -> addQuantity(10));

        screen.filledButton(g, "从市场买入", 226, tradeY + 52, 68, FinanceGui.COL_GOOD, () -> intlTrade(true));
        screen.filledButton(g, "卖给市场", 300, tradeY + 52, 68, FinanceGui.COL_BAD, () -> intlTrade(false));
    }

    /** 行情表可见行数（表格区高度固定）。 */
    static int marketCapacity() {
        return Math.max(1, (FinanceGui.MARKET_TRADE_Y - (FinanceGui.CONTENT_Y + 16) - 8) / FinanceGui.ROW_HEIGHT);
    }

    /** 行情表最多能滚动几行。 */
    int maxMarketScroll() {
        return Math.max(0, screen.cachedMarketData.size() - marketCapacity());
    }

    private void addQuantity(int delta) {
        int qty = FinanceFormat.parseInt(screen.intlQuantityBox.getValue());
        screen.intlQuantityBox.setValue(Integer.toString(delta < 0 ? Math.max(1, qty + delta) : qty + delta));
    }

    private void intlTrade(boolean buy) {
        if (screen.cachedSelectedRow == null) {
            screen.setStatus("该商品未接入国际市场");
            return;
        }
        int qty = screen.requireInt(screen.intlQuantityBox, "数量", 1, Integer.MAX_VALUE);
        screen.setStatus(buy ? "已提交国际市场买入" : "已提交国际市场卖出");
        FinancePacketHandler.CHANNEL.sendToServer(new TradeActionPacket(
                buy ? TradeActionPacket.ActionType.INTL_BUY : TradeActionPacket.ActionType.INTL_SELL,
                screen.selectedCommodity,
                0,
                qty));
    }
}
