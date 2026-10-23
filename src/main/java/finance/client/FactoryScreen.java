package finance.client;

import finance.block.entity.FactoryBlockEntity;
import finance.factory.FactoryRecipe;
import finance.factory.MachineCore;
import finance.gui.FactoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 加工机界面：原料 → 进度箭头 → 成品，燃料火焰；顶上写归属，右边选“要做什么”。 */
public final class FactoryScreen extends AbstractContainerScreen<FactoryMenu> {
    private static final int ARROW_X = 70, ARROW_Y = 40, ARROW_W = 24, ARROW_H = 17;
    private static final int RIGHT_X = 128;

    private Button recipeButton, upgradeButton;

    public FactoryScreen(FactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = FactoryMenu.INVENTORY_Y + 82;
        inventoryLabelY = FactoryMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        recipeButton = addRenderableWidget(Button.builder(recipeLabel(), b -> cycleRecipe())
                .bounds(leftPos + RIGHT_X, topPos + 30, 42, 16)
                .build());
        upgradeButton = addRenderableWidget(MachineUpgradeButton.create(
                minecraft, menu.containerId, leftPos + RIGHT_X, topPos + 56, 42, menu.tier(), menu.link()));
    }

    /** 下一个配方；最后一个之后回到“不指定”。 */
    private void cycleRecipe() {
        int next = menu.selectedRecipe() + 1;
        int id = next >= FactoryRecipe.ALL.size() ? FactoryMenu.NO_RECIPE : next;
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private Component recipeLabel() {
        int selected = menu.selectedRecipe();
        return selected < 0
                ? Component.translatable("screen.finance.factory.recipe.any")
                : new net.minecraft.world.item.ItemStack(
                                FactoryRecipe.ALL.get(selected).output())
                        .getHoverName();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (recipeButton != null) recipeButton.setMessage(recipeLabel());
        if (upgradeButton != null) MachineUpgradeButton.refresh(upgradeButton, menu.tier(), menu.link());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);
        int ax = leftPos + ARROW_X, ay = topPos + ARROW_Y;
        g.fill(ax, ay, ax + ARROW_W, ay + ARROW_H, 0xFF6B6B6B);
        int fill = Math.round(menu.progressFraction() * ARROW_W);
        if (fill > 0) g.fill(ax, ay, ax + fill, ay + ARROW_H, 0xFFFF8C1A);
        // 燃料火焰：燃料槽上方一小条，剩余燃烧时间越多越高
        int fx = leftPos + 54, fy = topPos + 56;
        g.fill(fx, fy, fx + 6, fy + 16, 0xFF6B6B6B);
        int flame = Math.round(menu.fuelFraction() * 16);
        if (flame > 0) g.fill(fx, fy + 16 - flame, fx + 6, fy + 16, 0xFFFFC400);
        for (Slot slot : menu.slots) {
            int sx = leftPos + slot.x - 1, sy = topPos + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
            g.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
            g.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFF404040, false);
        g.drawString(font, clip(ownerLine(), 160), 8, 17, 0xFF555555, false);
        g.drawString(font, Component.translatable("screen.finance.factory.make"), RIGHT_X, 21, 0xFF555555, false);
        g.drawString(
                font,
                Component.translatable("screen.finance.machine.tier", menu.tier()),
                RIGHT_X,
                47,
                0xFF555555,
                false);
        String status = Component.translatable(statusKey()).getString();
        if (menu.status() == MachineCore.WORKING) status += " " + Math.round(menu.progressFraction() * 100) + "%";
        g.drawString(font, clip(status, 160), 8, 77, statusColor(), false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF404040, false);
    }

    private Component ownerLine() {
        String company = menu.blockEntity().companyName();
        return switch (menu.link()) {
            case FactoryBlockEntity.LINKED -> Component.translatable("screen.finance.factory.owner.linked", company);
            case FactoryBlockEntity.COMPANY -> Component.translatable("screen.finance.factory.owner.company", company);
            default -> Component.translatable("screen.finance.factory.owner.personal");
        };
    }

    private String statusKey() {
        return switch (menu.status()) {
            case MachineCore.WORKING -> "screen.finance.factory.working";
            case MachineCore.OUTPUT_FULL -> "screen.finance.factory.output_full";
            case MachineCore.NO_FUEL -> "screen.finance.factory.no_fuel";
            default -> "screen.finance.factory.idle";
        };
    }

    private int statusColor() {
        return menu.status() == MachineCore.WORKING ? 0xFF2E7D32 : 0xFFB0581A;
    }

    private String clip(Component text, int width) {
        return clip(text.getString(), width);
    }

    private String clip(String s, int width) {
        while (font.width(s) > width && s.length() > 1) s = s.substring(0, s.length() - 2) + "…";
        return s;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
