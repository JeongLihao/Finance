package finance.simulation;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyGameplayProfile;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.gameplay.company.CompanyProductionService;
import finance.macro.GoodBook;
import finance.macro.MacroEconomyManager;
import finance.macro.MacroEconomyService;
import finance.macro.MacroFormulas;
import finance.macro.MacroIndicators;
import finance.macro.MacroLaborService;
import finance.macro.SettlementEconomy;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageProfession;
import finance.village.VillageRecord;
import finance.village.VillageService;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 宏观实体经济的无头长期模拟：3 个聚落、5 家公司（含食品加工、林业、煤矿、纺织），
 * 按 {@code EconomyCycleService.tickDay} 的顺序逐日推进，记录人口、价格系数、CPI、货币和公司现金，
 * 用来做默认参数的平衡基线（见 docs/MACRO_ECONOMY.md “平衡基线”）。
 *
 * <p>每天的顺序：公司按需从国际市场买原料（代表玩家运货进仓库）→
 * {@code MacroEconomyService.beforeVillages} → {@code VillageService.processDay} →
 * {@code MacroEconomyService.afterVillages} → {@code NpcMarketMaker.naturalConsumeAll} →
 * {@code MacroLaborService.processDay} → {@code CompanyProductionService} → 国际市场 20 次动能衰减（一个 MC 日的分钟 tick）。
 * 第 {@link #SHOCK_START}～{@link #SHOCK_END} 天铁厂停工，检验失业 → 迁出 → 复工回流的反应。</p>
 *
 * <p>货币守恒：模拟里凭空消失的钱只有设施维护费（{@code FACILITY_PRODUCTION} 流水），凭空出现的钱只有
 * 国库借款时央行准备金不足的增发（{@code MACRO_DEBT_ISSUANCE}），
 * 所以每天都应满足 {@code 账户余额合计 + 公司现金合计 + 累计维护费 − 累计增发 = 初始值}。</p>
 */
public final class MacroLongRunSimulationService {
    public static final int STANDARD_DAYS = 365;
    public static final int MAX_DAYS = 1_000;
    /** 每家公司的初始现金。 */
    public static final long COMPANY_START_CASH = 20_000L;
    /** 开服磨合期结束、开始计算稳态 CPI 通胀的日子。 */
    public static final int STEADY_STATE_DAY = 120;
    /** 铁厂停工冲击的起止日：检验人口能否因失业下降、迁移，并在复工后恢复。 */
    public static final int SHOCK_START = 150;
    public static final int SHOCK_END = 240;

    private MacroLongRunSimulationService() { }

    /** 一个聚落一天的状态。 */
    public record SettlementDay(String name, int population, int sol, long employed, long income) { }

    /** 一天的全局指标。 */
    public record Daily(long day, List<SettlementDay> settlements, double cpi, int pinnedLow, int pinnedHigh,
                       int books, long totalMoney, long burned, long issued, long treasury, long debt,
                       boolean austerity, Map<String, Long> companyCash) {
        public long population() { return settlements.stream().mapToLong(SettlementDay::population).sum(); }
    }

    /** 某商品在某聚落里价格系数的全年统计。 */
    public record FactorStats(String settlement, String commodity, int finalBps, int minBps, int maxBps,
                              int longestPinnedLowRun, int longestPinnedHighRun) { }

    public record Result(int days, List<Daily> daily, List<FactorStats> factors, long initialMoney,
                         Map<String, Long> companyStartCash, boolean deterministic) {
        public Daily last() { return daily.get(daily.size() - 1); }

        /** 每天都满足：账户 + 公司现金 + 累计销毁 = 初始值。 */
        public boolean moneyConserved() {
            return daily.stream().allMatch(d -> d.totalMoney() + d.burned() - d.issued() == initialMoney);
        }

        /** 某聚落的人口序列。 */
        public List<Integer> populationOf(String name) {
            List<Integer> out = new ArrayList<>();
            for (Daily d : daily) for (SettlementDay s : d.settlements()) if (s.name().equals(name)) out.add(s.population());
            return out;
        }

        /**
         * 从第 fromDay 天到最后一天的 CPI 年化变化（%）。开服头几十天本地价格系数从 10000 落到 NPC 供货的 8500 附近，
         * 公司开始向国际市场卖货、NPC 库存略高于参考库存，这段属于磨合，稳态通胀从 {@link #STEADY_STATE_DAY} 起算。
         */
        public double annualizedCpiInflationPct(long fromDay) {
            Daily start = null;
            for (Daily d : daily) if (d.day() >= fromDay && d.cpi() > 0) { start = d; break; }
            Daily end = last();
            if (start == null || end.cpi() <= 0 || end.day() <= start.day()) return 0;
            return (Math.pow(end.cpi() / start.cpi(), 365.0 / (end.day() - start.day())) - 1) * 100.0;
        }

        /** 某公司全年最低现金。 */
        public long minCash(String company) {
            return daily.stream().mapToLong(d -> d.companyCash().getOrDefault(company, 0L)).min().orElse(0);
        }

        /** 任意商品在任意聚落里连续卡在价格系数上下限的最长天数。 */
        public int longestPinnedRun() {
            return factors.stream().mapToInt(f -> Math.max(f.longestPinnedLowRun(), f.longestPinnedHighRun())).max().orElse(0);
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "days=%d moneyConserved=%s deterministic=%s initialMoney=%d burned=%d issued=%d%n",
                    days, moneyConserved(), deterministic, initialMoney, last().burned(), last().issued()));
            sb.append(String.format(Locale.ROOT, "treasury=%d debt=%d austerityDays=%d%n", last().treasury(), last().debt(),
                    daily.stream().filter(Daily::austerity).count()));
            sb.append(String.format(Locale.ROOT, "CPI annualized day30..end=%.2f%% day%d..end=%.2f%%  CPI final=%.2f%n",
                    annualizedCpiInflationPct(30), STEADY_STATE_DAY, annualizedCpiInflationPct(STEADY_STATE_DAY), last().cpi()));
            for (int d : new int[] {0, 29, 59, 89, 119, 149, 179, 209, 239, 269, 299, 329, days - 1}) {
                if (d < 0 || d >= daily.size()) continue;
                Daily x = daily.get(d);
                sb.append(String.format(Locale.ROOT, "day %3d cpi=%6.2f pinned=%d/%d/%d pops=", x.day(), x.cpi(),
                        x.pinnedLow(), x.pinnedHigh(), x.books()));
                for (SettlementDay s : x.settlements()) {
                    sb.append(String.format(Locale.ROOT, "%s:%d(sol %d, emp %d, inc %d) ", s.name(), s.population(),
                            s.sol(), s.employed(), s.income()));
                }
                sb.append("cash=").append(x.companyCash()).append('\n');
            }
            for (FactorStats f : factors) {
                sb.append(String.format(Locale.ROOT, "  %s/%-6s final=%5d min=%5d max=%5d pinLow=%d pinHigh=%d%n",
                        f.settlement(), f.commodity(), f.finalBps(), f.minBps(), f.maxBps(),
                        f.longestPinnedLowRun(), f.longestPinnedHighRun()));
            }
            return sb.toString();
        }
    }

    /** 本次模拟各工厂采用的生产方式（设施等级取该档的最低要求）。 */
    private static finance.gameplay.company.factory.ProductionTier tier = finance.gameplay.company.factory.ProductionTier.MANUAL;

    /** 跑两遍同样的模拟并比较指纹（确定性），返回第一遍的结果；结束后恢复调用前的全部运行时状态。 */
    public static synchronized Result run(int days) {
        return run(days, finance.gameplay.company.factory.ProductionTier.MANUAL);
    }

    /** 同上，但全部工厂按指定档位搭好厂房（工业化场景）。 */
    public static synchronized Result run(int days, finance.gameplay.company.factory.ProductionTier productionTier) {
        if (days < 1 || days > MAX_DAYS) throw new IllegalArgumentException("days");
        tier = productionTier == null ? finance.gameplay.company.factory.ProductionTier.MANUAL : productionTier;
        CompoundTag original = new EconomySavedData().save(new CompoundTag());
        // 商品库存单独存档，不在 EconomySavedData 里，要另外备份
        Map<UUID, Map<String, Integer>> inventories = new LinkedHashMap<>();
        CommodityInventoryManager.getInventories().forEach((id, inv) -> inventories.put(id, Map.copyOf(inv.getAllCommodities())));
        try {
            Result first = runOnce(days);
            Result second = runOnce(days);
            boolean same = fingerprint(first).equals(fingerprint(second));
            return new Result(first.days(), first.daily(), first.factors(), first.initialMoney(),
                    first.companyStartCash(), same);
        } finally {
            EconomySavedData.resetRuntimeState();
            CommodityRegistry.resetToDefaults();
            EconomySavedData.load(original);
            CommodityInventoryManager.clearInventoriesDirect();
            inventories.forEach((id, goods) -> goods.forEach((c, q) -> CommodityInventoryManager.setCommodity(id, c, q)));
        }
    }

    // ================================================================ 场景

    private record Site(String name, SettlementRecord settlement, VillageRecord village) { }

    private record Firm(String name, Company company) { }

    private static Result runOnce(int days) {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        ModuleHealthRegistry.clear();
        registerCommodities();
        NpcMarketMaker.resetSeedState();
        NpcMarketMaker.seedNpcIfNeeded();

        List<Site> sites = List.of(
                site("alpha", 0, 16, 12, 6, 6),
                site("beta", 1_000, 12, 9, 4, 5),
                site("gamma", 2_000, 8, 6, 3, 3));
        List<Firm> firms = List.of(
                firm("Bakery", CompanyType.FOOD_PROCESSING, 40),
                firm("Lumber", CompanyType.FORESTRY, -40),
                firm("Colliery", CompanyType.COAL_MINING, 1_040),
                firm("Weavers", CompanyType.TEXTILE, 960),
                firm("Ironworks", CompanyType.RAW_MATERIALS, 2_040));
        Map<String, Long> startCash = new LinkedHashMap<>();
        for (Firm f : firms) startCash.put(f.name(), f.company().getCash());

        long initialMoney = totalMoney();
        long burned = 0;
        long issued = 0;
        List<Daily> daily = new ArrayList<>();
        Map<String, int[]> factorTrack = new LinkedHashMap<>();
        Firm shocked = firms.get(firms.size() - 1);
        for (int day = 1; day <= days; day++) {
            AccountManager.clearTransactions();
            // 冲击：铁厂在第 SHOCK_START 天把工资降到保留工资以下（全员离职、停产），SHOCK_END 天恢复
            if (day == SHOCK_START) MacroLaborService.setWage(shocked.company().getOwnerId(), shocked.company().getCompanyId(), 0);
            if (day == SHOCK_END) MacroLaborService.setWage(shocked.company().getOwnerId(), shocked.company().getCompanyId(),
                    finance.config.FinanceConfig.macroDefaultWage());
            // 玩家已按本次档位的清单搭好厂房；没有世界可扫描，每天刷新一次设备快照
            for (Firm f : firms) {
                for (CompanyFacilityRecord facility : CompanyFacilityManager.forCompany(f.company().getCompanyId())) {
                    finance.gameplay.company.factory.FactoryService.recordBuiltEquipment(facility,
                            tier, day);
                }
            }
            for (Firm f : firms) buyInputs(f.company());
            MacroEconomyService.beforeVillages(day);
            VillageService.processDay(null, day);
            MacroEconomyService.afterVillages(day);
            NpcMarketMaker.naturalConsumeAll();
            MacroLaborService.processDay(day);
            for (Firm f : firms) CompanyProductionService.processCompanyDay(f.company(), day);
            // 日利润结算（结算前扣利润税）
            for (Firm f : firms) {
                finance.macro.MacroFiscalService.collectProfitTax(f.company());
                f.company().settleDailyProfits(day);
            }
            // 一个 MC 日里国际市场有 20 次动能衰减（每 1200 tick），不调用随机噪音以保持确定性
            for (int i = 0; i < 20; i++) {
                NpcMarketMaker.tickAllMomentum();
                NpcMarketMaker.recalculateAll();
            }
            for (TransactionRecord t : AccountManager.getTransactions()) {
                if (t.getType() == TransactionType.FACILITY_PRODUCTION) burned += t.getAmount();
                if (t.getType() == TransactionType.MACRO_DEBT_ISSUANCE) issued += t.getAmount();
            }
            daily.add(snapshot(day, sites, firms, burned, issued, factorTrack));
        }
        return new Result(days, List.copyOf(daily), factorStats(sites, factorTrack), initialMoney, startCash, true);
    }

    private static void registerCommodities() {
        // 与 FinanceMod 注册的默认商品一致（单测环境不跑模组构造）
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 10));
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        CommodityRegistry.register(new Commodity("stone", "minecraft:stone", "Stone", CommodityCategory.BUILDING_BLOCKS, 3));
        CommodityRegistry.register(new Commodity("bread", "minecraft:bread", "Bread", CommodityCategory.FOOD, 20));
        CommodityRegistry.register(new Commodity("wood", "minecraft:oak_log", "Wood", CommodityCategory.BUILDING_BLOCKS, 4));
        CommodityRegistry.register(new Commodity("coal", "minecraft:coal", "Coal", CommodityCategory.RAW_MATERIALS, 6));
        CommodityRegistry.register(new Commodity("wool", "minecraft:white_wool", "Wool", CommodityCategory.MISCELLANEOUS, 9));
    }

    private static Site site(String name, int x, int farmer, int artisan, int guard, int other) {
        SettlementRecord s = SettlementManager.registerOrRecover(stable("settlement-" + name), "minecraft:overworld",
                new BlockPos(x, 64, 0), name);
        EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, farmer);
        mix.put(VillageProfession.ARTISAN, artisan);
        mix.put(VillageProfession.GUARD, guard);
        mix.put(VillageProfession.OTHER, other);
        UUID budget = AccountManager.getOrCreateSystemAccount(stable("budget-" + name)).getPlayerId();
        VillageRecord v = new VillageRecord(s.id(), farmer + artisan + guard + other, mix, budget, 0L, 0);
        if (!VillageManager.restoreVillage(v)) throw new IllegalStateException("village " + name);
        return new Site(name, s, v);
    }

    private static Firm firm(String name, CompanyType type, int x) {
        UUID owner = stable("owner-" + name);
        Company company = new Company(stable("company-" + name), name, type, COMPANY_START_CASH, owner);
        company.setAutoSellRatio(0.95);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(stable("warehouse-" + name), "minecraft:overworld",
                new BlockPos(x, 64, 30), owner, company.getCompanyId(), 100_000, WarehouseStatus.ACTIVE, 0, 0,
                WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        CompanyFacilityManager.restore(new CompanyFacilityRecord(stable("facility-" + name), company.getCompanyId(),
                "minecraft:overworld", new BlockPos(x, 64, 30), CompanyFacilityType.FACTORY_CONTROLLER, tier.requiredLevel(),
                CompanyFacilityStatus.ACTIVE, -1, warehouse.warehouseId()));
        return new Firm(name, company);
    }

    /**
     * 代表玩家把原料运进仓库：按国际市场卖价从 NPC 买入当天缺的原料（钱从公司现金转给 NPC 资金池，守恒）。
     */
    private static void buyInputs(Company company) {
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        Account npc = AccountManager.getAccounts().get(NpcMarketMaker.NPC_UUID);
        if (npc == null) return;
        // 按当前档位的投入采购（铁矿手工档要烧木头，工业档要烧煤），数量乘设施等级的吞吐倍率
        int throughput = finance.config.FinanceConfig.factoryThroughput(tier.requiredLevel());
        for (Map.Entry<String, Integer> e : finance.gameplay.company.factory.ProductionMethods
                .get(company.getType(), tier).inputs().entrySet()) {
            int missing = e.getValue() * throughput - CommodityInventoryManager.getCommodityAmount(custody, e.getKey());
            MarketPrice mp = NpcMarketMaker.getMarketPrice(e.getKey());
            if (missing <= 0 || mp == null || mp.getAskPrice() <= 0) continue;
            if (CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, e.getKey()) < missing) continue;
            long cost = mp.getAskPrice() * missing;
            // 留出一天工资，避免买了原料却发不出工资
            long reserve = MacroLaborService.wageOf(company.getCompanyId()) * MacroLaborService.jobs(
                    CompanyFacilityManager.forCompany(company.getCompanyId()).iterator().next());
            if (company.getCash() - cost < reserve || !company.withdraw(cost)) continue;
            npc.deposit(cost);
            CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, e.getKey(), missing);
            CommodityInventoryManager.addCommodity(custody, e.getKey(), missing);
            company.recordGameplayCost(cost);
        }
    }

    // ================================================================ 指标

    private static long totalMoney() {
        long sum = 0;
        for (Account a : AccountManager.getAccounts().values()) sum = Math.addExact(sum, a.getBalance());
        for (Company c : CompanyManager.getCompanies()) sum = Math.addExact(sum, c.getCash());
        return sum;
    }

    private static Daily snapshot(long day, List<Site> sites, List<Firm> firms, long burned, long issued, Map<String, int[]> track) {
        List<SettlementDay> rows = new ArrayList<>();
        int low = 0, high = 0, books = 0;
        for (Site s : sites) {
            SettlementEconomy econ = MacroEconomyManager.settlement(s.settlement().id());
            if (econ == null) continue;
            rows.add(new SettlementDay(s.name(), s.village().population(), econ.solBps(), econ.employed(), econ.lastIncome()));
            for (Map.Entry<String, GoodBook> e : econ.goods().entrySet()) {
                int f = e.getValue().factorBps();
                books++;
                if (f <= MacroFormulas.MIN_FACTOR_BPS) low++;
                if (f >= MacroFormulas.MAX_FACTOR_BPS) high++;
                // [当前连续低, 当前连续高, 最长低, 最长高, 最小, 最大]
                int[] t = track.computeIfAbsent(s.name() + "/" + e.getKey(),
                        k -> new int[] {0, 0, 0, 0, Integer.MAX_VALUE, Integer.MIN_VALUE});
                t[0] = f <= MacroFormulas.MIN_FACTOR_BPS ? t[0] + 1 : 0;
                t[1] = f >= MacroFormulas.MAX_FACTOR_BPS ? t[1] + 1 : 0;
                t[2] = Math.max(t[2], t[0]);
                t[3] = Math.max(t[3], t[1]);
                t[4] = Math.min(t[4], f);
                t[5] = Math.max(t[5], f);
            }
        }
        Map<String, Long> cash = new LinkedHashMap<>();
        for (Firm f : firms) cash.put(f.name(), f.company().getCash());
        return new Daily(day, List.copyOf(rows), MacroIndicators.cpiOrNegative(), low, high, books,
                totalMoney(), burned, issued, finance.macro.MacroFiscalService.balance(), finance.macro.Treasury.debt(),
                finance.macro.Treasury.austerity(), cash);
    }

    private static List<FactorStats> factorStats(List<Site> sites, Map<String, int[]> track) {
        List<FactorStats> out = new ArrayList<>();
        for (Site s : sites) {
            SettlementEconomy econ = MacroEconomyManager.settlement(s.settlement().id());
            if (econ == null) continue;
            for (Map.Entry<String, GoodBook> e : econ.goods().entrySet()) {
                int[] t = track.get(s.name() + "/" + e.getKey());
                if (t == null) continue;
                out.add(new FactorStats(s.name(), e.getKey(), e.getValue().factorBps(), t[4], t[5], t[2], t[3]));
            }
        }
        return List.copyOf(out);
    }

    private static String fingerprint(Result r) {
        Daily d = r.last();
        return d.day() + ":" + d.population() + ":" + Math.round(d.cpi() * 100) + ":" + d.totalMoney() + ":"
                + d.burned() + ":" + d.issued() + ":" + d.debt() + ":" + d.companyCash() + ":" + r.factors();
    }

    private static UUID stable(String key) {
        return UUID.nameUUIDFromBytes(("macro-sim-" + key).getBytes(StandardCharsets.UTF_8));
    }
}
