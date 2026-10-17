package finance.gameplay.company.factory;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.macro.EconomyAchievements;
import finance.macro.NewsService;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static finance.gameplay.company.factory.FactoryEquipment.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryNewsAndAchievementsTest {
    private UUID owner;
    private Company company;
    private UUID warehouseId;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "麦田农庄", CompanyType.FOOD, 1_000_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO, owner,
                company.getCompanyId(), WarehouseTier.INDUSTRIAL, 100_000, WarehouseStatus.ACTIVE, 0, 0,
                WarehousePermissionMode.OWNER_ONLY);
        assertTrue(WarehouseManager.restore(warehouse));
        profile.bindWarehouse(warehouse.warehouseId());
        warehouseId = warehouse.warehouseId();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    private CompanyFacilityRecord facility(int x, int level) {
        CompanyFacilityRecord f = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(), "minecraft:overworld",
                new BlockPos(x, 64, 0), CompanyFacilityType.FACTORY_CONTROLLER, level, CompanyFacilityStatus.ACTIVE, -1,
                warehouseId);
        f.factory().markNewBuild();
        assertTrue(CompanyFacilityManager.restore(f));
        return f;
    }

    @Test void industrialSwitchMakesTheNewsWithLayoffsAndUnlocksIndustrialRevolution() {
        CompanyFacilityRecord f = facility(0, 3);
        // 人力耕作摆了 6 个堆肥桶：30 个岗位
        f.factory().recordScan(Map.of(COMPOSTER, 6), 1, 16);
        FactoryService.processDay(null, 1);
        assertEquals(30, FactoryService.jobs(f));
        // 换成机械农场：8 台 × 2 岗 = 16，裁员 14 人
        f.factory().recordScan(Map.of(PISTON, 4, OBSERVER, 2, HOPPER, 2), 2, 16);
        f.factory().setTargetTier(ProductionTier.INDUSTRIAL);
        FactoryService.processDay(null, 2);
        assertEquals(ProductionTier.INDUSTRIAL, f.factory().activeTier());
        assertTrue(NewsService.pool().stream().anyMatch(i -> i.headline().equals("麦田农庄启用机械农场，裁员 14 人")),
                "周报应报道启用工业档：" + NewsService.pool());
        // 同一天重复处理不重复上报
        int before = NewsService.pool().size();
        FactoryService.processDay(null, 2);
        assertEquals(before, NewsService.pool().size());

        EconomyAchievements.processDay(null, 2);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.INDUSTRIAL_REVOLUTION));
    }

    @Test void artisanNeedsThreeProfitableManualFactories() {
        for (int i = 0; i < 3; i++) facility(i * 20, 1).factory().recordScan(Map.of(COMPOSTER, 2), 1, 16);
        FactoryService.processDay(null, 1);
        company.recordGameplayRevenue(1_000);
        EconomyAchievements.processDay(null, 1);
        assertTrue(EconomyAchievements.pending(owner).contains(EconomyAchievements.ARTISAN));
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.INDUSTRIAL_REVOLUTION));
    }

    @Test void twoManualFactoriesAreNotEnoughForArtisan() {
        for (int i = 0; i < 2; i++) facility(i * 20, 1).factory().recordScan(Map.of(COMPOSTER, 2), 1, 16);
        FactoryService.processDay(null, 1);
        company.recordGameplayRevenue(1_000);
        EconomyAchievements.processDay(null, 1);
        assertFalse(EconomyAchievements.pending(owner).contains(EconomyAchievements.ARTISAN));
    }

    @Test void tierCountsFeedTheMacroTab() {
        facility(0, 1).factory().recordScan(Map.of(COMPOSTER, 2), 1, 16);
        facility(20, 1);
        FactoryService.processDay(null, 1);
        assertEquals(java.util.List.of(1, 0, 0, 1), FactoryService.tierCounts());
    }
}
