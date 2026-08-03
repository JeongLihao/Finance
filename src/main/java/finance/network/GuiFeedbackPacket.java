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

    public GuiFeedbackPacket(String message) {
        String normalized = message == null ? "" : message;
        this.message = normalized.length() > MAX_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_MESSAGE_LENGTH)
                : normalized;
    }

    public static void send(ServerPlayer player, String message) {
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GuiFeedbackPacket(message));
    }

    public static void encode(GuiFeedbackPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
    }

    public static GuiFeedbackPacket decode(FriendlyByteBuf buffer) {
        return new GuiFeedbackPacket(buffer.readUtf(MAX_MESSAGE_LENGTH));
    }

    public static void handle(GuiFeedbackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    finance.client.GuiFeedbackClientHandler.handle(packet.message);
                }));
        ctx.get().setPacketHandled(true);
    }
}
