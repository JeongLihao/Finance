package finance.data;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.serializer.CapitalProjectDataSerializer;
import finance.gameplay.company.capital.*;
import finance.testutil.MinecraftTestBootstrap;
import finance.warehouse.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectPersistenceTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void fundedProjectRoundTripsWithExactEscrowAndOperationKeys() {
        UUID owner = UUID.randomUUID(), companyId = UUID.randomUUID(), warehouseId = UUID.randomUUID();
        CompanyManager.registerDirect(new Company(companyId, "PersistCapital", CompanyType.RAW_MATERIALS, 1_000, owner));
        assertTrue(WarehouseManager.restore(new WarehouseRecord(warehouseId, "minecraft:overworld", BlockPos.ZERO,
                owner, companyId, WarehouseTier.BASIC, WarehouseTier.BASIC.capacity(), WarehouseStatus.ACTIVE,
                0, 0, WarehousePermissionMode.OWNER_ONLY)));
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), companyId,
                WorldCapitalProjectType.WAREHOUSE_UPGRADE, warehouseId, owner, 0, 30, 2,
                CapitalFundingSource.RETAINED_EARNINGS, 250, Map.of(Items.IRON_INGOT, 8), false,
                CapitalProjectStatus.MATERIALS_PENDING, 2);
        project.restoreReferences(250, null, null, null, null, null, true, "");
        project.restoreOperation("once");
        AccountManager.getOrCreateSystemAccount(project.escrowAccountId()).deposit(250);
        assertTrue(CapitalProjectManager.register(project));
        CompoundTag root = new CompoundTag();
        CapitalProjectDataSerializer.save(root);
        CapitalProjectManager.clearDirect();
        CapitalProjectDataSerializer.load(root);
        WorldCapitalProject restored = CapitalProjectManager.get(project.projectId());
        assertNotNull(restored);
        assertEquals(CapitalProjectStatus.MATERIALS_PENDING, restored.status());
        assertEquals(250, restored.fundedAmount());
        assertTrue(restored.hasOperation("once"));
    }

    @Test void corruptEnumIsIsolatedWithoutDroppingOtherRecords() {
        CompoundTag root = new CompoundTag(), data = new CompoundTag();
        net.minecraft.nbt.ListTag records = new net.minecraft.nbt.ListTag();
        CompoundTag bad = new CompoundTag(); bad.putString("Type", "NOT_A_PROJECT"); records.add(bad);
        data.put("Records", records); root.put(CapitalProjectDataSerializer.ROOT, data);
        assertDoesNotThrow(() -> CapitalProjectDataSerializer.load(root));
        assertTrue(CapitalProjectManager.projects().isEmpty());
    }
}
