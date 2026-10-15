package finance.client;

import finance.gui.FactoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 工厂方块 Screen —— 仿熔炉布局：输入槽 → 进度箭头 → 输出槽。
 */
public final class FactoryScreen extends AbstractContainerScreen<FactoryMenu> {
    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 35;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 17;

    public FactoryScreen(FactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 面板：深色外框＋浅色内底（沿用模组风格）。
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);

        // 进度箭头：底色深灰，填充按进度。
        int ax = leftPos + ARROW_X;
        int ay = topPos + ARROW_Y;
        graphics.fill(ax, ay, ax + ARROW_W, ay + ARROW_H, 0xFF6B6B6B);
        int fillW = Math.round(menu.progressFraction() * ARROW_W);
        if (fillW > 0) {
            graphics.fill(ax, ay, ax + fillW, ay + ARROW_H, 0xFFFF8C1A);
        }
        // 箭头框线。
        graphics.fill(ax - 1, ay - 1, ax + ARROW_W + 1, ay, 0xFF373737);
        graphics.fill(ax - 1, ay + ARROW_H, ax + ARROW_W + 1, ay + ARROW_H + 1, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title.getString(), 8, 6, 0xFF202020, false);
        graphics.drawString(font, Component.translatable("screen.finance.factory.input").getString(),
                56, 24, 0xFF555555, false);
        graphics.drawString(font, Component.translatable("screen.finance.factory.output").getString(),
                116, 24, 0xFF555555, false);
        String statusKey = switch (menu.status()) {
            case 0 -> "screen.finance.factory.working";
            case 2 -> "screen.finance.factory.output_full";
            default -> "screen.finance.factory.idle";
        };
        String status = Component.translatable(statusKey).getString();
        int progressPct = Math.round(menu.progressFraction() * 100);
        if (menu.status() == 0) status += " " + progressPct + "%";
        graphics.drawString(font, status, 8, 60, 0xFF555555, false);
        super.renderLabels(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
