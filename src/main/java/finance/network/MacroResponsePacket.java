package finance.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 宏观经济页签快照（服务端 → 客户端）。只含公开的聚合数据：不含玩家身份、坐标或账户。
 */
public record MacroResponsePacket(long requestId, boolean enabled, long cpiX100, int inflationX10, long population,
                                  int avgSol, long employed, long wages, boolean strike, boolean unrest,
                                  long treasury, long debt, long revenue, long expense, boolean austerity,
                                  List<String> laws, String proposal, List<GroupRow> groups,
                                  List<SettlementRow> settlements) {
    public static final int MAX_SETTLEMENTS = 16, MAX_GOODS = 16, MAX_LAWS = 8, MAX_GROUPS = 8, CATEGORIES = 5;

    public record GroupRow(String name, int cloutBps, int approval, int lobby) { }

    public record GoodRow(String commodity, int factorBps, long demand, long supply, long localPrice) { }

    public record SettlementRow(String name, int population, int sol, long employed, long income, boolean unrest,
                                List<Integer> fulfilmentPct, List<GoodRow> goods) { }

    public MacroResponsePacket {
        laws = bound(laws, MAX_LAWS);
        groups = bound(groups, MAX_GROUPS);
        settlements = bound(settlements, MAX_SETTLEMENTS);
        proposal = proposal == null ? "" : proposal;
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
        b.writeBoolean(p.strike);
        b.writeBoolean(p.unrest);
        b.writeLong(p.treasury);
        b.writeLong(p.debt);
        b.writeLong(p.revenue);
        b.writeLong(p.expense);
        b.writeBoolean(p.austerity);
        b.writeVarInt(p.laws.size());
        for (String s : p.laws) b.writeUtf(s, 64);
        b.writeUtf(p.proposal, 160);
        b.writeVarInt(p.groups.size());
        for (GroupRow g : p.groups) {
            b.writeUtf(g.name, 32);
            b.writeVarInt(g.cloutBps);
            b.writeVarInt(g.approval);
            b.writeVarInt(g.lobby);
        }
        b.writeVarInt(p.settlements.size());
        for (SettlementRow s : p.settlements) {
            b.writeUtf(s.name, 64);
            b.writeVarInt(s.population);
            b.writeVarInt(s.sol);
            b.writeLong(s.employed);
            b.writeLong(s.income);
            b.writeBoolean(s.unrest);
            List<Integer> f = s.fulfilmentPct == null ? List.of() : s.fulfilmentPct.subList(0, Math.min(CATEGORIES, s.fulfilmentPct.size()));
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
        boolean strike = b.readBoolean();
        boolean unrest = b.readBoolean();
        long treasury = b.readLong();
        long debt = b.readLong();
        long revenue = b.readLong();
        long expense = b.readLong();
        boolean austerity = b.readBoolean();
        int n = count(b, MAX_LAWS);
        List<String> laws = new ArrayList<>(n);
        for (int i = 0; i < n; i++) laws.add(b.readUtf(64));
        String proposal = b.readUtf(160);
        n = count(b, MAX_GROUPS);
        List<GroupRow> groups = new ArrayList<>(n);
        for (int i = 0; i < n; i++) groups.add(new GroupRow(b.readUtf(32), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        n = count(b, MAX_SETTLEMENTS);
        List<SettlementRow> settlements = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = b.readUtf(64);
            int pop = b.readVarInt();
            int sol = b.readVarInt();
            long emp = b.readLong();
            long income = b.readLong();
            boolean u = b.readBoolean();
            int fc = count(b, CATEGORIES);
            List<Integer> f = new ArrayList<>(fc);
            for (int j = 0; j < fc; j++) f.add(b.readVarInt());
            int gc = count(b, MAX_GOODS);
            List<GoodRow> goods = new ArrayList<>(gc);
            for (int j = 0; j < gc; j++) goods.add(new GoodRow(b.readUtf(64), b.readVarInt(), b.readLong(), b.readLong(), b.readLong()));
            settlements.add(new SettlementRow(name, pop, sol, emp, income, u, f, goods));
        }
        return new MacroResponsePacket(id, enabled, cpi, inflation, population, avgSol, employed, wages, strike, unrest,
                treasury, debt, revenue, expense, austerity, laws, proposal, groups, settlements);
    }

    private static int count(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("macro rows");
        return n;
    }

    public static void handle(MacroResponsePacket p, Supplier<NetworkEvent.Context> s) {
        var c = s.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> finance.client.MacroClientCache.accept(p)));
        c.setPacketHandled(true);
    }
}
