package finance.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 宏观经济页签快照（服务端 → 客户端）。只含公开的聚合数据：不含玩家身份、坐标或账户。
 */
public record MacroResponsePacket(
        long requestId,
        boolean enabled,
        long cpiX100,
        int inflationX10,
        long population,
        int avgSol,
        long employed,
        long wages,
        CentralBankRow centralBank,
        List<SettlementRow> settlements,
        List<Integer> factoryTiers,
        List<RivalRow> rivals) {
    public static final int MAX_RIVALS = 8;

    /** 竞争对手一行：名称、主营商品、人格、当前策略、是否上市、最近一条动作。 */
    public record RivalRow(
            String name, String commodity, String personality, String strategy, boolean listed, String lastAction) {}
    /** 全服工厂档位统计的项数：手工、改良、工业、停产或临时作坊。 */
    public static final int FACTORY_TIER_BUCKETS = 4;

    public static final int MAX_SETTLEMENTS = 16, MAX_GOODS = 16, CATEGORIES = 5, MAX_STABILIZATION = 4;

    /**
     * 中央银行概况：基准利率、物价目标与实际通胀（基点）、活期准备金率、下次议息日与倾向、
     * 公开市场操作的存量（银行持有的央行票据 / 欠央行的回购款）、最近一天的商品平准动作。
     */
    public record CentralBankRow(
            int rateBps,
            int targetBps,
            int inflationBps,
            int reserveBps,
            long nextMeetingDay,
            String guidance,
            long absorbed,
            long injected,
            List<String> stabilization) {
        public CentralBankRow {
            guidance = guidance == null ? "" : guidance;
            stabilization = bound(stabilization, MAX_STABILIZATION);
        }

        public static final CentralBankRow EMPTY = new CentralBankRow(0, 0, 0, 0, -1, "", 0, 0, List.of());
    }

    public record GoodRow(String commodity, int factorBps, long demand, long supply, long localPrice) {}

    public record SettlementRow(
            String name,
            int population,
            int sol,
            long employed,
            long income,
            List<Integer> fulfilmentPct,
            List<GoodRow> goods) {}

    public MacroResponsePacket {
        centralBank = centralBank == null ? CentralBankRow.EMPTY : centralBank;
        settlements = bound(settlements, MAX_SETTLEMENTS);
        factoryTiers = bound(factoryTiers, FACTORY_TIER_BUCKETS);
        rivals = bound(rivals, MAX_RIVALS);
    }

    private static <T> List<T> bound(List<T> list, int max) {
        return list == null ? List.of() : List.copyOf(list.subList(0, Math.min(max, list.size())));
    }

    public static void encode(MacroResponsePacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId);
        b.writeBoolean(p.enabled);
        b.writeLong(p.cpiX100);
        b.writeVarInt(p.inflationX10);
        b.writeLong(p.population);
        b.writeVarInt(p.avgSol);
        b.writeLong(p.employed);
        b.writeLong(p.wages);
        CentralBankRow cb = p.centralBank;
        b.writeVarInt(cb.rateBps());
        b.writeVarInt(cb.targetBps());
        b.writeVarInt(cb.inflationBps());
        b.writeVarInt(cb.reserveBps());
        b.writeLong(cb.nextMeetingDay());
        b.writeUtf(cut(cb.guidance(), 16), 16);
        b.writeLong(cb.absorbed());
        b.writeLong(cb.injected());
        b.writeVarInt(cb.stabilization().size());
        for (String s : cb.stabilization()) b.writeUtf(cut(s, 64), 64);
        b.writeVarInt(p.settlements.size());
        for (SettlementRow s : p.settlements) {
            b.writeUtf(s.name, 64);
            b.writeVarInt(s.population);
            b.writeVarInt(s.sol);
            b.writeLong(s.employed);
            b.writeLong(s.income);
            List<Integer> f = s.fulfilmentPct == null
                    ? List.of()
                    : s.fulfilmentPct.subList(0, Math.min(CATEGORIES, s.fulfilmentPct.size()));
            b.writeVarInt(f.size());
            for (int v : f) b.writeVarInt(v);
            List<GoodRow> goods = s.goods == null ? List.of() : s.goods.subList(0, Math.min(MAX_GOODS, s.goods.size()));
            b.writeVarInt(goods.size());
            for (GoodRow g : goods) {
                b.writeUtf(g.commodity, 64);
                b.writeVarInt(g.factorBps);
                b.writeLong(g.demand);
                b.writeLong(g.supply);
                b.writeLong(g.localPrice);
            }
        }
        b.writeVarInt(p.factoryTiers.size());
        for (int v : p.factoryTiers) b.writeVarInt(Math.max(0, v));
        b.writeVarInt(p.rivals.size());
        for (RivalRow r : p.rivals) {
            b.writeUtf(cut(r.name, 32), 32);
            b.writeUtf(cut(r.commodity, 64), 64);
            b.writeUtf(cut(r.personality, 16), 16);
            b.writeUtf(cut(r.strategy, 16), 16);
            b.writeBoolean(r.listed);
            b.writeUtf(cut(r.lastAction, 64), 64);
        }
    }

    private static String cut(String s, int max) {
        String v = s == null ? "" : s;
        return v.length() > max ? v.substring(0, max) : v;
    }

    public static MacroResponsePacket decode(FriendlyByteBuf b) {
        long id = b.readLong();
        boolean enabled = b.readBoolean();
        long cpi = b.readLong();
        int inflation = b.readVarInt();
        long population = b.readLong();
        int avgSol = b.readVarInt();
        long employed = b.readLong();
        long wages = b.readLong();
        int rate = b.readVarInt(), target = b.readVarInt(), cbInflation = b.readVarInt(), reserve = b.readVarInt();
        long nextMeeting = b.readLong();
        String guidance = b.readUtf(16);
        long absorbed = b.readLong(), injected = b.readLong();
        int n = count(b, MAX_STABILIZATION);
        List<String> stabilization = new ArrayList<>(n);
        for (int i = 0; i < n; i++) stabilization.add(b.readUtf(64));
        CentralBankRow cb = new CentralBankRow(
                rate, target, cbInflation, reserve, nextMeeting, guidance, absorbed, injected, stabilization);
        n = count(b, MAX_SETTLEMENTS);
        List<SettlementRow> settlements = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = b.readUtf(64);
            int pop = b.readVarInt();
            int sol = b.readVarInt();
            long emp = b.readLong();
            long income = b.readLong();
            int fc = count(b, CATEGORIES);
            List<Integer> f = new ArrayList<>(fc);
            for (int j = 0; j < fc; j++) f.add(b.readVarInt());
            int gc = count(b, MAX_GOODS);
            List<GoodRow> goods = new ArrayList<>(gc);
            for (int j = 0; j < gc; j++)
                goods.add(new GoodRow(b.readUtf(64), b.readVarInt(), b.readLong(), b.readLong(), b.readLong()));
            settlements.add(new SettlementRow(name, pop, sol, emp, income, f, goods));
        }
        n = count(b, FACTORY_TIER_BUCKETS);
        List<Integer> factoryTiers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) factoryTiers.add(b.readVarInt());
        n = count(b, MAX_RIVALS);
        List<RivalRow> rivals = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            rivals.add(new RivalRow(
                    b.readUtf(32), b.readUtf(64), b.readUtf(16), b.readUtf(16), b.readBoolean(), b.readUtf(64)));
        return new MacroResponsePacket(
                id,
                enabled,
                cpi,
                inflation,
                population,
                avgSol,
                employed,
                wages,
                cb,
                settlements,
                factoryTiers,
                rivals);
    }

    private static int count(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("macro rows");
        return n;
    }

    public static void handle(MacroResponsePacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> finance.client.MacroClientCache.accept(p)));
        c.setPacketHandled(true);
    }
}
