package finance.gameplay;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * 服务端构造的金融入口上下文。
 *
 * @param playerId 玩家 UUID
 * @param terminalType 声明的入口类型
 * @param requestedMode 请求的界面模式
 * @param dimensionId 服务端维度标识
 * @param sourcePos 物理入口位置；非物理入口为空
 * @param serverTick 构造上下文时的世界持久化时间
 * @param permissionLevel 服务端权限等级
 * @param entryVerified 服务端是否已验证物品、方块或可信命令入口
 * @param sameDimension 玩家与入口是否仍在同一维度
 * @param distanceSquared 玩家到物理终端的距离平方；非物理入口使用 0
 */
public record FinanceAccessContext(
        UUID playerId,
        FinanceTerminalType terminalType,
        FinanceScreenMode requestedMode,
        String dimensionId,
        BlockPos sourcePos,
        long serverTick,
        int permissionLevel,
        boolean entryVerified,
        boolean sameDimension,
        double distanceSquared
) {
    public FinanceAccessContext {
        if (permissionLevel < 0) permissionLevel = 0;
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) {
            distanceSquared = Double.POSITIVE_INFINITY;
        }
    }
}
