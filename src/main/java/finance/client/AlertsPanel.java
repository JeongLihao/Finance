package finance.client;

import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertType;
import finance.gui.RecordRows;
import finance.gui.StockRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 提醒页：商品和股票的价格提醒，新建和取消。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class AlertsPanel {
    private final FinanceScreen screen;

    AlertsPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderAlertTab(GuiGraphics g) {
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font(), g, "我的提醒", 10, y);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "当前商品: " + FinanceFormat.commodityDisplayName(screen.selectedCommodity),
                14,
                y + 18,
                130,
                FinanceGui.COL_TEXT_DIM);
        StockRows.StockRow stock = screen.stockTabs.findSelectedStock();
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "当前股票: " + (stock != null ? FinanceFormat.displayStockSymbol(stock) : "-"),
                160,
                y + 18,
                100,
                FinanceGui.COL_TEXT_DIM);
        g.drawString(screen.font(), "目标价:", 14, y + 62, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawFilledButton(screen.font(), g, "商品涨到", 148, y + 58, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "商品跌到", 212, y + 58, 58, FinanceGui.COL_BAD);
        FinanceGui.drawFilledButton(screen.font(), g, "股票涨到", 276, y + 58, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "股票跌到", 340, y + 58, 50, FinanceGui.COL_BAD);

        FinanceGui.drawFilledButton(screen.font(), g, "商品前高", 10, y + 74, 88, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "商品前低", 104, y + 74, 88, FinanceGui.COL_BAD);
        FinanceGui.drawFilledButton(screen.font(), g, "股票前高", 198, y + 74, 88, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font(), g, "股票前低", 292, y + 74, 88, FinanceGui.COL_BAD);

        y += 104;
        FinanceGui.drawHeader(screen.font(), g, "类型", 12, y);
        FinanceGui.drawHeader(screen.font(), g, "对象", 58, y);
        FinanceGui.drawHeader(screen.font(), g, "方向", 146, y);
        FinanceGui.drawHeader(screen.font(), g, "目标价", 204, y);
        FinanceGui.drawHeader(screen.font(), g, "操作", 316, y);
        g.fill(8, y + 12, screen.imageWidth() - 8, y + 13, FinanceGui.COL_PANEL_BORDER);

        List<RecordRows.PriceAlertRow> alerts = screen.menu().getPriceAlerts();
        if (alerts.isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "暂无提醒。设置后价格到达目标会发送游戏内消息。",
                    12,
                    y + 20,
                    screen.imageWidth() - 24,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }
        int rowY = y + 18;
        int maxRows = Math.min(alerts.size(), Math.max(1, (screen.imageHeight() - rowY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            RecordRows.PriceAlertRow row = alerts.get(i);
            int ry = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(
                    8,
                    ry - 2,
                    screen.imageWidth() - 8,
                    ry + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.type().equals("COMMODITY") ? "商品" : "股票",
                    12,
                    ry + 2,
                    40,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, row.targetId(), 58, ry + 2, 84, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.direction().equals("ABOVE") ? "涨到" : "跌到",
                    146,
                    ry + 2,
                    54,
                    row.direction().equals("ABOVE") ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.targetPrice()), 204, ry + 2, 80, FinanceGui.COL_ACCENT);
            FinanceGui.drawButton(screen.font(), g, "取消", 316, ry - 2, 42, false);
        }
    }

    public boolean handleAlertClick(int mx, int my) {
        int y = FinanceGui.CONTENT_Y;
        if (my >= y + 74 && my < y + 87) {
            if (mx >= 10 && mx < 98) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY,
                        screen.selectedCommodity,
                        PriceAlertDirection.PREVIOUS_HIGH_BREAKOUT,
                        0));
                return true;
            }
            if (mx >= 104 && mx < 192) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY,
                        screen.selectedCommodity,
                        PriceAlertDirection.PREVIOUS_LOW_BREAKDOWN,
                        0));
                return true;
            }
            if (screen.selectedStock == null || screen.selectedStock.isBlank()) {
                screen.setStatus("请先选择股票");
                return true;
            }
            if (mx >= 198 && mx < 286) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, screen.selectedStock, PriceAlertDirection.PREVIOUS_HIGH_BREAKOUT, 0));
                return true;
            }
            if (mx >= 292 && mx < 380) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, screen.selectedStock, PriceAlertDirection.PREVIOUS_LOW_BREAKDOWN, 0));
                return true;
            }
        }
        if (my >= y + 58 && my < y + 71) {
            long targetPrice = screen.requireAmount(screen.alertPriceBox, "提醒价格");
            if (mx >= 148 && mx < 206) {
                screen.setStatus("已提交商品涨到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, screen.selectedCommodity, PriceAlertDirection.ABOVE, targetPrice));
                return true;
            }
            if (mx >= 212 && mx < 270) {
                screen.setStatus("已提交商品跌到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, screen.selectedCommodity, PriceAlertDirection.BELOW, targetPrice));
                return true;
            }
            if (mx >= 276 && mx < 334) {
                if (screen.selectedStock == null || screen.selectedStock.isBlank()) {
                    screen.setStatus("请先选择股票");
                    return true;
                }
                screen.setStatus("已提交股票涨到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, screen.selectedStock, PriceAlertDirection.ABOVE, targetPrice));
                return true;
            }
            if (mx >= 340 && mx < 390) {
                if (screen.selectedStock == null || screen.selectedStock.isBlank()) {
                    screen.setStatus("请先选择股票");
                    return true;
                }
                screen.setStatus("已提交股票跌到提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.STOCK, screen.selectedStock, PriceAlertDirection.BELOW, targetPrice));
                return true;
            }
        }

        int listY = y + 104 + 18;
        List<RecordRows.PriceAlertRow> alerts = screen.menu().getPriceAlerts();
        int maxRows = Math.min(alerts.size(), Math.max(1, (screen.imageHeight() - listY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int rowY = listY + i * FinanceGui.ROW_HEIGHT;
            if (my >= rowY - 2 && my < rowY + 11 && mx >= 316 && mx < 358) {
                screen.setStatus("已提交取消提醒");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new PriceAlertPacket(alerts.get(i).alertId()));
                return true;
            }
        }
        return false;
    }
}
