package finance.network;

import finance.simple.SimpleCenterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：简单模式四页需要、但金融中心快照里没有的数据（银行存款、工厂等级、今日机会……）。 */
public record SimpleStatusPacket(SimpleCenterService.Status status) {

    static final int MAX_OPPORTUNITIES = 3, MAX_TEXT = 160;

    public static void send(ServerPlayer player) {
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SimpleStatusPacket(SimpleCenterService.status(player)));
    }

    public static void encode(SimpleStatusPacket p, FriendlyByteBuf buf) {
        SimpleCenterService.Status s = p.status;
        buf.writeVarLong(Math.max(0, s.bankBalance()));
        buf.writeVarInt(Math.max(0, s.bankRateBps()));
        buf.writeVarInt(Math.max(0, s.factoryLevel()));
        buf.writeVarLong(Math.max(0, s.nextUpgradeCost()));
        buf.writeVarLong(Math.max(0, s.companyValuation()));
        buf.writeVarLong(Math.max(0, s.ipoMinValuation()));
        buf.writeVarLong(Math.max(0, s.stockUnlockBalance()));
        buf.writeUtf(s.advancedMode(), 16);
        List<String> lines = s.opportunities().subList(0, Math.min(MAX_OPPORTUNITIES, s.opportunities().size()));
        buf.writeVarInt(lines.size());
        for (String line : lines) buf.writeUtf(line.length() > MAX_TEXT ? line.substring(0, MAX_TEXT) : line, MAX_TEXT);
    }

    public static SimpleStatusPacket decode(FriendlyByteBuf buf) {
        long bank = buf.readVarLong();
        int rate = buf.readVarInt(), level = buf.readVarInt();
        long cost = buf.readVarLong(), valuation = buf.readVarLong(), ipoMin = buf.readVarLong(), stockUnlock = buf.readVarLong();
        String mode = buf.readUtf(16);
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_OPPORTUNITIES) throw new IllegalArgumentException("opportunities");
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(MAX_TEXT));
        return new SimpleStatusPacket(new SimpleCenterService.Status(bank, rate, level, cost, valuation, ipoMin,
                stockUnlock, mode, lines));
    }

    public static void handle(SimpleStatusPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                finance.client.simple.SimpleClientState.accept(p.status)));
        ctx.get().setPacketHandled(true);
    }
}
