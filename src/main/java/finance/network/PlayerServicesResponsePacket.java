package finance.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * “更多操作”界面的快照（服务端 → 客户端）：融资融券、我的银行、要约收购、中央银行、宏观行动、财报，
 * 最近一次查询类操作（商情调查、季报）的结果文字，以及实体现金页（余额、随身现金、绿宝石汇率与今日额度）。
 */
public record PlayerServicesResponsePacket(
        long requestId,
        Margin margin,
        Bank bank,
        List<TenderRow> tenders,
        List<SqueezeRow> squeezable,
        CentralBankPage centralBank,
        Macro macro,
        List<String> calendar,
        String detailTitle,
        List<String> detail,
        Cash cash) {
    public static final int MAX_TENDERS = 16,
            MAX_SQUEEZE = 16,
            MAX_OFFERS = 8,
            MAX_BONDS = 16,
            MAX_DEPOSITS = 16,
            MAX_LINES = 24,
            MAX_TEXT = 256;

    public record Margin(
            long cash,
            long debt,
            long interest,
            long badDebt,
            String ratio,
            boolean warning,
            String financed,
            String shorts,
            long freeCash) {}

    public record Bank(boolean owned, String summary, String rates, long minCapital, long charterFee) {}

    public record TenderRow(
            UUID id,
            String target,
            String buyer,
            long price,
            long accepted,
            long targetShares,
            long endDay,
            boolean mine,
            boolean squeeze,
            long myShares) {}

    public record SqueezeRow(UUID companyId, String name, int percentX10) {}

    public record Macro(
            boolean active, String company, long wage, long reservationWage, boolean newsEnabled, long radarCost) {}

    /** 央行债券的一个期限档：期限（天）和今天认购能锁定的年收益率（基点）。 */
    public record BondOffer(int termDays, int yieldBps) {}

    /** 我持有的一笔央行债券。 */
    public record BondHolding(UUID id, int termDays, int rateBps, long principal, long maturityDay) {}

    /** 我的一笔未到期定期存款。 */
    public record DepositRow(UUID id, String bank, long principal, int rateBps, long maturityDay, long accrued) {}

    /** 中央银行页：央行概况、央行债券期限档与我的持仓、我的定期存款。 */
    public record CentralBankPage(
            MacroResponsePacket.CentralBankRow overview,
            List<BondOffer> offers,
            List<BondHolding> bonds,
            List<DepositRow> deposits,
            long bondMinAmount,
            long bondEarlyFee) {
        public CentralBankPage {
            overview = overview == null ? MacroResponsePacket.CentralBankRow.EMPTY : overview;
            offers = bound(offers, MAX_OFFERS);
            bonds = bound(bonds, MAX_BONDS);
            deposits = bound(deposits, MAX_DEPOSITS);
        }
    }

    public record Cash(
            boolean enabled,
            long balance,
            long cashOnHand,
            long emeralds,
            long emeraldToCash,
            long cashToEmerald,
            long sellLeft,
            long buyLeft,
            long sellLimit,
            long buyLimit) {
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
        b.writeLong(m.cash());
        b.writeLong(m.debt());
        b.writeLong(m.interest());
        b.writeLong(m.badDebt());
        utf(b, m.ratio());
        b.writeBoolean(m.warning());
        utf(b, m.financed());
        utf(b, m.shorts());
        b.writeLong(m.freeCash());
        Bank k = p.bank;
        b.writeBoolean(k.owned());
        utf(b, k.summary());
        utf(b, k.rates());
        b.writeLong(k.minCapital());
        b.writeLong(k.charterFee());
        list(b, p.tenders, (buf, t) -> {
            buf.writeUUID(t.id());
            utf(buf, t.target());
            utf(buf, t.buyer());
            buf.writeLong(t.price());
            buf.writeLong(t.accepted());
            buf.writeLong(t.targetShares());
            buf.writeLong(t.endDay());
            buf.writeBoolean(t.mine());
            buf.writeBoolean(t.squeeze());
            buf.writeLong(t.myShares());
        });
        list(b, p.squeezable, (buf, s) -> {
            buf.writeUUID(s.companyId());
            utf(buf, s.name());
            buf.writeVarInt(s.percentX10());
        });
        CentralBankPage cb = p.centralBank;
        var o = cb.overview();
        b.writeVarInt(o.rateBps());
        b.writeVarInt(o.targetBps());
        b.writeVarInt(o.inflationBps());
        b.writeVarInt(o.reserveBps());
        b.writeLong(o.nextMeetingDay());
        utf(b, o.guidance());
        b.writeLong(o.absorbed());
        b.writeLong(o.injected());
        list(b, o.stabilization(), PlayerServicesResponsePacket::utf);
        list(b, cb.offers(), (buf, f) -> {
            buf.writeVarInt(f.termDays());
            buf.writeVarInt(f.yieldBps());
        });
        list(b, cb.bonds(), (buf, h) -> {
            buf.writeUUID(h.id());
            buf.writeVarInt(h.termDays());
            buf.writeVarInt(h.rateBps());
            buf.writeLong(h.principal());
            buf.writeLong(h.maturityDay());
        });
        list(b, cb.deposits(), (buf, d) -> {
            buf.writeUUID(d.id());
            utf(buf, d.bank());
            buf.writeLong(d.principal());
            buf.writeVarInt(d.rateBps());
            buf.writeLong(d.maturityDay());
            buf.writeLong(d.accrued());
        });
        b.writeLong(cb.bondMinAmount());
        b.writeLong(cb.bondEarlyFee());
        Macro x = p.macro;
        b.writeBoolean(x.active());
        utf(b, x.company());
        b.writeLong(x.wage());
        b.writeLong(x.reservationWage());
        b.writeBoolean(x.newsEnabled());
        b.writeLong(x.radarCost());
        list(b, p.calendar, PlayerServicesResponsePacket::utf);
        utf(b, p.detailTitle);
        list(b, p.detail, PlayerServicesResponsePacket::utf);
        Cash k2 = p.cash;
        b.writeBoolean(k2.enabled());
        for (long v : new long[] {
            k2.balance(),
            k2.cashOnHand(),
            k2.emeralds(),
            k2.emeraldToCash(),
            k2.cashToEmerald(),
            k2.sellLeft(),
            k2.buyLeft(),
            k2.sellLimit(),
            k2.buyLimit()
        }) b.writeLong(v);
    }

    public static PlayerServicesResponsePacket decode(FriendlyByteBuf b) {
        long id = b.readLong();
        Margin m = new Margin(
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readUtf(MAX_TEXT),
                b.readBoolean(),
                b.readUtf(MAX_TEXT),
                b.readUtf(MAX_TEXT),
                b.readLong());
        Bank k = new Bank(b.readBoolean(), b.readUtf(MAX_TEXT), b.readUtf(MAX_TEXT), b.readLong(), b.readLong());
        List<TenderRow> tenders = read(
                b,
                MAX_TENDERS,
                buf -> new TenderRow(
                        buf.readUUID(),
                        buf.readUtf(MAX_TEXT),
                        buf.readUtf(MAX_TEXT),
                        buf.readLong(),
                        buf.readLong(),
                        buf.readLong(),
                        buf.readLong(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readLong()));
        List<SqueezeRow> squeeze =
                read(b, MAX_SQUEEZE, buf -> new SqueezeRow(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readVarInt()));
        int rate = b.readVarInt(), target = b.readVarInt(), inflation = b.readVarInt(), reserve = b.readVarInt();
        long nextMeeting = b.readLong();
        String guidance = b.readUtf(MAX_TEXT);
        long absorbed = b.readLong(), injected = b.readLong();
        List<String> stabilization = read(b, MacroResponsePacket.MAX_STABILIZATION, buf -> buf.readUtf(MAX_TEXT));
        var overview = new MacroResponsePacket.CentralBankRow(
                rate, target, inflation, reserve, nextMeeting, guidance, absorbed, injected, stabilization);
        List<BondOffer> offers = read(b, MAX_OFFERS, buf -> new BondOffer(buf.readVarInt(), buf.readVarInt()));
        List<BondHolding> bonds = read(
                b,
                MAX_BONDS,
                buf -> new BondHolding(
                        buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong()));
        List<DepositRow> deposits = read(
                b,
                MAX_DEPOSITS,
                buf -> new DepositRow(
                        buf.readUUID(),
                        buf.readUtf(MAX_TEXT),
                        buf.readLong(),
                        buf.readVarInt(),
                        buf.readLong(),
                        buf.readLong()));
        CentralBankPage cb = new CentralBankPage(overview, offers, bonds, deposits, b.readLong(), b.readLong());
        boolean active = b.readBoolean();
        String company = b.readUtf(MAX_TEXT);
        long wage = b.readLong(), reservation = b.readLong();
        Macro x = new Macro(active, company, wage, reservation, b.readBoolean(), b.readLong());
        List<String> calendar = read(b, MAX_LINES, buf -> buf.readUtf(MAX_TEXT));
        String title = b.readUtf(MAX_TEXT);
        List<String> detail = read(b, MAX_LINES, buf -> buf.readUtf(MAX_TEXT));
        Cash cash = new Cash(
                b.readBoolean(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong(),
                b.readLong());
        return new PlayerServicesResponsePacket(id, m, k, tenders, squeeze, cb, x, calendar, title, detail, cash);
    }

    private static void utf(FriendlyByteBuf b, String s) {
        String v = s == null ? "" : s;
        b.writeUtf(v.length() > MAX_TEXT ? v.substring(0, MAX_TEXT) : v, MAX_TEXT);
    }

    private static <T> void list(
            FriendlyByteBuf b, List<T> list, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
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
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> finance.client.PlayerServicesClientCache.accept(p)));
        c.setPacketHandled(true);
    }
}
