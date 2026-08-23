package finance.client;

import finance.gui.WalletMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WalletScreen extends AbstractContainerScreen<WalletMenu> {
    static final int PANEL_WIDTH = 300;
    static final int PANEL_HEIGHT = 210;
    private static final int BG = 0xFFE7E2D3;
    private static final int BORDER = 0xFF373737;
    private static final int TEXT = 0xFF202020;
    private static final int DIM = 0xFF555555;

    public WalletScreen(WalletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, BG);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component day = Component.translatable("screen.finance.wallet.day", menu.mcDay());
        int dayX = imageWidth - 12 - font.width(day);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(40, dayX - 20)),
                12, 10, TEXT, false);
        graphics.drawString(font, day, dayX, 10, DIM, false);
        graphics.drawString(font, Component.translatable("screen.finance.wallet.balance", menu.balance()), 12, 31, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.finance.wallet.frozen", menu.frozenBalance()), 12, 45, DIM, false);
        graphics.drawString(font, Component.translatable("screen.finance.wallet.total", menu.totalAsset()), 12, 59, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.finance.wallet.recent"), 12, 82, TEXT, false);
        int y = 96;
        for (WalletMenu.WalletTransaction row : menu.transactions()) {
            String objectName = row.objectName().isBlank() ? "-" : row.objectName();
            String line = row.type() + "  " + objectName + "  " + row.amount();
            graphics.drawString(font, font.plainSubstrByWidth(line, 274), 14, y, DIM, false);
            y += 10;
        }
        graphics.drawString(font, font.plainSubstrByWidth(
                Component.translatable("screen.finance.wallet.transfer_hint").getString(), 276), 12, 198, DIM, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
