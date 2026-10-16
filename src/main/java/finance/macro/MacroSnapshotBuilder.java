package finance.macro;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.network.MacroResponsePacket;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 把宏观经济状态整理成 GUI 页签用的公开快照。 */
public final class MacroSnapshotBuilder {
    private MacroSnapshotBuilder() { }

    public static MacroResponsePacket build(long requestId) {
        boolean enabled = MacroEconomyService.active();
        if (enabled && PoliticsService.active()) PoliticsService.recompute();
        MacroEconomyManager.Snapshot s = MacroEconomyManager.latestSnapshot();
        double cpi = MacroIndicators.cpiOrNegative();
        List<String> laws = new ArrayList<>();
        for (LawGroup g : LawGroup.values()) laws.add(g.displayName() + "·" + MacroEconomyManager.law(g).displayName());
        String proposal = "";
        MacroEconomyManager.Proposal p = MacroEconomyManager.proposal();
        if (p != null) {
            long[] t = PoliticsService.tally(p.level());
            proposal = "提案 " + p.level().group().displayName() + "→" + p.level().displayName() + " 第" + p.endDay()
                    + "天表决 支持" + t[0] + "/反对" + t[1];
        }
        List<MacroResponsePacket.GroupRow> groups = new ArrayList<>();
        for (InterestGroup ig : InterestGroup.values()) {
            groups.add(new MacroResponsePacket.GroupRow(ig.displayName(), MacroEconomyManager.cloutBps(ig),
                    MacroEconomyManager.approval(ig), MacroEconomyManager.lobby(ig)));
        }
        List<MacroResponsePacket.SettlementRow> rows = new ArrayList<>();
        for (SettlementRecord settlement : SettlementManager.settlements().values()) {
            if (rows.size() >= MacroResponsePacket.MAX_SETTLEMENTS) break;
            VillageRecord village = VillageManager.get(settlement.id());
            SettlementEconomy econ = MacroEconomyManager.settlement(settlement.id());
            if (village == null || econ == null) continue;
            List<Integer> fulfil = new ArrayList<>();
            for (NeedCategory c : NeedCategory.values()) {
                Double v = econ.lastFulfilment.get(c);
                fulfil.add(v == null ? -1 : (int) Math.round(v * 100));
            }
            List<MacroResponsePacket.GoodRow> goods = new ArrayList<>();
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                if (goods.size() >= MacroResponsePacket.MAX_GOODS) break;
                Commodity def = CommodityRegistry.getCommodity(e.getKey());
                GoodBook b = e.getValue();
                goods.add(new MacroResponsePacket.GoodRow(def == null ? e.getKey() : def.getDisplayName(), b.factorBps,
                        b.lastDemand, b.lastSupply, MacroEconomyService.localPrice(econ, e.getKey())));
            }
            rows.add(new MacroResponsePacket.SettlementRow(settlement.displayName(), village.population(), econ.solBps,
                    econ.employed, econ.lastIncome, econ.unrest, fulfil, goods));
        }
        return new MacroResponsePacket(requestId, enabled, cpi < 0 ? 0 : Math.round(cpi * 100),
                (int) Math.round(MacroIndicators.annualizedInflationPct() * 10),
                s == null ? 0 : s.population(), s == null ? 0 : s.avgSol(), s == null ? 0 : s.employed(),
                s == null ? 0 : s.wages(), MacroEconomyManager.strike(), MacroEconomyManager.unrest(),
                laws, proposal, groups, rows);
    }
}
