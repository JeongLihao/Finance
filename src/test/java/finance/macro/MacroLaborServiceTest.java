package finance.macro;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.village.VillageRecord;
import finance.warehouse.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 第二步：雇工、发工资、按在岗比例生产。 */
class MacroLaborServiceTest {
    private UUID owner;
    private Company company;
    private CompanyFacilityRecord facility;
    private UUID custody;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "Mill", CompanyType.RAW_MATERIALS, 100_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(
                UUID.randomUUID(),
                "minecraft:overworld",
                BlockPos.ZERO,
                owner,
                company.getCompanyId(),
                4096,
                WarehouseStatus.ACTIVE,
                0,
                0,
                WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        facility = new CompanyFacilityRecord(
                UUID.randomUUID(),
                company.getCompanyId(),
                "minecraft:overworld",
                new BlockPos(10, 64, 10),
                CompanyFacilityType.FACTORY_CONTROLLER,
                1,
                CompanyFacilityStatus.ACTIVE,
                -1,
                warehouse.warehouseId());
        CompanyFacilityManager.restore(facility);
        custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    private VillageRecord settlementAt(int x, String name) {
        SettlementRecord s = SettlementManager.registerOrRecover(
                UUID.randomUUID(), "minecraft:overworld", new BlockPos(x, 64, 0), name);
        return MacroEconomyServiceTest.makeVillage(s, 12, 9, 4, 5);
    }

    @Test
    void withoutAnySettlementFacilitiesRunFullyStaffed() {
        MacroLaborService.processDay(1);
        assertEquals(10_000, MacroLaborService.staffingBps(facility), "没有劳动力市场时不改变旧行为");
        assertEquals(1, CompanyProductionService.processCompanyDay(company, 1).producedFacilities());
        assertEquals(100, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
    }

    @Test
    void hiringRampsUpAndWagesFlowToTheSettlement() {
        VillageRecord village = settlementAt(0, "alpha");
        long cash = company.getCash();
        MacroLaborService.processDay(1);
        assertEquals(4, MacroEconomyManager.employment(facility.facilityId()), "每天最多招岗位数的 1/3（向上取整）");
        assertEquals(4_000, MacroLaborService.staffingBps(facility));
        long wage = MacroLaborService.wageOf(company.getCompanyId());
        assertEquals(cash - 4 * wage, company.getCash());
        assertEquals(4 * wage, AccountManager.getBalance(village.budgetAccountId()));
        assertTrue(AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == TransactionType.MACRO_WAGE));
        assertEquals(4, MacroEconomyManager.settlement(village.settlementId()).employed());

        MacroLaborService.processDay(2);
        MacroLaborService.processDay(3);
        assertEquals(10, MacroEconomyManager.employment(facility.facilityId()), "三天招满 10 个岗位");
        MacroLaborService.processDay(3);
        assertEquals(10, MacroEconomyManager.employment(facility.facilityId()), "同一天不重复招工发薪");
    }

    @Test
    void productionScalesWithStaffing() {
        settlementAt(0, "alpha");
        MacroLaborService.processDay(1);
        assertEquals(1, CompanyProductionService.processCompanyDay(company, 1).producedFacilities());
        assertEquals(40, CommodityInventoryManager.getCommodityAmount(custody, "iron"), "在岗 40% → 产量 40%");
    }

    @Test
    void wagesBelowReservationHireNobodyAndStopProduction() {
        settlementAt(0, "alpha");
        assertTrue(MacroLaborService.setWage(owner, company.getCompanyId(), 1));
        assertFalse(MacroLaborService.setWage(UUID.randomUUID(), company.getCompanyId(), 50), "只有所有者能定工资");
        MacroLaborService.processDay(1);
        assertEquals(0, MacroEconomyManager.employment(facility.facilityId()));
        assertEquals(0, CompanyProductionService.processCompanyDay(company, 1).producedFacilities());
        assertEquals(CompanyFacilityStatus.UNDERSTAFFED, facility.status());
    }

    @Test
    void higherWageWinsTheLaborPool() {
        VillageRecord village = settlementAt(0, "alpha");
        // 人口 30 → 可雇用 18。第二家公司出更高工资、两个设施共 20 个岗位
        Company rival = new Company(UUID.randomUUID(), "Rival", CompanyType.FORESTRY, 100_000, UUID.randomUUID());
        CompanyManager.registerDirect(rival);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(rival);
        WarehouseRecord warehouse = new WarehouseRecord(
                UUID.randomUUID(),
                "minecraft:overworld",
                BlockPos.ZERO,
                rival.getOwnerId(),
                rival.getCompanyId(),
                4096,
                WarehouseStatus.ACTIVE,
                0,
                0,
                WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
        CompanyFacilityRecord rivalFacility = new CompanyFacilityRecord(
                UUID.randomUUID(),
                rival.getCompanyId(),
                "minecraft:overworld",
                new BlockPos(5, 64, 5),
                CompanyFacilityType.FACTORY_CONTROLLER,
                2,
                CompanyFacilityStatus.ACTIVE,
                -1,
                warehouse.warehouseId());
        CompanyFacilityManager.restore(rivalFacility);
        assertTrue(MacroLaborService.setWage(rival.getOwnerId(), rival.getCompanyId(), 30));
        for (long d = 1; d <= 10; d++) MacroLaborService.processDay(d);
        assertEquals(18, MacroEconomyManager.employment(rivalFacility.facilityId()), "高工资先招满可雇用人口");
        assertEquals(0, MacroEconomyManager.employment(facility.facilityId()), "低工资公司被挖空");
        assertEquals(18, MacroEconomyManager.settlement(village.settlementId()).employed());
    }

    @Test
    void farSettlementMeansHalfEfficiency() {
        settlementAt(5_000, "far");
        for (long d = 1; d <= 3; d++) MacroLaborService.processDay(d);
        assertEquals(10, MacroEconomyManager.employment(facility.facilityId()));
        assertEquals(5_000, MacroLaborService.staffingBps(facility), "远距离通勤效率减半");
    }

    @Test
    void brokeCompanyKeepsOnlyWorkersItCanPay() {
        settlementAt(0, "alpha");
        long wage = MacroLaborService.wageOf(company.getCompanyId());
        company.withdraw(company.getCash() - wage * 2);
        MacroLaborService.processDay(1);
        assertEquals(2, MacroEconomyManager.employment(facility.facilityId()));
        assertEquals(0, company.getCash());
    }
}
