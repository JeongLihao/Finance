package finance.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class GuiFeedbackPacket {

    private static final int MAX_MESSAGE_LENGTH = 160;

    private final String message;
    /** 这次操作让钱包变了多少（便士）；非 0 时客户端飘字并播放音效。 */
    private final long moneyDelta;

    public GuiFeedbackPacket(String message) {
        this(message, 0);
    }

    public GuiFeedbackPacket(String message, long moneyDelta) {
        this.moneyDelta = moneyDelta;
        String normalized = message == null ? "" : message;
        this.message = normalized.length() > MAX_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_MESSAGE_LENGTH)
                : normalized;
    }

    public static void send(ServerPlayer player, String message) {
        send(player, message, 0);
    }

    public static void send(ServerPlayer player, String message, long moneyDelta) {
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GuiFeedbackPacket(message, moneyDelta));
        // 每个操作都以一条结果提示收尾：借此在下一 tick 把新的余额、库存、订单推给开着的金融中心
        FinanceMenuRefresher.markDirty(player);
    }

    public static void encode(GuiFeedbackPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
        buffer.writeVarLong(packet.moneyDelta);
    }

    public static GuiFeedbackPacket decode(FriendlyByteBuf buffer) {
        return new GuiFeedbackPacket(buffer.readUtf(MAX_MESSAGE_LENGTH), buffer.readVarLong());
    }

    public static void handle(GuiFeedbackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    finance.client.GuiFeedbackClientHandler.handle(packet.message, packet.moneyDelta);
                }));
        ctx.get().setPacketHandled(true);
    }
}
