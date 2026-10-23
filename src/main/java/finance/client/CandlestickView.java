package finance.client;

import finance.chart.MarketInstrumentType;
import finance.chart.MarketSummary;
import finance.client.chart.CandlestickChart;
import finance.client.chart.CandlestickClientCache;
import finance.client.chart.TechnicalIndicatorChart;
import finance.network.*;
import finance.util.FormatUtil;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/**
 * K 线视图：市场页、股票页、期货面板共用的价格图，下面一栏可切换均线、盘口、最近成交、涨跌和成交排行。
 * 原在 StockTabs，由 {@link MarketTabs}、{@link StockTabs}、{@link FuturesPanel} 调用。
 */
final class CandlestickView {
    private final FinanceScreen screen;

    CandlestickView(FinanceScreen screen) {
        this.screen = screen;
    }

    /** K 线图下面一栏显示什么，点按钮依次切换。 */
    enum ChartPanel {
        KLINE_MA("K线+均线"),
        MACD("MACD"),
        RSI("RSI"),
        ORDER_BOOK("盘口"),
        RECENT("最近成交"),
        RANKINGS("涨跌排行"),
        RANK_VOLUME("成交量排行");
        private final String label;

        ChartPanel(String label) {
            this.label = label;
        }

        ChartPanel next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    ChartPanel chartPanel = ChartPanel.KLINE_MA;

    public void renderCandlestickView(
            GuiGraphics g, MarketInstrumentType type, String id, String title, int mouseX, int mouseY) {
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "日K · " + (title == null ? "" : title),
                10,
                FinanceGui.CONTENT_Y,
                88,
                FinanceGui.COL_TEXT);
        FinanceGui.drawButton(screen.font(), g, "30天", 178, FinanceGui.CONTENT_Y - 2, 48, screen.chartLimit == 30);
        FinanceGui.drawButton(screen.font(), g, "60天", 230, FinanceGui.CONTENT_Y - 2, 48, screen.chartLimit == 60);
        FinanceGui.drawButton(screen.font(), g, "120天", 282, FinanceGui.CONTENT_Y - 2, 52, screen.chartLimit == 120);
        FinanceGui.drawButton(screen.font(), g, "返回", 342, FinanceGui.CONTENT_Y - 2, 48, false);
        FinanceGui.drawButton(screen.font(), g, chartPanel.label, 104, FinanceGui.CONTENT_Y - 2, 66, true);
        CandlestickClientCache.Entry chartEntry = CandlestickClientCache.get(type, id, screen.chartLimit);
        List<finance.chart.Candlestick> bars = chartEntry.bars();
        if (chartEntry.state() == CandlestickClientCache.State.LOADING) {
            FinanceGui.drawClippedString(
                    screen.font(), g, "正在加载行情数据…", 10, FinanceGui.CONTENT_Y + 16, 240, FinanceGui.COL_TEXT_DIM);
        } else if (chartEntry.state() == CandlestickClientCache.State.SLOW) {
            FinanceGui.drawClippedString(
                    screen.font(), g, "行情响应较慢，可重试", 10, FinanceGui.CONTENT_Y + 16, 280, FinanceGui.COL_BAD);
        } else if (chartEntry.state() == CandlestickClientCache.State.EMPTY) {
            FinanceGui.drawClippedString(
                    screen.font(), g, "该时间窗口内无成交", 10, FinanceGui.CONTENT_Y + 16, 240, FinanceGui.COL_TEXT_DIM);
        }
        MarketSummary summary = chartEntry.state() == CandlestickClientCache.State.READY
                ? MarketSummary.from(bars, chartEntry.serverCurrentMcDay())
                : null;
        if (summary != null) {
            int color = summary.change() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD;
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "最新 " + summary.latestPrice() + "  " + FinanceFormat.signedLong(summary.change()) + " ("
                            + FormatUtil.formatPercent(summary.changePercent()) + ")",
                    10,
                    FinanceGui.CONTENT_Y + 16,
                    240,
                    color);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "高 " + summary.high() + " 低 " + summary.low() + " 量 " + summary.volume()
                            + (summary.volumeSpike() ? " 放量" : "")
                            + (!chartEntry.latestBarComplete() ? " 未收盘" : ""),
                    250,
                    FinanceGui.CONTENT_Y + 16,
                    140,
                    FinanceGui.COL_TEXT_DIM);
        }
        int panelY = FinanceGui.CONTENT_Y + 29;
        int panelHeight = screen.imageHeight() - FinanceGui.CONTENT_Y - 37;
        switch (chartPanel) {
            case KLINE_MA -> CandlestickChart.render(
                    g, screen.font(), bars, 8, panelY, screen.imageWidth() - 16, panelHeight, mouseX, mouseY);
            case MACD -> TechnicalIndicatorChart.renderMacd(
                    g, screen.font(), bars, 8, panelY, screen.imageWidth() - 16, panelHeight);
            case RSI -> TechnicalIndicatorChart.renderRsi(
                    g, screen.font(), bars, 8, panelY, screen.imageWidth() - 16, panelHeight);
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
                FinanceGui.drawClippedString(
                        screen.font(), g, level.price() + " / " + level.quantity(), 26, rowY, 150, FinanceGui.COL_GOOD);
            }
            if (i < book.asks().size()) {
                finance.marketdata.OrderBookLevel level = book.asks().get(i);
                FinanceGui.drawClippedString(
                        screen.font(), g, level.price() + " / " + level.quantity(), 214, rowY, 150, FinanceGui.COL_BAD);
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
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "D" + trade.mcDay() + " " + trade.timestamp().toLocalTime().withNano(0),
                    18,
                    rowY,
                    76,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    trade.direction().name(),
                    104,
                    rowY,
                    62,
                    trade.direction() == finance.marketdata.TradeDirection.BUY
                            ? FinanceGui.COL_GOOD
                            : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(trade.price()), 180, rowY, 90, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(trade.quantity()), 282, rowY, 84, FinanceGui.COL_TEXT);
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

    public void renderRankingColumn(
            GuiGraphics g,
            String title,
            List<finance.marketdata.MarketRankingEntry> entries,
            int x,
            int y,
            boolean change,
            boolean ratio) {
        FinanceGui.drawHeader(screen.font(), g, title, x, y);
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            finance.marketdata.MarketRankingEntry entry = entries.get(i);
            FinanceGui.drawClippedString(screen.font(), g, entry.id(), x, y + 15 + i * 17, 84, FinanceGui.COL_TEXT);
            String value = change
                    ? String.format(java.util.Locale.ROOT, "%+.1f%%", entry.changePercent())
                    : ratio
                            ? String.format(java.util.Locale.ROOT, "x%.1f", entry.volumeRatio())
                            : Long.toString(entry.volume());
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    value,
                    x,
                    y + 24 + i * 17,
                    84,
                    change
                            ? (entry.changePercent() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD)
                            : FinanceGui.COL_ACCENT);
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
            else if (type == MarketInstrumentType.STOCK) screen.stockChartVisible = false;
            else if (type == MarketInstrumentType.FUTURES) screen.futuresChartVisible = false;
            screen.updateInputVisibility();
            return true;
        }
        return false;
    }
}
