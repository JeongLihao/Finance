package finance.client;

import finance.block.entity.CompanyMachineBlockEntity;
import finance.gui.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** 公司机器界面共用的部分：米色底和槽位框、归属一行、升档按钮、截断文字、悬停提示。 */
abstract class CompanyMachineScreen<M extends AbstractContainerMenu & MachineMenu> extends AbstractContainerScreen<M> {
    static final int TEXT = 0xFF404040, DIM = 0xFF555555, GOOD = 0xFF2E7D32, WARN = 0xFFB0581A;

    private Button upgradeButton;

    /** inventoryY：玩家背包第一行槽位的 y。 */
    protected CompanyMachineScreen(M menu, Inventory inventory, Component title, int inventoryY) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = inventoryY + 82;
        inventoryLabelY = inventoryY - 11;
    }

    /** 在界面相对坐标 (x, y) 放升档按钮。 */
    protected void addUpgradeButton(int x, int y) {
        upgradeButton = addRenderableWidget(MachineUpgradeButton.create(
                minecraft, menu.containerId, leftPos + x, topPos + y, 42, menu.tier(), menu.link()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (upgradeButton != null) MachineUpgradeButton.refresh(upgradeButton, menu.tier(), menu.link());
    }

    @Override
    protected final void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);
        renderMachine(g);
        for (Slot slot : menu.slots) {
            int sx = leftPos + slot.x - 1, sy = topPos + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
            g.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
            g.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
        }
    }

    /** 背景上机器自己的图形（进度条、火焰等），画在槽位框下面。屏幕坐标。 */
    protected void renderMachine(GuiGraphics g) {}

    /** “属于哪家公司、连没连仓库”那一行。 */
    protected Component ownerLine() {
        String company = menu.blockEntity().companyName();
        return switch (menu.link()) {
            case CompanyMachineBlockEntity.LINKED -> Component.translatable(
                    "screen.finance.factory.owner.linked", company);
            case CompanyMachineBlockEntity.COMPANY -> Component.translatable(
                    "screen.finance.factory.owner.company", company);
            default -> Component.translatable("screen.finance.factory.owner.personal");
        };
    }

    protected Component tierLabel() {
        return Component.translatable("screen.finance.machine.tier", menu.tier());
    }

    /** 在界面相对坐标画一行字，超过 width 用省略号截断。 */
    protected void line(GuiGraphics g, Component text, int x, int y, int width, int color) {
        line(g, text.getString(), x, y, width, color);
    }

    protected void line(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, FinanceGui.clipText(font, text, width), x, y, color, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
