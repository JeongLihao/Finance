package finance.macro;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import finance.village.VillageStall;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 经济成就：没有服务器时成就进待发队列，便于断言。 */
class EconomyAchievementsTest {
    private UUID owner;
    private Company company;
    private SettlementRecord settlement;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "Wheat", CommodityCategory.FOOD, 8));
        CommodityRegistry.register(new Commodity("wool", "minecraft:white_wool", "Wool", CommodityCategory.MISCELLANEOUS, 9));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        owner = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(owner);
        AccountManager.deposit(owner, 100_000L);
        settlement = SettlementManager.registerOrRecover(UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        MacroEconomyServiceTest.makeVillage(settlement, 24, 18, 8, 10);
        company = new Company(UUID.randomUUID(), "Mill", CompanyType.RAW_MATERIALS, 100_000, owner);
        CompanyManager.registerDirect(company);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    private void facility(int level) {
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO, owner,
                company.getCompanyId(), 4096, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        CompanyFacilityManager.restore(new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(), "minecraft:overworld",
                new BlockPos(10, 64, 10), CompanyFacilityType.FACTORY_CONTROLLER, level, CompanyFacilityStatus.ACTIVE, -1,
                warehouse.warehouseId()));
    }

    private void days(int from, int to) {
        for (int d = from; d <= to; d++) {
            MacroLaborService.processDay(d);
            EconomyAchievements.processDay(null, d);
        }
    }

    @Test void generousEmployerEarnsFirstHireAndGoodBoss() {
        facility(2);
        assertTrue(MacroLaborService.setWage(owner, company.getCompanyId(), 30));
        days(1, 1);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.FIRST_HIRE));
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.GOOD_BOSS), "第一天还没招满 20 人");
        days(2, 4);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.GOOD_BOSS));
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.SWEATSHOP));
    }

    @Test void stingyEmployerEarnsSweatshop() {
        facility(1);
        assertTrue(MacroLaborService.setWage(owner, company.getCompanyId(), MacroLaborService.reservationWage()));
        days(1, 4);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.SWEATSHOP));
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.GOOD_BOSS));
    }

    @Test void strikeIsAwardedOnceWhenItStarts() {
        facility(1);
        days(1, 1);
        MacroEconomyManager.setUnrestFlags(true, false);
        days(2, 2);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.STRIKE));
        assertTrue(EconomyAchievements.lastStrike());
    }

    @Test void deliveringFoodToAStarvingSettlementIsFamineRelief() {
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlement.id());
        econ.lastFulfilment.put(NeedCategory.FOOD, 0.3);
        EconomyAchievements.onCaravanDelivered(owner, settlement.id(), "wool");
        assertTrue(EconomyAchievements.pending(owner).isEmpty(), "羊毛不是粮食");
        EconomyAchievements.onCaravanDelivered(owner, settlement.id(), "wheat");
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.FAMINE_RELIEF));
        UUID other = UUID.randomUUID();
        econ.lastFulfilment.put(NeedCategory.FOOD, 0.9);
        EconomyAchievements.onCaravanDelivered(other, settlement.id(), "wheat");
        assertTrue(EconomyAchievements.pending(other).isEmpty(), "不饿的地方不算救灾");
    }

    @Test void dominatingALocalMarketIsMonopoly() {
        SettlementEconomy econ = MacroEconomyManager.getOrCreate(settlement.id());
        econ.book("wheat").lastConsumed = 40;
        UUID rival = UUID.randomUUID();
        EconomyAchievements.recordStallSale(owner, settlement.id(), "wheat", 30);
        EconomyAchievements.recordStallSale(rival, settlement.id(), "wheat", 10);
        EconomyAchievements.processDay(null, 1);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.MONOPOLY), "30/40 = 75%");
        assertFalse(EconomyAchievements.pending(rival).contains(EconomyAchievements.MONOPOLY));
    }

    @Test void sittingOnStockDuringAShortageIsHoarding() {
        VillageStall stall = VillageManager.registerStall(settlement.id(), owner, "wool", 20);
        stall.addStock(5);
        MacroEconomyManager.getOrCreate(settlement.id()).book("wool").factorBps = 17_500;
        EconomyAchievements.processDay(null, 1);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.HOARDER));
    }

    @Test void politicsAchievements() {
        assertTrue(PoliticsService.lobby(owner, InterestGroup.FINANCIERS, 5_000).success());
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.LOBBYIST));
        assertTrue(PoliticsService.propose(owner, LawLevel.PUBLIC_HEALTH, 1).success());
        PoliticsService.processDay(10);
        String outcome = MacroEconomyManager.lawHistory().get(0).passed() ? EconomyAchievements.LAWMAKER : EconomyAchievements.FAILED_BILL;
        assertTrue(EconomyAchievements.pending(owner).contains(outcome));
    }

    @Test void prosperousSettlementAchievements() {
        facility(1);
        VillageRecord village = VillageManager.get(settlement.id());
        days(1, 1);
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.BOOMTOWN));
        village.resize(PopulationMix.resized(village.professionMix(), 120));
        MacroEconomyManager.getOrCreate(settlement.id()).solBps = 8_500;
        days(2, 2);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.BOOMTOWN));
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.GOLDEN_AGE));
    }

    @Test void debtFreeStreakCountsDaysAndResetsOnDebt() {
        for (int d = 1; d <= 5; d++) EconomyAchievements.processDay(null, d);
        assertEquals(5, EconomyAchievements.debtFreeStreak());
        Treasury.setDebt(100);
        EconomyAchievements.processDay(null, 6);
        assertEquals(0, EconomyAchievements.debtFreeStreak());
    }

    @Test void pendingAchievementsSurviveSaveLoad() {
        EconomyAchievements.award(owner, EconomyAchievements.LAWMAKER);
        EconomyAchievements.award(owner, "not_an_achievement");
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(java.util.Set.of(EconomyAchievements.LAWMAKER), EconomyAchievements.pending(owner));
    }

    @Test void everyAchievementHasAdvancementAndTranslations() throws Exception {
        JsonObject zh = json("assets/finance/lang/zh_cn.json");
        JsonObject en = json("assets/finance/lang/en_us.json");
        for (String id : EconomyAchievements.ALL) {
            JsonObject adv = json("data/finance/advancements/" + id + ".json");
            assertEquals(id, adv.getAsJsonObject("criteria").getAsJsonObject("event").getAsJsonObject("conditions")
                    .get("event").getAsString(), id);
            assertTrue(id.length() <= 48, "触发器事件名最长 48");
            for (String key : new String[]{"advancement.finance." + id, "advancement.finance." + id + ".desc"}) {
                assertTrue(zh.has(key), key);
                assertTrue(en.has(key), key);
            }
            String parent = adv.get("parent").getAsString().substring("finance:".length());
            assertNotNull(getClass().getClassLoader().getResource("data/finance/advancements/" + parent + ".json"), parent);
        }
    }

    private JsonObject json(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
