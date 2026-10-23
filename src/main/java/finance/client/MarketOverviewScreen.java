package finance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import finance.gui.MarketOverviewMenu;
import finance.gui.MarketSnapshot;
import finance.network.FinancePacketHandler;
import finance.network.TradeActionPacket;
import finance.util.FormatUtil;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MarketOverviewScreen extends AbstractContainerScreen<MarketOverviewMenu> {

    static final int PANEL_WIDTH = 310;
    static final int PANEL_HEIGHT = 240;
    private static final int ROW_HEIGHT = 14;
    private int selectedRow;
    private int rowOffset;
    private EditBox quantity;
    private String status = "";

    public MarketOverviewScreen(MarketOverviewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        quantity = new EditBox(
                font, leftPos + 10, topPos + 218, 58, 18, Component.translatable("screen.finance.warehouse.amount"));
        quantity.setValue("1");
        addRenderableWidget(quantity);
        addRenderableWidget(Button.builder(Component.translatable("finance.market.buy"), b -> trade(true))
                .bounds(leftPos + 74, topPos + 218, 64, 18)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("finance.market.sell"), b -> trade(false))
                .bounds(leftPos + 142, topPos + 218, 64, 18)
                .build());
    }

    private void trade(boolean buy) {
        List<MarketSnapshot> rows = menu.getSnapshots();
        if (rows.isEmpty() || selectedRow < 0 || selectedRow >= rows.size()) {
            status = Component.translatable("finance.market.select_first").getString();
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(quantity.getValue().trim());
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount <= 0) {
            status = Component.translatable("finance.market.invalid_quantity").getString();
            return;
        }
        FinancePacketHandler.CHANNEL.sendToServer(new TradeActionPacket(
                buy ? TradeActionPacket.ActionType.INTL_BUY : TradeActionPacket.ActionType.INTL_SELL,
                rows.get(selectedRow).commodityId(),
                0,
                amount));
        status = Component.translatable("finance.market.submitted").getString();
    }

    public void setGuiStatus(String message) {
        status = message == null ? "" : message;
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
        drawHeader(graphics, "commodity", 10, headerY, 44);
        drawHeader(graphics, "price", 58, headerY, 34);
        drawHeader(graphics, "bid", 96, headerY, 34);
        drawHeader(graphics, "ask", 134, headerY, 36);
        drawHeader(graphics, "change", 174, headerY, 36);
        drawHeader(graphics, "volume", 214, headerY, 38);
        drawHeader(graphics, "stock", 256, headerY, 44);

        List<MarketSnapshot> snapshots = menu.getSnapshots();
        if (snapshots.isEmpty()) {
            graphics.drawString(
                    font, Component.translatable("screen.finance.market_overview.empty"), 10, 52, 0xB9C0C8, false);
            return;
        }

        int rowY = 42;
        int maxRows = Math.min(Math.max(0, snapshots.size() - rowOffset), 9);
        for (int visibleIndex = 0; visibleIndex < maxRows; visibleIndex++) {
            int i = rowOffset + visibleIndex;
            MarketSnapshot snapshot = snapshots.get(i);
            int y = rowY + visibleIndex * ROW_HEIGHT;
            int bg = (i % 2 == 0) ? 0x221C252D : 0x111C252D;
            if (i == selectedRow) bg = 0x553A6C8E;
            graphics.fill(8, y - 2, imageWidth - 8, y + ROW_HEIGHT - 2, bg);

            drawClipped(graphics, snapshot.commodityId(), 10, y, 44, 0xE6E9ED);
            drawClipped(graphics, Long.toString(snapshot.midPrice()), 58, y, 34, 0xE6E9ED);
            drawClipped(graphics, Long.toString(snapshot.bidPrice()), 96, y, 34, 0xB7E4C7);
            drawClipped(graphics, Long.toString(snapshot.askPrice()), 134, y, 36, 0xFFD6A5);
            drawClipped(
                    graphics,
                    FormatUtil.formatPercent(snapshot.dayChange()),
                    174,
                    y,
                    36,
                    changeColor(snapshot.dayChange()));
            drawClipped(graphics, Integer.toString(snapshot.dayVolume()), 214, y, 38, 0xC8CED6);
            drawClipped(graphics, Integer.toString(snapshot.marketStock()), 256, y, 44, 0xC8CED6);
        }

        var opportunity = menu.opportunity();
        int infoY = 174;
        graphics.drawString(
                font, Component.translatable("finance.market.today_opportunity"), 10, infoY, 0xF5D76E, false);
        drawClipped(
                graphics,
                Component.translatable("finance.market.shortage", opportunity.shortageId(), opportunity.shortageStock())
                        .getString(),
                10,
                infoY + 12,
                290,
                0xC8CED6);
        drawClipped(
                graphics,
                Component.translatable(
                                "finance.market.contract", opportunity.contractId(), opportunity.contractReward())
                        .getString(),
                10,
                infoY + 24,
                290,
                0xC8CED6);
        drawClipped(
                graphics, Component.translatable("finance.market.advanced_hint").getString(), 10, 207, 290, 0x8F99A4);
        if (!status.isBlank())
            graphics.drawString(font, font.plainSubstrByWidth(status, 94), 212, 223, 0xF5D76E, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (button == 0
                && x >= 8
                && x <= imageWidth - 8
                && y >= 40
                && y < 40 + Math.min(Math.max(0, menu.getSnapshots().size() - rowOffset), 9) * ROW_HEIGHT) {
            selectedRow =
                    Math.min(menu.getSnapshots().size() - 1, rowOffset + Math.max(0, (int) ((y - 40) / ROW_HEIGHT)));
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= 8 && x <= imageWidth - 8 && y >= 40 && y < 168 && delta != 0) {
            int direction = delta > 0 ? -1 : 1;
            rowOffset = Math.max(0, Math.min(Math.max(0, menu.getSnapshots().size() - 9), rowOffset + direction));
            selectedRow = Math.max(
                    rowOffset,
                    Math.min(selectedRow, Math.min(menu.getSnapshots().size() - 1, rowOffset + 8)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphics graphics, String column, int x, int y, int width) {
        drawClipped(
                graphics,
                Component.translatable("screen.finance.market_overview.col." + column)
                        .getString(),
                x,
                y,
                width,
                0x9EA8B3);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ScreenInput.handleTextBoxKey(getFocused(), keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawClipped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        if (width <= 0) return;
        graphics.drawString(font, FinanceGui.clipText(font, text, width), x, y, color, false);
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
