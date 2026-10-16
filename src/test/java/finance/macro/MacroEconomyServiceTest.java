package finance.macro;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageProfession;
import finance.village.VillageRecord;
import finance.village.VillageService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 第一步：聚落市场、生活水平、人口增长与迁移，以及和村庄集市、商队的衔接。 */
class MacroEconomyServiceTest {
    private SettlementRecord settlement;
    private VillageRecord village;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 20));
        CommodityRegistry.register(new Commodity("stone", "minecraft:stone", "Stone", CommodityCategory.BUILDING_BLOCKS, 4));
        CommodityRegistry.register(new Commodity("wool", "minecraft:white_wool", "Wool", CommodityCategory.MISCELLANEOUS, 9));
        ModuleHealthRegistry.clear();
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", 100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "stone", 100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wool", 0);
        settlement = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        village = makeVillage(settlement, 12, 9, 4, 5);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    static VillageRecord makeVillage(SettlementRecord s, int farmer, int artisan, int guard, int other) {
        EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, farmer);
        mix.put(VillageProfession.ARTISAN, artisan);
        mix.put(VillageProfession.GUARD, guard);
        mix.put(VillageProfession.OTHER, other);
        UUID budget = AccountManager.getOrCreateSystemAccount(UUID.randomUUID()).getPlayerId();
        VillageRecord v = new VillageRecord(s.id(), farmer + artisan + guard + other, mix, budget, 0L, 0);
        assertTrue(VillageManager.restoreVillage(v));
        return v;
    }

    static void runDay(long day) {
        MacroEconomyService.beforeVillages(day);
        VillageService.processDay(null, day);
        MacroEconomyService.afterVillages(day);
    }

    private SettlementEconomy econ() {
        return MacroEconomyManager.settlement(settlement.id());
    }

    @Test void shortageRaisesLocalPriceAndLeavesCategoryUnmet() {
        runDay(1);
        GoodBook wool = econ().goods().get("wool");
        assertNotNull(wool, "羊毛是衣物类别的唯一候选，即使没货也要下需求");
        assertTrue(wool.lastDemand() > 0);
        assertEquals(0, wool.lastSupply());
        assertEquals(11_000, wool.factorBps(), "没有供给 → 价格系数第一天上调一个步长");
        assertEquals(0.0, econ().lastFulfilment().get(NeedCategory.CLOTHING), 1e-9);
        assertEquals(1.0, econ().lastFulfilment().get(NeedCategory.FOOD), 1e-9, "NPC 有小麦库存，食物满足");
        GoodBook wheat = econ().goods().get("wheat");
        assertTrue(wheat.lastSupply() >= wheat.lastDemand());
        assertTrue(wheat.factorBps() < 10_000, "小麦供大于求 → 价格下行");
    }

    @Test void supplyArrivingFulfilsTheCategoryAndPriceFalls() {
        for (long d = 1; d <= 5; d++) runDay(d);
        int highFactor = econ().goods().get("wool").factorBps();
        assertTrue(highFactor > 10_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wool", 100_000);
        runDay(6);
        assertEquals(1.0, econ().lastFulfilment().get(NeedCategory.CLOTHING), 1e-9);
        assertTrue(econ().goods().get("wool").factorBps() < highFactor, "有了供给价格回落");
    }

    /** 补齐取暖的候选商品，让五类需求都能满足。 */
    private void stockAllNeeds() {
        CommodityRegistry.register(new Commodity("coal", "minecraft:coal", "Coal", CommodityCategory.RAW_MATERIALS, 6));
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "coal", 1_000_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wool", 1_000_000);
    }

    @Test void incomeGrowsPopulationAndFamineShrinksIt() {
        stockAllNeeds();
        for (long d = 1; d <= 60; d++) {
            MacroEconomyManager.getOrCreate(settlement.id()).addIncome(300); // 人均 10 元/天，相当于有工资收入
            runDay(d);
        }
        int grown = village.population();
        assertTrue(econ().solBps() > 5_000, "需求都满足且有收入，生活水平高于 50%：" + econ().solBps());
        assertTrue(grown > 30, "人口应增长：" + grown);
        assertEquals(grown, village.professionMix().values().stream().mapToInt(Integer::intValue).sum());

        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 0);
        for (long d = 61; d <= 160; d++) runDay(d);
        assertTrue(econ().solBps() < 5_000, "断粮后生活水平跌破 50%：" + econ().solBps());
        assertTrue(village.population() < grown, "断粮后人口减少");
        assertEquals(17_500, econ().goods().get("wheat").factorBps(), "持续缺粮价格到顶");
        assertTrue(village.population() >= 5, "不低于人口下限");
    }

    @Test void villageWithoutIncomeSettlesAtItsNaturalSize() {
        stockAllNeeds();
        for (long d = 1; d <= 30; d++) runDay(d);
        assertEquals(30, village.population(), "自然规模（villagePopulation=30）、没有收入：不涨不跌");
        assertEquals(5_000, econ().solBps(), 50);
        village.resize(PopulationMix.resized(village.professionMix(), 60));
        for (long d = 31; d <= 200; d++) runDay(d);
        assertTrue(village.population() < 60 && village.population() > 30, "超过自然规模又没有收入时回落：" + village.population());
    }

    @Test void migrationMovesPeopleTowardTheBetterSettlement() {
        SettlementRecord other = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld",
                new BlockPos(500, 64, 0), "beta");
        VillageRecord rich = makeVillage(other, 12, 9, 4, 5);
        MacroEconomyManager.getOrCreate(settlement.id()).solBps = 2_000;
        MacroEconomyManager.getOrCreate(other.id()).solBps = 8_000;
        MacroEconomyService.migrate(List.of(settlement.id(), other.id()), 1);
        assertEquals(30, village.population(), "30 人 × 0.5% = 0.15 人/天，每 7 天迁 1 人");
        for (long d = 2; d <= 20; d++) MacroEconomyService.migrate(List.of(settlement.id(), other.id()), d);
        assertEquals(27, village.population(), "约每 7 天迁出 1 人：第 7、14、16 天");
        assertEquals(33, rich.population());
        MacroEconomyManager.getOrCreate(settlement.id()).solBps = 7_600;
        for (long d = 21; d <= 60; d++) MacroEconomyService.migrate(List.of(settlement.id(), other.id()), d);
        assertEquals(27, village.population(), "差距不足 500 不迁移");
    }

    @Test void excessVillageBudgetFlowsBackToNpcPool() {
        AccountManager.deposit(village.budgetAccountId(), 1_000_000L);
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        runDay(1);
        long cap = 100L * village.population() * 3;
        assertTrue(AccountManager.getBalance(village.budgetAccountId()) <= cap * 2, "超过上限两倍的部分回流");
        assertTrue(AccountManager.getBalance(NpcMarketMaker.NPC_UUID) > pool - cap, "资金池收回村民消费");
    }

    @Test void caravanImportsLandInSettlementCustody() {
        assertTrue(MacroEconomyService.deliverImport(settlement.id(), "wool", 40));
        assertEquals(40, CommodityInventoryManager.getCommodityAmount(settlement.id(), "wool"));
        MacroEconomyService.recordExport(settlement.id(), "wheat", 25);
        runDay(1);
        assertEquals(40, econ().goods().get("wool").lastImports());
        assertEquals(25, econ().goods().get("wheat").lastExports());
        assertTrue(econ().lastFulfilment().get(NeedCategory.CLOTHING) > 0, "进口的羊毛被人口消费");
    }

    @Test void raidAlertDayDoesNotCountAsAGoodDay() {
        runDay(1);
        int sol = econ().solBps();
        settlement.raidAlert(2, "raid:2");
        runDay(2);
        assertEquals(sol, econ().solBps(), "集市没开的日子不更新生活水平");
        assertEquals(30, village.population());
    }

    @Test void welfareIsPaidOncePerDay() {
        MacroEconomyManager.setLaw(LawLevel.POOR_RELIEF);
        runDay(1);
        long pool = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        MacroEconomyService.beforeVillages(1);
        assertEquals(pool, AccountManager.getBalance(NpcMarketMaker.NPC_UUID), "同一天重复调用不重复发福利");
    }

    @Test void readOnlyModuleFallsBackToLegacyVillageLogic() {
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.MACRO_ECONOMY, ModuleRunState.READ_ONLY, "test", 0);
        assertNull(MacroEconomyService.planNeeds(village));
        runDay(1);
        assertNull(econ(), "模块只读时不写任何宏观状态");
        assertEquals(30, village.population());
    }

    @Test void cpiTracksBasketCost() {
        assertTrue(MacroIndicators.cpiOrNegative() < 0, "没有数据时返回 −1，价格指数沿用旧算法");
        for (long d = 1; d <= 3; d++) runDay(d);
        double cpi = MacroIndicators.cpiOrNegative();
        assertTrue(cpi > 0);
        assertEquals(3, MacroEconomyManager.snapshots().size());
        assertEquals(30, MacroEconomyManager.latestSnapshot().population());
    }

    @Test void stateSurvivesSaveAndLoad() {
        for (long d = 1; d <= 3; d++) runDay(d);
        int factor = econ().goods().get("wool").factorBps();
        int sol = econ().solBps();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        SettlementEconomy loaded = MacroEconomyManager.settlement(settlement.id());
        assertNotNull(loaded);
        assertEquals(factor, loaded.goods().get("wool").factorBps());
        assertEquals(sol, loaded.solBps());
        assertEquals(3, loaded.goods().get("wool").history().size());
        assertEquals(3, MacroEconomyManager.lastProcessedDay());
        assertTrue(ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.MACRO_ECONOMY));

        saved.remove(MacroPersistence.ROOT);
        EconomySavedData.load(saved);
        assertNull(MacroEconomyManager.settlement(settlement.id()), "旧存档没有宏观标签时按刚启用初始化");
    }
}
