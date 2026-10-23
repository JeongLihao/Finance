package finance.gui;

import finance.block.entity.CompanyMachineBlockEntity;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyUpgradeRequirementService;
import finance.gameplay.company.CompanyUpgradeService;
import finance.util.MoneyFormat;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 机器界面上的“升档”按钮：加工机和采集机共用。服务端按公司升级规则扣钱扣材料，档次 +1。 */
public final class MachineUpgrade {

    /** 菜单按钮 ID（clickMenuButton），和加工机的配方序号错开。 */
    public static final int BUTTON_ID = 100;

    private MachineUpgrade() {}

    /** 服务端处理点击：个人机器不能升档，其余交给 CompanyUpgradeService，结果显示在动作栏。 */
    public static boolean click(Player player, CompanyMachineBlockEntity machine) {
        if (!(player instanceof ServerPlayer server) || machine == null) return false;
        UUID facilityId = machine.facilityId();
        if (facilityId == null) {
            server.displayClientMessage(
                    Component.translatable("screen.finance.machine.upgrade.personal")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        var result = CompanyUpgradeService.upgrade(
                server, facilityId, UUID.randomUUID().toString());
        server.displayClientMessage(
                Component.translatable(result.messageKey())
                        .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
        return result.success();
    }

    /** 能不能再升：1～2 档可以。 */
    public static boolean canUpgrade(int tier) {
        return tier >= 1 && tier < CompanyFacilityRecord.MAX_LEVEL;
    }

    /** 给界面看的下一档要求：“升到 2 档：12 铁锭、8 铜锭、8 红石，公司付 £20.00”；满档时说明已最高。 */
    public static Component describeNext(int tier) {
        var requirement = CompanyUpgradeRequirementService.requirement(tier);
        if (requirement == null) return Component.translatable("screen.finance.machine.upgrade.max");
        StringBuilder materials = new StringBuilder();
        requirement.materials().forEach((item, amount) -> {
            if (!materials.isEmpty()) materials.append("、");
            materials
                    .append(amount)
                    .append(" ")
                    .append(new ItemStack(item).getHoverName().getString());
        });
        return Component.translatable(
                "screen.finance.machine.upgrade.next",
                tier + 1,
                materials.toString(),
                MoneyFormat.pounds(requirement.cash()));
    }
}
