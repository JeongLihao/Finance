package finance.factory;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 公司机器（加工机、收货箱）的归属：谁放下就归谁的公司；只有公司成员能打开。 */
public final class MachineOwnership {

    private MachineOwnership() {}

    /** 放下机器的玩家能替哪家公司管生产；没有公司或没有生产管理权限返回 null（机器算个人的）。 */
    public static Company companyForPlacer(LivingEntity placer) {
        if (!(placer instanceof ServerPlayer player)) return null;
        Company company = CompanyMembershipService.companyOf(player.getUUID());
        return company != null
                        && CompanyMembershipService.hasPermission(
                                company.getCompanyId(), player.getUUID(), CompanyPermission.MANAGE_PRODUCTION)
                ? company
                : null;
    }

    /** 放下后告诉玩家这台机器归谁。 */
    public static void announce(LivingEntity placer, Company company, String machineKey) {
        if (!(placer instanceof ServerPlayer player)) return;
        player.sendSystemMessage(
                company == null
                        ? Component.translatable(machineKey + ".personal")
                        : Component.translatable(machineKey + ".owned", company.getName()));
    }

    /** 能不能打开这台机器：个人机器谁都能开，公司机器只有成员能开（公司已不存在时谁都能开，方便拆）。 */
    public static boolean mayUse(ServerPlayer player, UUID companyId) {
        if (companyId == null || CompanyManager.getCompany(companyId) == null) return true;
        return CompanyMembershipService.hasPermission(companyId, player.getUUID(), CompanyPermission.VIEW_COMPANY);
    }
}
