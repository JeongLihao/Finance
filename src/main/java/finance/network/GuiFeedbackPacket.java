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
    /** 提示音：0 普通，1 押注赚了，2 押注亏了。 */
    private final int tone;
    public static final int TONE_NORMAL = 0, TONE_WIN = 1, TONE_LOSS = 2;

    public GuiFeedbackPacket(String message) {
        this(message, 0);
    }

    public GuiFeedbackPacket(String message, long moneyDelta) {
        this(message, moneyDelta, TONE_NORMAL);
    }

    public GuiFeedbackPacket(String message, long moneyDelta, int tone) {
        this.moneyDelta = moneyDelta;
        this.tone = tone == TONE_WIN || tone == TONE_LOSS ? tone : TONE_NORMAL;
        String normalized = message == null ? "" : message;
        this.message = normalized.length() > MAX_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_MESSAGE_LENGTH)
                : normalized;
    }

    public static void send(ServerPlayer player, String message) {
        send(player, message, 0);
    }

    public static void send(ServerPlayer player, String message, long moneyDelta) {
        send(player, message, moneyDelta, TONE_NORMAL);
    }

    public static void send(ServerPlayer player, String message, long moneyDelta, int tone) {
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GuiFeedbackPacket(message, moneyDelta, tone));
        // 每个操作都以一条结果提示收尾：借此在下一 tick 把新的余额、库存、订单推给开着的金融中心
        FinanceMenuRefresher.markDirty(player);
    }

    public static void encode(GuiFeedbackPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.message, MAX_MESSAGE_LENGTH);
        buffer.writeVarLong(packet.moneyDelta);
        buffer.writeByte(packet.tone);
    }

    public static GuiFeedbackPacket decode(FriendlyByteBuf buffer) {
        return new GuiFeedbackPacket(buffer.readUtf(MAX_MESSAGE_LENGTH), buffer.readVarLong(), buffer.readByte());
    }

    public static void handle(GuiFeedbackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    finance.client.GuiFeedbackClientHandler.handle(packet.message, packet.moneyDelta, packet.tone);
                }));
        ctx.get().setPacketHandled(true);
    }
}
