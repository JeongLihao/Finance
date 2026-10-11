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

/** 记录类页签：订单 / 库存 / 成交记录 / 资产 / 提醒 / 仪表：从 FinanceScreen 抽取的页签行为类。
 * 通过 screen 反向引用共享界面状态；未来做跨模组页签注册表时，此类即天然的注册单元。 */

public final class RecordTabs {
    private final FinanceScreen screen;

    public RecordTabs(FinanceScreen screen) {
        this.screen = screen;
    }

    // ================================================================
    // 标签 2: 订单管理
    // ================================================================

    public void renderOrdersTab(GuiGraphics g) {
        int tableX = 8;
        int tableW = screen.imageWidth - 16;
        int headerY = FinanceGui.CONTENT_Y;

        List<FinanceMenu.OrderRow> orders = screen.menu.getPlayerOrders();
        g.fill(tableX, headerY - 2, tableX + tableW, screen.imageHeight - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, tableX, headerY - 2, tableW, screen.imageHeight - headerY - 6, FinanceGui.COL_PANEL_BORDER);
        g.fill(tableX + 1, headerY - 1, tableX + tableW - 1, headerY + 13, 0xFFE0DAC9);
        FinanceGui.drawHeader(screen.font, g, "#", 12, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "商品", 34, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "方向", 92, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "单价", 130, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "数量", 178, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "归属", 226, headerY + 2);
        FinanceGui.drawHeader(screen.font, g, "操作", 286, headerY + 2);
        g.fill(tableX + 1, headerY + 13, tableX + tableW - 1, headerY + 14, FinanceGui.COL_PANEL_BORDER);
        if (orders.isEmpty()) {
            FinanceGui.drawClippedString(screen.font, g, "暂无挂单。玩家挂出的买单和卖单会显示在这里。",
                    12, headerY + 20, screen.imageWidth - 24, FinanceGui.COL_TEXT_DIM);
            return;
        }

        int rowY = headerY + 16;
        int maxRows = Math.min(orders.size(), Math.max(1, (screen.imageHeight - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.OrderRow row = orders.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(tableX + 1, y, tableX + tableW - 1, y + FinanceGui.ROW_HEIGHT, (i % 2 == 0) ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font, g, String.valueOf(i + 1), 12, y + 3, 20, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.commodityDisplayName(row.commodityId()), 34, y + 3, 54, FinanceGui.COL_TEXT);
            boolean isBuy = "BUY".equals(row.type());
            FinanceGui.drawClippedString(screen.font, g, isBuy ? "买单" : "卖单", 92, y + 3, 34, isBuy ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.price()), 130, y + 3, 44, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, Integer.toString(row.quantity()), 178, y + 3, 42, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, row.ownedByPlayer() ? "自己" : "其他", 226, y + 3, 48, row.ownedByPlayer() ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawButton(screen.font, g, isBuy ? "卖出" : "买入", 286, y + 1, 38, false);
            if (row.ownedByPlayer()) {
                FinanceGui.drawButton(screen.font, g, "取消", 330, y + 1, 38, false);
            }
        }
    }

    // ================================================================
    // 标签 3: 库存与账户
    // ================================================================

    public void renderInventoryTab(GuiGraphics g) {
        int cardY = FinanceGui.CONTENT_Y;
        int cardH = 38;
        g.fill(8, cardY, screen.imageWidth - 8, cardY + cardH, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, cardY, screen.imageWidth - 16, cardH, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawSectionTitle(screen.font, g, "账户", 10, cardY + 2);
        g.drawString(screen.font, "可用: ", 12, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(screen.menu.getBalance()), 46, cardY + 18, 120, FinanceGui.COL_GOOD);
        g.drawString(screen.font, "冻结: ", 190, cardY + 18, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(screen.menu.getFrozenBalance()), 224, cardY + 18, 120, FinanceGui.COL_WARN);

        int invY = cardY + cardH + 6;
        int invH = screen.imageHeight - invY - 4;
        g.fill(8, invY, screen.imageWidth - 8, invY + invH, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, invY, screen.imageWidth - 16, invH, FinanceGui.COL_PANEL_BORDER);

        FinanceGui.drawSectionTitle(screen.font, g, "商品库存（点击选择）", 10, invY + 2);
        FinanceGui.drawClippedString(screen.font, g, "当前: " + FinanceFormat.commodityDisplayName(screen.selectedCommodity), 122, invY + 3, 90, FinanceGui.COL_ACCENT);
        g.drawString(screen.font, "数量:", 12, invY + 22, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawButton(screen.font, g, "-64", 116, invY + 20, 32, false);
        FinanceGui.drawButton(screen.font, g, "+64", 152, invY + 20, 32, false);
        FinanceGui.drawFilledButton(screen.font, g, "存入", 246, invY + 20, 44, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font, g, "取出", 296, invY + 20, 44, FinanceGui.COL_ACCENT);

        int headerLineY = invY + 42;
        FinanceGui.drawHeader(screen.font, g, "商品", 14, headerLineY);
        FinanceGui.drawHeader(screen.font, g, "库存", 140, headerLineY);
        FinanceGui.drawHeader(screen.font, g, "背包", 210, headerLineY);
        g.fill(8, headerLineY + 11, screen.imageWidth - 8, headerLineY + 12, FinanceGui.COL_PANEL_BORDER);

        Map<String, Integer> inv = screen.menu.getPlayerInventory();
        Map<String, Integer> mcInv = screen.menu.getMcInventory();
        Set<String> allCommodityIds = new LinkedHashSet<>();
        allCommodityIds.addAll(inv.keySet());
        allCommodityIds.addAll(mcInv.keySet());

        if (allCommodityIds.isEmpty()) {
            FinanceGui.drawClippedString(screen.font, g, "暂无商品（可通过管理标签添加常用物品）",
                    12, headerLineY + 18, screen.imageWidth - 24, FinanceGui.COL_TEXT_DIM);
            return;
        }

        int y = headerLineY + 14;
        int maxY = invY + invH - FinanceGui.ROW_HEIGHT;
        for (String commodityId : allCommodityIds) {
            if (y > maxY) break;
            boolean selected = commodityId.equals(screen.selectedCommodity);
            int bg = selected ? FinanceGui.COL_ROW_SELECT : ((y / FinanceGui.ROW_HEIGHT) % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            g.fill(9, y, screen.imageWidth - 9, y + FinanceGui.ROW_HEIGHT, bg);
            if (selected) {
                g.fill(9, y, 11, y + FinanceGui.ROW_HEIGHT, FinanceGui.COL_ACCENT);
            }
            Commodity c = CommodityRegistry.getCommodity(commodityId);
            FinanceGui.renderItemIcon(g, c, 14, y + 1);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.commodityDisplayName(commodityId), 28, y + 3, 106, selected ? FinanceGui.COL_ACCENT : FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, String.valueOf(inv.getOrDefault(commodityId, 0)), 140, y + 3, 60, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, String.valueOf(mcInv.getOrDefault(commodityId, 0)), 210, y + 3, 60, FinanceGui.COL_TEXT_DIM);
            y += FinanceGui.ROW_HEIGHT;
        }
    }

    // ================================================================
    // 标签 6: 交易记录
    // ================================================================

    public void renderTransactionTab(GuiGraphics g) {
        int headerY = FinanceGui.CONTENT_Y;
        FinanceGui.drawHeader(screen.font, g, "时间", 12, headerY);
        FinanceGui.drawHeader(screen.font, g, "玩家", 70, headerY);
        FinanceGui.drawHeader(screen.font, g, "类型", 126, headerY);
        FinanceGui.drawHeader(screen.font, g, "对象", 196, headerY);
        FinanceGui.drawHeader(screen.font, g, "数量", 278, headerY);
        FinanceGui.drawHeader(screen.font, g, "金额", 326, headerY);
        g.fill(8, headerY + 12, screen.imageWidth - 8, headerY + 13, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.TransactionRow> rows = screen.menu.getTransactions();
        if (rows.isEmpty()) {
            g.drawString(screen.font,
                    screen.isAdmin ? "暂无交易记录。" : "暂无你的交易记录。",
                    12, headerY + 20, FinanceGui.COL_TEXT_DIM, false);
            return;
        }

        int rowY = headerY + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (screen.imageHeight - rowY - 8) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.TransactionRow row = rows.get(i);
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(8, y - 2, screen.imageWidth - 8, y + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.formatRecordTime(row.timestamp()), 12, y + 2, 54, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.shortUuid(row.playerId()), 70, y + 2, 52, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.displayTransactionType(row.type()), 126, y + 2, 66, FinanceFormat.typeColor(row.type()));
            FinanceGui.drawClippedString(screen.font, g, row.objectName().isEmpty() ? "-" : row.objectName(), 196, y + 2, 78, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, row.quantity() > 0 ? Long.toString(row.quantity()) : "-", 278, y + 2, 44, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, row.amount() > 0 ? Long.toString(row.amount()) : "-", 326, y + 2, 64, FinanceGui.COL_ACCENT);
        }
    }

    // ================================================================
    // 标签 7: 我的资产
    // ================================================================

    public void renderAssetTab(GuiGraphics g) {
        FinanceMenu.AssetSummary summary = screen.menu.getAssetSummary();
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font, g, "我的资产", 10, y);
        FinanceGui.drawButton(screen.font, g, assetSortMode == 0 ? "按价值排序" : "切到价值", 270, y - 2, 58, assetSortMode == 0);
        FinanceGui.drawButton(screen.font, g, assetSortMode == 1 ? "按收益排序" : "切到收益", 332, y - 2, 58, assetSortMode == 1);

        y += 18;
        g.fill(8, y - 4, screen.imageWidth - 8, y + 42, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, y - 4, screen.imageWidth - 16, 46, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawClippedString(screen.font, g, "总资产: " + summary.totalAsset(), 14, y, 110, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(screen.font, g, "今日盈亏: " + FinanceFormat.signedLong(summary.todayProfit()), 136, y, 110,
                summary.todayProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        FinanceGui.drawClippedString(screen.font, g, "现金 " + summary.cash() + " 冻结 " + summary.frozenCash(), 254, y, 130, FinanceGui.COL_TEXT_DIM);
        y += 16;
        FinanceGui.drawClippedString(screen.font, g, "商品 " + summary.commodityValue() + " (" + FinanceFormat.percentOf(summary.commodityValue(), summary.totalAsset()) + ")",
                14, y, 120, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, "股票 " + summary.stockValue() + " (" + FinanceFormat.percentOf(summary.stockValue(), summary.totalAsset()) + ")",
                146, y, 120, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, "现金占比 " + FinanceFormat.percentOf(summary.cash() + summary.frozenCash(), summary.totalAsset()),
                278, y, 110, FinanceGui.COL_TEXT_DIM);

        y += 26;
        FinanceGui.drawHeader(screen.font, g, "类别", 12, y);
        FinanceGui.drawHeader(screen.font, g, "资产", 54, y);
        FinanceGui.drawHeader(screen.font, g, "数量", 136, y);
        FinanceGui.drawHeader(screen.font, g, "现价", 188, y);
        FinanceGui.drawHeader(screen.font, g, "成本", 238, y);
        FinanceGui.drawHeader(screen.font, g, "价值", 288, y);
        FinanceGui.drawHeader(screen.font, g, "浮盈", 342, y);
        g.fill(8, y + 12, screen.imageWidth - 8, y + 13, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.AssetRow> rows = FinanceFormat.sortedAssetRows();
        int rowY = y + 18;
        int maxRows = Math.min(rows.size(), Math.max(1, (screen.imageHeight - rowY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.AssetRow row = rows.get(i);
            int ry = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(8, ry - 2, screen.imageWidth - 8, ry + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font, g, row.category(), 12, ry + 2, 38, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, row.name(), 54, ry + 2, 78, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.quantity()), 136, ry + 2, 48, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.currentPrice()), 188, ry + 2, 46, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.cost()), 238, ry + 2, 46, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.value()), 288, ry + 2, 50, FinanceGui.COL_ACCENT);
            FinanceGui.drawClippedString(screen.font, g, FinanceFormat.signedLong(row.floatingProfit()), 342, ry + 2, 46,
                    row.floatingProfit() >= 0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        }
    }

    // ================================================================
    // 标签 8: 我的提醒
    // ================================================================

    public void renderAlertTab(GuiGraphics g) {
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font, g, "我的提醒", 10, y);
        FinanceGui.drawClippedString(screen.font, g, "当前商品: " + FinanceFormat.commodityDisplayName(screen.selectedCommodity), 14, y + 18, 130, FinanceGui.COL_TEXT_DIM);
        FinanceMenu.StockRow stock = screen.stockTabs.findSelectedStock();
        FinanceGui.drawClippedString(screen.font, g, "当前股票: " + (stock != null ? FinanceFormat.displayStockSymbol(stock) : "-"), 160, y + 18, 100, FinanceGui.COL_TEXT_DIM);
        g.drawString(screen.font, "目标价:", 14, y + 62, FinanceGui.COL_TEXT_DIM, false);
        FinanceGui.drawFilledButton(screen.font, g, "商品涨到", 148, y + 58, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font, g, "商品跌到", 212, y + 58, 58, FinanceGui.COL_BAD);
        FinanceGui.drawFilledButton(screen.font, g, "股票涨到", 276, y + 58, 58, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font, g, "股票跌到", 340, y + 58, 50, FinanceGui.COL_BAD);

        FinanceGui.drawFilledButton(screen.font, g, "商品前高", 10, y + 74, 88, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font, g, "商品前低", 104, y + 74, 88, FinanceGui.COL_BAD);
        FinanceGui.drawFilledButton(screen.font, g, "股票前高", 198, y + 74, 88, FinanceGui.COL_GOOD);
        FinanceGui.drawFilledButton(screen.font, g, "股票前低", 292, y + 74, 88, FinanceGui.COL_BAD);

        y += 104;
        FinanceGui.drawHeader(screen.font, g, "类型", 12, y);
        FinanceGui.drawHeader(screen.font, g, "对象", 58, y);
        FinanceGui.drawHeader(screen.font, g, "方向", 146, y);
        FinanceGui.drawHeader(screen.font, g, "目标价", 204, y);
        FinanceGui.drawHeader(screen.font, g, "操作", 316, y);
        g.fill(8, y + 12, screen.imageWidth - 8, y + 13, FinanceGui.COL_PANEL_BORDER);

        List<FinanceMenu.PriceAlertRow> alerts = screen.menu.getPriceAlerts();
        if (alerts.isEmpty()) {
            FinanceGui.drawClippedString(screen.font, g, "暂无提醒。设置后价格到达目标会发送游戏内消息。",
                    12, y + 20, screen.imageWidth - 24, FinanceGui.COL_TEXT_DIM);
            return;
        }
        int rowY = y + 18;
        int maxRows = Math.min(alerts.size(), Math.max(1, (screen.imageHeight - rowY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            FinanceMenu.PriceAlertRow row = alerts.get(i);
            int ry = rowY + i * FinanceGui.ROW_HEIGHT;
            g.fill(8, ry - 2, screen.imageWidth - 8, ry + FinanceGui.ROW_HEIGHT - 2,
                    i % 2 == 0 ? FinanceGui.COL_ROW_EVEN : FinanceGui.COL_ROW_ODD);
            FinanceGui.drawClippedString(screen.font, g, row.type().equals("COMMODITY") ? "商品" : "股票", 12, ry + 2, 40, FinanceGui.COL_TEXT_DIM);
            FinanceGui.drawClippedString(screen.font, g, row.targetId(), 58, ry + 2, 84, FinanceGui.COL_TEXT);
            FinanceGui.drawClippedString(screen.font, g, row.direction().equals("ABOVE") ? "涨到" : "跌到", 146, ry + 2, 54,
                    row.direction().equals("ABOVE") ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
            FinanceGui.drawClippedString(screen.font, g, Long.toString(row.targetPrice()), 204, ry + 2, 80, FinanceGui.COL_ACCENT);
            FinanceGui.drawButton(screen.font, g, "取消", 316, ry - 2, 42, false);
        }
    }

    // ================================================================
    // 标签 9: 经济仪表盘（管理员）
    // ================================================================

    public void renderDashboardTab(GuiGraphics g) {
        FinanceMenu.EconomyDashboardRow dashboard = screen.menu.getDashboard();
        int y = FinanceGui.CONTENT_Y;
        FinanceGui.drawSectionTitle(screen.font, g, "经济仪表盘", 10, y);
        FinanceGui.drawClippedString(screen.font, g, "管理员只读监控：当前日数据与最近 30 天趋势", 100, y, 260, FinanceGui.COL_TEXT_DIM);
        y += 20;

        g.fill(8, y - 4, screen.imageWidth - 8, screen.imageHeight - 8, 0xFFF1ECDD);
        FinanceGui.drawSimpleBorder(g, 8, y - 4, screen.imageWidth - 16, screen.imageHeight - y - 4, FinanceGui.COL_PANEL_BORDER);
        FinanceGui.drawClippedString(screen.font, g, "货币总量", 18, y + 4, 75, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.totalMoney()), 92, y + 4, 115, FinanceGui.COL_ACCENT);
        FinanceGui.drawClippedString(screen.font, g, "风险公司", 220, y + 4, 65, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Integer.toString(dashboard.bankruptcyRiskCompanies()), 292, y + 4, 35,
                dashboard.bankruptcyRiskCompanies() > 0 ? FinanceGui.COL_BAD : FinanceGui.COL_GOOD);

        FinanceGui.drawClippedString(screen.font, g, "玩家", 18, y + 22, 35, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.playerCash()), 52, y + 22, 72, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font, g, "冻结", 130, y + 22, 35, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.playerFrozenFunds()), 165, y + 22, 72, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font, g, "公司", 244, y + 22, 32, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.companyCash()), 277, y + 22, 80, FinanceGui.COL_TEXT);

        FinanceGui.drawClippedString(screen.font, g, "NPC市场", 18, y + 40, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.npcCash()), 65, y + 40, 92, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font, g, "央行储备", 165, y + 40, 50, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.centralBankReserve()), 218, y + 40, 120, FinanceGui.COL_TEXT);

        FinanceGui.drawClippedString(screen.font, g, "当日商品", 18, y + 58, 48, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.dailyCommodityVolume()), 68, y + 58, 65, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font, g, "当日股票", 142, y + 58, 48, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, Long.toString(dashboard.dailyStockVolume()), 192, y + 58, 65, FinanceGui.COL_TEXT);
        FinanceGui.drawClippedString(screen.font, g, "价格指数", 266, y + 58, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, String.format(Locale.ROOT, "%.1f", dashboard.priceIndex()), 312, y + 58, 50,
                dashboard.priceIndex() >= 100.0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);

        FinanceGui.drawClippedString(screen.font, g, "近30日价格指数", 18, y + 80, 90, FinanceGui.COL_TEXT_DIM);
        renderDashboardTrend(g, dashboard.trends(), 18, y + 94, 340, 48);
        FinanceGui.drawClippedString(screen.font, g, "央行最近干预：" + dashboard.centralBankSummary(), 18, y + 148, 340, FinanceGui.COL_WARN);
    }
    public void renderDashboardTrend(GuiGraphics g, List<FinanceMenu.EconomyTrendRow> trends,
                                      int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFFE8E2D3);
        if (trends == null || trends.isEmpty()) {
            FinanceGui.drawClippedString(screen.font, g, "尚无完整日度快照", x + 8, y + 18, width - 16, FinanceGui.COL_TEXT_DIM);
            return;
        }
        double min = trends.stream().mapToDouble(FinanceMenu.EconomyTrendRow::priceIndex).min().orElse(100.0);
        double max = trends.stream().mapToDouble(FinanceMenu.EconomyTrendRow::priceIndex).max().orElse(100.0);
        double span = Math.max(1.0, max - min);
        int count = trends.size();
        for (int index = 0; index < count; index++) {
            double value = trends.get(index).priceIndex();
            int barHeight = Math.max(1, (int) Math.round((value - min) / span * (height - 8)));
            int left = x + index * width / count;
            int right = x + (index + 1) * width / count - 1;
            g.fill(left, y + height - 3 - barHeight, Math.max(left + 1, right), y + height - 3,
                    value >= 100.0 ? FinanceGui.COL_GOOD : FinanceGui.COL_BAD);
        }
        FinanceGui.drawClippedString(screen.font, g, String.format(Locale.ROOT, "%.1f", max), x + 2, y + 2, 45, FinanceGui.COL_TEXT_DIM);
        FinanceGui.drawClippedString(screen.font, g, String.format(Locale.ROOT, "%.1f", min), x + 2, y + height - 11, 45, FinanceGui.COL_TEXT_DIM);
    }


    // ================================================================
    // 管理（管理员专用）
    // ================================================================


    // ---- 标签 2 点击: 订单取消 ----

    public boolean handleOrdersClick(int mx, int my) {
        List<FinanceMenu.OrderRow> orders = screen.menu.getPlayerOrders();
        int rowY = FinanceGui.CONTENT_Y + 16;
        int maxRows = Math.min(orders.size(), Math.max(1, (screen.imageHeight - rowY - 10) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int y = rowY + i * FinanceGui.ROW_HEIGHT;
            if (my >= y && my < y + FinanceGui.ROW_HEIGHT && mx >= 286 && mx < 324) {
                screen.setStatus("已提交吃单请求");
                FinancePacketHandler.CHANNEL.sendToServer(new TakeOrderPacket(orders.get(i).orderId()));
                return true;
            }
            if (orders.get(i).ownedByPlayer() && my >= y && my < y + FinanceGui.ROW_HEIGHT && mx >= 330 && mx < 368) {
                screen.setStatus("已提交取消订单");
                FinancePacketHandler.CHANNEL.sendToServer(new CancelOrderPacket(orders.get(i).orderId()));
                return true;
            }
        }
        return false;
    }

    // ---- 标签 3 点击: 库存 ----

    public boolean handleInventoryClick(int mx, int my) {
        int cardY = FinanceGui.CONTENT_Y;
        int invY = cardY + 44;

        if (my >= invY + 20 && my < invY + 33) {
            int amount = FinanceFormat.parseInventoryAmount(screen, );
            if (mx >= 116 && mx < 148) {
                screen.inventoryQuantityBox.setValue(Integer.toString(Math.max(64, amount - 64)));
                return true;
            }
            if (mx >= 152 && mx < 184) {
                screen.inventoryQuantityBox.setValue(Integer.toString(amount + 64));
                return true;
            }
        }

        // 存入按钮
        if (mx >= 246 && mx < 290 && my >= invY + 20 && my < invY + 33) {
            Commodity c = CommodityRegistry.getCommodity(screen.selectedCommodity);
            if (c != null && c.getItemId() != null) {
                screen.setStatus("已提交存入请求");
                FinancePacketHandler.CHANNEL.sendToServer(
                        new InventoryActionPacket(InventoryActionPacket.ActionType.DEPOSIT, screen.selectedCommodity, FinanceFormat.parseInventoryAmount(screen, )));
            } else {
                screen.setStatus("该商品没有绑定物品");
            }
            return true;
        }

        // 取出按钮
        if (mx >= 296 && mx < 340 && my >= invY + 20 && my < invY + 33) {
            screen.setStatus("已提交取出请求");
            FinancePacketHandler.CHANNEL.sendToServer(
                    new InventoryActionPacket(InventoryActionPacket.ActionType.WITHDRAW, screen.selectedCommodity, FinanceFormat.parseInventoryAmount(screen, )));
            return true;
        }

        // 表格行点击
        int y = invY + 56;
        int maxY = invY + (screen.imageHeight - invY - 4) - FinanceGui.ROW_HEIGHT;
        Map<String, Integer> inv = screen.menu.getPlayerInventory();
        Map<String, Integer> mcInv = screen.menu.getMcInventory();
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(inv.keySet());
        allIds.addAll(mcInv.keySet());

        for (String commodityId : allIds) {
            if (y > maxY) break;
            if (my >= y && my < y + FinanceGui.ROW_HEIGHT && mx >= 8 && mx < screen.imageWidth - 8) {
                screen.selectedCommodity = commodityId;
                // 更新数量输入框为该商品的库存数量
                int stock = inv.getOrDefault(commodityId, 0);
                screen.inventoryQuantityBox.setValue(Integer.toString(Math.max(64, stock)));
                screen.rememberUiState();
                return true;
            }
            y += FinanceGui.ROW_HEIGHT;
        }
        return false;
    }
    public boolean handleAssetClick(int mx, int my) {
        int y = FinanceGui.CONTENT_Y;
        if (my >= y - 2 && my < y + 11) {
            if (mx >= 270 && mx < 328) {
                assetSortMode = 0;
                screen.setStatus("资产明细已按价值排序");
                return true;
            }
            if (mx >= 332 && mx < 390) {
                assetSortMode = 1;
                screen.setStatus("资产明细已按收益排序");
                return true;
            }
        }
        return false;
    }
    public boolean handleAlertClick(int mx, int my) {
        int y = FinanceGui.CONTENT_Y;
        long targetPrice = FinanceFormat.parseLong(screen.alertPriceBox.getValue());
        if (my >= y + 74 && my < y + 87) {
            if (mx >= 10 && mx < 98) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, screen.selectedCommodity, PriceAlertDirection.PREVIOUS_HIGH_BREAKOUT, 0));
                return true;
            }
            if (mx >= 104 && mx < 192) {
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(
                        PriceAlertType.COMMODITY, screen.selectedCommodity, PriceAlertDirection.PREVIOUS_LOW_BREAKDOWN, 0));
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
            if (targetPrice <= 0) {
                screen.setStatus("请输入有效提醒价格");
                return true;
            }
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
        List<FinanceMenu.PriceAlertRow> alerts = screen.menu.getPriceAlerts();
        int maxRows = Math.min(alerts.size(), Math.max(1, (screen.imageHeight - listY - 6) / FinanceGui.ROW_HEIGHT));
        for (int i = 0; i < maxRows; i++) {
            int rowY = listY + i * FinanceGui.ROW_HEIGHT;
            if (my >= rowY - 2 && my < rowY + 11 && mx >= 316 && mx < 358) {
                screen.setStatus("已提交取消提醒");
                FinancePacketHandler.CHANNEL.sendToServer(new PriceAlertPacket(alerts.get(i).alertId()));
                return true;
            }
        }
        return false;
    }
}
