package finance.client;

import finance.block.entity.HarvesterBlockEntity;
import finance.factory.HarvestMode;
import finance.gui.HarvesterMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 采集机界面：归属、在干什么、收获仓；采矿机多一个燃料槽和挖到第几层。 */
public final class HarvesterScreen extends CompanyMachineScreen<HarvesterMenu> {

    public HarvesterScreen(HarvesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HarvesterMenu.INVENTORY_Y);
    }

    @Override
    protected void init() {
        super.init();
        addUpgradeButton(128, 2);
    }

    private HarvestMode mode() {
        return menu.blockEntity().mode();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        line(g, title.getString() + " · " + tierLabel().getString(), 8, 6, 116, TEXT);
        line(g, ownerLine(), 8, 16, 160, DIM);
        line(g, statusText(), 8, 27, 160, menu.status() == HarvesterBlockEntity.WORKING ? GOOD : WARN);
        g.drawString(
                font, Component.translatable("screen.finance.harvester.fuel"), 26, HarvesterMenu.BIN_Y + 8, DIM, false);
        line(
                g,
                Component.translatable(
                        "screen.finance.harvester.job." + mode().name().toLowerCase(Locale.ROOT)),
                8,
                HarvesterMenu.BIN_Y + 58,
                160,
                DIM);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
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
        return mode() == HarvestMode.MINE
                ? text + " · "
                        + Component.translatable("screen.finance.harvester.depth", menu.shaftY())
                                .getString()
                : text;
    }
}
