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

    CommodityDropdown(FinanceScreen screen) {
        this.screen = screen;
    }

    /** 渲染分类下拉面板（在行情/交易标签中使用） */
    public void renderDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (screen.categorizedCacheDirty) screen.rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap = (screen.currentTab == FinanceScreen.TAB_TRADE)
                ? screen.inventoryCategorizedCommodities
                : screen.categorizedCommodities;

        int localX = 82;
        int localY = (screen.currentTab == FinanceScreen.TAB_MARKET)
                ? FinanceGui.MARKET_TRADE_Y + 14
                : FinanceGui.CONTENT_Y + 14;
        int ddX = screen.leftPos() + localX;
        int ddY = screen.topPos() + localY;
        int ddW = 200;
        int ddH = 120;

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

            // 分类标题
            if (y + 12 > 0 && y < maxY) {
                g.fill(ddX + 2, ddY + y, ddX + ddW - 2, ddY + y + 12, FinanceGui.COL_CATEGORY);
                g.drawString(
                        screen.font(), "▸ " + cat.getDisplayName(), ddX + 6, ddY + y + 2, FinanceGui.COL_TEXT, false);
            }
            y += 14;

            // 展开的分类显示其商品
            if (screen.expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
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

    public boolean handleDropdownClick(int mx, int my) {
        int ddX = 82;
        int ddY = (screen.currentTab == FinanceScreen.TAB_MARKET)
                ? FinanceGui.MARKET_TRADE_Y + 14
                : FinanceGui.CONTENT_Y + 14;
        int ddW = 200;
        int ddH = 120;

        if (mx < ddX || mx >= ddX + ddW || my < ddY || my >= ddY + ddH) {
            return false; // 点击外部，由上层关闭
        }

        if (screen.categorizedCacheDirty) screen.rebuildCategorizedCache();

        // 交易标签使用库存商品缓存，行情标签使用全部商品缓存
        Map<CommodityCategory, List<Commodity>> activeMap = (screen.currentTab == FinanceScreen.TAB_TRADE)
                ? screen.inventoryCategorizedCommodities
                : screen.categorizedCommodities;

        int localMx = mx - ddX;
        int localMy = my - ddY;
        int y = 2 - screen.dropdownScroll;
        int maxY = ddH - 2;

        for (CommodityCategory cat : CommodityCategory.values()) {
            List<Commodity> items = activeMap.get(cat);
            if (items == null || items.isEmpty()) continue;

            // 分类标题点击 → 展开/折叠（与渲染完全对称：12px 高）
            if (y + 12 > 0 && y < maxY) {
                if (localMy >= y && localMy < y + 12) {
                    screen.expandedCategory = (screen.expandedCategory == cat) ? null : cat;
                    return true;
                }
            }
            y += 14;

            if (screen.expandedCategory == cat) {
                for (Commodity c : items) {
                    if (y + 14 > 0 && y < maxY) {
                        if (localMx > 0 && localMx < ddW && localMy >= y && localMy < y + 14) {
                            screen.selectedCommodity = c.getId();
                            screen.rememberUiState();
                            if (screen.currentTab == FinanceScreen.TAB_TRADE) {
                                finance.market.MarketPrice mp =
                                        NpcMarketMaker.getAllMarketPrices().get(c.getId());
                                if (mp != null) screen.priceBox.setValue(Long.toString(mp.getMidPrice()));
                            }
                            screen.dropdownOpen = false;
                            screen.expandedCategory = null;
                            screen.refreshSelectedRow();
                            return true;
                        }
                    }
                    y += 14;
                }
            }
        }
        return true; // 点击在下拉区域内但未命中项目
    }
}
