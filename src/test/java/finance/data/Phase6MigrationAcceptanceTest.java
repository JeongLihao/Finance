package finance.data;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.contract.ContractIssuerType;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;
import finance.contract.ContractType;
import finance.contract.FinanceContract;
import finance.diagnostic.EconomyConsistencyService;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyGameplayProfile;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6MigrationAcceptanceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000006001");
    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-000000006002");
    private static final UUID WAREHOUSE = UUID.fromString("00000000-0000-0000-0000-000000006003");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-0000-0000-000000006004");
    private static final UUID CONTRACT = UUID.fromString("00000000-0000-0000-0000-000000006005");
    private static final UUID ESCROW = UUID.fromString("00000000-0000-0000-0000-000000006006");
    private static final String COMMODITY = "phase6_migration_iron";

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity(COMMODITY, "minecraft:iron_ingot",
                "Phase 6 Migration Iron", CommodityCategory.RAW_MATERIALS, 100));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.removeCommodity(COMMODITY);
    }

    @Test
    void upgradedSaveLoadsThreeTimesWithoutMintingAndIsolatesCorruptChildren() {
        AccountManager.deposit(OWNER, 12_345);
        assertTrue(AccountManager.getOrCreateSystemAccount(ESCROW).deposit(500));
        Company company = new Company(COMPANY, "Phase 6 Migration", CompanyType.RAW_MATERIALS, 20_000, OWNER);
        CompanyManager.registerDirect(company);
        CompanyGameplayProfile profile = CompanyGameplayManager.createForNewCompany(company);
        WarehouseRecord warehouse = new WarehouseRecord(WAREHOUSE, "minecraft:overworld", new BlockPos(4, 64, 4),
                OWNER, COMPANY, 4_096, WarehouseStatus.ACTIVE, 2, 2, WarehousePermissionMode.OWNER_ONLY);
        assertTrue(WarehouseManager.restore(warehouse));
        profile.bindWarehouse(WAREHOUSE);
        assertTrue(CompanyFacilityManager.restore(new CompanyFacilityRecord(FACILITY, COMPANY,
                "minecraft:overworld", new BlockPos(5, 64, 4), CompanyFacilityType.FACTORY_CONTROLLER,
                1, CompanyFacilityStatus.ACTIVE, 2, WAREHOUSE)));
        assertTrue(ContractManager.restore(new FinanceContract(CONTRACT, ContractType.PROCUREMENT,
                ContractIssuerType.NPC_MARKET, UUID.randomUUID(), COMMODITY, 5, 0, 500, ESCROW,
                null, 2, 8, null, ContractStatus.OPEN, "")));

        CompoundTag fixture = new EconomySavedData().save(new CompoundTag());
        fixture.putInt("DataVersion", 27);
        UUID badWarehouse = injectCorruptWarehouse(fixture);
        UUID badFacility = injectCorruptFacility(fixture);
        UUID badContract = injectCorruptContract(fixture);

        long expectedMoney = totalAccountMoney();
        for (int load = 0; load < 3; load++) {
            EconomySavedData.resetRuntimeState();
            EconomySavedData.load(fixture);

            assertEquals(expectedMoney, totalAccountMoney(), "load " + load + " changed account money");
            // Player accounts receive the normal 1,000 opening balance before this
            // fixture deposits 12,345. Migration must preserve that exact total.
            assertEquals(13_345, AccountManager.getBalance(OWNER));
            assertEquals(500, AccountManager.getAccounts().get(ESCROW).getBalance());
            assertNotNull(CompanyManager.getCompany(COMPANY));
            assertNotNull(WarehouseManager.get(WAREHOUSE));
            assertNotNull(CompanyFacilityManager.get(FACILITY));
            assertNotNull(ContractManager.get(CONTRACT));
            assertNull(WarehouseManager.get(badWarehouse));
            assertNull(CompanyFacilityManager.get(badFacility));
            assertNull(ContractManager.get(badContract));
            assertTrue(EconomyConsistencyService.run(8).healthy());

            fixture = new EconomySavedData().save(new CompoundTag());
            assertEquals(EconomySavedData.currentDataVersion(), fixture.getInt("DataVersion"));
        }
    }

    private static UUID injectCorruptWarehouse(CompoundTag root) {
        UUID id = UUID.randomUUID();
        CompoundTag bad = new CompoundTag();
        bad.putUUID("Id", id);
        bad.putString("Dimension", "minecraft:overworld");
        bad.putLong("Pos", BlockPos.ZERO.asLong());
        bad.putUUID("Owner", OWNER);
        bad.putInt("Capacity", -1);
        bad.putString("Status", WarehouseStatus.ACTIVE.name());
        bad.putLong("CreatedDay", 0);
        bad.putLong("LastAuditDay", 0);
        bad.putString("Permission", WarehousePermissionMode.OWNER_ONLY.name());
        root.getCompound("Warehouses").getList("Records", Tag.TAG_COMPOUND).add(bad);
        return id;
    }

    private static UUID injectCorruptFacility(CompoundTag root) {
        UUID id = UUID.randomUUID();
        CompoundTag bad = new CompoundTag();
        bad.putUUID("Id", id);
        bad.putUUID("Company", COMPANY);
        bad.putString("Dimension", "minecraft:overworld");
        bad.putLong("Pos", new BlockPos(6, 64, 4).asLong());
        bad.putString("Type", CompanyFacilityType.FACTORY_CONTROLLER.name());
        bad.putInt("Level", CompanyFacilityRecord.MAX_LEVEL + 1);
        bad.putString("Status", CompanyFacilityStatus.ACTIVE.name());
        bad.putLong("LastDay", 2);
        root.getCompound("CompanyGameplay").getList("Facilities", Tag.TAG_COMPOUND).add(bad);
        return id;
    }

    private static UUID injectCorruptContract(CompoundTag root) {
        UUID id = UUID.randomUUID();
        CompoundTag bad = new CompoundTag();
        bad.putUUID("Id", id);
        bad.putString("Type", ContractType.PROCUREMENT.name());
        bad.putString("IssuerType", ContractIssuerType.NPC_MARKET.name());
        bad.putUUID("Issuer", UUID.randomUUID());
        bad.putString("Commodity", COMMODITY);
        bad.putInt("Required", 5);
        bad.putInt("Delivered", 6);
        bad.putLong("Reward", 500);
        bad.putUUID("Escrow", ESCROW);
        bad.putLong("CreatedDay", 2);
        bad.putLong("DeadlineDay", 8);
        bad.putString("Status", ContractStatus.OPEN.name());
        root.getCompound("FinanceContracts").getList("Records", Tag.TAG_COMPOUND).add(bad);
        return id;
    }

    private static long totalAccountMoney() {
        return AccountManager.getAccounts().values().stream()
                .mapToLong(account -> Math.addExact(account.getBalance(), account.getFrozenBalance()))
                .reduce(0L, Math::addExact);
    }
}
