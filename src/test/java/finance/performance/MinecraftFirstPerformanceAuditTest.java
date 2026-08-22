package finance.performance;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.contract.*;
import finance.data.EconomySavedData;
import finance.gameplay.company.*;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftFirstPerformanceAuditTest {
    private static final String COMMODITY = "phase5_perf_iron";

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity(COMMODITY, "minecraft:iron_ingot", "Performance Iron",
                CommodityCategory.RAW_MATERIALS, 10));
    }

    @AfterEach void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.removeCommodity(COMMODITY);
    }

    @Test void boundedProductionScaleSaveAndLoadPreservesCardinality() {
        assertTimeout(Duration.ofSeconds(15), () -> {
            for (int i = 0; i < 1_000; i++) {
                AccountManager.getOrCreateSystemAccount(stableId("player", i)).setBalance(10_000 + i);
            }

            List<Company> companies = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Company company = new Company(stableId("company", i), "Performance Company " + i,
                        CompanyType.RAW_MATERIALS, 100_000, stableId("owner", i));
                CompanyManager.registerDirect(company);
                CompanyGameplayManager.createForNewCompany(company);
                companies.add(company);
            }

            for (int i = 0; i < 500; i++) {
                Company company = i < 200 ? companies.get(i / 2) : null;
                UUID owner = company == null ? stableId("warehouse-owner", i) : company.getOwnerId();
                WarehouseRecord warehouse = new WarehouseRecord(stableId("warehouse", i), "minecraft:overworld",
                        new BlockPos(i % 100, 64, i / 100), owner, company == null ? null : company.getCompanyId(),
                        4_096, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
                assertTrue(WarehouseManager.restore(warehouse));
                if (company != null) {
                    CompanyGameplayManager.get(company.getCompanyId()).bindWarehouse(warehouse.warehouseId());
                    assertTrue(CompanyFacilityManager.restore(new CompanyFacilityRecord(stableId("facility", i),
                            company.getCompanyId(), "minecraft:overworld", new BlockPos(i % 100, 65, i / 100),
                            CompanyFacilityType.FACTORY_CONTROLLER, 1, CompanyFacilityStatus.ACTIVE, -1,
                            warehouse.warehouseId())));
                }
            }

            for (int i = 0; i < 1_000; i++) {
                boolean active = i < 100;
                UUID escrow = stableId("escrow", i);
                AccountManager.getOrCreateSystemAccount(escrow).setBalance(active ? 1 : 0);
                FinanceContract contract = new FinanceContract(stableId("contract", i), ContractType.PROCUREMENT,
                        ContractIssuerType.NPC_MARKET, stableId("issuer", i), COMMODITY, 1, active ? 0 : 1,
                        1, escrow, null, 0, 30, null, active ? ContractStatus.OPEN : ContractStatus.COMPLETED, "");
                assertTrue(ContractManager.restore(contract));
            }

            long saveStart = System.nanoTime();
            CompoundTag snapshot = new EconomySavedData().save(new CompoundTag());
            long saveMillis = (System.nanoTime() - saveStart) / 1_000_000L;
            EconomySavedData.resetRuntimeState();
            long loadStart = System.nanoTime();
            EconomySavedData.load(snapshot);
            long loadMillis = (System.nanoTime() - loadStart) / 1_000_000L;

            assertEquals(500, WarehouseManager.all().size());
            assertEquals(1_000, ContractManager.contracts().size());
            assertEquals(100, CompanyGameplayManager.profiles().size());
            assertEquals(200, CompanyFacilityManager.all().size());
            System.out.printf("phase5-performance save=%dms load=%dms accounts=%d warehouses=%d contracts=%d companies=%d facilities=%d%n",
                    saveMillis, loadMillis, AccountManager.getAccounts().size(), WarehouseManager.all().size(),
                    ContractManager.contracts().size(), CompanyManager.getCompanies().size(), CompanyFacilityManager.all().size());
        });
    }

    private static UUID stableId(String kind, int index) {
        return UUID.nameUUIDFromBytes(("finance-phase5:" + kind + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
