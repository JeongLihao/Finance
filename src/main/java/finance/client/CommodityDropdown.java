package finance.client;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.market.NpcMarketMaker;
import finance.network.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** 行情页和交易页共用的商品分类下拉菜单。原在 MarketTabs。 */
final class CommodityDropdown {
    private final FinanceScreen screen;

    /** 下拉框在内容区里的位置和大小（内容区坐标，画和点都用这一份）。 */
    private static final int DD_X = 82, DD_W = 200, DD_H = 120;

    /** 下拉框里的分类标题和商品行，画的时候登记（内容区坐标）。 */
    private final ClickRegions clicks = new ClickRegions();

    CommodityDropdown(FinanceScreen screen) {
        this.screen = screen;
    }

    private int ddLocalY() {
        return screen.currentTab == FinanceScreen.TAB_MARKET
                ? FinanceGui.MARKET_TRADE_Y + 14
                : FinanceGui.CONTENT_Y + 14;
    }

    /** 渲染分类下拉面板（在行情/交易标签中使用） */
    public void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.categorizedCacheDirty) screen.rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap = (screen.currentTab == FinanceScreen.TAB_TRADE)
                ? screen.inventoryCategorizedCommodities
                : screen.categorizedCommodities;

        int localX = DD_X;
        int localY = ddLocalY();
        int ddX = screen.leftPos() + localX;
        int ddY = screen.topPos() + localY;
        int ddW = DD_W;
        int ddH = DD_H;
        clicks.clear();

        // 背景
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, FinanceGui.COL_DROPDOWN_BG);
        FinanceGui.drawSimpleBorder(g, ddX, ddY, ddW, ddH, FinanceGui.COL_PANEL_BORDER);
        // 裁剪框不受 pose 平移影响，要手动扣掉内容上移量
        int shift = FinanceGui.CONTENT_SHIFT;
        g.enableScissor(
                screen.toScreen(ddX + 2),
                screen.toScreen(ddY + 2 - shift),
                screen.toScreen(ddX + ddW - 2),
                screen.toScreen(ddY + ddH - 2 - shift));

        int localMx = mouseX - ddX;
        int localMy = mouseY - ddY;
        int y = 2 - screen.dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题：点击展开 / 折叠
            if (y + 12 > 0 && y < maxY) {
                clicks.add(
                        localX,
                        localY + y,
                        ddW,
                        12,
                        () -> screen.expandedCategory = (screen.expandedCategory == cat) ? null : cat);
                g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 12, FinanceGui.COL_CATEGORY);
                g.drawString(
                        screen.font(), "▸ " + cat.getDisplayName(), ddX + 6, ddY + y + 2, FinanceGui.COL_TEXT, false);
            }
            y += 14;

            // 展开的分类显示其商品
            if (screen.expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        clicks.add(localX + 1, localY + y, ddW - 1, 14, () -> select(c));
                        boolean hovered = localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14;
                        if (hovered) {
                            g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 14, FinanceGui.COL_DROPDOWN_HOVER);
                        }
                        FinanceGui.renderItemIcon(g, c, ddX + 8, ddY + y + 1);
                        FinanceGui.drawClippedString(
                                screen.font(), g, c.getDisplayName(), ddX + 26, ddY + y + 3, 100, FinanceGui.COL_TEXT);
                        FinanceGui.drawClippedString(
                                screen.font(),
                                g,
                                Long.toString(c.getBasePrice()),
                                ddX + 140,
                                ddY + y + 3,
                                50,
                                FinanceGui.COL_GOOD);
                    }
                    y += 14;
                }
            }
        }
        g.disableScissor();

        // 滚动条
        int totalHeight = calcDropdownTotalHeight();
        if (totalHeight > ddH) {
            int barH = Math.max(10, ddH * ddH / totalHeight);
            int barY = (int) ((long) screen.dropdownScroll * (ddH - barH) / (totalHeight - ddH));
            g.fill(ddX + ddW - 5, ddY + barY, ddX + ddW - 2, ddY + barY + barH, FinanceGui.COL_PANEL_BORDER);
        }
    }

    public int calcDropdownTotalHeight() {
        Map<CommodityCategory, List<Commodity>> activeMap = (screen.currentTab == FinanceScreen.TAB_TRADE)
                ? screen.inventoryCategorizedCommodities
                : screen.categorizedCommodities;
        int h = 0;
        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;
            h += 14; // 分类标题
            if (screen.expandedCategory == cat) {
                h += items.size() * 14;
            }
        }
        return h;
    }

    /** 点在下拉框外返回 false（由上层关闭下拉）；框内一律吃掉这次点击。 */
    public boolean handleDropdownClick(int mx, int my) {
        int ddY = ddLocalY();
        if (mx < DD_X || mx >= DD_X + DD_W || my < ddY || my >= ddY + DD_H) {
            return false; // 点击外部，由上层关闭
        }
        clicks.click(mx, my);
        return true;
    }

    private void select(Commodity c) {
        screen.selectedCommodity = c.getId();
        screen.rememberUiState();
        if (screen.currentTab == FinanceScreen.TAB_TRADE) {
            finance.market.MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(c.getId());
            if (mp != null) screen.priceBox.setValue(Long.toString(mp.getMidPrice()));
        }
        screen.dropdownOpen = false;
        screen.expandedCategory = null;
        screen.refreshSelectedRow();
    }
}
