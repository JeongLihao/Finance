package finance.gameplay.company.factory;

import finance.account.AccountManager;
import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.gameplay.company.*;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 厂房接入生产与雇工后的端到端行为（没有世界，设备快照直接写入）。 */
class FactoryProductionTest {
    private Company company;
    private CompanyFacilityRecord facility;
    private UUID custody;

    private void setup(CompanyType type, int level) {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        AccountManager.clearAccountsDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
        UUID owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "Works", type, 1_000_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO, owner,
                company.getCompanyId(), 100_000, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        facility = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(), "minecraft:overworld",
                new BlockPos(1, 64, 1), CompanyFacilityType.FACTORY_CONTROLLER, level, CompanyFacilityStatus.ACTIVE, -1,
                warehouse.warehouseId());
        facility.factory().markNewBuild();
        CompanyFacilityManager.restore(facility);
        custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
    }

    @AfterEach
    void cleanup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        AccountManager.clearAccountsDirect();
        CompanyGameplayManager.clearDirect();
        CompanyFacilityManager.clearDirect();
    }

    private int stock(String id) { return CommodityInventoryManager.getCommodityAmount(custody, id); }

    private boolean produce(long day) { return CompanyProductionService.processFacility(company, facility, day); }

    @Test void newBuildWithoutEquipmentStopsWithoutTouchingInputs() {
        setup(CompanyType.FOOD_PROCESSING, 1);
        CommodityInventoryManager.addCommodity(custody, "wheat", 100);
        long cash = company.getCash();
        assertFalse(produce(1));
        assertEquals(CompanyFacilityStatus.EQUIPMENT_MISSING, facility.status());
        assertEquals(100, stock("wheat"), "缺设备不扣原料");
        assertEquals(cash, company.getCash(), "缺设备不扣维护费");
        assertEquals(0, FactoryService.jobs(facility));
    }

    @Test void manualTierUsesItsOwnInputsAndOutputs() {
        setup(CompanyType.RAW_MATERIALS, 1);
        FactoryService.recordBuiltEquipment(facility, ProductionTier.MANUAL, 1);
        CommodityInventoryManager.addCommodity(custody, "wood", 50);
        assertTrue(produce(1));
        assertEquals(100, stock("iron"));
        assertEquals(30, stock("wood"), "手工冶炼烧 20 木头");
        assertEquals(15, FactoryService.jobs(facility), "3 座熔炉 × 5 岗");
    }

    @Test void industrialTierAtLevelThreeWithRetoolThenFullOutput() {
        setup(CompanyType.FOOD, 3);
        FactoryService.recordBuiltEquipment(facility, ProductionTier.MANUAL, 1);
        assertTrue(produce(1));
        int afterManual = stock("wheat");
        assertEquals(80 * 4, afterManual, "3 级吞吐 ×4");
        // 搭好机械农场并切换：次日生效，改造期产量减半
        facility.factory().recordScan(Map.of(FactoryEquipment.COMPOSTER, 2, FactoryEquipment.PISTON, 4,
                FactoryEquipment.OBSERVER, 2, FactoryEquipment.HOPPER, 2), 2, 16);
        facility.factory().setTargetTier(ProductionTier.INDUSTRIAL);
        CommodityInventoryManager.addCommodity(custody, "iron", 1_000);
        CommodityInventoryManager.addCommodity(custody, "coal", 1_000);
        assertTrue(produce(2));
        assertEquals(ProductionTier.INDUSTRIAL, facility.factory().activeTier());
        assertEquals(180 * 4 / 2, stock("wheat") - afterManual, "改造期 ×0.5");
        assertEquals(1_000 - 3 * 4 / 2, stock("iron"), "投入同比例减半");
        // 改造期结束后满产
        for (long day = 3; day <= 4; day++) {
            facility.factory().recordScan(facility.factory().equipment(), day, 16);
            produce(day);
        }
        facility.factory().recordScan(facility.factory().equipment(), 5, 16);
        int before = stock("wheat");
        assertTrue(produce(5), () -> "status=" + facility.status() + " active=" + facility.factory().activeTier() + " scanDay=" + facility.factory().equipmentScanDay() + " eq=" + facility.factory().equipment());
        assertEquals(180 * 4, stock("wheat") - before);
        assertEquals(16, FactoryService.jobs(facility), "机械农场只认本档设备：8 台 × 2 岗");
    }

    @Test void legacyFacilityKeepsOldRulesDuringProvisionalWorkshop() {
        setup(CompanyType.RAW_MATERIALS, 2);
        CompanyFacilityRecord legacy = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(),
                "minecraft:overworld", new BlockPos(30, 64, 30), CompanyFacilityType.FACTORY_CONTROLLER, 2,
                CompanyFacilityStatus.ACTIVE, -1, facility.boundWarehouseId());
        CompanyFacilityManager.disable(facility.facilityId());
        assertTrue(CompanyFacilityManager.restore(legacy));
        facility = legacy;
        assertTrue(produce(10));
        assertEquals(200, stock("iron"), "临时作坊沿用原规则：不烧木头");
        assertEquals(20, FactoryService.jobs(facility), "岗位沿用 等级 × 10");
        assertTrue(produce(24));
        assertFalse(produce(25), "14 天后没设备就停产");
        assertEquals(CompanyFacilityStatus.EQUIPMENT_MISSING, facility.status());
    }

    @Test void degradingKeepsProducingAtLowerTier() {
        setup(CompanyType.BUILDING_BLOCKS, 2);
        FactoryService.recordBuiltEquipment(facility, ProductionTier.IMPROVED, 1);
        CommodityInventoryManager.addCommodity(custody, "iron", 100);
        assertTrue(produce(1));
        assertEquals(180 * 2, stock("stone"));
        // 铁砧被搬走：自动降到人工采石
        facility.factory().recordScan(Map.of(FactoryEquipment.STONECUTTER, 3), 2, 16);
        int before = stock("stone");
        assertTrue(produce(2));
        assertEquals(ProductionTier.MANUAL, facility.factory().activeTier());
        assertEquals(120 * 2, stock("stone") - before, "退化不进改造期");
    }
}
