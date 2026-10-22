package finance.network;

import finance.simple.SimpleBetService;
import finance.simple.SimpleBossService;
import finance.simple.SimpleCenterService;
import finance.simple.SimpleInvestService;
import finance.simple.SimpleOpportunityService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** 服务端 → 客户端：简单模式四页需要、但金融中心快照里没有的数据（银行存款、工厂等级、今日机会……）。 */
public record SimpleStatusPacket(SimpleCenterService.Status status) {

    static final int MAX_LINES = 6, MAX_TEXT = 160;

    public static void send(ServerPlayer player) {
        send(player, List.of());
    }

    public static void send(ServerPlayer player, List<String> summary) {
        FinancePacketHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SimpleStatusPacket(SimpleCenterService.status(player, summary)));
    }

    /** 可用玩法名单：三个组名加九个单项名。 */
    static final int MAX_NAMES = 16;

    private static void writeLines(FriendlyByteBuf buf, List<String> lines) {
        writeLines(buf, lines, MAX_LINES);
    }

    private static void writeLines(FriendlyByteBuf buf, List<String> lines, int max) {
        List<String> safe = lines.subList(0, Math.min(max, lines.size()));
        buf.writeVarInt(safe.size());
        for (String line : safe) buf.writeUtf(line.length() > MAX_TEXT ? line.substring(0, MAX_TEXT) : line, MAX_TEXT);
    }

    private static List<String> readLines(FriendlyByteBuf buf) {
        return readLines(buf, MAX_LINES);
    }

    private static List<String> readLines(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("lines");
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
        writeLines(buf, s.extras(), MAX_NAMES);
        writeLines(buf, s.locked());
        writeLines(buf, s.unlockedNow());
        List<SimpleOpportunityService.Card> cards = s.cards()
                .subList(
                        0,
                        Math.min(SimpleOpportunityService.MAX_CARDS, s.cards().size()));
        buf.writeVarInt(cards.size());
        for (SimpleOpportunityService.Card c : cards) {
            buf.writeUtf(cut(c.text()), MAX_TEXT);
            buf.writeUtf(cut(c.button()), MAX_TEXT);
            buf.writeUtf(cut(c.action()), MAX_TEXT);
            buf.writeUtf(cut(c.target()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, c.amount()));
            buf.writeUtf(cut(c.goTo()), MAX_TEXT);
            buf.writeBoolean(c.boss());
        }
        writeLines(buf, s.weather());
        writeLines(buf, s.summary());
        List<SimpleInvestService.Product> products =
                s.products().subList(0, Math.min(MAX_ROWS, s.products().size()));
        buf.writeVarInt(products.size());
        for (SimpleInvestService.Product prod : products) {
            buf.writeUtf(cut(prod.key()), MAX_TEXT);
            buf.writeUtf(cut(prod.name()), MAX_TEXT);
            buf.writeUtf(cut(prod.term()), MAX_TEXT);
            buf.writeVarInt(Math.max(-1, prod.rateBps()));
            buf.writeUtf(cut(prod.risk()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, prod.minAmount()));
        }
        List<SimpleInvestService.Holding> holdings =
                s.holdings().subList(0, Math.min(MAX_ROWS, s.holdings().size()));
        buf.writeVarInt(holdings.size());
        for (SimpleInvestService.Holding h : holdings) {
            buf.writeUtf(cut(h.key()), MAX_TEXT);
            buf.writeUtf(cut(h.name()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, h.amount()));
            buf.writeUtf(cut(h.status()), MAX_TEXT);
            buf.writeUtf(cut(h.redeemLabel()), MAX_TEXT);
        }
        SimpleInvestService.MarginInfo m = s.margin();
        buf.writeVarLong(Math.max(0, m.collateral()));
        buf.writeVarLong(Math.max(0, m.debt()));
        buf.writeVarInt(Math.max(-1, m.safetyPct()));
        buf.writeVarInt(Math.max(0, m.liquidationPct()));
        writeMap(buf, m.levered());
        writeMap(buf, m.shorts());
        List<SimpleBetService.Bet> bets =
                s.bets().subList(0, Math.min(MAX_ROWS, s.bets().size()));
        buf.writeVarInt(bets.size());
        for (SimpleBetService.Bet bet : bets) {
            buf.writeUtf(cut(bet.key()), MAX_TEXT);
            buf.writeUtf(cut(bet.name()), MAX_TEXT);
            buf.writeBoolean(bet.up());
            buf.writeVarLong(Math.max(0, bet.stake()));
            buf.writeVarLong(Math.max(0, bet.value()));
            buf.writeUtf(cut(bet.note()), MAX_TEXT);
            buf.writeBoolean(bet.tracked());
        }
        buf.writeVarInt(Math.max(0, s.futuresMarginBps()));
        SimpleBossService.BossInfo b = s.boss();
        buf.writeBoolean(b.publicCompany());
        buf.writeVarInt(Math.max(0, b.dividendPct()));
        buf.writeBoolean(b.wageActive());
        buf.writeVarLong(Math.max(0, b.wage()));
        buf.writeVarLong(Math.max(0, b.reservationWage()));
        List<SimpleBossService.Insurance> insurance =
                b.insurance().subList(0, Math.min(MAX_ROWS, b.insurance().size()));
        buf.writeVarInt(insurance.size());
        for (SimpleBossService.Insurance i : insurance) {
            buf.writeUtf(cut(i.product()), MAX_TEXT);
            buf.writeUtf(cut(i.name()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, i.coverage()));
            buf.writeLong(i.premium());
            buf.writeBoolean(i.active());
            buf.writeVarLong(Math.max(0, i.expiryDay()));
        }
        List<SimpleBossService.Target> targets =
                b.targets().subList(0, Math.min(MAX_ROWS, b.targets().size()));
        buf.writeVarInt(targets.size());
        for (SimpleBossService.Target t : targets) {
            buf.writeUtf(cut(t.companyId()), MAX_TEXT);
            buf.writeUtf(cut(t.name()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, t.sharePrice()));
            buf.writeVarInt(Math.max(0, t.myPctX10()));
            buf.writeBoolean(t.squeezable());
        }
        buf.writeBoolean(b.ownsBank());
        buf.writeVarLong(Math.max(0, b.bankMinCapital()));
        buf.writeVarLong(Math.max(0, b.bankCharterFee()));
        PlayerServicesResponsePacket.Cash c = s.cash() == null ? PlayerServicesResponsePacket.Cash.EMPTY : s.cash();
        buf.writeBoolean(c.enabled());
        for (long v : new long[] {
            c.balance(),
            c.cashOnHand(),
            c.emeralds(),
            c.emeraldToCash(),
            c.cashToEmerald(),
            c.baseBid(),
            c.baseAsk(),
            c.recoverPercent(),
            c.impactBps()
        }) buf.writeVarLong(Math.max(0, v));
        List<SimpleCenterService.Valuation> vals =
                s.valuations().subList(0, Math.min(MAX_ROWS, s.valuations().size()));
        buf.writeVarInt(vals.size());
        for (SimpleCenterService.Valuation v : vals) {
            buf.writeUtf(cut(v.symbol()), MAX_TEXT);
            buf.writeVarLong(Math.max(0, v.netAssets()));
            buf.writeLong(v.dailyProfit());
            buf.writeVarInt(Math.max(0, v.multipleX10()));
            buf.writeVarLong(Math.max(0, v.shares()));
        }
        List<Long> worth = s.worth()
                .subList(Math.max(0, s.worth().size() - MAX_WORTH), s.worth().size());
        buf.writeVarInt(worth.size());
        for (long w : worth) buf.writeVarLong(Math.max(0, w));
        writeLines(buf, s.ranks());
        buf.writeUtf(cut(s.betStats()), MAX_TEXT);
    }

    /** 首页身家走势最多 8 个点（7 天前到今天）。 */
    static final int MAX_WORTH = 8;

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
        long cost = buf.readVarLong(),
                valuation = buf.readVarLong(),
                ipoMin = buf.readVarLong(),
                stockUnlock = buf.readVarLong();
        String mode = buf.readUtf(16);
        List<String> extras = readLines(buf, MAX_NAMES);
        List<String> locked = readLines(buf), unlockedNow = readLines(buf);
        int n = buf.readVarInt();
        if (n < 0 || n > SimpleOpportunityService.MAX_CARDS) throw new IllegalArgumentException("cards");
        List<SimpleOpportunityService.Card> cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cards.add(new SimpleOpportunityService.Card(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarLong(),
                    buf.readUtf(MAX_TEXT),
                    buf.readBoolean()));
        }
        List<String> weather = readLines(buf), summary = readLines(buf);
        n = count(buf);
        List<SimpleInvestService.Product> products = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            products.add(new SimpleInvestService.Product(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarInt(),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarLong()));
        }
        n = count(buf);
        List<SimpleInvestService.Holding> holdings = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            holdings.add(new SimpleInvestService.Holding(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarLong(),
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT)));
        }
        SimpleInvestService.MarginInfo margin = new SimpleInvestService.MarginInfo(
                buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), readMap(buf), readMap(buf));
        n = count(buf);
        List<SimpleBetService.Bet> bets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bets.add(new SimpleBetService.Bet(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readBoolean(),
                    buf.readVarLong(),
                    buf.readVarLong(),
                    buf.readUtf(MAX_TEXT),
                    buf.readBoolean()));
        }
        int futuresMarginBps = buf.readVarInt();
        boolean isPublic = buf.readBoolean();
        int dividend = buf.readVarInt();
        boolean wageActive = buf.readBoolean();
        long wage = buf.readVarLong(), reservation = buf.readVarLong();
        n = count(buf);
        List<SimpleBossService.Insurance> insurance = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            insurance.add(new SimpleBossService.Insurance(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarLong(),
                    buf.readLong(),
                    buf.readBoolean(),
                    buf.readVarLong()));
        }
        n = count(buf);
        List<SimpleBossService.Target> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            targets.add(new SimpleBossService.Target(
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readBoolean()));
        }
        SimpleBossService.BossInfo boss = new SimpleBossService.BossInfo(
                isPublic,
                dividend,
                wageActive,
                wage,
                reservation,
                insurance,
                targets,
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readVarLong());
        boolean cashEnabled = buf.readBoolean();
        long[] cv = new long[9];
        for (int i = 0; i < cv.length; i++) cv[i] = buf.readVarLong();
        PlayerServicesResponsePacket.Cash cash = new PlayerServicesResponsePacket.Cash(
                cashEnabled, cv[0], cv[1], cv[2], cv[3], cv[4], cv[5], cv[6], cv[7], cv[8]);
        n = count(buf);
        List<SimpleCenterService.Valuation> vals = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            vals.add(new SimpleCenterService.Valuation(
                    buf.readUtf(MAX_TEXT), buf.readVarLong(), buf.readLong(), buf.readVarInt(), buf.readVarLong()));
        }
        n = buf.readVarInt();
        if (n < 0 || n > MAX_WORTH) throw new IllegalArgumentException("worth");
        List<Long> worth = new ArrayList<>(n);
        for (int i = 0; i < n; i++) worth.add(buf.readVarLong());
        List<String> ranks = readLines(buf);
        String betStats = buf.readUtf(MAX_TEXT);
        return new SimpleStatusPacket(new SimpleCenterService.Status(
                bank,
                rate,
                level,
                cost,
                valuation,
                ipoMin,
                stockUnlock,
                mode,
                extras,
                locked,
                unlockedNow,
                cards,
                weather,
                summary,
                products,
                holdings,
                margin,
                bets,
                futuresMarginBps,
                boss,
                cash,
                vals,
                worth,
                ranks,
                betStats));
    }

    public static void handle(SimpleStatusPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT, () -> () -> finance.client.simple.SimpleClientState.accept(p.status)));
        ctx.get().setPacketHandled(true);
    }
}
