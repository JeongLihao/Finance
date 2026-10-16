package finance.macro;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementStatus;
import finance.util.MathUtil;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import finance.village.VillageStall;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 宏观经济第一步：聚落市场与人口。
 *
 * <p>每日流程（由 {@code EconomyCycleService.tickDay} 驱动）：
 * <ol>
 *   <li>{@link #beforeVillages}：发福利；</li>
 *   <li>{@code VillageService} 调用 {@link #planNeeds} 拿到当天需求，采购后用 {@link #recordProcurement} 回报供需；</li>
 *   <li>{@link #afterVillages}：本地价格收盘、生活水平、人口增长、迁移、预算回流、CPI 快照、政治结算。</li>
 * </ol>
 * 关闭配置或模块熔断时所有钩子返回 null/原值，调用方走旧逻辑。</p>
 */
public final class MacroEconomyService {
    /** 可雇用人口占聚落人口的比例（基点）。 */
    public static final int WORKFORCE_BPS = 6_000;

    private MacroEconomyService() { }

    public static boolean active() {
        return FinanceConfig.macroEnabled() && ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.MACRO_ECONOMY);
    }

    static boolean usable(SettlementRecord s) {
        return s != null && (s.status() == SettlementStatus.ACTIVE || s.status() == SettlementStatus.REBUILDING
                || s.status() == SettlementStatus.RAID_ALERT);
    }

    // ================================================================ VillageService 钩子

    /**
     * 当天的需求（商品 → 数量）。未启用时返回 null，调用方用旧篮子。
     */
    public static Map<String, Long> planNeeds(VillageRecord village) {
        if (!active() || village == null) return null;
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(village.settlementId());
        if (econ == null) return null;
        UUID settlementId = village.settlementId();
        econ.plan.clear();
        econ.required.clear();
        econ.consumedUnits.clear();
        econ.required.putAll(MacroNeeds.requiredUnits(village.professionMix(), econ.employed));
        Map<String, Long> stallStock = stallStock(settlementId);
        Map<String, Long> needs = new TreeMap<>();
        for (NeedCategory category : NeedCategory.values()) {
            double units = econ.required.getOrDefault(category, 0.0);
            Map<String, Long> goods = MacroNeeds.allocate(category, units,
                    id -> CommodityRegistry.getCommodity(id) != null,
                    id -> stallStock.getOrDefault(id, 0L) > 0
                            || CommodityInventoryManager.getCommodityAmount(settlementId, id) > 0
                            || CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id) > 0,
                    id -> localPrice(econ, id));
            econ.plan.put(category, goods);
            goods.forEach((id, q) -> needs.merge(id, q, Long::sum));
            // 满足率按实际下单的整件份数计算；没有任何候选商品时按原始份数计（不足 1 份忽略）
            double planned = 0;
            for (Map.Entry<String, Long> g : goods.entrySet()) planned += g.getValue() * category.unitsOf(g.getKey());
            econ.required.put(category, planned > 0 ? planned : (units >= 1 ? units : 0));
        }
        return needs;
    }

    /** 村民愿付价溢价：有本地账本历史时用价格系数，否则用调用方的覆盖率溢价。 */
    public static int premiumBps(UUID settlementId, String commodityId, int fallback) {
        if (!active()) return fallback;
        SettlementEconomy econ = MacroEconomyManager.settlement(settlementId);
        GoodBook book = econ == null ? null : econ.goods.get(commodityId);
        if (book == null || book.history.isEmpty()) return fallback;
        return book.factorBps;
    }

    /** NPC 行商卖给聚落的价格按贸易政策调整。 */
    public static long applyTradePolicy(long caravanPrice) {
        if (caravanPrice <= 0 || !active() || !FinanceConfig.macroPoliticsEnabled()) return caravanPrice;
        int bps = MacroEconomyManager.law(LawGroup.TRADE).caravanPriceBps();
        if (bps == 10_000) return caravanPrice;
        long scaled = MathUtil.multiplyExactOrNegative1(caravanPrice, bps);
        return scaled <= 0 ? caravanPrice : Math.max(1, scaled / 10_000);
    }

    /** 采购结束后回报一种商品的需求、供给和实际消费。 */
    public static void recordProcurement(UUID settlementId, String commodityId, long demand, long supply, long consumed) {
        if (!active()) return;
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlementId);
        if (econ == null) return;
        GoodBook book = econ.book(commodityId);
        if (book != null) book.recordProcurement(demand, supply, consumed);
        // 实际消费按当天分摊比例折算回各需求类别
        Map<NeedCategory, Long> shares = econ.planFor(commodityId);
        long planned = shares.values().stream().mapToLong(Long::longValue).sum();
        if (planned <= 0 || consumed <= 0) return;
        long used = Math.min(consumed, planned);
        for (Map.Entry<NeedCategory, Long> e : shares.entrySet()) {
            double qty = (double) used * e.getValue() / planned;
            econ.consumedUnits.merge(e.getKey(), qty * e.getKey().unitsOf(commodityId), Double::sum);
        }
    }

    // ================================================================ 商队钩子

    /** 商队发车：采购量计入出发地需求（出口）。 */
    public static void recordExport(UUID settlementId, String commodityId, long qty) {
        if (!active() || qty <= 0) return;
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlementId);
        GoodBook book = econ == null ? null : econ.book(commodityId);
        if (book != null) book.exportsToday = GoodBook.sat(book.exportsToday, qty);
    }

    /**
     * 商队到货：商品放进目的地聚落托管供人口消费，并记为进口。返回是否入库成功。
     */
    public static boolean deliverImport(UUID settlementId, String commodityId, int qty) {
        if (!active() || qty <= 0) return false;
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlementId);
        GoodBook book = econ == null ? null : econ.book(commodityId);
        if (book == null || !CommodityInventoryManager.addCommodity(settlementId, commodityId, qty)) return false;
        book.importsToday = GoodBook.sat(book.importsToday, qty);
        return true;
    }

    /** 商队报价修正：启用时用本地价格系数代替区域报价修正。 */
    public static int caravanQuoteBps(SettlementRecord settlement, String commodityId, int fallback) {
        if (!active() || settlement == null) return fallback;
        SettlementEconomy econ = MacroEconomyManager.settlement(settlement.id());
        GoodBook book = econ == null ? null : econ.goods.get(commodityId);
        return book == null || book.history.isEmpty() ? fallback : book.factorBps;
    }

    // ================================================================ 日结

    public static void beforeVillages(long day) {
        if (!active() || day <= MacroEconomyManager.lastProcessedDay()) return;
        // 财政启用时由国库付利息、行政开支和福利；否则福利仍由 NPC 资金池支付
        if (MacroFiscalService.active()) MacroFiscalService.processDay(day);
        else PoliticsService.payWelfare(day);
    }

    public static void afterVillages(long day) {
        if (!active() || day <= MacroEconomyManager.lastProcessedDay()) return;
        MacroEconomyManager.setLastProcessedDay(day);
        int step = FinanceConfig.macroPriceStepBps();
        int growthBps = FinanceConfig.macroGrowthMaxBps();
        int solBonus = FinanceConfig.macroPoliticsEnabled()
                && MacroEconomyManager.law(LawGroup.LABOR) == LawLevel.WORKER_PROTECTION ? 500 : 0;
        boolean unrest = FinanceConfig.macroPoliticsEnabled() && MacroEconomyManager.unrest();
        Set<UUID> known = new HashSet<>();
        Map<String, List<UUID>> byDimension = new LinkedHashMap<>();
        for (SettlementRecord settlement : SettlementManager.settlements().values()) {
            known.add(settlement.id());
            if (!usable(settlement)) continue;
            VillageRecord village = VillageManager.get(settlement.id());
            if (village == null) continue;
            SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlement.id());
            if (econ == null) continue;
            // 当天集市没有结算（如袭击警报）时只收盘价格账本，不更新生活水平和人口
            boolean marketRan = village.lastMarketDay() == day && !econ.required.isEmpty();
            closeSettlementDay(econ, village, day, step, growthBps, solBonus, unrest, marketRan);
            recycleBudget(village);
            byDimension.computeIfAbsent(settlement.dimensionId(), k -> new ArrayList<>()).add(settlement.id());
        }
        MacroEconomyManager.retainSettlements(known);
        for (List<UUID> ids : byDimension.values()) migrate(ids, day);
        MacroIndicators.closeDay(day);
        PoliticsService.processDay(day);
        EconomySavedData.markDirty();
    }

    static void closeSettlementDay(SettlementEconomy econ, VillageRecord village, long day, int step,
                                   int growthBps, int solBonus, boolean unrest, boolean marketRan) {
        for (GoodBook book : econ.goods.values()) book.close(day, step);
        econ.unrest = unrest;
        if (!marketRan) {
            econ.lastIncome = econ.incomeToday;
            econ.incomeToday = 0;
            econ.plan.clear();
            econ.consumedUnits.clear();
            econ.required.clear();
            econ.pushHistory(day, village.population());
            return;
        }
        EnumMap<NeedCategory, Double> fulfilment = new EnumMap<>(NeedCategory.class);
        for (NeedCategory c : NeedCategory.values()) {
            double req = econ.required.getOrDefault(c, 0.0);
            double got = econ.consumedUnits.getOrDefault(c, 0.0);
            fulfilment.put(c, req <= 0 ? 1.0 : Math.min(1.0, got / req));
        }
        econ.lastFulfilment.clear();
        econ.lastFulfilment.putAll(fulfilment);
        int population = village.population();
        econ.lastIncome = econ.incomeToday;
        econ.incomeToday = 0;
        double incomePerCapita = population <= 0 ? 0 : (double) econ.lastIncome / population;
        int subsistence = MacroFormulas.subsistenceSol(population, FinanceConfig.villagePopulation());
        econ.solBps = MacroFormulas.smoothSol(econ.solBps,
                MacroFormulas.targetSol(fulfilment, incomePerCapita, solBonus + subsistence));

        econ.growthMilli += MacroFormulas.growthMilli(population, econ.solBps, growthBps, unrest);
        long whole = econ.growthMilli / 1000;
        econ.growthMilli -= whole * 1000;
        if (whole != 0) {
            int min = Math.min(FinanceConfig.macroMinPopulation(), FinanceConfig.macroMaxPopulation());
            int max = Math.max(FinanceConfig.macroMinPopulation(), FinanceConfig.macroMaxPopulation());
            long next = Math.max(min, Math.min(max, population + whole));
            // 已经低于下限/高于上限的旧聚落不强行拉回，只阻止继续越界
            if (population < min) next = Math.max(population, Math.min(next, max));
            if (population > max) next = Math.min(population, Math.max(next, min));
            if (next != population) village.resize(PopulationMix.resized(village.professionMix(), (int) next));
        }
        econ.plan.clear();
        econ.consumedUnits.clear();
        econ.required.clear();
        econ.pushHistory(day, village.population());
    }

    /** 聚落预算超过补足上限两倍的部分，视为村民消费回流 NPC 资金池，避免工资和福利在聚落里无限沉淀。 */
    static void recycleBudget(VillageRecord village) {
        UUID budget = village.budgetAccountId();
        if (budget == null) return;
        long daily = MathUtil.multiplyExactOrNegative1(FinanceConfig.villagePerCapitaBudget(), Math.max(0, village.population()));
        long cap = MathUtil.multiplyExactOrNegative1(daily, Math.max(1, FinanceConfig.villageBudgetReserveDays()));
        if (cap <= 0) return;
        long excess = AccountManager.getBalance(budget) - cap * 2;
        if (excess <= 0) return;
        if (AccountManager.moveFunds(budget, NpcMarketMaker.NPC_UUID, excess)) {
            AccountManager.addTransactionRecord(new TransactionRecord(budget, NpcMarketMaker.NPC_UUID, excess,
                    TransactionType.MACRO_VILLAGE_SPENDING, budget, "", 0));
        }
    }

    /** 同一维度内，从最不宜居的聚落迁一部分人口到最宜居的聚落（人数见 {@link MacroFormulas#migrants}）。 */
    static void migrate(List<UUID> settlementIds, long day) {
        if (settlementIds.size() < 2) return;
        int gap = FinanceConfig.macroMigrationGapBps();
        int rate = FinanceConfig.macroMigrationMaxBps();
        if (rate <= 0) return;
        UUID worst = null, best = null;
        int worstScore = Integer.MAX_VALUE, bestScore = Integer.MIN_VALUE;
        for (UUID id : settlementIds) {
            SettlementEconomy econ = MacroEconomyManager.settlement(id);
            if (econ == null) continue;
            int score = MacroFormulas.attractiveness(econ.solBps, econ.unrest);
            if (score < worstScore) { worstScore = score; worst = id; }
            if (score > bestScore) { bestScore = score; best = id; }
        }
        if (worst == null || best == null || worst.equals(best) || bestScore - worstScore < gap) return;
        VillageRecord from = VillageManager.get(worst);
        VillageRecord to = VillageManager.get(best);
        if (from == null || to == null) return;
        int min = FinanceConfig.macroMinPopulation();
        int max = FinanceConfig.macroMaxPopulation();
        long movers = MacroFormulas.migrants(from.population(), rate, day);
        movers = Math.min(movers, Math.max(0, from.population() - min));
        movers = Math.min(movers, Math.max(0, max - to.population()));
        if (movers <= 0) return;
        int fromNext = (int) (from.population() - movers);
        int toNext = (int) (to.population() + movers);
        if (from.resize(PopulationMix.resized(from.professionMix(), fromNext))) {
            if (!to.resize(PopulationMix.resized(to.professionMix(), toNext))) {
                from.resize(PopulationMix.resized(from.professionMix(), fromNext + (int) movers));
            }
        }
    }

    // ================================================================ 工具

    /** 本地单价 = NPC 中间价 × 本地价格系数，至少 1。 */
    public static long localPrice(SettlementEconomy econ, String commodityId) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (mp == null || mp.getMidPrice() <= 0) return 0;
        int factor = econ == null ? MacroFormulas.NEUTRAL_BPS : econ.factorBps(commodityId);
        long scaled = MathUtil.multiplyExactOrNegative1(mp.getMidPrice(), factor);
        return scaled <= 0 ? mp.getMidPrice() : Math.max(1, scaled / 10_000);
    }

    private static Map<String, Long> stallStock(UUID settlementId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (VillageStall stall : VillageManager.stallsForSettlement(settlementId)) {
            if (stall != null && stall.stock() > 0) out.merge(stall.commodityId(), (long) stall.stock(), Long::sum);
        }
        return out;
    }
}
