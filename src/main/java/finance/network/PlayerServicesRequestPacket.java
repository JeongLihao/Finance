package finance.network;

import finance.cycle.EconomyCycleService;
import finance.playerservice.PlayerServicesService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** “更多操作”界面快照请求（客户端 → 服务端），限流后回 {@link PlayerServicesResponsePacket}。 */
public record PlayerServicesRequestPacket(long requestId) {
    public static void encode(PlayerServicesRequestPacket p, FriendlyByteBuf b) { b.writeLong(p.requestId); }

    public static PlayerServicesRequestPacket decode(FriendlyByteBuf b) { return new PlayerServicesRequestPacket(b.readLong()); }

    public static void handle(PlayerServicesRequestPacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> send(p, c.getSender()));
        c.setPacketHandled(true);
    }

    private static void send(PlayerServicesRequestPacket p, ServerPlayer player) {
        if (player == null || p.requestId <= 0
                || !MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "player-services")) return;
        long day = EconomyCycleService.currentMcDay(player.server);
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PlayerServicesService.snapshot(p.requestId, player.getUUID(), day));
    }
}
