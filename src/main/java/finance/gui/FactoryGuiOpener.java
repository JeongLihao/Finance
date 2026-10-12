package finance.gui;

import finance.block.entity.FactoryBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * 工厂 GUI 开启器 —— 把 BlockPos 写进网络包，客户端据此找到 BlockEntity。
 */
public final class FactoryGuiOpener {
    private FactoryGuiOpener() {}

    public static void open(ServerPlayer player, FactoryBlockEntity factory) {
        NetworkHooks.openScreen(player, factory, buffer -> buffer.writeBlockPos(factory.getBlockPos()));
    }
}
