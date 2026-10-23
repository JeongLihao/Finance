package finance.client;

import finance.block.entity.CompanyMachineBlockEntity;
import finance.gui.MachineUpgrade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** 机器界面上的“升档”按钮：悬停显示下一档要什么；个人机器和满档时变灰。 */
final class MachineUpgradeButton {

    private MachineUpgradeButton() {}

    static Button create(Minecraft minecraft, int containerId, int x, int y, int width, int tier, int link) {
        Button button = Button.builder(Component.translatable("screen.finance.machine.upgrade"), b -> {
                    if (minecraft != null && minecraft.gameMode != null)
                        minecraft.gameMode.handleInventoryButtonClick(containerId, MachineUpgrade.BUTTON_ID);
                })
                .bounds(x, y, width, 14)
                .build();
        refresh(button, tier, link);
        return button;
    }

    static void refresh(Button button, int tier, int link) {
        boolean personal = link == CompanyMachineBlockEntity.PERSONAL;
        button.active = !personal && MachineUpgrade.canUpgrade(tier);
        button.setTooltip(Tooltip.create(
                personal
                        ? Component.translatable("screen.finance.machine.upgrade.personal")
                        : MachineUpgrade.describeNext(tier)));
    }
}
