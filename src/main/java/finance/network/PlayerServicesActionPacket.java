package finance.network;

import finance.cycle.EconomyCycleService;
import finance.playerservice.PlayerServicesService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * “更多操作”界面的动作（客户端 → 服务端）。执行后回一条状态提示，并推送一份新快照（requestId = -1）。
 */
public record PlayerServicesActionPacket(PlayerServicesService.Action action, String text, long a, long b, long c, long d) {
    static final int MAX_TEXT = 64;

    public static void encode(PlayerServicesActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.text == null ? "" : p.text, MAX_TEXT);
        buf.writeLong(p.a);
        buf.writeLong(p.b);
        buf.writeLong(p.c);
        buf.writeLong(p.d);
    }

    public static PlayerServicesActionPacket decode(FriendlyByteBuf buf) {
        return new PlayerServicesActionPacket(buf.readEnum(PlayerServicesService.Action.class), buf.readUtf(MAX_TEXT),
                buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong());
    }

    public static void handle(PlayerServicesActionPacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> run(p, c.getSender()));
        c.setPacketHandled(true);
    }

    private static void run(PlayerServicesActionPacket p, ServerPlayer player) {
        if (player == null || p.action == null) return;
        if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), "player-services:" + p.action)) {
            GuiFeedbackPacket.send(player, "操作过于频繁");
            return;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        PlayerServicesService.Result r = PlayerServicesService.act(player, player.getUUID(), p.action, p.text,
                p.a, p.b, p.c, p.d, day);
        GuiFeedbackPacket.send(player, r.message());
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                PlayerServicesService.snapshot(-1, player.getUUID(), new finance.cash.PlayerCashPurse(player), day));
    }
}
