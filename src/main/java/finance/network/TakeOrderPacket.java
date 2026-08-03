package finance.network;

import finance.market.MarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 从订单页直接吃掉指定挂单。
 */
public class TakeOrderPacket {

    private final UUID orderId;

    public TakeOrderPacket(UUID orderId) {
        this.orderId = orderId;
    }

    public static void encode(TakeOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.orderId);
    }

    public static TakeOrderPacket decode(FriendlyByteBuf buffer) {
        return new TakeOrderPacket(buffer.readUUID());
    }

    public static void handle(TakeOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MarketManager.TakeOrderResult result = MarketManager.takeOrder(packet.orderId, player.getUUID());
            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
