package finance.network;

import finance.gui.FinanceGuiOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端快捷键请求打开金融中心 GUI。
 */
public class OpenFinanceGuiPacket {

    public static void encode(OpenFinanceGuiPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenFinanceGuiPacket decode(FriendlyByteBuf buffer) {
        return new OpenFinanceGuiPacket();
    }

    public static void handle(OpenFinanceGuiPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FinanceGuiOpener.open(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
