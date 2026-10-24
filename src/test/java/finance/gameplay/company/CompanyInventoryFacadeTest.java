package finance.gameplay.company;

import static org.junit.jupiter.api.Assertions.*;

import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.warehouse.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.*;

class CompanyInventoryFacadeTest {
    private Company company;
    private UUID custody;

    @BeforeEach
    void setup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        UUID owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "Hybrid", CompanyType.RAW_MATERIALS, 100_000, owner);
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
        custody = CompanyInventoryFacade.custodyId(company.getCompanyId());
    }

    @AfterEach
    void cleanup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
    }

    @Test
    void playerCompanyUsesOnlyCustodyAndCanRollback() {
        company.addInventory("iron", 5);
        CommodityInventoryManager.setCommodity(custody, "iron", 7);
        assertEquals(7, CompanyInventoryFacade.availableInput(company, "iron"), "玩家公司只看托管账户");
        assertNull(CompanyInventoryFacade.consumeInputAtomically(company, Map.of("iron", 10)), "旧库存不能凑数");
        var consumption = CompanyInventoryFacade.consumeInputAtomically(company, Map.of("iron", 6));
        assertNotNull(consumption);
        assertEquals(1, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
        assertEquals(5, company.getInventoryAmount("iron"), "旧库存不动");
        assertTrue(CompanyInventoryFacade.rollback(company, consumption));
        assertEquals(7, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
    }

    @Test
    void npcCompanyUsesOnlyLegacyInventory() {
        Company npc = new Company(UUID.randomUUID(), "Npc", CompanyType.RAW_MATERIALS, 100_000);
        CompanyManager.registerDirect(npc);
        npc.addInventory("iron", 9);
        CommodityInventoryManager.setCommodity(CompanyInventoryFacade.custodyId(npc.getCompanyId()), "iron", 50);
        assertEquals(9, CompanyInventoryFacade.availableInput(npc, "iron"));
        assertEquals(Map.of("iron", 9), CompanyInventoryFacade.inventorySnapshot(npc));
        assertTrue(CompanyInventoryFacade.addOutputAtomically(npc, Map.of("iron", 1)));
        assertEquals(10, npc.getInventoryAmount("iron"));
    }

    @Test
    void playerOutputRequiresRealBoundWarehouseCapacity() {
        assertTrue(CompanyInventoryFacade.addOutputAtomically(company, Map.of("iron", 100)));
        assertEquals(100, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
        assertEquals(0, company.getInventoryAmount("iron"));
    }

    @Test
    void legacyInventoryOfPlayerCompanyMigratesIntoCustodyOnce() {
        company.addInventory("iron", 5);
        company.addInventory("wheat", 3);
        CommodityInventoryManager.setCommodity(custody, "iron", 7);
        assertEquals(1, CompanyGameplayManager.migratePlayerLegacyInventory());
        assertEquals(12, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
        assertEquals(3, CommodityInventoryManager.getCommodityAmount(custody, "wheat"));
        assertTrue(company.getInventory().values().stream().allMatch(v -> v == null || v <= 0), "旧库存清空");
        assertEquals(0, CompanyGameplayManager.migratePlayerLegacyInventory(), "再跑一遍不动");
        assertEquals(12, CommodityInventoryManager.getCommodityAmount(custody, "iron"));
    }

    @Test
    void npcCompanyInventoryIsNotMigrated() {
        Company npc = new Company(UUID.randomUUID(), "Npc", CompanyType.RAW_MATERIALS, 100_000);
        CompanyManager.registerDirect(npc);
        npc.addInventory("iron", 9);
        assertEquals(0, CompanyGameplayManager.migratePlayerLegacyInventory());
        assertEquals(9, npc.getInventoryAmount("iron"));
    }
}
