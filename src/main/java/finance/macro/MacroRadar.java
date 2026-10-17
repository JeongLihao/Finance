package finance.macro;

import finance.caravan.CaravanPricing;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 商机雷达：从当前经济状态里找出最值得做的几件事。只读，不改任何状态。
 *
 * <p>四类机会：短缺（本地价格系数 ≥ 130% 且供不应求）、商路套利（扣掉保镖工资后每趟 64 件仍赚钱）、
 * 廉价劳动力（可雇用人口里还有 ≥ 10 人没工作）、政策风向（进行中的提案）。按分数排序取前几名。</p>
 */
public final class MacroRadar {
    public static final int SHORTAGE_BPS = 13_000;
    public static final int TRIP_QTY = 64;
    public static final int IDLE_WORKERS = 10;
    /** 套利时每种商品只比较的出发地/目的地个数。 */
    static final int ARBITRAGE_CANDIDATES = 3;

    public enum Kind { SHORTAGE, ARBITRAGE, LABOR, POLICY }

    /** 一条商机。text 是给玩家看的一句话。 */
    public record Opportunity(Kind kind, long score, String text) { }

    private MacroRadar() { }

    public static List<Opportunity> scan(int limit) {
        List<Opportunity> out = new ArrayList<>();
        if (!MacroEconomyService.active()) return out;
        shortages(out);
        arbitrage(out);
        labor(out);
        policy(out);
        out.sort(Comparator.comparingLong(Opportunity::score).reversed().thenComparing(Opportunity::text));
        return out.subList(0, Math.min(Math.max(0, limit), out.size()));
    }

    private static void shortages(List<Opportunity> out) {
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            SettlementRecord s = SettlementManager.get(econ.settlementId);
            if (s == null || !MacroEconomyService.usable(s)) continue;
            for (Map.Entry<String, GoodBook> e : econ.goods.entrySet()) {
                GoodBook b = e.getValue();
                if (b.factorBps < SHORTAGE_BPS || b.lastSupply >= b.lastDemand) continue;
                long gap = b.lastDemand - b.lastSupply;
                out.add(new Opportunity(Kind.SHORTAGE, b.factorBps / 10 + gap,
                        String.format("%s 缺%s：本地价 %d（%+d%%），每天还差约 %d 件", s.displayName(), name(e.getKey()),
                                MacroEconomyService.localPrice(econ, e.getKey()), (b.factorBps - 10_000) / 100, gap)));
            }
        }
    }

    private static void arbitrage(List<Opportunity> out) {
        List<SettlementRecord> sites = new ArrayList<>();
        for (SettlementRecord s : SettlementManager.settlements().values()) {
            if (MacroEconomyService.usable(s) && MacroEconomyManager.settlement(s.id()) != null) sites.add(s);
        }
        long wage = CaravanPricing.guardWage(0);
        // 每趟利润只取决于两端价格，与距离无关：每个维度每种商品只需比较最便宜的几个出发地和最贵的几个目的地，
        // 线性扫描定价后排序，代替逐对枚举（O(聚落² × 商品)）
        java.util.Map<String, java.util.Map<String, List<long[]>>> buys = new java.util.HashMap<>();
        java.util.Map<String, java.util.Map<String, List<long[]>>> sells = new java.util.HashMap<>();
        for (int i = 0; i < sites.size(); i++) {
            SettlementRecord s = sites.get(i);
            for (String commodity : MacroEconomyManager.settlement(s.id()).goods.keySet()) {
                long buy = CaravanPricing.sourceBuyPrice(commodity, s);
                long sell = CaravanPricing.destSellPrice(commodity, s, 0);
                if (buy > 0) buys.computeIfAbsent(s.dimensionId(), k -> new java.util.HashMap<>())
                        .computeIfAbsent(commodity, k -> new ArrayList<>()).add(new long[]{buy, i});
                if (sell > 0) sells.computeIfAbsent(s.dimensionId(), k -> new java.util.HashMap<>())
                        .computeIfAbsent(commodity, k -> new ArrayList<>()).add(new long[]{sell, i});
            }
        }
        for (var dim : sells.entrySet()) {
            for (var good : dim.getValue().entrySet()) {
                List<long[]> from = buys.getOrDefault(dim.getKey(), java.util.Map.of()).getOrDefault(good.getKey(), List.of());
                List<long[]> to = new ArrayList<>(good.getValue());
                if (from.isEmpty()) continue;
                List<long[]> cheapest = new ArrayList<>(from);
                cheapest.sort(Comparator.comparingLong(a -> a[0]));
                to.sort(Comparator.comparingLong((long[] a) -> a[0]).reversed());
                for (long[] b : cheapest.subList(0, Math.min(ARBITRAGE_CANDIDATES, cheapest.size()))) {
                    for (long[] s : to.subList(0, Math.min(ARBITRAGE_CANDIDATES, to.size()))) {
                        if (b[1] == s[1] || s[0] <= b[0]) continue;
                        long profit = (s[0] - b[0]) * TRIP_QTY - wage;
                        if (profit <= 0) continue;
                        SettlementRecord src = sites.get((int) b[1]);
                        SettlementRecord dst = sites.get((int) s[1]);
                        long days = CaravanPricing.travelDays(CaravanPricing.distanceBlocks(src, dst));
                        if (days <= 0) continue;
                        out.add(new Opportunity(Kind.ARBITRAGE, profit,
                                String.format("商路 %s → %s 运%s：每趟 %d 件约赚 %d，路上 %d 天", src.displayName(), dst.displayName(),
                                        name(good.getKey()), TRIP_QTY, profit, days)));
                    }
                }
            }
        }
    }

    private static void labor(List<Opportunity> out) {
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) {
            SettlementRecord s = SettlementManager.get(econ.settlementId);
            VillageRecord v = VillageManager.get(econ.settlementId);
            if (s == null || v == null || !MacroEconomyService.usable(s)) continue;
            long idle = (long) v.population() * MacroEconomyService.WORKFORCE_BPS / 10_000 - econ.employed;
            if (idle < IDLE_WORKERS) continue;
            out.add(new Opportunity(Kind.LABOR, idle * 20,
                    String.format("%s 有约 %d 人没工作：在附近建厂，工资开到 %d 就能招到人", s.displayName(), idle,
                            MacroLaborService.reservationWage())));
        }
    }

    /** 央行动向：议息前瞻窗口里提示锁定利率的机会。 */
    private static void policy(List<Opportunity> out) {
        var outlook = finance.policy.PolicyMeetingService.outlook();
        if (outlook.nextMeetingDay() < 0) return;
        long today = MacroEconomyManager.lastProcessedDay();
        long left = today == Long.MIN_VALUE ? Long.MAX_VALUE : outlook.nextMeetingDay() - today;
        if (left > Math.max(1, finance.config.FinanceConfig.policyGuidanceLeadDays())) return;
        String hint = outlook.recommendedBps() < outlook.currentBps() ? "可能降息：现在认购央行债券或开定期存款，能锁住更高的利率"
                : outlook.recommendedBps() > outlook.currentBps() ? "可能加息：贷款和发债成本会上升，央行债券的新利率会更高"
                : "大概率维持利率";
        out.add(new Opportunity(Kind.POLICY, 300, String.format("第 %d 天央行议息（当前 %.2f%%）：%s",
                outlook.nextMeetingDay(), outlook.currentBps() / 100.0, hint)));
    }

    /** 一次付费商情调查的结果。 */
    public record Purchase(boolean success, String message, List<Opportunity> opportunities) { }

    /** 付费做一次商情调查：费用进 NPC 资金池，冷却期内拒绝。 */
    public static Purchase purchase(java.util.UUID player, long day) {
        if (player == null) return new Purchase(false, "只有玩家可以做商情调查。", List.of());
        if (!MacroEconomyService.active()) return new Purchase(false, "宏观经济未启用。", List.of());
        long last = NewsService.lastRadarDay(player);
        int cooldown = Math.max(0, finance.config.FinanceConfig.macroRadarCooldownDays());
        if (last != Long.MIN_VALUE && day - last < cooldown) {
            return new Purchase(false, "调查员还在路上，第 " + (last + cooldown) + " 天才能再查。", List.of());
        }
        long cost = Math.max(0, finance.config.FinanceConfig.macroRadarCost());
        if (cost > 0) {
            if (!payToPool(player, cost)) return new Purchase(false, "余额不足，商情调查要 " + cost + "。", List.of());
        }
        NewsService.markRadar(player, day);
        finance.data.EconomySavedData.markDirty();
        return new Purchase(true, "商情调查（第 " + day + " 天）", scan(3));
    }

    private static boolean payToPool(java.util.UUID player, long cost) {
        if (!finance.account.AccountManager.moveFunds(player, finance.market.NpcMarketMaker.NPC_UUID, cost)) return false;
        finance.account.AccountManager.addTransactionRecord(new finance.account.TransactionRecord(player,
                finance.market.NpcMarketMaker.NPC_UUID, cost, finance.account.TransactionType.MACRO_SURVEY, player, "radar", 0));
        return true;
    }

    static String name(String commodityId) {
        Commodity c = CommodityRegistry.getCommodity(commodityId);
        return c == null ? commodityId : c.getDisplayName();
    }
}
