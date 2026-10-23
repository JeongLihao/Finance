package finance.macro;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.block.entity.CompanyMachineBlockEntity;
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

/** 雇工、发工资；在岗比例决定机器速度（每台机器每档 3 个岗位）。 */
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
                CompanyFacilityType.PROCESSOR,
                1,
                CompanyFacilityStatus.ACTIVE,
                -1);
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
        assertEquals(10_000, MacroLaborService.staffingBps(facility), "附近没有村庄算满员");
        assertEquals(3, MacroLaborService.jobs(facility), "1 档机器 3 个岗位");
    }

    @Test
    void hiringRampsUpAndWagesFlowToTheSettlement() {
        VillageRecord village = settlementAt(0, "alpha");
        long cash = company.getCash();
        MacroLaborService.processDay(1);
        assertEquals(1, MacroEconomyManager.employment(facility.facilityId()), "每天最多招岗位数的 1/3（向上取整）");
        assertEquals(3_333, MacroLaborService.staffingBps(facility));
        long wage = MacroLaborService.wageOf(company.getCompanyId());
        assertEquals(cash - wage, company.getCash());
        assertEquals(wage, AccountManager.getBalance(village.budgetAccountId()));
        assertTrue(AccountManager.getTransactions().stream().anyMatch(t -> t.getType() == TransactionType.MACRO_WAGE));
        assertEquals(1, MacroEconomyManager.settlement(village.settlementId()).employed());

        MacroLaborService.processDay(2);
        MacroLaborService.processDay(3);
        assertEquals(3, MacroEconomyManager.employment(facility.facilityId()), "三天招满 3 个岗位");
        MacroLaborService.processDay(3);
        assertEquals(3, MacroEconomyManager.employment(facility.facilityId()), "同一天不重复招工发薪");
    }

    @Test
    void machineSpeedScalesWithStaffing() {
        settlementAt(0, "alpha");
        MacroLaborService.processDay(1);
        int staffing = MacroLaborService.staffingBps(facility);
        assertEquals(6_666, CompanyMachineBlockEntity.speedBps(1, staffing), "在岗 1/3 → 半速再加 1/6");
        assertEquals(5_000, CompanyMachineBlockEntity.speedBps(1, 0), "雇不到人也有半速");
        assertEquals(10_000, CompanyMachineBlockEntity.speedBps(1, 10_000));
        assertEquals(40_000, CompanyMachineBlockEntity.speedBps(4, 10_000), "3 档满员 4 倍速");
    }

    @Test
    void wagesBelowReservationHireNobody() {
        settlementAt(0, "alpha");
        assertTrue(MacroLaborService.setWage(owner, company.getCompanyId(), 1));
        assertFalse(MacroLaborService.setWage(UUID.randomUUID(), company.getCompanyId(), 50), "只有所有者能定工资");
        MacroLaborService.processDay(1);
        assertEquals(0, MacroEconomyManager.employment(facility.facilityId()));
        assertEquals(0, MacroLaborService.staffingBps(facility), "工资太低没人干，机器只剩半速");
    }

    @Test
    void higherWageWinsTheLaborPool() {
        SettlementRecord small = SettlementManager.registerOrRecover(
                UUID.randomUUID(), "minecraft:overworld", new BlockPos(0, 64, 0), "small");
        VillageRecord village = MacroEconomyServiceTest.makeVillage(small, 3, 2, 1, 4);
        // 人口 10 → 可雇用 6。第二家公司出更高工资，3 档机器 9 个岗位，加上我们的 3 个共 12 个
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
                CompanyFacilityType.PROCESSOR,
                3,
                CompanyFacilityStatus.ACTIVE,
                -1);
        CompanyFacilityManager.restore(rivalFacility);
        assertTrue(MacroLaborService.setWage(rival.getOwnerId(), rival.getCompanyId(), 30));
        for (long d = 1; d <= 10; d++) MacroLaborService.processDay(d);
        assertEquals(6, MacroEconomyManager.employment(rivalFacility.facilityId()), "高工资先招满可雇用人口");
        assertEquals(0, MacroEconomyManager.employment(facility.facilityId()), "低工资公司被挖空");
        assertEquals(6, MacroEconomyManager.settlement(village.settlementId()).employed());
    }

    @Test
    void farSettlementMeansHalfEfficiency() {
        settlementAt(5_000, "far");
        for (long d = 1; d <= 3; d++) MacroLaborService.processDay(d);
        assertEquals(3, MacroEconomyManager.employment(facility.facilityId()));
        assertEquals(5_000, MacroLaborService.staffingBps(facility), "远距离通勤效率减半");
    }

    @Test
    void brokeCompanyKeepsOnlyWorkersItCanPay() {
        settlementAt(0, "alpha");
        long wage = MacroLaborService.wageOf(company.getCompanyId());
        for (long d = 1; d <= 3; d++) MacroLaborService.processDay(d);
        assertEquals(3, MacroEconomyManager.employment(facility.facilityId()));
        company.withdraw(company.getCash() - wage * 2);
        MacroLaborService.processDay(4);
        assertEquals(2, MacroEconomyManager.employment(facility.facilityId()), "现金只够发 2 个人");
        assertEquals(0, company.getCash());
    }
}
