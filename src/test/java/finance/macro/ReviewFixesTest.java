package finance.macro;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.insurance.InsuranceManager;
import finance.market.NpcMarketMaker;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.warehouse.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 代码审查发现的问题的回归测试。 */
class ReviewFixesTest {
    private SettlementRecord alpha;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "小麦", CommodityCategory.FOOD, 8));
        AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, 10_000_000L);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "wheat", 100_000);
        alpha = SettlementManager.registerOrRecover(
                UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "alpha");
        MacroEconomyServiceTest.makeVillage(alpha, 12, 9, 4, 5);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    private CompanyFacilityRecord facility(Company company) {
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(
                UUID.randomUUID(),
                "minecraft:overworld",
                BlockPos.ZERO,
                company.getOwnerId(),
                company.getCompanyId(),
                4096,
                WarehouseStatus.ACTIVE,
                0,
                0,
                WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        CompanyFacilityRecord f = new CompanyFacilityRecord(
                UUID.randomUUID(),
                company.getCompanyId(),
                "minecraft:overworld",
                new BlockPos(10, 64, 10),
                CompanyFacilityType.FACTORY_CONTROLLER,
                1,
                CompanyFacilityStatus.ACTIVE,
                -1,
                warehouse.warehouseId());
        CompanyFacilityManager.restore(f);
        return f;
    }

    @Test
    void understaffedByChoiceIsNotAnInsurableShutdown() {
        Company company = new Company(UUID.randomUUID(), "Mill", CompanyType.RAW_MATERIALS, 100_000, UUID.randomUUID());
        CompanyManager.registerDirect(company);
        for (int d = 1; d <= 3; d++) {
            company.recordGameplayRevenue(5_000);
            company.settleDailyProfits(d);
        }
        CompanyFacilityRecord f = facility(company);
        f.setLastProcessedDay(10);
        f.setStatus(CompanyFacilityStatus.UNDERSTAFFED, 5);
        assertFalse(
                InsuranceManager.createBusinessInterruption(company.getCompanyId(), 10, 1)
                        .success(),
                "压低工资导致的停产不能领停业险");
        f.setStatus(CompanyFacilityStatus.MISSING_INPUT, 6);
        assertTrue(
                InsuranceManager.createBusinessInterruption(company.getCompanyId(), 10, 1)
                        .success(),
                "真正的断料停产仍可理赔");
    }

    @Test
    void cpiIsChainLinkedToTheOldIndex() {
        assertTrue(MacroIndicators.linkedIndex(100.0) < 0, "没有 CPI 数据时沿用旧指数");
        MacroEconomyServiceTest.runDay(1);
        double cpi = MacroIndicators.cpiOrNegative();
        assertTrue(cpi > 0);
        double link = MacroIndicators.link();
        assertTrue(link > 0, "日结里第一次读价格指数时就按当时的旧指数确定链接系数");
        double index = finance.metrics.EconomyMetricsService.getCurrentMetrics().priceIndex();
        assertEquals(cpi * link, index, 1e-6, "价格指数 = CPI × 链接系数");
        assertTrue(Math.abs(index - 100.0) < 15, "开服附近旧指数约 100，切换后不再跳到 80 多：" + index);
        assertEquals(cpi * link, MacroIndicators.linkedIndex(50.0), 1e-6, "链接系数只在第一次确定");
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(link, MacroIndicators.link(), 1e-9, "链接系数随存档保存");
    }

    @Test
    void caravanPersonalNewsIsRecordedWithoutAServer() {
        MacroWorldFeedback.onCaravanArrived(null, UUID.nameUUIDFromBytes(new byte[] {1}), alpha, "小麦", 64, 300);
        assertEquals(
                1,
                NewsService.personalDirect()
                        .get(UUID.nameUUIDFromBytes(new byte[] {1}))
                        .size(),
                "个人新闻属于报社，不依赖视觉反馈或服务器引用");
    }

    @Test
    void clearingEmploymentResetsSettlementCounts() {
        Company company = new Company(UUID.randomUUID(), "Mill", CompanyType.RAW_MATERIALS, 100_000, UUID.randomUUID());
        CompanyManager.registerDirect(company);
        CompanyFacilityRecord f = facility(company);
        MacroLaborService.processDay(1);
        assertTrue(MacroEconomyManager.employment(f.facilityId()) > 0);
        MacroLaborService.clearEmployment();
        assertEquals(0, MacroEconomyManager.employment(f.facilityId()));
        assertEquals(0, MacroEconomyManager.settlement(alpha.id()).employed(), "旧就业数不再影响需求和工会");
    }

    @Test
    void surveyFeeIsBookedAsSurvey() {
        UUID player = UUID.randomUUID();
        AccountManager.getOrCreateSystemAccount(player);
        AccountManager.deposit(player, 1_000);
        MacroEconomyManager.getOrCreate(alpha.id());
        assertTrue(MacroRadar.purchase(player, 1).success());
        assertTrue(
                AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == TransactionType.MACRO_SURVEY));
        assertTrue(AccountManager.getTransactions().stream()
                .noneMatch(t -> t.getType() == TransactionType.MACRO_POLITICS));
    }

    @Test
    void radarStillFindsTheBestArbitrage() {
        SettlementRecord beta = SettlementManager.registerOrRecover(
                UUID.randomUUID(), "minecraft:overworld", new BlockPos(300, 64, 0), "beta");
        MacroEconomyServiceTest.makeVillage(beta, 12, 9, 4, 5);
        MacroEconomyManager.getOrCreate(alpha.id()).book("wheat").factorBps = 5_000;
        MacroEconomyManager.getOrCreate(beta.id()).book("wheat").factorBps = 17_500;
        MacroEconomyManager.getOrCreate(alpha.id())
                .book("wheat")
                .history
                .addLast(new GoodBook.Point(1, 5_000, 1, 1, 1));
        MacroEconomyManager.getOrCreate(beta.id())
                .book("wheat")
                .history
                .addLast(new GoodBook.Point(1, 17_500, 1, 1, 1));
        var arbitrage = MacroRadar.scan(10).stream()
                .filter(o -> o.kind() == MacroRadar.Kind.ARBITRAGE)
                .toList();
        assertFalse(arbitrage.isEmpty());
        assertTrue(
                arbitrage.get(0).text().startsWith("商路 alpha → beta"),
                arbitrage.get(0).text());
    }
}
