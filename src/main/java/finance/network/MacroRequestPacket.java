package finance.network;

import finance.gui.FinanceMenu;
import finance.macro.MacroSnapshotBuilder;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** 宏观经济页签快照请求（客户端 → 服务端），限流后回 {@link MacroResponsePacket}。 */
public record MacroRequestPacket(long requestId) {
    public static void encode(MacroRequestPacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId);
    }

    public static MacroRequestPacket decode(FriendlyByteBuf b) {
        return new MacroRequestPacket(b.readLong());
    }

    public static void handle(MacroRequestPacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> send(p, c.getSender()));
        c.setPacketHandled(true);
    }

    private static void send(MacroRequestPacket p, ServerPlayer player) {
        if (player == null
                || p.requestId <= 0
                || !(player.containerMenu instanceof FinanceMenu menu)
                || !menu.stillValid(player)
                || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "macro-request"))
            return;
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), MacroSnapshotBuilder.build(p.requestId));
    }
}
