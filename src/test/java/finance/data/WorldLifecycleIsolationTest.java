package finance.data;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.contract.*;
import finance.gameplay.company.*;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldLifecycleIsolationTest {
    private static final String COMMODITY = "phase5_lifecycle_iron";

    @AfterEach
    void cleanup() {
        EconomySavedData.unload();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void worldSwitchClearsRuntimeStateAndReloadingOriginalWorldRestoresDependentRecords() {
        UUID owner = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID escrowId = UUID.randomUUID();

        CommodityRegistry.register(new Commodity(COMMODITY, "minecraft:iron_ingot",
                "Lifecycle Iron", CommodityCategory.RAW_MATERIALS, 100));
        Company company = new Company(companyId, "World A Company", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(warehouseId, "minecraft:overworld", new BlockPos(4, 64, 8),
                owner, companyId, 4_096, WarehouseStatus.ACTIVE, 2, 2, WarehousePermissionMode.OWNER_ONLY);
        assertTrue(WarehouseManager.restore(warehouse));
        profile.bindWarehouse(warehouseId);
        assertTrue(CompanyFacilityManager.restore(new CompanyFacilityRecord(facilityId, companyId,
                "minecraft:overworld", new BlockPos(5, 64, 8), CompanyFacilityType.FACTORY_CONTROLLER,
                1, CompanyFacilityStatus.ACTIVE, 2, warehouseId)));
        assertTrue(AccountManager.getOrCreateSystemAccount(escrowId).deposit(500));
        assertTrue(ContractManager.restore(new FinanceContract(contractId, ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, UUID.randomUUID(), COMMODITY, 5, 0, 500,
                escrowId, null, 2, 7, null, ContractStatus.OPEN, "")));

        CompoundTag worldA = new EconomySavedData().save(new CompoundTag());

        // Emulate a complete server stop followed by a fresh world B start.
        EconomySavedData.unload();
        CommodityRegistry.resetToDefaults();
        EconomySavedData.load(new CompoundTag());
        assertNull(CompanyManager.getCompany(companyId));
        assertNull(WarehouseManager.get(warehouseId));
        assertNull(CompanyFacilityManager.get(facilityId));
        assertNull(ContractManager.get(contractId));
        assertNull(CommodityRegistry.getCommodity(COMMODITY));

        // Returning to world A must rebuild references in dependency order.
        EconomySavedData.unload();
        CommodityRegistry.resetToDefaults();
        EconomySavedData.load(worldA);
        assertNotNull(CompanyManager.getCompany(companyId));
        assertNotNull(WarehouseManager.get(warehouseId));
        assertNotNull(CompanyGameplayManager.get(companyId));
        assertNotNull(CompanyFacilityManager.get(facilityId));
        assertNotNull(ContractManager.get(contractId));
        assertEquals(500, AccountManager.getAccounts().get(escrowId).getBalance());
        assertNotNull(CommodityRegistry.getCommodity(COMMODITY));
    }
}
