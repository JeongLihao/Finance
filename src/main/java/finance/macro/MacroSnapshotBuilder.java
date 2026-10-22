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
    private MacroSnapshotBuilder() {}

    /** 中央银行概况（宏观页签顶部和“更多操作”的央行页共用）。 */
    public static MacroResponsePacket.CentralBankRow centralBankRow() {
        var outlook = finance.policy.PolicyMeetingService.outlook();
        List<String> stabilization = new ArrayList<>();
        for (var a : finance.centralbank.PriceStabilizationService.lastActions()) {
            if (stabilization.size() >= MacroResponsePacket.MAX_STABILIZATION) break;
            stabilization.add((a.sold() ? "抛售" : "收储") + MacroRadar.name(a.commodityId()) + " " + a.quantity());
        }
        return new MacroResponsePacket.CentralBankRow(
                finance.policy.MonetaryPolicyService.benchmarkRateBasisPoints(),
                finance.centralbank.CentralBankService.inflationTargetBps(),
                finance.centralbank.CentralBankService.inflationBps(),
                finance.centralbank.CentralBankService.demandReserveBps(),
                outlook.nextMeetingDay(),
                outlook.guidanceDirection(),
                finance.centralbank.CentralBankService.outstandingAbsorbed(),
                finance.centralbank.CentralBankService.outstandingInjected(),
                stabilization);
    }

    public static MacroResponsePacket build(long requestId) {
        boolean enabled = MacroEconomyService.active();
        MacroEconomyManager.Snapshot s = MacroEconomyManager.latestSnapshot();
        double cpi = MacroIndicators.cpiOrNegative();
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
                goods.add(new MacroResponsePacket.GoodRow(
                        def == null ? e.getKey() : def.getDisplayName(),
                        b.factorBps,
                        b.lastDemand,
                        b.lastSupply,
                        MacroEconomyService.localPrice(econ, e.getKey())));
            }
            rows.add(new MacroResponsePacket.SettlementRow(
                    settlement.displayName(),
                    village.population(),
                    econ.solBps,
                    econ.employed,
                    econ.lastIncome,
                    fulfil,
                    goods));
        }
        return new MacroResponsePacket(
                requestId,
                enabled,
                cpi < 0 ? 0 : Math.round(cpi * 100),
                (int) Math.round(MacroIndicators.annualizedInflationPct() * 10),
                s == null ? 0 : s.population(),
                s == null ? 0 : s.avgSol(),
                s == null ? 0 : s.employed(),
                s == null ? 0 : s.wages(),
                centralBankRow(),
                rows,
                finance.gameplay.company.CompanyFacilityManager.tierCounts(),
                finance.rival.RivalService.snapshotRows(MacroResponsePacket.MAX_RIVALS));
    }
}
