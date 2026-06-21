package finance.network;

import finance.gui.FinanceGuiOpener;
import finance.market.MarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 取消订单数据包（按订单 UUID 精确取消）。
 */
public class CancelOrderPacket {

    private final UUID orderId;

    public CancelOrderPacket(UUID orderId) {
        this.orderId = orderId;
    }

    public static void encode(CancelOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.orderId);
    }

    public static CancelOrderPacket decode(FriendlyByteBuf buffer) {
        return new CancelOrderPacket(buffer.readUUID());
    }

    public static void handle(CancelOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (MarketManager.cancelOrder(packet.orderId, player.getUUID())) {
                player.sendSystemMessage(Component.literal("订单已取消。"));
                FinanceGuiOpener.open(player);
            } else {
                player.sendSystemMessage(Component.literal("取消失败，请检查是否是你的订单。"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
