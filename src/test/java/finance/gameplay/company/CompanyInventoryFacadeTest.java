package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.CommodityInventoryManager;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyInventoryFacadeTest {
    private Company company; private UUID custody;
    @BeforeEach void setup() { CompanyManager.clearCompaniesDirect(); WarehouseManager.clearDirect(); CommodityInventoryManager.clearInventoriesDirect();
        UUID owner = UUID.randomUUID(); company = new Company(UUID.randomUUID(), "Hybrid", CompanyType.RAW_MATERIALS, 100_000, owner);
        CompanyManager.registerDirect(company); CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                owner, company.getCompanyId(), 4096, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(warehouse); profile.bindWarehouse(warehouse.warehouseId()); custody = CompanyInventoryFacade.custodyId(company.getCompanyId()); }
    @AfterEach void cleanup() { CompanyManager.clearCompaniesDirect(); WarehouseManager.clearDirect(); CommodityInventoryManager.clearInventoriesDirect(); }

    @Test void hybridConsumesCustodyFirstThenDistinctLegacyInventoryAndCanRollback() {
        company.addInventory("iron", 5); CommodityInventoryManager.setCommodity(custody, "iron", 7);
        assertEquals(12, CompanyInventoryFacade.availableInput(company, "iron"));
        var consumption = CompanyInventoryFacade.consumeInputAtomically(company, Map.of("iron", 10));
        assertNotNull(consumption); assertEquals(0, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
        assertEquals(2, company.getInventoryAmount("iron"));
        assertTrue(CompanyInventoryFacade.rollback(company, consumption));
        assertEquals(7, CommodityInventoryManager.getCommodityAmount(custody, "iron")); assertEquals(5, company.getInventoryAmount("iron"));
    }

    @Test void playerDrivenOutputRequiresRealBoundWarehouseCapacity() {
        CompanyGameplayManager.get(company.getCompanyId()).setOperatingMode(CompanyOperatingMode.PLAYER_DRIVEN);
        assertTrue(CompanyInventoryFacade.addOutputAtomically(company, Map.of("iron", 100)));
        assertEquals(100, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
        assertEquals(0, company.getInventoryAmount("iron"));
    }
}
