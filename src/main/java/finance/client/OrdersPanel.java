package finance.client;

import finance.gui.MarketRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 订单页：商品挂单列表，可以吃别人的单、取消自己的单。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class OrdersPanel {
    private final FinanceScreen screen;

    OrdersPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderOrdersTab(GuiGraphics g) {
        int tableX = 8;
        int tableW = screen.imageWidth() - 16;
        int headerY = FinanceGui.CONTENT_Y;

        List<MarketRows.OrderRow> orders = screen.menu().getPlayerOrders();
        g.fill(tableX, headerY - 2, tableX + tableW, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, tableX, headerY - 2, tableW, screen.imageHeight() - headerY - 6, FinanceGui.COL_PANEL_BORDER);
        g.fill(tableX + 1, headerY - 1, tableX + tableW - 1, headerY + 13, 0xFFE0DAC9);
        FinanceGui.drawHeader(screen.font(), g, "#", 12, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "商品", 34, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "方向", 92, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "单价", 130, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "数量", 178, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "归属", 226, headerY + 2);
        FinanceGui.drawHeader(screen.font(), g, "操作", 286, headerY + 2);
        g.fill(tableX + 1, headerY + 13, tableX + tableW - 1, headerY + 14, FinanceGui.COL_PANEL_BORDER);
        if (orders.isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    "暂无挂单。玩家挂出的买单和卖单会显示在这里。",
                    12,
                    headerY + 20,
                    screen.imageWidth() - 24,
                    FinanceGui.COL_TEXT_DIM);
            return;
        }

        int rowY = headerY + 16;
        int maxRows = Math.min(orders.size(), Math.max(1, (screen.imageHeight() - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            MarketRows.OrderRow row = orders.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(
                    tableX + 1,
                    y,
                    tableX + tableW - 1,
                    y + FinanceGui.ROW_HEIGHT,
                    (i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(
                    screen.font(), g, String.valueOf(i + 1), 12, y + 3, 20, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.commodityDisplayName(row.commodityId()),
                    34,
                    y + 3,
                    54,
                    FinanceGui.COL_TEXT);
            boolean isBuy = "BUY".equals(row.type());
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    isBuy ? "买单" : "卖单",
                    92,
                    y + 3,
                    34,
                    isBuy ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.price()), 130, y + 3, 44, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Integer.toString(row.quantity()), 178, y + 3, 42, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.ownedByPlayer() ? "自己" : "其他",
                    226,
                    y + 3,
                    48,
                    row.ownedByPlayer() ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT_DIM);
            UUID orderId = row.orderId();
            screen.button(g, isBuy ? "卖出" : "买入", 286, y + 1, 38, false, () -> {
                screen.setStatus("已提交吃单请求");
                FinancePacketHandler.CHANNEL.sendToServer(new TakeOrderPacket(orderId));
            });
            if (row.ownedByPlayer()) {
                screen.button(g, "取消", 330, y + 1, 38, false, () -> {
                    screen.setStatus("已提交取消订单");
                    FinancePacketHandler.CHANNEL.sendToServer(new CancelOrderPacket(orderId));
                });
            }
        }
    }
}
