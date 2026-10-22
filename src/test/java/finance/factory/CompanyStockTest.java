package finance.factory;

import static org.junit.jupiter.api.Assertions.*;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.testutil.MinecraftTestBootstrap;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;

/** 机器和公司库存之间搬货：只收普通商品物品，仓库满了不收，取原料按库存来。 */
class CompanyStockTest {
    private Company company;
    private UUID custody;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @BeforeEach
    void setup() {
        CompanyManager.clearCompaniesDirect();
        WarehouseManager.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.register(new Commodity("bread", "minecraft:bread", "面包", CommodityCategory.FOOD, 30));
        CommodityRegistry.register(new Commodity("wheat", "minecraft:wheat", "小麦", CommodityCategory.FOOD, 8));
        UUID owner = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "Bakery", CompanyType.FOOD_PROCESSING, 100_000, owner);
        CompanyManager.registerDirect(company);
        var profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(
                UUID.randomUUID(),
                "minecraft:overworld",
                BlockPos.ZERO,
                owner,
                company.getCompanyId(),
                100,
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
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void depositsPlainCommodityItemsIntoCompanyCustody() {
        assertEquals(12, CompanyStock.deposit(company, new ItemStack(Items.BREAD, 12)));
        assertEquals(12, CommodityInventoryManager.getCommodityAmount(custody, "bread"));
    }

    @Test
    void refusesNonCommoditiesTaggedItemsAndOverflow() {
        assertEquals(0, CompanyStock.deposit(company, new ItemStack(Items.DIAMOND, 1)));
        ItemStack named = new ItemStack(Items.BREAD, 5);
        named.getOrCreateTag().putString("custom", "x");
        assertEquals(0, CompanyStock.deposit(company, named), "带数据的物品不收");
        assertEquals(
                0, CompanyStock.deposit(company, new ItemStack(Items.BREAD, 64).copyWithCount(101)), "仓库只有 100 容量");
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(custody, "bread"));
    }

    @Test
    void withdrawsAtMostWhatTheCompanyHas() {
        CommodityInventoryManager.setCommodity(custody, "wheat", 10);
        ItemStack taken = CompanyStock.withdraw(company, Items.WHEAT, 64);
        assertEquals(10, taken.getCount());
        assertTrue(taken.is(Items.WHEAT));
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(custody, "wheat"));
        assertTrue(CompanyStock.withdraw(company, Items.WHEAT, 64).isEmpty());
        assertTrue(CompanyStock.withdraw(company, Items.DIAMOND, 64).isEmpty());
    }
}
