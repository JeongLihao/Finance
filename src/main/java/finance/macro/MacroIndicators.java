package finance.macro;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.village.VillageManager;
import finance.village.VillageRecord;

import java.util.List;
import java.util.Map;

/**
 * 宏观指标：消费篮子 CPI 与每日快照。
 *
 * <p>CPI = Σ(各聚落各商品昨日需求 × 本地价) / Σ(昨日需求 × 基准价) × 100。
 * 有数据时 {@code EconomyMetricsService} 用它代替等权价格指数，央行议息的通胀缺口就来自真实生活成本。</p>
 */
public final class MacroIndicators {
    private MacroIndicators() { }

    /** 当前 CPI（100 = 基准）；没有聚落需求数据或未启用时返回 −1。 */
    public static double cpiOrNegative() {
        if (!MacroEconomyService.active()) return -1;
        double now = 0, base = 0;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                long qty = e.getValue().lastDemand;
                if (qty <= 0) continue;
                Commodity def = CommodityRegistry.getCommodity(e.getKey());
                long price = MacroEconomyService.localPrice(econ, e.getKey());
                if (def == null || def.getBasePrice() <= 0 || price <= 0) continue;
                now += (double) qty * price;
                base += (double) qty * def.getBasePrice();
            }
        }
        return base <= 0 ? -1 : now / base * 100.0;
    }

    /** 最近 30 天的年化 CPI 通胀（%）；历史不足时返回 0。 */
    public static double annualizedInflationPct() {
        List<MacroEconomyManager.Snapshot> snaps = MacroEconomyManager.snapshots();
        if (snaps.size() < 2) return 0;
        MacroEconomyManager.Snapshot last = snaps.get(snaps.size() - 1);
        MacroEconomyManager.Snapshot base = snaps.get(0);
        for (MacroEconomyManager.Snapshot s : snaps) {
            if (s.day() <= last.day() - 30) base = s; else break;
        }
        long days = Math.max(1, last.day() - base.day());
        if (base.cpiX100() <= 0 || last.cpiX100() <= 0 || base == last) return 0;
        return (last.cpiX100() - base.cpiX100()) / (double) base.cpiX100() * (365.0 / days) * 100.0;
    }

    static void closeDay(long day) {
        long population = 0, employed = 0, wages = 0, consumption = 0;
        double solWeighted = 0;
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            VillageRecord village = VillageManager.get(econ.settlementId);
            int pop = village == null ? 0 : village.population();
            population += pop;
            solWeighted += (double) econ.solBps * pop;
            employed += econ.employed;
            wages = GoodBook.sat(wages, econ.lastIncome);
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                long price = MacroEconomyService.localPrice(econ, e.getKey());
                if (price > 0) consumption = GoodBook.sat(consumption, finance.util.MathUtil.saturatedMultiplyNonNegative(
                        e.getValue().lastConsumed, price));
            }
        }
        double cpi = cpiOrNegative();
        int avgSol = population <= 0 ? 0 : (int) Math.round(solWeighted / population);
        MacroEconomyManager.addSnapshot(new MacroEconomyManager.Snapshot(day,
                cpi < 0 ? 0 : Math.round(cpi * 100), population, avgSol, employed, wages, consumption));
    }
}
