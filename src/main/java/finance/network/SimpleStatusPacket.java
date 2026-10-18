package finance.network;

import finance.simple.SimpleCenterService;
import finance.simple.SimpleInvestService;
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

    static final int MAX_LINES = 6, MAX_TEXT = 160;

    public static void send(ServerPlayer player) {
        send(player, List.of());
    }

    public static void send(ServerPlayer player, List<String> summary) {
        FinancePacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SimpleStatusPacket(SimpleCenterService.status(player, summary)));
    }

    private static void writeLines(FriendlyByteBuf buf, List<String> lines) {
        List<String> safe = lines.subList(0, Math.min(MAX_LINES, lines.size()));
        buf.writeVarInt(safe.size());
        for (String line : safe) buf.writeUtf(line.length() > MAX_TEXT ? line.substring(0, MAX_TEXT) : line, MAX_TEXT);
    }

    private static List<String> readLines(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LINES) throw new IllegalArgumentException("lines");
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(MAX_TEXT));
        return lines;
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
        writeLines(buf, s.opportunities());
        writeLines(buf, s.weather());
        writeLines(buf, s.summary());
        List<SimpleInvestService.Product> products = s.products().subList(0, Math.min(MAX_ROWS, s.products().size()));
        buf.writeVarInt(products.size());
        for (SimpleInvestService.Product prod : products) {
            buf.writeUtf(cut(prod.key()), MAX_TEXT); buf.writeUtf(cut(prod.name()), MAX_TEXT); buf.writeUtf(cut(prod.term()), MAX_TEXT);
            buf.writeVarInt(Math.max(-1, prod.rateBps())); buf.writeUtf(cut(prod.risk()), MAX_TEXT); buf.writeVarLong(Math.max(0, prod.minAmount()));
        }
        List<SimpleInvestService.Holding> holdings = s.holdings().subList(0, Math.min(MAX_ROWS, s.holdings().size()));
        buf.writeVarInt(holdings.size());
        for (SimpleInvestService.Holding h : holdings) {
            buf.writeUtf(cut(h.key()), MAX_TEXT); buf.writeUtf(cut(h.name()), MAX_TEXT); buf.writeVarLong(Math.max(0, h.amount()));
            buf.writeUtf(cut(h.status()), MAX_TEXT); buf.writeUtf(cut(h.redeemLabel()), MAX_TEXT);
        }
        SimpleInvestService.MarginInfo m = s.margin();
        buf.writeVarLong(Math.max(0, m.collateral()));
        buf.writeVarLong(Math.max(0, m.debt()));
        buf.writeVarInt(Math.max(-1, m.safetyPct()));
        buf.writeVarInt(Math.max(0, m.liquidationPct()));
        writeMap(buf, m.levered());
        writeMap(buf, m.shorts());
    }

    static final int MAX_ROWS = 32;

    private static String cut(String text) {
        if (text == null) return "";
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    private static void writeMap(FriendlyByteBuf buf, java.util.Map<String, Long> map) {
        List<java.util.Map.Entry<String, Long>> entries = new ArrayList<>(map.entrySet());
        entries = entries.subList(0, Math.min(MAX_ROWS, entries.size()));
        buf.writeVarInt(entries.size());
        for (java.util.Map.Entry<String, Long> e : entries) {
            buf.writeUtf(cut(e.getKey()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, e.getValue()));
        }
    }

    private static java.util.Map<String, Long> readMap(FriendlyByteBuf buf) {
        int n = count(buf);
        java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readUtf(MAX_TEXT), buf.readVarLong());
        return map;
    }

    private static int count(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ROWS) throw new IllegalArgumentException("rows");
        return n;
    }

    public static SimpleStatusPacket decode(FriendlyByteBuf buf) {
        long bank = buf.readVarLong();
        int rate = buf.readVarInt(), level = buf.readVarInt();
        long cost = buf.readVarLong(), valuation = buf.readVarLong(), ipoMin = buf.readVarLong(), stockUnlock = buf.readVarLong();
        String mode = buf.readUtf(16);
        List<String> opportunities = readLines(buf), weather = readLines(buf), summary = readLines(buf);
        int n = count(buf);
        List<SimpleInvestService.Product> products = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            products.add(new SimpleInvestService.Product(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                    buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readVarLong()));
        }
        n = count(buf);
        List<SimpleInvestService.Holding> holdings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            holdings.add(new SimpleInvestService.Holding(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readVarLong(),
                    buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)));
        }
        SimpleInvestService.MarginInfo margin = new SimpleInvestService.MarginInfo(buf.readVarLong(), buf.readVarLong(),
                buf.readVarInt(), buf.readVarInt(), readMap(buf), readMap(buf));
        return new SimpleStatusPacket(new SimpleCenterService.Status(bank, rate, level, cost, valuation, ipoMin,
                stockUnlock, mode, opportunities, weather, summary, products, holdings, margin));
    }

    public static void handle(SimpleStatusPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                finance.client.simple.SimpleClientState.accept(p.status)));
        ctx.get().setPacketHandled(true);
    }
}
