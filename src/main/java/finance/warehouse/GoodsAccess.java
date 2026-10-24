package finance.warehouse;

import finance.config.FinanceConfig;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.WorldTerminalRegistry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 个人托管（简单模式的“仓库里的货”）和背包之间搬货的位置规则：只有站在自己的仓库控制器或市场终端旁边才行。
 * 买卖本身可以远程做，货一直在托管里；要把货拿到手里或放进去，得回到仓库或终端。
 */
public final class GoodsAccess {
    public static final String DENIED = "要站在自己的仓库控制器或市场终端旁边，才能把货存进去或取出来。";

    private GoodsAccess() {}

    /** 玩家现在能不能在背包和个人托管之间搬货。 */
    public static boolean canMoveGoods(ServerPlayer player) {
        if (player == null || !player.isAlive()) return false;
        String dimension = player.serverLevel().dimension().location().toString();
        double max = FinanceConfig.terminalInteractionDistance();
        UUID id = player.getUUID();
        for (WarehouseRecord record : WarehouseManager.all()) {
            if (record.companyId() != null || !id.equals(record.ownerId())) continue;
            if (record.status() != WarehouseStatus.ACTIVE) continue;
            if (dimension.equals(record.dimensionId()) && within(player, record.blockPos(), max)) return true;
        }
        for (WorldTerminalRegistry.TerminalRecord t :
                WorldTerminalRegistry.byType(FinanceTerminalType.MARKET_TERMINAL)) {
            if (dimension.equals(t.dimensionId()) && within(player, t.position(), max)) return true;
        }
        return false;
    }

    static boolean within(ServerPlayer player, BlockPos pos, double max) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= max * max;
    }
}
