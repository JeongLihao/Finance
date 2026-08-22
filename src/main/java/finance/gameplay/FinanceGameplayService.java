package finance.gameplay;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Minecraft 世界入口与现有金融 GUI/内核之间的服务端桥梁。 */
public final class FinanceGameplayService {

    private FinanceGameplayService() {
    }

    /**
     * 处理没有可信方块会话的客户端请求。目前只有受配置约束的旧快捷键可从这里打开界面。
     * 物理终端在完成注册后必须走带方块位置和方块类型复核的入口。
     */
    public static GameplayActionResult openRemoteRequest(ServerPlayer player,
                                                          FinanceTerminalType terminalType,
                                                          FinanceScreenMode requestedMode) {
        if (player == null || terminalType == null || requestedMode == null) {
            return GameplayActionResult.failure("finance.access.invalid_request");
        }
        // 客户端不能声明自己位于实体终端旁；网络快捷键只进入服务端决定的快捷入口。
        return FinanceGameplayOpener.openShortcut(player);
    }

    /** 兼容命令入口；Minecraft-first 默认下仅管理员或显式开启旧入口时可用。 */
    public static GameplayActionResult openLegacyCommand(ServerPlayer player) {
        if (player == null) return GameplayActionResult.failure("finance.access.invalid_request");
        return FinanceGameplayOpener.openLegacy(player);
    }

    public static FinanceAccessDecision authorize(FinanceAccessContext context, FinanceScreenMode requestedMode) {
        if (context == null || context.requestedMode() != requestedMode) {
            return FinanceAccessDecision.deny("finance.access.mode_mismatch");
        }
        return FinanceAccessService.authorize(context, FinanceAccessPolicy.current());
    }

    /** 用于纯逻辑测试和后续终端会话构造，避免客户端提供玩家身份。 */
    static FinanceAccessContext verifiedContext(UUID playerId, FinanceTerminalType type,
                                                int permissionLevel, double distanceSquared) {
        return new FinanceAccessContext(playerId, type, FinanceAccessService.modeFor(type),
                "minecraft:overworld", type.isPhysicalTerminal() ? net.minecraft.core.BlockPos.ZERO : null,
                0L, permissionLevel, true, true, distanceSquared);
    }
}
