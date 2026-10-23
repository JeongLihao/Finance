package finance.client;

import finance.factory.FactoryRecipe;
import finance.factory.MachineCore;
import finance.gui.FactoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 加工机界面：原料 → 进度箭头 → 成品，燃料火焰；顶上写归属，右边选“要做什么”和升档。 */
public final class FactoryScreen extends CompanyMachineScreen<FactoryMenu> {
    private static final int ARROW_X = 70, ARROW_Y = 40, ARROW_W = 24, ARROW_H = 17;
    private static final int RIGHT_X = 128;

    private Button recipeButton;

    public FactoryScreen(FactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FactoryMenu.INVENTORY_Y);
    }

    @Override
    protected void init() {
        super.init();
        recipeButton = addRenderableWidget(Button.builder(recipeLabel(), b -> cycleRecipe())
                .bounds(leftPos + RIGHT_X, topPos + 30, 42, 16)
                .build());
        addUpgradeButton(RIGHT_X, 56);
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
                : new ItemStack(FactoryRecipe.ALL.get(selected).output()).getHoverName();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (recipeButton != null) recipeButton.setMessage(recipeLabel());
    }

    @Override
    protected void renderMachine(GuiGraphics g) {
        int ax = leftPos + ARROW_X, ay = topPos + ARROW_Y;
        g.fill(ax, ay, ax + ARROW_W, ay + ARROW_H, 0xFF6B6B6B);
        int fill = Math.round(menu.progressFraction() * ARROW_W);
        if (fill > 0) g.fill(ax, ay, ax + fill, ay + ARROW_H, 0xFFFF8C1A);
        // 燃料火焰：燃料槽上方一小条，剩余燃烧时间越多越高
        int fx = leftPos + 54, fy = topPos + 56;
        g.fill(fx, fy, fx + 6, fy + 16, 0xFF6B6B6B);
        int flame = Math.round(menu.fuelFraction() * 16);
        if (flame > 0) g.fill(fx, fy + 16 - flame, fx + 6, fy + 16, 0xFFFFC400);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        line(g, ownerLine(), 8, 17, 160, DIM);
        g.drawString(font, Component.translatable("screen.finance.factory.make"), RIGHT_X, 21, DIM, false);
        g.drawString(font, tierLabel(), RIGHT_X, 47, DIM, false);
        boolean working = menu.status() == MachineCore.WORKING;
        String status = Component.translatable(statusKey()).getString();
        if (working) status += " " + Math.round(menu.progressFraction() * 100) + "%";
        line(g, status, 8, 77, 160, working ? GOOD : WARN);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
    }

    private String statusKey() {
        return switch (menu.status()) {
            case MachineCore.WORKING -> "screen.finance.factory.working";
            case MachineCore.OUTPUT_FULL -> "screen.finance.factory.output_full";
            case MachineCore.NO_FUEL -> "screen.finance.factory.no_fuel";
            default -> "screen.finance.factory.idle";
        };
    }
}
