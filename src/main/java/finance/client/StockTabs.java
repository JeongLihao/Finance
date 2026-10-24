package finance.client;

import finance.chart.MarketInstrumentType;
import finance.gui.CompanyRows;
import finance.gui.StockRows;
import finance.network.*;
import finance.network.CompanyIPOPacket;
import finance.stock.ConditionalStockOrderType;
import finance.util.FormatUtil;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 股票 / K 线 / 深度 / 排行 / 图表控件：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */
public final class StockTabs {
    private final FinanceScreen screen;

    public StockTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderStockTab(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.stockChartVisible) {
            StockRows.StockRow row = findSelectedStock();
            screen.candlestickView.renderCandlestickView(
                    g,
                    MarketInstrumentType.STOCK,
                    screen.selectedStock,
                    row == null ? screen.selectedStock : FinanceFormat.displayStockSymbol(row) + " " + row.name(),
                    mouseX,
                    mouseY);
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

        List<StockRows.StockRow> stocks = screen.menu().getStocks();
        if (stocks.isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "暂无股票。系统公司初始化后会自动生成股票。",
                    12,
                    headerY + 18,
                    screen.imageWidth() - 24,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }

        int rowY = headerY + 16;
        int maxRows =
                Math.min(stocks.size(), Math.max(1, (FinanceGui.STOCK_TRADE_Y - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            StockRows.StockRow row = stocks.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            boolean selected = row.symbol().equals(screen.selectedStock);
            screen.contentClicks.add(8, y, screen.imageWidth() - 16, FinanceGui.ROW_HEIGHT, () -> {
                screen.selectedStock = row.symbol();
                StockRows.StockRow picked = findSelectedStock();
                if (picked != null) {
                    screen.stockPriceBox.setValue(Long.toString(picked.lastPrice()));
                }
                screen.rememberUiState();
            });
            g.fill(
                    8,
                    y,
                    screen.imageWidth() - 8,
                    y + FinanceGui.ROW_HEIGHT,
                    selected
                            ? FinanceGui.COL_ROW_SELECT
                            : (i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD));
            if (selected) g.fill(8, y, 10, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.displayStockSymbol(row),
                    12,
                    y + 5,
                    46,
                    selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font(), g, row.name(), 62, y + 5, 82, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.lastPrice()), 150, y + 5, 40, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.fairValue()), 196, y + 5, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FormatUtil.formatPercent(row.dayChange()),
                    240,
                    y + 5,
                    46,
                    FinanceGui.changeColor(row.dayChange()));
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.dayVolume()), 292, y + 5, 36, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.availableShares()), 334, y + 5, 80, FinanceGui.COL_TEXT_DIM);
        }

        int tradeY = FinanceGui.STOCK_TRADE_Y;
        FinanceGui.drawSimpleSeparator(g, 8, tradeY - 6, screen.imageWidth() - 16);
        FinanceGui.drawSectionTitle(screen.font(), g, "限价委托", 10, tradeY);
        screen.button(g, "K线", 360, tradeY - 2, 30, false, () -> {
            screen.stockChartVisible = true;
            screen.candlestickView.requestCandlesticks(MarketInstrumentType.STOCK, screen.selectedStock);
            screen.updateInputVisibility();
        });
        StockRows.StockRow selected = findSelectedStock();
        if (selected != null) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.displayStockSymbol(selected) + " " + selected.name(),
                    92,
                    tradeY,
                    160,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, "现价: " + selected.lastPrice(), 260, tradeY, 90, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "高 " + selected.dayHigh() + " 低 " + selected.dayLow() + " 量 " + selected.dayVolume(),
                    12,
                    tradeY + 13,
                    190,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawTrendGraph(
                    g,
                    selected.priceHistory(),
                    258,
                    tradeY + 14,
                    102,
                    18,
                    FinanceGui.changeColor(selected.dayChange()));
            long total = FinanceFormat.safeMultiply(
                    Math.max(1, FinanceFormat.parseLong(screen.stockPriceBox.getValue())),
                    Math.max(1, FinanceFormat.parseInt(screen.stockQuantityBox.getValue())));
            StockRows.StockHoldingRow holding = findHolding(selected.symbol());
            FinanceGui.drawClippedString(screen.font(), g, "总价: " + total, 12, tradeY + 52, 100, FinanceGui.COL_ACCENT);
            if (holding != null) {
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "持仓 " + holding.quantity() + "  成本 " + holding.averageCost(),
                        128,
                        tradeY + 52,
                        160,
                        FinanceGui.COL_TEXT_DIM);
            } else {
                FinanceGui.drawClippedString(screen.font(), g, "当前无持仓", 128, tradeY + 52, 90, FinanceGui.COL_TEXT_DIM);
            }
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "预息/股 " + selected.expectedDividendPerShare(),
                    288,
                    tradeY + 52,
                    82,
                    FinanceGui.COL_WARN);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "上次 " + selected.lastDividendPerShare() + "/股 总 " + selected.lastDividendTotal(),
                    12,
                    tradeY + 79,
                    200,
                    FinanceGui.COL_TEXT_DIM);
        }
        g.drawString(screen.font(), "价格:", 12, tradeY + 19, FinanceGui.COL_TEXT_DIM, false);
        g.drawString(screen.font(), "股数:", 12, tradeY + 39, FinanceGui.COL_TEXT_DIM, false);
        screen.button(g, "-", 124, tradeY + 36, 20, false, () -> addQuantity(-1));
        screen.button(g, "+", 148, tradeY + 36, 20, false, () -> addQuantity(1));
        screen.button(g, "+10", 176, tradeY + 36, 30, false, () -> addQuantity(10));
        screen.filledButton(g, "买单", 234, tradeY + 36, 58, FinanceGui.COL_GOOD, () -> placeOrder(true));
        screen.filledButton(g, "卖单", 300, tradeY + 36, 58, FinanceGui.COL_BAD, () -> placeOrder(false));
        g.drawString(screen.font(), "条件:", 12, tradeY + 66, FinanceGui.COL_TEXT_DIM, false);
        screen.filledButton(g, "设止盈", 54, tradeY + 64, 58, FinanceGui.COL_GOOD, () -> conditional(true));
        screen.filledButton(g, "设止损", 120, tradeY + 64, 58, FinanceGui.COL_BAD, () -> conditional(false));
        FinanceGui.drawClippedString(
                screen.font(), g, "使用上方价格/股数，触发后自动提交卖单", 188, tradeY + 66, 180, FinanceGui.COL_TEXT_DIM);

        CompanyRows.CompanyFinancingRow financing = selectedFinancingProject();
        // 选中的股票没有增发时，显示第一个进行中的新股认购（还没有股票，选不中）
        if (financing == null) financing = firstIpoRow();
        if (financing != null) {
            int fy = tradeY + 92;
            boolean ipo = financing.symbol().isEmpty();
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    (ipo
                                    ? "新股 " + financing.companyName() + " 第 " + financing.deadlineMcDay() + " 天截止"
                                    : "融资 " + financing.raisedAmount() + "/" + financing.fundingTarget())
                            + " 股 " + financing.subscribedShares() + "/" + financing.issueQuantity()
                            + " 价 " + financing.issuePrice(),
                    12,
                    fy,
                    230,
                    FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "我已认 " + financing.playerSubscribedShares(),
                    246,
                    fy,
                    48,
                    FinanceGui.COL_TEXT_DIM);
            CompanyRows.CompanyFinancingRow row = financing;
            screen.filledButton(g, "认购", 300, fy - 2, 42, FinanceGui.COL_GOOD, () -> {
                long qty = screen.requireAmount(screen.stockQuantityBox, "认购股数");
                if (ipo) {
                    screen.setStatus("已提交新股认购");
                    FinancePacketHandler.CHANNEL.sendToServer(CompanyIPOPacket.subscribe(row.projectId(), qty));
                } else {
                    screen.setStatus("已提交增发认购");
                    FinancePacketHandler.CHANNEL.sendToServer(CompanyFinancingPacket.subscribe(row.projectId(), qty));
                }
            });
        }

        int orderY = tradeY + (financing != null ? 112 : 96);
        FinanceGui.drawSectionTitle(screen.font(), g, "当前委托", 10, orderY);
        List<StockRows.StockOrderRow> orders = selectedStockOrders();
        int shownOrders = 0;
        if (orders.isEmpty()) {
            g.drawString(screen.font(), "该股票暂无活跃委托", 12, orderY + 16, FinanceGui.COL_TEXT_DIM, false);
        } else {
            int y = orderY + 16;
            int reservedForConditional = 30;
            int maxOrders = Math.min(
                    orders.size(),
                    Math.max(1, (screen.imageHeight() - y - reservedForConditional) / FinanceGui.ROW_HEIGHT));
            for (int i = 0; i < maxOrders; i++) {
                StockRows.StockOrderRow row = orders.get(i);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.type().equals("BUY") ? "买" : "卖",
                        14,
                        y,
                        20,
                        row.type().equals("BUY") ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
                FinanceGui.drawClippedString(screen.font(), g, "价 " + row.price(), 44, y, 70, FinanceGui.COL_TEXT_DIM);
                FinanceGui.drawClippedString(
                        screen.font(), g, "量 " + row.quantity(), 124, y, 70, FinanceGui.COL_TEXT_DIM);
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        row.ownedByPlayer() ? "我的" : "市场",
                        206,
                        y,
                        42,
                        row.ownedByPlayer() ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT_DIM);
                if (row.ownedByPlayer()) {
                    screen.button(g, "撤单", 300, y - 2, 42, false, () -> {
                        screen.setStatus("已提交撤单");
                        FinancePacketHandler.CHANNEL.sendToServer(
                                new StockOrderPacket(StockOrderPacket.ActionType.CANCEL, row.orderId()));
                    });
                }
                y += FinanceGui.ROW_HEIGHT;
                shownOrders++;
            }
            if (orders.size() > maxOrders) {
                FinanceGui.drawClippedString(
                        screen.font(),
                        g,
                        "还有 " + (orders.size() - maxOrders) + " 条委托未显示",
                        14,
                        y,
                        160,
                        FinanceGui.COL_TEXT_DIM);
            }
        }

        int conditionalY = orderY + 16 + Math.max(1, shownOrders) * FinanceGui.ROW_HEIGHT + 8;
        if (conditionalY + 16 >= screen.imageHeight() - 4) {
            return;
        }
        FinanceGui.drawSectionTitle(screen.font(), g, "条件委托", 10, conditionalY);
        List<StockRows.ConditionalStockOrderRow> conditionalOrders = selectedConditionalStockOrders();
        if (conditionalOrders.isEmpty()) {
            g.drawString(screen.font(), "该股票暂无止盈止损委托", 12, conditionalY + 16, FinanceGui.COL_TEXT_DIM, false);
            return;
        }
        int y = conditionalY + 16;
        int maxConditionalOrders =
                Math.min(conditionalOrders.size(), Math.max(0, (screen.imageHeight() - y - 4) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxConditionalOrders; i++) {
            StockRows.ConditionalStockOrderRow row = conditionalOrders.get(i);
            boolean takeProfit = row.type().equals("TAKE_PROFIT");
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    takeProfit ? "止盈" : "止损",
                    14,
                    y,
                    34,
                    takeProfit ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(
                    screen.font(), g, "触发 " + row.triggerPrice(), 54, y, 78, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, "量 " + row.quantity(), 142, y, 58, FinanceGui.COL_TEXT_DIM);
            screen.button(g, "取消", 300, y - 2, 42, false, () -> {
                screen.setStatus("已提交取消条件委托");
                FinancePacketHandler.CHANNEL.sendToServer(new ConditionalStockOrderPacket(row.orderId()));
            });
            y += FinanceGui.ROW_HEIGHT;
        }
    }

    // ---- 标签 5 点击: 股票 ----

    private void addQuantity(int delta) {
        int qty = FinanceFormat.parseInt(screen.stockQuantityBox.getValue());
        screen.stockQuantityBox.setValue(Integer.toString(delta < 0 ? Math.max(1, qty + delta) : qty + delta));
    }

    private void placeOrder(boolean buy) {
        long price = screen.requireAmount(screen.stockPriceBox, "价格");
        int qty = screen.requireInt(screen.stockQuantityBox, "股数", 1, Integer.MAX_VALUE);
        screen.setStatus(buy ? "已提交买入委托" : "已提交卖出委托");
        FinancePacketHandler.CHANNEL.sendToServer(new StockOrderPacket(
                buy ? StockOrderPacket.ActionType.PLACE_BUY : StockOrderPacket.ActionType.PLACE_SELL,
                screen.selectedStock,
                price,
                qty));
    }

    private void conditional(boolean takeProfit) {
        int qty = screen.requireInt(screen.stockQuantityBox, "股数", 1, Integer.MAX_VALUE);
        long triggerPrice = screen.requireAmount(screen.stockPriceBox, "触发价");
        screen.setStatus(takeProfit ? "已提交止盈委托" : "已提交止损委托");
        FinancePacketHandler.CHANNEL.sendToServer(new ConditionalStockOrderPacket(
                takeProfit ? ConditionalStockOrderType.TAKE_PROFIT : ConditionalStockOrderType.STOP_LOSS,
                screen.selectedStock,
                triggerPrice,
                qty));
    }

    public StockRows.StockRow findSelectedStock() {
        for (StockRows.StockRow row : screen.menu().getStocks()) {
            if (row.symbol().equals(screen.selectedStock)) return row;
        }
        return screen.menu().getStocks().isEmpty()
                ? null
                : screen.menu().getStocks().get(0);
    }

    public StockRows.StockHoldingRow findHolding(String symbol) {
        for (StockRows.StockHoldingRow row : screen.menu().getStockHoldings()) {
            if (row.symbol().equals(symbol)) return row;
        }
        return null;
    }

    public List<StockRows.StockOrderRow> selectedStockOrders() {
        List<StockRows.StockOrderRow> rows = new ArrayList<>();
        String symbol = screen.selectedStock;
        StockRows.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (StockRows.StockOrderRow row : screen.menu().getStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }

    public List<StockRows.ConditionalStockOrderRow> selectedConditionalStockOrders() {
        List<StockRows.ConditionalStockOrderRow> rows = new ArrayList<>();
        String symbol = screen.selectedStock;
        StockRows.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return rows;
        }
        for (StockRows.ConditionalStockOrderRow row : screen.menu().getConditionalStockOrders()) {
            if (symbol.equals(row.symbol())) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** 第一个进行中的新股认购（股票代码为空的融资行），不含自己公司的。 */
    CompanyRows.CompanyFinancingRow firstIpoRow() {
        CompanyRows.CompanyInfo mine = screen.menu().getPlayerCompany();
        for (CompanyRows.CompanyFinancingRow row : screen.menu().getCompanyFinancingRows()) {
            if (!row.symbol().isEmpty()) continue;
            if (mine != null && mine.playerOwned() && mine.companyId().equals(row.companyId())) continue;
            return row;
        }
        return null;
    }

    public CompanyRows.CompanyFinancingRow selectedFinancingProject() {
        String symbol = screen.selectedStock;
        StockRows.StockRow selected = findSelectedStock();
        if ((symbol == null || symbol.isEmpty()) && selected != null) {
            symbol = selected.symbol();
        }
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        for (CompanyRows.CompanyFinancingRow row : screen.menu().getCompanyFinancingRows()) {
            if (symbol.equals(row.symbol())) {
                return row;
            }
        }
        return null;
    }
}
