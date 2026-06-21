package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.gui.MarketOverviewMenu;
import finance.gui.MarketSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import finance.util.FormatUtil;

import java.util.List;

public class MarketOverviewScreen extends AbstractContainerScreen<MarketOverviewMenu> {

    private static final int PANEL_WIDTH = 310;
    private static final int PANEL_HEIGHT = 186;
    private static final int ROW_HEIGHT = 14;

    public MarketOverviewScreen(MarketOverviewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101418);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 22, 0xF0202A33);
        graphics.fill(leftPos + 8, topPos + 36, leftPos + imageWidth - 8, topPos + 37, 0xFF56616C);
        RenderSystem.disableBlend();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // 只在面板区域绘制半透明遮罩，不遮挡游戏画面
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xB0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0xEDEFF2, false);

        int headerY = 26;
        drawHeader(graphics, "商品", 10, headerY);
        drawHeader(graphics, "价格", 58, headerY);
        drawHeader(graphics, "买入", 96, headerY);
        drawHeader(graphics, "卖出", 134, headerY);
        drawHeader(graphics, "涨跌", 174, headerY);
        drawHeader(graphics, "成交", 214, headerY);
        drawHeader(graphics, "库存", 256, headerY);

        List<MarketSnapshot> snapshots = menu.getSnapshots();
        if (snapshots.isEmpty()) {
            graphics.drawString(font, "暂无国际市场数据", 10, 52, 0xB9C0C8, false);
            return;
        }

        int rowY = 42;
        int maxRows = Math.min(snapshots.size(), 9);
        for (int i = 0; i < maxRows; i++) {
            MarketSnapshot snapshot = snapshots.get(i);
            int y = rowY + i * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0x221C252D : 0x111C252D;
            graphics.fill(8, y - 2, imageWidth - 8, y + ROW_HEIGHT - 2, bg);

            graphics.drawString(font, snapshot.commodityId(), 10, y, 0xE6E9ED, false);
            graphics.drawString(font, Long.toString(snapshot.midPrice()), 58, y, 0xE6E9ED, false);
            graphics.drawString(font, Long.toString(snapshot.bidPrice()), 96, y, 0xB7E4C7, false);
            graphics.drawString(font, Long.toString(snapshot.askPrice()), 134, y, 0xFFD6A5, false);
            graphics.drawString(font, formatChange(snapshot.dayChange()), 174, y, changeColor(snapshot.dayChange()), false);
            graphics.drawString(font, Integer.toString(snapshot.dayVolume()), 214, y, 0xC8CED6, false);
            graphics.drawString(font, Integer.toString(snapshot.marketStock()), 256, y, 0xC8CED6, false);
        }

        graphics.drawString(font, "使用 /market international buy|sell 交易", 10, imageHeight - 16, 0x8F99A4, false);
    }

    private void drawHeader(GuiGraphics graphics, String label, int x, int y) {
        graphics.drawString(font, label, x, y, 0x9EA8B3, false);
    }

    private static String formatChange(double change) {
        return FormatUtil.formatPercent(change);
    }

    private static int changeColor(double change) {
        if (change > 0) {
            return 0x7BE495;
        }
        if (change < 0) {
            return 0xFF8A8A;
        }
        return 0xC8CED6;
    }
}
