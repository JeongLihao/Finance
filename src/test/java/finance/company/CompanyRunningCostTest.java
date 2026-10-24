package finance.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.account.AccountManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.macro.MacroLaborService;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.policy.MonetaryPolicyService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** B1：公司日常开销（机器维护、库存持有利息、仓储费）每天真从现金里付给 NPC 资金池。 */
class CompanyRunningCostTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000004201");
    private static final UUID PLAYER_CO = UUID.fromString("00000000-0000-0000-0000-000000004202");
    private static final UUID NPC_CO = UUID.fromString("00000000-0000-0000-0000-000000004203");

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        NpcMarketMaker.putMarketPrice("iron", new MarketPrice("iron", 100, 0.0));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    private static Company playerCompany(long cash) {
        Company company = new Company(PLAYER_CO, "Machines Inc", CompanyType.FORESTRY, cash, OWNER);
        CompanyManager.registerDirect(company);
        return company;
    }

    private static void machine(int index, int tier, CompanyFacilityStatus status) {
        CompanyFacilityManager.restore(new CompanyFacilityRecord(
                UUID.nameUUIDFromBytes(("running-cost-machine-" + index).getBytes()),
                PLAYER_CO,
                "minecraft:overworld",
                new BlockPos(index * 4, 64, 0),
                CompanyFacilityType.LUMBER,
                tier,
                status,
                0));
    }

    @Test
    void maintenanceIsPaidFromCashToNpcPoolAndMatchesReportedExpenses() {
        Company company = playerCompany(1_000);
        machine(0, 1, CompanyFacilityStatus.ACTIVE);
        machine(1, 2, CompanyFacilityStatus.MISSING_INPUT);
        machine(2, 3, CompanyFacilityStatus.OUTPUT_FULL);
        // 停用和破产冻结的机器不收维护费
        machine(3, 3, CompanyFacilityStatus.DISABLED);
        machine(4, 3, CompanyFacilityStatus.BANKRUPTCY_HOLD);
        long expected = FinanceConfig.machineMaintenance(1)
                + FinanceConfig.machineMaintenance(2)
                + FinanceConfig.machineMaintenance(3);
        assertEquals(expected, company.runningCostDue().maintenance());
        long poolBefore = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);

        company.settleDailyProfits(1);

        assertEquals(1_000 - expected, company.getCash());
        assertEquals(expected, AccountManager.getBalance(NpcMarketMaker.NPC_UUID) - poolBefore, "钱进 NPC 资金池，不凭空消失");
        assertEquals(expected, company.getLatestFinancialReport().expenses(), "报表费用 = 现金减少额");
        assertEquals(0, company.getLastCostShortfall());
        assertEquals(expected, company.estimateDailyOperatingCost(), "安全线按真实日均开销算");
    }

    @Test
    void wagesCountTowardRunningCost() {
        Company company = playerCompany(1_000);
        machine(0, 1, CompanyFacilityStatus.ACTIVE);
        assertTrue(company.withdraw(36));
        company.recordRunningCost(36);

        company.settleDailyProfits(1);

        long maintenance = FinanceConfig.machineMaintenance(1);
        assertEquals(36 + maintenance, company.getLatestFinancialReport().expenses());
        assertEquals(36 + maintenance, company.estimateDailyOperatingCost());
    }

    @Test
    void shortCashPaysWhatItCanRecordsShortfallAndNeverGoesNegative() {
        Company company = playerCompany(15);
        machine(0, 3, CompanyFacilityStatus.ACTIVE);
        long due = company.runningCostDue().total();
        assertTrue(due > 15);

        company.settleDailyProfits(1);

        assertEquals(0, company.getCash());
        assertEquals(due - 15, company.getLastCostShortfall());
        assertEquals(15, company.getLatestFinancialReport().expenses(), "只记实际付出的部分");
        assertEquals(due, company.estimateDailyOperatingCost(), "安全线算应付额，含没付上的缺口");

        company.settleDailyProfits(2);
        assertEquals(0, company.getCash());
        assertEquals(due, company.getLastCostShortfall());
    }

    @Test
    void shortfallPutsPublicCompanyAtBankruptcyRisk() {
        Company company = playerCompany(15);
        company.setPublic(true);
        machine(0, 3, CompanyFacilityStatus.ACTIVE);
        company.settleDailyProfits(1);
        assertTrue(company.getLastCostShortfall() > 0);

        CompanyBankruptcyManager.tick(1);

        assertTrue(company.isBankruptcyRisk());
    }

    @Test
    void managementLevelDiscountsMaintenance() {
        Company company = playerCompany(10_000);
        for (int i = 0; i < 4; i++) machine(i, 3, CompanyFacilityStatus.ACTIVE);
        long base = company.runningCostDue().maintenance();
        company.restoreManagement(CompanyStrategy.STABLE, 0, 0, 5, 0.5);
        long discounted = company.runningCostDue().maintenance();
        double expected = base * (1.0 - 5 * FinanceConfig.managementLevelDiscountBasisPoints() / 10_000.0);
        assertEquals(Math.round(expected), discounted);
        assertTrue(discounted < base);
    }

    @Test
    void carryInterestFollowsBenchmarkRateAndStorageDiscountsOnlyTheStorageFee() {
        Company company = new Company(NPC_CO, "Npc Iron", CompanyType.RAW_MATERIALS, 1_000_000);
        company.addInventory("iron", 36_500); // 市值 3,650,000
        MonetaryPolicyService.restore(1_000, List.of()); // 10%
        Company.RunningCost at10 = company.runningCostDue();
        assertEquals(1_000, at10.carryInterest(), "3,650,000 × 10% ÷ 365");
        MonetaryPolicyService.restore(500, List.of());
        assertEquals(500, company.runningCostDue().carryInterest(), "利率减半，持有利息减半");

        long fullStorage = company.runningCostDue().storageFee();
        assertEquals(Math.round(3_650_000 * FinanceConfig.inventoryStorageFeeBasisPoints() / 10_000.0), fullStorage);
        company.restoreManagement(CompanyStrategy.STABLE, 0, 5, 0, 0.5);
        Company.RunningCost upgraded = company.runningCostDue();
        assertTrue(upgraded.storageFee() < fullStorage, "仓储等级让仓储费打折");
        assertEquals(500, upgraded.carryInterest(), "利息是资金占用，仓储升级打不了折");
    }

    @Test
    void npcCompanyPaysMaintenanceForAbstractMachines() {
        Company company = new Company(NPC_CO, "Npc Iron", CompanyType.RAW_MATERIALS, 1_000);
        assertEquals(Company.ABSTRACT_MACHINES_PER_STANDARD_OUTPUT, company.abstractMachineCount());
        assertEquals(
                Company.ABSTRACT_MACHINES_PER_STANDARD_OUTPUT * FinanceConfig.machineMaintenance(1),
                company.runningCostDue().maintenance());
        company.upgradeProduction();
        company.upgradeProduction();
        assertTrue(company.abstractMachineCount() > Company.ABSTRACT_MACHINES_PER_STANDARD_OUTPUT, "产线越大机器越多");

        long poolBefore = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        long due = company.runningCostDue().total();
        company.settleDailyProfits(1);
        assertEquals(1_000 - due, company.getCash());
        assertEquals(due, AccountManager.getBalance(NpcMarketMaker.NPC_UUID) - poolBefore);
    }

    @Test
    void managementSpeedsUpHiringAndWidensLaborRadius() {
        assertEquals(4, MacroLaborService.maxDailyChange(9, 0) + 1); // 0 级：岗位的三分之一（9 → 3）
        assertTrue(MacroLaborService.maxDailyChange(9, 4) > MacroLaborService.maxDailyChange(9, 0));
        assertEquals(6, MacroLaborService.maxDailyChange(9, 4), "4 级快 100%");
        assertEquals(FinanceConfig.macroLaborRadius(), MacroLaborService.laborRadius(0));
        assertTrue(MacroLaborService.laborRadius(5) > MacroLaborService.laborRadius(0));
    }

    @Test
    void runningCostHistoryAndShortfallSurviveSaveAndLoad() {
        Company company = playerCompany(15);
        machine(0, 3, CompanyFacilityStatus.ACTIVE);
        company.settleDailyProfits(1);
        List<Long> history = company.getRecentRunningCosts();
        long shortfall = company.getLastCostShortfall();
        assertFalse(history.isEmpty());

        CompoundTag tag = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(tag);

        Company loaded = CompanyManager.getCompany(PLAYER_CO);
        assertEquals(history, loaded.getRecentRunningCosts());
        assertEquals(shortfall, loaded.getLastCostShortfall());
    }
}
