package finance.client;

import finance.gui.RecordRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 交易记录页：最近的成交流水。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class TransactionsPanel {
    private final FinanceScreen screen;

    TransactionsPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderTransactionTab(GuiGraphics g) {
        int headerY = FinanceGui.CONTENT_Y;
        FinanceGui.drawHeader(screen.font(), g, "时间", 12, headerY);
        FinanceGui.drawHeader(screen.font(), g, "玩家", 70, headerY);
        FinanceGui.drawHeader(screen.font(), g, "类型", 126, headerY);
        FinanceGui.drawHeader(screen.font(), g, "对象", 196, headerY);
        FinanceGui.drawHeader(screen.font(), g, "数量", 278, headerY);
        FinanceGui.drawHeader(screen.font(), g, "金额", 326, headerY);
        g.fill(8, headerY + 12, screen.imageWidth() - 8, headerY + 13, FinanceGui.COL_PANEL_BORDER);

        List<RecordRows.TransactionRow> rows = screen.menu().getTransactions();
        if (rows.isEmpty()) {
            g.drawString(
                    screen.font(),
                    screen.isAdmin ? "暂无交易记录。" : "暂无你的交易记录。",
                    12,
                    headerY + 20,
                    FinanceGui.COL_TEXT_DIM,
                    false);
            return;
        }

        int rowY = headerY + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (screen.imageHeight() - rowY - 8) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            RecordRows.TransactionRow row = rows.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(
                    8,
                    y - 2,
                    screen.imageWidth() - 8,
                    y + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.formatRecordTime(row.timestamp()),
                    12,
                    y + 2,
                    54,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, FinanceFormat.shortUuid(row.playerId()), 70, y + 2, 52, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.displayTransactionType(row.type()),
                    126,
                    y + 2,
                    66,
                    FinanceFormat.typeColor(row.type()));
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.objectName().isEmpty() ? "-" : row.objectName(),
                    196,
                    y + 2,
                    78,
                    FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.quantity() > 0 ? Long.toString(row.quantity()) : "-",
                    278,
                    y + 2,
                    44,
                    FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    row.amount() > 0 ? Long.toString(row.amount()) : "-",
                    326,
                    y + 2,
                    64,
                    FinanceGui.COL_ACCENT);
        }
    }
}
