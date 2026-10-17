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
import net.minecraft.client.gui.Font;
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

/** 绘制基元与主题常量：从 FinanceScreen 抽取的无状态 GUI 工具类。
 * 颜色 / 布局常量集中在此，绘制方法均为静态，可被各页签复用。 */

public final class FinanceGui {
    private FinanceGui() {}

    public static final int PANEL_WIDTH = 400;
    /** 面板高 232：原版“自动”界面尺寸下可用高度常为 240，留 8 像素边距后不必缩放，像素字体保持清晰。 */
    public static final int PANEL_HEIGHT = 232;
    public static final int ROW_HEIGHT = 16;
    public static final int TAB_X = 10;
    public static final int TAB_Y = 16;
    public static final int TAB_H = 12;
    /** 标题栏与页签栏的总高度（分隔线所在行）。 */
    public static final int HEADER_H = 28;
    /** 页签内容按原 250 高布局编写；绘制时整体上移这么多，坐标仍沿用 CONTENT_Y 等常量。 */
    public static final int CONTENT_SHIFT = 14;
    /** 页签内容坐标系下的可用高度（相当于原来的面板高）。 */
    public static final int LAYOUT_HEIGHT = PANEL_HEIGHT + CONTENT_SHIFT;
    public static final int CONTENT_Y = 46;
    public static final int MARKET_TRADE_Y = 166;
    public static final int STOCK_TRADE_Y = 116;
    public static final int COL_BG          = 0xFFE7E2D3;
    public static final int COL_TAB_BG      = 0xFFD6D0BE;
    public static final int COL_ACCENT      = 0xFF3F3F3F;
    public static final int COL_GOOD        = 0xFF2D7D32;
    public static final int COL_BAD         = 0xFF9A2F2F;
    public static final int COL_WARN        = 0xFF8A6500;
    public static final int COL_TEXT        = 0xFF202020;
    public static final int COL_TEXT_DIM    = 0xFF555555;
    public static final int COL_ROW_EVEN    = 0xFFF1ECDD;
    public static final int COL_ROW_ODD     = 0xFFE6E0CF;
    public static final int COL_ROW_SELECT  = 0xFFD5E3C7;
    public static final int COL_PANEL_BORDER = 0xFF373737;
    public static final int COL_BUTTON_BG   = 0xFFD0CAB8;
    public static final int COL_DROPDOWN_BG = 0xFFF5F0E1;
    public static final int COL_DROPDOWN_HOVER = 0xFFD5E3C7;
    public static final int COL_CATEGORY    = 0xFFB8B0A0;


    /** 渲染物品图标（约 10x10，避免默认 16x16 压住表格文字） */
    public static void renderItemIcon(GuiGraphics g, Commodity commodity, int x, int y) {
        if (commodity == null) return;
        String itemId = commodity.getItemId();
        if (itemId == null) return;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        FinanceGui.renderItemIcon(g, item, x, y);
    }


    /** 所有紧凑表格中的物品图标统一缩放为约 10×10。 */
    public static void renderItemIcon(GuiGraphics g, Item item, int x, int y) {
        if (item == null) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.625F, 0.625F, 1.0F);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popPose();
    }

    public static void drawSimpleBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawSimpleSeparator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, COL_PANEL_BORDER);
    }

    public static void drawSectionTitle(Font font, GuiGraphics g, String label, int x, int y) {
        g.drawString(font, label, x, y, COL_TEXT, false);
    }

    public static void drawHeader(Font font, GuiGraphics g, String label, int x, int y) {
        g.drawString(font, label, x, y, COL_TEXT, false);
    }

    public static void drawButton(Font font, GuiGraphics g, String label, int x, int y, int w, boolean active) {
        int borderColor = active ? 0xFFFFFFFF : COL_PANEL_BORDER;
        int bgColor = active ? 0xFFD6D6D6 : COL_BUTTON_BG;
        int textColor = active ? COL_TEXT : COL_TEXT_DIM;
        g.fill(x, y, x + w, y + 13, bgColor);
        FinanceGui.drawSimpleBorder(g, x, y, w, 13, borderColor);
        FinanceGui.drawCenteredClippedString(font, g, label, x, y + 3, w, textColor);
    }

    public static void drawFilledButton(Font font, GuiGraphics g, String label, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 13, color);
        FinanceGui.drawSimpleBorder(g, x, y, w, 13, COL_PANEL_BORDER);
        FinanceGui.drawCenteredClippedString(font, g, label, x, y + 3, w, 0xFFFFFFFF);
    }

    public static void drawClippedString(Font font, GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        g.drawString(font, FinanceGui.clipText(font, text, maxWidth), x, y, color, false);
    }

    public static void drawCenteredClippedString(Font font, GuiGraphics g, String text, int x, int y, int width, int color) {
        String clipped = FinanceGui.clipText(font, text, width - 6);
        int textX = x + Math.max(3, (width - font.width(clipped)) / 2);
        g.drawString(font, clipped, textX, y, color, false);
    }

    public static String clipText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        if (maxWidth <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }

    public static int changeColor(double change) {
        if (change > 0) return COL_GOOD;
        if (change < 0) return COL_BAD;
        return COL_TEXT_DIM;
    }

    public static void drawTrendGraph(GuiGraphics g, List<Long> values, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, 0x55FFFFFF);
        FinanceGui.drawSimpleBorder(g, x, y, w, h, 0x66555555);
        if (values == null || values.size() < 2 || w <= 2 || h <= 2) {
            return;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long value : values) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        if (min == Long.MAX_VALUE || max <= min) {
            int mid = y + h / 2;
            g.fill(x + 2, mid, x + w - 2, mid + 1, color);
            return;
        }

        int plotX = x + 2;
        int plotY = y + 2;
        int plotW = Math.max(1, w - 4);
        int plotH = Math.max(1, h - 4);
        int prevX = plotX;
        int prevY = FinanceGui.scaleGraphY(values.get(0), min, max, plotY, plotH);
        for (int i = 1; i < values.size(); i++) {
            int px = plotX + Math.round((float) i / (values.size() - 1) * plotW);
            int py = FinanceGui.scaleGraphY(values.get(i), min, max, plotY, plotH);
            FinanceGui.drawPixelLine(g, prevX, prevY, px, py, color);
            prevX = px;
            prevY = py;
        }
    }

    public static int scaleGraphY(long value, long min, long max, int y, int h) {
        double ratio = (double) (value - min) / Math.max(1.0, max - min);
        return y + h - (int) Math.round(ratio * h);
    }

    public static void drawPixelLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
