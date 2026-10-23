package finance.client;

import finance.block.entity.CompanyMachineBlockEntity;
import finance.block.entity.HarvesterBlockEntity;
import finance.factory.HarvestMode;
import finance.gui.HarvesterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 采集机界面：归属、在干什么、收获仓；采矿机多一个燃料槽和挖到第几层。 */
public final class HarvesterScreen extends AbstractContainerScreen<HarvesterMenu> {

    private Button upgradeButton;

    public HarvesterScreen(HarvesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = HarvesterMenu.INVENTORY_Y + 82;
        inventoryLabelY = HarvesterMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        upgradeButton = addRenderableWidget(MachineUpgradeButton.create(
                minecraft, menu.containerId, leftPos + 128, topPos + 2, 42, menu.tier(), menu.link()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (upgradeButton != null) MachineUpgradeButton.refresh(upgradeButton, menu.tier(), menu.link());
    }

    private HarvestMode mode() {
        return menu.blockEntity().mode();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFE7E2D3);
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
        g.drawString(
                font,
                clip(
                        title.getString() + " · "
                                + Component.translatable("screen.finance.machine.tier", menu.tier())
                                        .getString(),
                        116),
                8,
                6,
                0xFF404040,
                false);
        String company = menu.blockEntity().companyName();
        Component owner =
                switch (menu.link()) {
                    case CompanyMachineBlockEntity.LINKED -> Component.translatable(
                            "screen.finance.factory.owner.linked", company);
                    case CompanyMachineBlockEntity.COMPANY -> Component.translatable(
                            "screen.finance.factory.owner.company", company);
                    default -> Component.translatable("screen.finance.factory.owner.personal");
                };
        g.drawString(font, clip(owner.getString(), 160), 8, 16, 0xFF555555, false);
        g.drawString(
                font,
                Component.translatable("screen.finance.harvester.fuel"),
                26,
                HarvesterMenu.BIN_Y + 8,
                0xFF555555,
                false);
        g.drawString(
                font,
                clip(
                        Component.translatable("screen.finance.harvester.job."
                                        + mode().name().toLowerCase(java.util.Locale.ROOT))
                                .getString(),
                        160),
                8,
                HarvesterMenu.BIN_Y + 58,
                0xFF555555,
                false);
        g.drawString(font, clip(statusText(), 160), 8, 27, statusColor(), false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF404040, false);
    }

    private String statusText() {
        String key =
                switch (menu.status()) {
                    case HarvesterBlockEntity.WORKING -> "screen.finance.harvester.working";
                    case HarvesterBlockEntity.FULL -> "screen.finance.harvester.full";
                    case HarvesterBlockEntity.NO_FUEL -> "screen.finance.factory.no_fuel";
                    case HarvesterBlockEntity.DONE -> "screen.finance.harvester.done";
                    default -> "screen.finance.harvester.idle";
                };
        String text = Component.translatable(key).getString();
        if (mode() == HarvestMode.MINE)
            text += " · "
                    + Component.translatable("screen.finance.harvester.depth", menu.shaftY())
                            .getString();
        return text;
    }

    private int statusColor() {
        return menu.status() == HarvesterBlockEntity.WORKING ? 0xFF2E7D32 : 0xFFB0581A;
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
