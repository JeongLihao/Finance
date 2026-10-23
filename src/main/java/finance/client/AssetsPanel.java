package finance.client;

import finance.gui.RecordRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 资产页：资产汇总和明细，按价值或收益排序。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class AssetsPanel {
    private final FinanceScreen screen;

    AssetsPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderAssetTab(GuiGraphics g) {
        RecordRows.AssetSummary summary = screen.menu().getAssetSummary();
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font(), g, "我的资产", 10, y);
        FinanceGui.drawButton(
                screen.font(),
                g,
                screen.assetSortMode == 0 ? "按价值排序" : "切到价值",
                270,
                y - 2,
                58,
                screen.assetSortMode == 0);
        FinanceGui.drawButton(
                screen.font(),
                g,
                screen.assetSortMode == 1 ? "按收益排序" : "切到收益",
                332,
                y - 2,
                58,
                screen.assetSortMode == 1);

        y += 18;
        g.fill(8, y - 4, screen.imageWidth() - 8, y + 42, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, y - 4, screen.imageWidth() - 16, 46, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawClippedString(
                screen.font(), g, "总资产: " + summary.totalAsset(), 14, y, 110, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "今日盈亏: " + FinanceFormat.signedLong(summary.todayProfit()),
                136,
                y,
                110,
                summary.todayProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "现金 " + summary.cash() + " 冻结 " + summary.frozenCash(),
                254,
                y,
                130,
                FinanceGui.COL_TEXT_DIM);
        y += 16;
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "商品 " + summary.commodityValue() + " ("
                        + FinanceFormat.percentOf(summary.commodityValue(), summary.totalAsset()) + ")",
                14,
                y,
                120,
                FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "股票 " + summary.stockValue() + " ("
                        + FinanceFormat.percentOf(summary.stockValue(), summary.totalAsset()) + ")",
                146,
                y,
                120,
                FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                "现金占比 " + FinanceFormat.percentOf(summary.cash() + summary.frozenCash(), summary.totalAsset()),
                278,
                y,
                110,
                FinanceGui.COL_TEXT_DIM);

        y += 26;
        FinanceGui.drawHeader(screen.font(), g, "类别", 12, y);
        FinanceGui.drawHeader(screen.font(), g, "资产", 54, y);
        FinanceGui.drawHeader(screen.font(), g, "数量", 136, y);
        FinanceGui.drawHeader(screen.font(), g, "现价", 188, y);
        FinanceGui.drawHeader(screen.font(), g, "成本", 238, y);
        FinanceGui.drawHeader(screen.font(), g, "价值", 288, y);
        FinanceGui.drawHeader(screen.font(), g, "浮盈", 342, y);
        g.fill(8, y + 12, screen.imageWidth() - 8, y + 13, FinanceGui.COL_PANEL_BORDER);

        List<RecordRows.AssetRow> rows = FinanceFormat.sortedAssetRows(screen);
        int rowY = y + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (screen.imageHeight() - rowY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            RecordRows.AssetRow row = rows.get(i);
            int ry = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(
                    8,
                    ry - 2,
                    screen.imageWidth() - 8,
                    ry + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font(), g, row.category(), 12, ry + 2, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font(), g, row.name(), 54, ry + 2, 78, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.quantity()), 136, ry + 2, 48, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.currentPrice()), 188, ry + 2, 46, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.cost()), 238, ry + 2, 46, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(
                    screen.font(), g, Long.toString(row.value()), 288, ry + 2, 50, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(
                    screen.font(),
                    g,
                    FinanceFormat.signedLong(row.floatingProfit()),
                    342,
                    ry + 2,
                    46,
                    row.floatingProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        }
    }

    public boolean handleAssetClick(int mx, int my) {
        int y = FinanceGui.CONTENT_Y;
        if (my >= y - 2 && my < y + 11) {
            if (mx >= 270 && mx < 328) {
                screen.assetSortMode = 0;
                screen.setStatus("资产明细已按价值排序");
                return true;
            }
            if (mx >= 332 && mx < 390) {
                screen.assetSortMode = 1;
                screen.setStatus("资产明细已按收益排序");
                return true;
            }
        }
        return false;
    }
}
