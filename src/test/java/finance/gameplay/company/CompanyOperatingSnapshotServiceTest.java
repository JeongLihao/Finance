package finance.gameplay.company;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.market.NpcMarketMaker;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyOperatingSnapshotServiceTest {

    private Company company;
    private CompanyGameplayProfile profile;

    @BeforeEach
    void setup() {
        CompanyManager.clearCompaniesDirect();
        CompanyGameplayManager.clearDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        AccountManager.clearAccountsDirect();
        NpcMarketMaker.clearMarketPrices();
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        UUID owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "SnapshotCo", CompanyType.RAW_MATERIALS, 50_000, owner);
        CompanyManager.registerDirect(company);
        profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld",
                BlockPos.ZERO, owner, company.getCompanyId(), 4096, WarehouseStatus.ACTIVE,
                0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse);
        profile.bindWarehouse(warehouse.warehouseId());
    }

    @AfterEach
    void cleanup() {
        CompanyManager.clearCompaniesDirect();
        CompanyGameplayManager.clearDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        AccountManager.clearAccountsDirect();
        NpcMarketMaker.clearMarketPrices();
    }

    @Test
    void playerDrivenCompanyReadsOnlyCustodyInventory() {
        profile.setOperatingMode(CompanyOperatingMode.PLAYER_DRIVEN);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        CommodityInventoryManager.setCommodity(custody, "iron", 40);
        company.addInventory("iron", 11);

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertNotNull(snapshot);
        assertEquals(40L, snapshot.custodyInventory().get("iron"));
    }

    @Test
    void hybridModeSumsCustodyAndLegacyExactlyOnce() {
        profile.setOperatingMode(CompanyOperatingMode.HYBRID);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        CommodityInventoryManager.setCommodity(custody, "iron", 40);
        company.addInventory("iron", 11);

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertEquals(51L, snapshot.custodyInventory().get("iron"));

        CommodityInventoryManager.setCommodity(custody, "iron", 41);
        CompanyOperatingSnapshot next = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertEquals(52L, next.custodyInventory().get("iron"), "custody delta must appear exactly once");
    }

    @Test
    void legacyModeIgnoresCustodyInventory() {
        profile.setOperatingMode(CompanyOperatingMode.LEGACY_AUTOMATIC);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        CommodityInventoryManager.setCommodity(custody, "iron", 40);
        company.addInventory("iron", 11);

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertEquals(11L, snapshot.custodyInventory().get("iron"));
    }

    @Test
    void extremeQuantitiesAndPricesNeverWrapNegative() {
        profile.setOperatingMode(CompanyOperatingMode.PLAYER_DRIVEN);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        CommodityInventoryManager.setCommodity(custody, "iron", Integer.MAX_VALUE);

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertTrue(snapshot.inventoryValue() >= 0, "valuation must never wrap negative");
        assertTrue(snapshot.displayAssetValue() >= 0);
        assertTrue(snapshot.totalDebtPrincipal() >= 0);

        assertEquals(Long.MAX_VALUE, CompanyOperatingSnapshot.saturateMultiply(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, CompanyOperatingSnapshot.saturateAdd(Long.MAX_VALUE, 1));
    }

    @Test
    void missingMarketPriceDegradesValuationInsteadOfDefaultingToZero() {
        profile.setOperatingMode(CompanyOperatingMode.PLAYER_DRIVEN);
        UUID custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
        CommodityInventoryManager.setCommodity(custody, "iron", 10);
        CommodityInventoryManager.setCommodity(custody, "unregistered_gem", 5);

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 7);
        assertTrue(snapshot.inventoryValuationDegraded(), "missing price must be reported");
        assertTrue(snapshot.inventoryValue() > 0, "priced lines still contribute");
        assertEquals(5L, snapshot.custodyInventory().get("unregistered_gem"));
    }

    @Test
    void snapshotBuildsFromPersistedRecordsWithoutLoadingBlocks() {
        CompanyFacilityRecord facility = new CompanyFacilityRecord(UUID.randomUUID(),
                company.getCompanyId(), "minecraft:overworld", new BlockPos(1000, 200, -1000),
                CompanyFacilityType.FACTORY_CONTROLLER, 2, CompanyFacilityStatus.MISSING_INPUT,
                3, null);
        assertTrue(CompanyFacilityManager.restore(facility));

        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 9);
        assertEquals(1, snapshot.facilityCount());
        assertEquals(2, snapshot.highestFacilityLevel());
        assertEquals(3, snapshot.lastProductionDay());
        assertTrue(snapshot.materialShortage());
        assertEquals(CompanyOperatingHealth.MATERIAL_SHORTAGE, snapshot.health());
        assertFalse(snapshot.listed());
        assertEquals(0, snapshot.marketCapitalization());
    }

    @Test
    void bankruptcyRiskDominatesHealthClassification() {
        company.setBankruptcyRisk(true, 4);
        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, 5);
        assertEquals(CompanyOperatingHealth.BANKRUPTCY_RISK, snapshot.health());
        assertTrue(snapshot.bankruptcyRisk());
    }
}
