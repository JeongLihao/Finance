package finance.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * “更多操作”界面的快照（服务端 → 客户端）：融资融券、我的银行、要约收购、宏观行动、财报，
 * 最近一次查询类操作（商情调查、季报）的结果文字，以及实体现金页（余额、随身现金、绿宝石汇率与今日额度）。
 */
public record PlayerServicesResponsePacket(long requestId, Margin margin, Bank bank,
                                           List<TenderRow> tenders, List<SqueezeRow> squeezable,
                                           Macro macro, List<String> calendar,
                                           String detailTitle, List<String> detail, Cash cash) {
    public static final int MAX_TENDERS = 16, MAX_SQUEEZE = 16, MAX_LAWS = 8, MAX_LEVELS = 8,
            MAX_GROUPS = 8, MAX_LINES = 24, MAX_TEXT = 256;

    public record Margin(long cash, long debt, long interest, long badDebt, String ratio, boolean warning,
                         String financed, String shorts, long freeCash) { }

    public record Bank(boolean owned, String summary, String rates, long minCapital, long charterFee) { }

    public record TenderRow(UUID id, String target, String buyer, long price, long accepted, long targetShares,
                            long endDay, boolean mine, boolean squeeze, long myShares) { }

    public record SqueezeRow(UUID companyId, String name, int percentX10) { }

    public record LawRow(String group, List<String> levelIds, List<String> levelNames, int active) { }

    public record GroupRow(String id, String name, int cloutBps, int approval, long lobby) { }

    public record Macro(boolean active, String company, long wage, long reservationWage, List<LawRow> laws,
                        String proposal, List<GroupRow> groups, boolean newsEnabled, long radarCost) { }

    public record Cash(boolean enabled, long balance, long cashOnHand, long emeralds, long emeraldToCash,
                       long cashToEmerald, long sellLeft, long buyLeft, long sellLimit, long buyLimit) {
        public static final Cash EMPTY = new Cash(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public PlayerServicesResponsePacket {
        cash = cash == null ? Cash.EMPTY : cash;
        tenders = bound(tenders, MAX_TENDERS);
        squeezable = bound(squeezable, MAX_SQUEEZE);
        calendar = bound(calendar, MAX_LINES);
        detail = bound(detail, MAX_LINES);
        detailTitle = detailTitle == null ? "" : detailTitle;
    }

    private static <T> List<T> bound(List<T> list, int max) {
        if (list == null) return List.of();
        return List.copyOf(list.size() > max ? list.subList(0, max) : list);
    }

    // ---------------------------------------------------------------- 编码

    public static void encode(PlayerServicesResponsePacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId);
        Margin m = p.margin;
        b.writeLong(m.cash()); b.writeLong(m.debt()); b.writeLong(m.interest()); b.writeLong(m.badDebt());
        utf(b, m.ratio()); b.writeBoolean(m.warning()); utf(b, m.financed()); utf(b, m.shorts()); b.writeLong(m.freeCash());
        Bank k = p.bank;
        b.writeBoolean(k.owned()); utf(b, k.summary()); utf(b, k.rates()); b.writeLong(k.minCapital()); b.writeLong(k.charterFee());
        list(b, p.tenders, (buf, t) -> {
            buf.writeUUID(t.id()); utf(buf, t.target()); utf(buf, t.buyer()); buf.writeLong(t.price());
            buf.writeLong(t.accepted()); buf.writeLong(t.targetShares()); buf.writeLong(t.endDay());
            buf.writeBoolean(t.mine()); buf.writeBoolean(t.squeeze()); buf.writeLong(t.myShares());
        });
        list(b, p.squeezable, (buf, s) -> { buf.writeUUID(s.companyId()); utf(buf, s.name()); buf.writeVarInt(s.percentX10()); });
        Macro x = p.macro;
        b.writeBoolean(x.active()); utf(b, x.company()); b.writeLong(x.wage()); b.writeLong(x.reservationWage());
        list(b, bound(x.laws(), MAX_LAWS), (buf, l) -> {
            utf(buf, l.group());
            list(buf, bound(l.levelIds(), MAX_LEVELS), PlayerServicesResponsePacket::utf);
            list(buf, bound(l.levelNames(), MAX_LEVELS), PlayerServicesResponsePacket::utf);
            buf.writeVarInt(l.active());
        });
        utf(b, x.proposal());
        list(b, bound(x.groups(), MAX_GROUPS), (buf, g) -> {
            utf(buf, g.id()); utf(buf, g.name()); buf.writeVarInt(g.cloutBps()); buf.writeVarInt(g.approval()); buf.writeLong(g.lobby());
        });
        b.writeBoolean(x.newsEnabled()); b.writeLong(x.radarCost());
        list(b, p.calendar, PlayerServicesResponsePacket::utf);
        utf(b, p.detailTitle);
        list(b, p.detail, PlayerServicesResponsePacket::utf);
        Cash k2 = p.cash;
        b.writeBoolean(k2.enabled());
        for (long v : new long[]{k2.balance(), k2.cashOnHand(), k2.emeralds(), k2.emeraldToCash(), k2.cashToEmerald(),
                k2.sellLeft(), k2.buyLeft(), k2.sellLimit(), k2.buyLimit()}) b.writeLong(v);
    }

    public static PlayerServicesResponsePacket decode(FriendlyByteBuf b) {
        long id = b.readLong();
        Margin m = new Margin(b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUtf(MAX_TEXT), b.readBoolean(),
                b.readUtf(MAX_TEXT), b.readUtf(MAX_TEXT), b.readLong());
        Bank k = new Bank(b.readBoolean(), b.readUtf(MAX_TEXT), b.readUtf(MAX_TEXT), b.readLong(), b.readLong());
        List<TenderRow> tenders = read(b, MAX_TENDERS, buf -> new TenderRow(buf.readUUID(), buf.readUtf(MAX_TEXT),
                buf.readUtf(MAX_TEXT), buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readBoolean(), buf.readBoolean(), buf.readLong()));
        List<SqueezeRow> squeeze = read(b, MAX_SQUEEZE, buf -> new SqueezeRow(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readVarInt()));
        boolean active = b.readBoolean();
        String company = b.readUtf(MAX_TEXT);
        long wage = b.readLong(), reservation = b.readLong();
        List<LawRow> laws = read(b, MAX_LAWS, buf -> new LawRow(buf.readUtf(MAX_TEXT),
                read(buf, MAX_LEVELS, x -> x.readUtf(MAX_TEXT)), read(buf, MAX_LEVELS, x -> x.readUtf(MAX_TEXT)), buf.readVarInt()));
        String proposal = b.readUtf(MAX_TEXT);
        List<GroupRow> groups = read(b, MAX_GROUPS, buf -> new GroupRow(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                buf.readVarInt(), buf.readVarInt(), buf.readLong()));
        Macro x = new Macro(active, company, wage, reservation, laws, proposal, groups, b.readBoolean(), b.readLong());
        List<String> calendar = read(b, MAX_LINES, buf -> buf.readUtf(MAX_TEXT));
        String title = b.readUtf(MAX_TEXT);
        List<String> detail = read(b, MAX_LINES, buf -> buf.readUtf(MAX_TEXT));
        Cash cash = new Cash(b.readBoolean(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readLong(), b.readLong(), b.readLong(), b.readLong());
        return new PlayerServicesResponsePacket(id, m, k, tenders, squeeze, x, calendar, title, detail, cash);
    }

    private static void utf(FriendlyByteBuf b, String s) {
        String v = s == null ? "" : s;
        b.writeUtf(v.length() > MAX_TEXT ? v.substring(0, MAX_TEXT) : v, MAX_TEXT);
    }

    private static <T> void list(FriendlyByteBuf b, List<T> list, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        b.writeVarInt(list.size());
        for (T t : list) writer.accept(b, t);
    }

    private static <T> List<T> read(FriendlyByteBuf b, int max, Function<FriendlyByteBuf, T> reader) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("player services list size " + n);
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(reader.apply(b));
        return out;
    }

    public static void handle(PlayerServicesResponsePacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> finance.client.PlayerServicesClientCache.accept(p)));
        c.setPacketHandled(true);
    }
}
