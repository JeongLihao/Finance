package finance.network;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端 → 客户端：每种有对应物品的商品现在卖得多少、买要多少。
 * 登录时和之后每分钟发一次，客户端在物品悬停提示里显示，不用打开金融中心也知道背包里的东西值多少。
 */
public record PriceTablePacket(List<Row> rows) {

    /** itemId：物品注册名；bid：卖给市场每个得多少；ask：从市场买每个付多少。 */
    public record Row(String itemId, long bid, long ask) {}

    static final int MAX_ROWS = 512, MAX_ID = 96;

    /** 每分钟（1200 tick）推送一次。 */
    public static final int INTERVAL_TICKS = 1_200;

    public static List<Row> snapshot() {
        List<Row> rows = new ArrayList<>();
        for (Commodity c : CommodityRegistry.getAllCommodities()) {
            if (c.getItemId() == null
                    || c.getItemId().isEmpty()
                    || c.getItemId().length() > MAX_ID) continue;
            MarketPrice price = NpcMarketMaker.getMarketPrice(c.getId());
            if (price == null || price.getBidPrice() <= 0) continue;
            rows.add(new Row(c.getItemId(), price.getBidPrice(), Math.max(0, price.getAskPrice())));
            if (rows.size() >= MAX_ROWS) break;
        }
        return rows;
    }

    public static void send(ServerPlayer player) {
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), new PriceTablePacket(snapshot()));
    }

    public static void broadcast(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() == 0) return;
        FinancePacketHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), new PriceTablePacket(snapshot()));
    }

    public static void encode(PriceTablePacket p, FriendlyByteBuf buf) {
        List<Row> rows = p.rows.subList(0, Math.min(MAX_ROWS, p.rows.size()));
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUtf(r.itemId(), MAX_ID);
            buf.writeVarLong(Math.max(0, r.bid()));
            buf.writeVarLong(Math.max(0, r.ask()));
        }
    }

    public static PriceTablePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ROWS) throw new IllegalArgumentException("rows");
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) rows.add(new Row(buf.readUtf(MAX_ID), buf.readVarLong(), buf.readVarLong()));
        return new PriceTablePacket(rows);
    }

    public static void handle(PriceTablePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> finance.client.ClientPriceCache.accept(p.rows)));
        ctx.get().setPacketHandled(true);
    }
}
