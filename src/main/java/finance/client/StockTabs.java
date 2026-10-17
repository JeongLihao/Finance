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

/** 股票 / K 线 / 深度 / 排行 / 图表控件：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */

public final class StockTabs {
    private final FinanceScreen screen;

    public StockTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    /** K 线面板模式（原 FinanceScreen.ChartPanel，随股票页签迁移）。 */
    enum ChartPanel {
        KLINE_MA("K+MA"), MACD("MACD"), RSI("RSI"), ORDER_BOOK("Book"), RECENT("Trades"),
        RANKINGS("Ranks"), RANK_VOLUME("VolRank");
        private final String label;
        ChartPanel(String label) { this.label = label; }
        ChartPanel next() { return values()[(ordinal() + 1) % values().length]; }
    }

    ChartPanel chartPanel = ChartPanel.KLINE_MA;

    public void renderStockTab(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.stockChartVisible) {
            FinanceMenu.StockRow row = findSelectedStock();
            renderCandlestickView(g, MarketInstrumentType.STOCK, screen.selectedStock,
                    row == null ? screen.selectedStock : FinanceFormat.displayStockSymbol(row) + " " + row.name(), mouseX, mouseY);
            return;
        }
        int headerY = FinanceGui.CONTENT_Y;
        FinanceGui.drawHeader(screen.font(), g, "代码", 12, headerY);
        FinanceGui.drawHeader(screen.font(), g, "名称", 62, headerY);
        FinanceGui.drawHeader(screen.font(), g, "价格", 150, headerY);
        FinanceGui.drawHeader(screen.font(), g, "估值", 196, headerY);
        FinanceGui.drawHeader(screen.font(), g, "涨跌", 240, headerY);
        FinanceGui.drawHeader(screen.font(), g, "成交", 292, headerY);
        FinanceGui.drawHeader(screen.font(), g, "流通", 334, headerY);

        List<FinanceMenu.StockRow> stocks = screen.menu().getStocks();
        if (stocks.isEmpty()) {
            FinanceGui.drawClippedString(screen.font(), g, "暂无股票。系统公司初始化后会自动生成股票。",
                    12, headerY + 18, screen.imageWidth() - 24, FinanceGui.COL_TEXT_DIM);
            return;
        }

        int rowY = headerY + 16;
        int maxRows = Math.min(stocks.size(), Math.max(1, (FinanceGui.STOCK_TRADE_Y - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.StockRow row = stocks.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            boolean selected = row.symbol().equals(screen.selectedStock);
            g.fill(8, y, screen.imageWidth() - 8, y + FinanceGui.ROW_HEIGHT,
                    selected ? FinanceGui.COL_ROW_SELECT : (i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD));
            if (selected) g.fill(8, y, 10, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(screen.font(), g, FinanceFormat.displayStockSymbol(row), 12, y + 5, 46, selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, row.name(), 62, y + 5, 82, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.lastPrice()), 150, y + 5, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.fairValue()), 196, y + 5, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, FormatUtil.formatPercent(row.dayChange()), 240, y + 5, 46, FinanceGui.changeColor(row.dayChange()));
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.dayVolume()), 292, y + 5, 36, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(row.availableShares()), 334, y + 5, 80, FinanceGui.COL_TEXT_DIM);
        }

        int tradeY = FinanceGui.STOCK_TRADE_Y;
        FinanceGui.drawSimpleSeparator(g, 8, tradeY - 6, screen.imageWidth() - 16);
        FinanceGui.drawSectionTitle(screen.font(), g, "限价委托", 10, tradeY);
        FinanceGui.drawButton(screen.font(), g, "K线", 360, tradeY - 2, 30, false);
        FinanceMenu.StockRow selected = findSelectedStock();
        if (selected != null) {
            FinanceGui.drawClippedString(screen.font(), g, FinanceFormat.displayStockSymbol(selected) + " " + selected.name(), 92, tradeY, 160, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, "现价: " + selected.lastPrice(), 260, tradeY, 90, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "高 " + selected.dayHigh() + " 低 " + selected.dayLow() + " 量 " + selected.dayVolume(),
                    12, tradeY + 13, 190, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawTrendGraph(g, selected.priceHistory(), 258, tradeY + 14, 102, 18, FinanceGui.changeColor(selected.dayChange()));
            long total = FinanceFormat.safeMultiply(Math.max(1, FinanceFormat.parseLong(screen.stockPriceBox.getValue())), Math.max(1, FinanceFormat.parseInt(screen.stockQuantityBox.getValue())));
            FinanceMenu.StockHoldingRow holding = findHolding(selected.symbol());
            FinanceGui.drawClippedString(screen.font(), g, "总价: " + total, 12, tradeY + 52, 100, FinanceGui.COL_ACCENT);
            if (holding != null) {
                FinanceGui.drawClippedString(screen.font(), g, "持仓 " + holding.quantity() + "  成本 " + holding.averageCost(), 128, tradeY + 52, 160, FinanceGui.COL_TEXT_DIM);
            } else {
                FinanceGui.drawClippedString(screen.font(), g, "当前无持仓", 128, tradeY + 52, 90, FinanceGui.COL_TEXT_DIM);
            }
            FinanceGui.drawClippedString(screen.font(), g, "预息/股 " + selected.expectedDividendPerShare(),
                    288, tradeY + 52, 82, FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(screen.font(), g, "上次 " + selected.lastDividendPerShare() + "/股 总 " + selected.lastDividendTotal(),
                    12, tradeY + 79, 200, FinanceGui.COL_TEXT_DIM);
        }
        g.drawString(screen.font(), "价格:", 12, tradeY + 19, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "股数:", 12, tradeY + 39, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawButton(screen.font(), g, "-", 124, tradeY + 36, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+", 148, tradeY + 36, 20, false);
        FinanceGui.drawButton(screen.font(), g, "+10", 176, tradeY + 36, 30, false);
        FinanceGui.drawFilledButton(screen.font(), g, "买单", 234, tradeY + 36, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "卖单", 300, tradeY + 36, 58, FinanceGui.COL_BAD);
        g.drawString(screen.font(), "条件:", 12, tradeY + 66, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawFilledButton(screen.font(), g, "设止盈", 54, tradeY + 64, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "设止损", 120, tradeY + 64, 58, FinanceGui.COL_BAD);
        FinanceGui.drawClippedString(screen.font(), g, "使用上方价格/股数，触发后自动提交卖单", 188, tradeY + 66, 180, FinanceGui.COL_TEXT_DIM);

        FinanceMenu.CompanyFinancingRow financing = selectedFinancingProject();
        if (financing != null) {
            int fy = tradeY + 92;
            FinanceGui.drawClippedString(screen.font(), g, "融资 " + financing.raisedAmount() + "/" + financing.fundingTarget()
                            + " 股 " + financing.subscribedShares() + "/" + financing.issueQuantity()
                            + " 价 " + financing.issuePrice(),
                    12, fy, 230, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(screen.font(), g, "我已认 " + financing.playerSubscribedShares(), 246, fy, 48, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawFilledButton(screen.font(), g, "认购", 300, fy - 2, 42, FinanceGui.COL_GOOD);
        }

        int orderY = tradeY + (financing != null ? 112 : 96);
        FinanceGui.drawSectionTitle(screen.font(), g, "当前委托", 10, orderY);
        List<FinanceMenu.StockOrderRow> orders = selectedStockOrders();
        int shownOrders = 0;
        if (orders.isEmpty()) {
            g.drawString(screen.font(), "该股票暂无活跃委托", 12, orderY + 16, FinanceGui.COL_TEXT_DIM, false);
        } else {
            int y = orderY + 16;
            int reservedForConditional = 30;
            int maxOrders = Math.min(orders.size(), Math.max(1, (screen.imageHeight() - y - reservedForConditional) / FinanceGui.ROW_HEIGHT));
            for (int i = 0; i < maxOrders; i++) {
                FinanceMenu.StockOrderRow row = orders.get(i);
                FinanceGui.drawClippedString(screen.font(), g, row.type().equals("BUY") ? "买" : "卖", 14, y, 20,
                        row.type().equals("BUY") ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
                FinanceGui.drawClippedString(screen.font(), g, "价 " + row.price(), 44, y, 70, FinanceGui.COL_TEXT_DIM);
                FinanceGui.drawClippedString(screen.font(), g, "量 " + row.quantity(), 124, y, 70, FinanceGui.COL_TEXT_DIM);
                FinanceGui.drawClippedString(screen.font(), g, row.ownedByPlayer() ? "我的" : "市场", 206, y, 42,
                        row.ownedByPlayer() ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT_DIM);
                if (row.ownedByPlayer()) {
                    FinanceGui.drawButton(screen.font(), g, "撤单", 300, y - 2, 42, false);
                }
                y += FinanceGui.ROW_HEIGHT;
                shownOrders++;
            }
            if (orders.size() > maxOrders) {
                FinanceGui.drawClippedString(screen.font(), g, "还有 " + (orders.size() - maxOrders) + " 条委托未显示", 14, y, 160, FinanceGui.COL_TEXT_DIM);
            }
        }

        int conditionalY = orderY + 16 + Math.max(1, shownOrders) * FinanceGui.ROW_HEIGHT + 8;
        if (conditionalY + 16 >= screen.imageHeight() - 4) {
            return;
        }
        FinanceGui.drawSectionTitle(screen.font(), g, "条件委托", 10, conditionalY);
        List<FinanceMenu.ConditionalStockOrderRow> conditionalOrders = selectedConditionalStockOrders();
        if (conditionalOrders.isEmpty()) {
            g.drawString(screen.font(), "该股票暂无止盈止损委托", 12, conditionalY + 16, FinanceGui.COL_TEXT_DIM, false);
            return;
        }
        int y = conditionalY + 16;
        int maxConditionalOrders = Math.min(conditionalOrders.size(),
                Math.max(0, (screen.imageHeight() - y - 4) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxConditionalOrders; i++) {
            FinanceMenu.ConditionalStockOrderRow row = conditionalOrders.get(i);
            boolean takeProfit = row.type().equals("TAKE_PROFIT");
            FinanceGui.drawClippedString(screen.font(), g, takeProfit ? "止盈" : "止损", 14, y, 34,
                    takeProfit ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(screen.font(), g, "触发 " + row.triggerPrice(), 54, y, 78, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "量 " + row.quantity(), 142, y, 58, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawButton(screen.font(), g, "取消", 300, y - 2, 42, false);
            y += FinanceGui.ROW_HEIGHT;
        }
    }
    public void renderCandlestickView(GuiGraphics g, MarketInstrumentType type, String id,
                                       String title, int mouseX, int mouseY) {
        FinanceGui.drawClippedString(screen.font(), g, "日K · " + (title == null ? "" : title), 10, FinanceGui.CONTENT_Y, 88, FinanceGui.COL_TEXT);
        FinanceGui.drawButton(screen.font(), g, "30天", 178, FinanceGui.CONTENT_Y - 2, 48, screen.chartLimit == 30);
        FinanceGui.drawButton(screen.font(), g, "60天", 230, FinanceGui.CONTENT_Y - 2, 48, screen.chartLimit == 60);
        FinanceGui.drawButton(screen.font(), g, "120天", 282, FinanceGui.CONTENT_Y - 2, 52, screen.chartLimit == 120);
        FinanceGui.drawButton(screen.font(), g, "返回", 342, FinanceGui.CONTENT_Y - 2, 48, false);
        FinanceGui.drawButton(screen.font(), g, chartPanel.label, 104, FinanceGui.CONTENT_Y - 2, 66, true);
        CandlestickClientCache.Entry chartEntry = CandlestickClientCache.get(type, id, screen.chartLimit);
        List<finance.chart.Candlestick> bars = chartEntry.bars();
        if (chartEntry.state() == CandlestickClientCache.State.LOADING) {
            FinanceGui.drawClippedString(screen.font(), g, "正在加载行情数据…", 10, FinanceGui.CONTENT_Y + 16, 240, FinanceGui.COL_TEXT_DIM);
        } else if (chartEntry.state() == CandlestickClientCache.State.SLOW) {
            FinanceGui.drawClippedString(screen.font(), g, "行情响应较慢，可重试", 10, FinanceGui.CONTENT_Y + 16, 280, FinanceGui.COL_BAD);
        } else if (chartEntry.state() == CandlestickClientCache.State.EMPTY) {
            FinanceGui.drawClippedString(screen.font(), g, "该时间窗口内无成交", 10, FinanceGui.CONTENT_Y + 16, 240, FinanceGui.COL_TEXT_DIM);
        }
        MarketSummary summary = chartEntry.state() == CandlestickClientCache.State.READY
                ? MarketSummary.from(bars, chartEntry.serverCurrentMcDay()) : null;
        if (summary != null) {
            int color = summary.change() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD;
            FinanceGui.drawClippedString(screen.font(), g, "最新 " + summary.latestPrice() + "  " + FinanceFormat.signedLong(summary.change())
                    + " (" + FormatUtil.formatPercent(summary.changePercent()) + ")", 10, FinanceGui.CONTENT_Y + 16, 240, color);
            FinanceGui.drawClippedString(screen.font(), g, "高 " + summary.high() + " 低 " + summary.low() + " 量 " + summary.volume()
                    + (summary.volumeSpike() ? " 放量" : "")
                    + (!chartEntry.latestBarComplete() ? " 未收盘" : ""), 250, FinanceGui.CONTENT_Y + 16, 140, FinanceGui.COL_TEXT_DIM);
        }
        int panelY = FinanceGui.CONTENT_Y + 29;
        int panelHeight = screen.imageHeight() - FinanceGui.CONTENT_Y - 37;
        switch (chartPanel) {
            case KLINE_MA -> CandlestickChart.render(g, screen.font(), bars,
                    8, panelY, screen.imageWidth() - 16, panelHeight, mouseX, mouseY);
            case MACD -> TechnicalIndicatorChart.renderMacd(g, screen.font(), bars,
                    8, panelY, screen.imageWidth() - 16, panelHeight);
            case RSI -> TechnicalIndicatorChart.renderRsi(g, screen.font(), bars,
                    8, panelY, screen.imageWidth() - 16, panelHeight);
            case ORDER_BOOK -> renderOrderBook(g, chartEntry.orderBook(), panelY);
            case RECENT -> renderRecentTrades(g, chartEntry.recentTrades(), panelY);
            case RANKINGS -> renderRankings(g, chartEntry.rankings(), panelY);
            case RANK_VOLUME -> renderVolumeRankings(g, chartEntry.rankings(), panelY);
        }
    }
    public void renderOrderBook(GuiGraphics g, finance.marketdata.OrderBookSnapshot book, int y) {
        FinanceGui.drawHeader(screen.font(), g, "买盘 价/量", 26, y);
        FinanceGui.drawHeader(screen.font(), g, "卖盘 价/量", 214, y);
        for (int i = 0; i < 5; i++) {
            int rowY = y + 16 + i * 15;
            if (i < book.bids().size()) {
                finance.marketdata.OrderBookLevel level = book.bids().get(i);
                FinanceGui.drawClippedString(screen.font(), g, level.price() + " / " + level.quantity(), 26, rowY, 150, FinanceGui.COL_GOOD);
            }
            if (i < book.asks().size()) {
                finance.marketdata.OrderBookLevel level = book.asks().get(i);
                FinanceGui.drawClippedString(screen.font(), g, level.price() + " / " + level.quantity(), 214, rowY, 150, FinanceGui.COL_BAD);
            }
        }
    }
    public void renderRecentTrades(GuiGraphics g, List<finance.marketdata.RecentTradeEntry> trades, int y) {
        FinanceGui.drawHeader(screen.font(), g, "时间", 18, y);
        FinanceGui.drawHeader(screen.font(), g, "方向", 104, y);
        FinanceGui.drawHeader(screen.font(), g, "价格", 180, y);
        FinanceGui.drawHeader(screen.font(), g, "数量", 282, y);
        int rows = Math.min(8, trades.size());
        for (int i = 0; i < rows; i++) {
            finance.marketdata.RecentTradeEntry trade = trades.get(i);
            int rowY = y + 15 + i * 13;
            FinanceGui.drawClippedString(screen.font(), g, "D" + trade.mcDay() + " " + trade.timestamp().toLocalTime().withNano(0),
                    18, rowY, 76, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, trade.direction().name(), 104, rowY, 62,
                    trade.direction() == finance.marketdata.TradeDirection.BUY ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(trade.price()), 180, rowY, 90, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, Long.toString(trade.quantity()), 282, rowY, 84, FinanceGui.COL_TEXT);
        }
    }
    public void renderRankings(GuiGraphics g, finance.marketdata.MarketRankingSnapshot rankings, int y) {
        renderRankingColumn(g, "商品涨幅", rankings.commodityGainers(), 12, y, true, false);
        renderRankingColumn(g, "商品跌幅", rankings.commodityLosers(), 108, y, true, false);
        renderRankingColumn(g, "股票涨幅", rankings.stockGainers(), 204, y, true, false);
        renderRankingColumn(g, "股票跌幅", rankings.stockLosers(), 300, y, true, false);
    }
    public void renderVolumeRankings(GuiGraphics g, finance.marketdata.MarketRankingSnapshot rankings, int y) {
        renderRankingColumn(g, "商品成交", rankings.commodityVolumeLeaders(), 36, y, false, false);
        renderRankingColumn(g, "股票成交", rankings.stockVolumeLeaders(), 156, y, false, false);
        renderRankingColumn(g, "异动", rankings.unusualVolume(), 276, y, false, true);
    }
    public void renderRankingColumn(GuiGraphics g, String title,
                                     List<finance.marketdata.MarketRankingEntry> entries,
                                     int x, int y, boolean change, boolean ratio) {
        FinanceGui.drawHeader(screen.font(), g, title, x, y);
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            finance.marketdata.MarketRankingEntry entry = entries.get(i);
            FinanceGui.drawClippedString(screen.font(), g, entry.id(), x, y + 15 + i * 17, 84, FinanceGui.COL_TEXT);
            String value = change ? String.format(java.util.Locale.ROOT, "%+.1f%%", entry.changePercent())
                    : ratio ? String.format(java.util.Locale.ROOT, "x%.1f", entry.volumeRatio())
                    : Long.toString(entry.volume());
            FinanceGui.drawClippedString(screen.font(), g, value, x, y + 24 + i * 17, 84,
                    change ? (entry.changePercent() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD) : FinanceGui.COL_ACCENT);
        }
    }
    public void requestCandlesticks(MarketInstrumentType type, String id) {
        if (id == null || id.isBlank()) return;
        long requestId = CandlestickClientCache.nextRequestId();
        CandlestickClientCache.begin(requestId, type, id, screen.chartLimit);
        FinancePacketHandler.CHANNEL.sendToServer(new CandlestickRequestPacket(requestId, type, id, screen.chartLimit));
    }
    public boolean handleCandlestickControls(int mx, int my, MarketInstrumentType type, String id) {
        if (my < FinanceGui.CONTENT_Y - 2 || my >= FinanceGui.CONTENT_Y + 12) return false;
        if (mx >= 104 && mx < 170) {
            chartPanel = chartPanel.next();
            return true;
        }
        int requestedLimit = 0;
        if (mx >= 178 && mx < 226) requestedLimit = 30;
        else if (mx >= 230 && mx < 278) requestedLimit = 60;
        else if (mx >= 282 && mx < 334) requestedLimit = 120;
        if (requestedLimit > 0) {
            screen.chartLimit = requestedLimit;
            requestCandlesticks(type, id);
            return true;
        }
        if (mx >= 342 && mx < 390) {
            if (type == MarketInstrumentType.COMMODITY) screen.commodityChartVisible = false;
            else if(type == MarketInstrumentType.STOCK) screen.stockChartVisible = false;
            else if(type == MarketInstrumentType.FUTURES) screen.futuresChartVisible = false;
            screen.updateInputVisibility();
            return true;
        }
        return false;
    }

    // ---- 标签 5 点击: 股票 ----

    public boolean handleStockClick(int mx, int my) {
        if (screen.stockChartVisible) {
            return handleCandlestickControls(mx, my, MarketInstrumentType.STOCK, screen.selectedStock);
        }
        int rowY = FinanceGui.CONTENT_Y + 16;
        int maxRows = Math.min(screen.menu().getStocks().size(), Math.max(1, (FinanceGui.STOCK_TRADE_Y - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            if (my >= y && my < y + FinanceGui.ROW_HEIGHT && mx >= 8 && mx < screen.imageWidth() - 8) {
                screen.selectedStock = screen.menu().getStocks().get(i).symbol();
                FinanceMenu.StockRow selected = findSelectedStock();
                if (selected != null) {
                    screen.stockPriceBox.setValue(Long.toString(selected.lastPrice()));
                }
                screen.rememberUiState();
                return true;
            }
        }

        int tradeY = FinanceGui.STOCK_TRADE_Y;
        if (mx >= 360 && mx < 390 && my >= tradeY - 2 && my < tradeY + 12) {
            screen.stockChartVisible = true;
            requestCandlesticks(MarketInstrumentType.STOCK, screen.selectedStock);
            screen.updateInputVisibility();
            return true;
        }
        if (my >= tradeY + 36 && my < tradeY + 50) {
            int qty = FinanceFormat.parseInt(screen.stockQuantityBox.getValue());
            if (mx >= 124 && mx < 144) {
                screen.stockQuantityBox.setValue(Integer.toString(Math.max(1, qty - 1)));
                return true;
            }
            if (mx >= 148 && mx < 168) {
                screen.stockQuantityBox.setValue(Integer.toString(qty + 1));
                return true;
            }
            if (mx >= 176 && mx < 206) {
                screen.stockQuantityBox.setValue(Integer.toString(qty + 10));
                return true;
            }
            if (mx >= 234 && mx < 292) {
                long price = screen.requireAmount(screen.stockPriceBox, "价格");
                qty = screen.requireInt(screen.stockQuantityBox, "股数", 1, Integer.MAX_VALUE);
                screen.setStatus("已提交买入委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.PLACE_BUY, screen.selectedStock, price, qty));
                return true;
            }
            if (mx >= 300 && mx < 358) {
                long price = screen.requireAmount(screen.stockPriceBox, "价格");
                qty = screen.requireInt(screen.stockQuantityBox, "股数", 1, Integer.MAX_VALUE);
                screen.setStatus("已提交卖出委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.PLACE_SELL, screen.selectedStock, price, qty));
                return true;
            }
        }
        if (my >= tradeY + 64 && my < tradeY + 77 && (mx >= 54 && mx < 112 || mx >= 120 && mx < 178)) {
            int qty = screen.requireInt(screen.stockQuantityBox, "股数", 1, Integer.MAX_VALUE);
            long triggerPrice = screen.requireAmount(screen.stockPriceBox, "触发价");
            if (mx >= 54 && mx < 112) {
                screen.setStatus("已提交止盈委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new ConditionalStockOrderPacket(ConditionalStockOrderType.TAKE_PROFIT,
                                screen.selectedStock, triggerPrice, qty));
                return true;
            }
            if (mx >= 120 && mx < 178) {
                screen.setStatus("已提交止损委托");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new ConditionalStockOrderPacket(ConditionalStockOrderType.STOP_LOSS,
                                screen.selectedStock, triggerPrice, qty));
                return true;
            }
        }

        FinanceMenu.CompanyFinancingRow financing = selectedFinancingProject();
        if (financing != null && mx >= 300 && mx < 342 && my >= tradeY + 90 && my < tradeY + 105) {
            long qty = screen.requireAmount(screen.stockQuantityBox, "认购股数");
            screen.setStatus("已提交增发认购");
            FinancePacketHandler.CHANNEL.sendToServer(
                    CompanyFinancingPacket.subscribe(financing.projectId(), qty));
            return true;
        }

        int orderY = tradeY + (financing != null ? 112 : 96);
        List<FinanceMenu.StockOrderRow> orders = selectedStockOrders();
        int maxOrders = Math.min(orders.size(),
                Math.max(1, (screen.imageHeight() - (orderY + 16) - 30) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxOrders; i++) {
            FinanceMenu.StockOrderRow row = orders.get(i);
            int y = orderY + 16 + i * FinanceGui.ROW_HEIGHT;
            if (row.ownedByPlayer() && mx >= 300 && mx < 342 && my >= y - 2 && my < y + 11) {
                screen.setStatus("已提交撤单");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new StockOrderPacket(StockOrderPacket.ActionType.CANCEL, row.orderId()));
                return true;
            }
        }
        int conditionalY = orderY + 16 + Math.max(1, maxOrders) * FinanceGui.ROW_HEIGHT + 8;
        if (conditionalY + 16 >= screen.imageHeight() - 4) {
            return false;
        }
        List<FinanceMenu.ConditionalStockOrderRow> conditionalOrders = selectedConditionalStockOrders();
        int maxConditionalOrders = Math.min(conditionalOrders.size(),
                Math.max(0, (screen.imageHeight() - (conditionalY + 16) - 4) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxConditionalOrders; i++) {
            FinanceMenu.ConditionalStockOrderRow row = conditionalOrders.get(i);
            int y = conditionalY + 16 + i * FinanceGui.ROW_HEIGHT;
            if (mx >= 300 && mx < 342 && my >= y - 2 && my < y + 11) {
                screen.setStatus("已提交取消条件委托");
                FinancePacketHandler.CHANNEL.sendToServer(new ConditionalStockOrderPacket(row.orderId()));
                return true;
            }
        }
        return false;
    }
    public FinanceMenu.StockRow findSelectedStock() {
        for (FinanceMenu.StockRow row : screen.menu().getStocks()) {
            if (row.symbol().equals(screen.selectedStock)) return row;
        }
        return screen.menu().getStocks().isEmpty() ? null : screen.menu().getStocks().get(0);
    }
    public FinanceMenu.StockHoldingRow findHolding(String symbol) {
        for (FinanceMenu.StockHoldingRow row : screen.menu().getStockHoldings()) {
            if (row.symbol().equals(symbol)) return row;
        }
        return null;
    }
    public List<FinanceMenu.StockOrderRow> selectedStockOrders() {
        List<FinanceMenu.StockOrderRow> rows = new ArrayList<>();
        String symbol = screen.selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (FinanceMenu.StockOrderRow row : screen.menu().getStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }
    public List<FinanceMenu.ConditionalStockOrderRow> selectedConditionalStockOrders() {
        List<FinanceMenu.ConditionalStockOrderRow> rows = new ArrayList<>();
        String symbol = screen.selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (FinanceMenu.ConditionalStockOrderRow row : screen.menu().getConditionalStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }
    public FinanceMenu.CompanyFinancingRow selectedFinancingProject() {
        String symbol = screen.selectedStock;
        FinanceMenu.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        for (FinanceMenu.CompanyFinancingRow row : screen.menu().getCompanyFinancingRows()) {
            if (symbol.equals(row.symbol())) {
                return row;
            }
        }
        return null;
    }
}
