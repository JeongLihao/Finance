package finance.client;

import finance.gui.DashboardRows;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 经济仪表盘页（管理员）：货币分布、风险公司、当日成交、近 30 日价格指数和央行干预。原在 RecordTabs，由 {@link FinanceScreen} 直接调用。 */
final class DashboardPanel {
    private final FinanceScreen screen;

    DashboardPanel(FinanceScreen screen) {
        this.screen = screen;
    }

    public void renderDashboardTab(GuiGraphics g) {
        DashboardRows.EconomyDashboardRow dashboard = screen.menu().getDashboard();
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font(), g, "经济仪表盘", 10, y);
        FinanceGui.drawClippedString(screen.font(), g, "管理员只读监控：当前日数据与最近 30 天趋势", 100, y, 260, FinanceGui.COL_TEXT_DIM);
        y += 20;

        g.fill(8, y - 4, screen.imageWidth() - 8, screen.imageHeight() - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(
                g, 8, y - 4, screen.imageWidth() - 16, screen.imageHeight() - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawClippedString(screen.font(), g, "货币总量", 18, y + 4, 75, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.totalMoney()), 92, y + 4, 115, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(screen.font(), g, "风险公司", 220, y + 4, 65, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                Integer.toString(dashboard.bankruptcyRiskCompanies()),
                292,
                y + 4,
                35,
                dashboard.bankruptcyRiskCompanies() > 0 ? FinanceGui.COL_BAD : FinanceGui.COL_GOOD);

        FinanceGui.drawClippedString(screen.font(), g, "玩家", 18, y + 22, 35, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.playerCash()), 52, y + 22, 72, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "冻结", 130, y + 22, 35, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.playerFrozenFunds()), 165, y + 22, 72, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "公司", 244, y + 22, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.companyCash()), 277, y + 22, 80, FinanceGui.COL_TEXT);

        FinanceGui.drawClippedString(screen.font(), g, "NPC市场", 18, y + 40, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.npcCash()), 65, y + 40, 92, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "央行储备", 165, y + 40, 50, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.centralBankReserve()), 218, y + 40, 120, FinanceGui.COL_TEXT);

        FinanceGui.drawClippedString(screen.font(), g, "当日商品", 18, y + 58, 48, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.dailyCommodityVolume()), 68, y + 58, 65, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "当日股票", 142, y + 58, 48, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(), g, Long.toString(dashboard.dailyStockVolume()), 192, y + 58, 65, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font(), g, "价格指数", 266, y + 58, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                String.format(Locale.ROOT, "%.1f", dashboard.priceIndex()),
                312,
                y + 58,
                50,
                dashboard.priceIndex() >= 100.0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);

        FinanceGui.drawClippedString(screen.font(), g, "近30日价格指数", 18, y + 80, 90, FinanceGui.COL_TEXT_DIM);
        renderDashboardTrend(g, dashboard.trends(), 18, y + 94, 340, 48);
        FinanceGui.drawClippedString(
                screen.font(), g, "央行最近干预：" + dashboard.centralBankSummary(), 18, y + 148, 340, FinanceGui.COL_WARN);
    }

    public void renderDashboardTrend(
            GuiGraphics g, List<DashboardRows.EconomyTrendRow> trends, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFFE8E2D3);
        if (trends == null || trends.isEmpty()) {
            FinanceGui.drawClippedString(
                    screen.font(), g, "尚无完整日度快照", x + 8, y + 18, width - 16, FinanceGui.COL_TEXT_DIM);
            return;
        }
        double min = trends.stream()
                .mapToDouble(DashboardRows.EconomyTrendRow::priceIndex)
                .min()
                .orElse(100.0);
        double max = trends.stream()
                .mapToDouble(DashboardRows.EconomyTrendRow::priceIndex)
                .max()
                .orElse(100.0);
        double span = Math.max(1.0, max - min);
        int count = trends.size();
        for (int index = 0; index < count; index++) {
            double value = trends.get(index).priceIndex();
            int barHeight = Math.max(1, (int) Math.round((value - min) / span * (height - 8)));
            int left = x + index * width / count;
            int right = x + (index + 1) * width / count - 1;
            g.fill(
                    left,
                    y + height - 3 - barHeight,
                    Math.max(left + 1, right),
                    y + height - 3,
                    value >= 100.0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        }
        FinanceGui.drawClippedString(
                screen.font(), g, String.format(Locale.ROOT, "%.1f", max), x + 2, y + 2, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(
                screen.font(),
                g,
                String.format(Locale.ROOT, "%.1f", min),
                x + 2,
                y + height - 11,
                45,
                FinanceGui.COL_TEXT_DIM);
    }
}
