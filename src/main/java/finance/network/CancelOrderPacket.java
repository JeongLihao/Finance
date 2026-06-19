package finance.network;

import finance.gui.FinanceGuiOpener;
import finance.market.MarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 取消订单数据包。
 */
public class CancelOrderPacket {

    private final int orderIndex;

    public CancelOrderPacket(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public static void encode(CancelOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.orderIndex);
    }

    public static CancelOrderPacket decode(FriendlyByteBuf buffer) {
        return new CancelOrderPacket(buffer.readVarInt());
    }

    public static void handle(CancelOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (MarketManager.cancelOrder(packet.orderIndex, player.getUUID())) {
                player.sendSystemMessage(Component.literal("订单 #" + packet.orderIndex + " 已取消。"));
                FinanceGuiOpener.open(player);
            } else {
                player.sendSystemMessage(Component.literal("取消失败，请检查编号是否是你的订单。"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
