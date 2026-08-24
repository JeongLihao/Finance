package finance.warehouse;

import finance.commodity.CommodityInventoryManager;
import finance.data.serializer.WarehouseDataSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarehousePersistenceTest {
    @AfterEach void cleanup() { WarehouseManager.clearDirect(); CommodityInventoryManager.clearInventoriesDirect(); }

    @Test void roundTripPreservesIdentityCapacityAndBoundedOperations() {
        UUID owner = UUID.randomUUID();
        WarehouseRecord record = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", new BlockPos(1, 64, 2),
                owner, null, 4096, WarehouseStatus.ACTIVE, 3, 4, WarehousePermissionMode.OWNER_ONLY);
        for (int i = 0; i < 300; i++) record.recordOperation("operation-" + i);
        assertTrue(WarehouseManager.restore(record));
        CompoundTag root = new CompoundTag();
        WarehouseDataSerializer.save(root);
        WarehouseManager.clearDirect();
        WarehouseDataSerializer.load(root);
        WarehouseRecord loaded = WarehouseManager.get(record.warehouseId());
        assertNotNull(loaded);
        assertEquals(owner, loaded.ownerId());
        assertEquals(4096, loaded.capacityUnits());
        assertEquals(WarehouseRecord.MAX_OPERATION_KEYS, loaded.operationKeys().size());
    }

    @Test void corruptAndDuplicateRecordsAreIsolated() {
        UUID id = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        WarehouseRecord record = new WarehouseRecord(id, "minecraft:overworld", BlockPos.ZERO, owner,
                null, 100, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(record);
        CompoundTag root = new CompoundTag(); WarehouseDataSerializer.save(root);
        CompoundTag warehouseRoot = root.getCompound(WarehouseDataSerializer.ROOT);
        ListTag records = warehouseRoot.getList("Records", 10);
        CompoundTag duplicate = records.getCompound(0).copy(); duplicate.putLong("Pos", new BlockPos(2, 64, 2).asLong()); records.add(duplicate);
        CompoundTag bad = new CompoundTag(); bad.putUUID("Id", UUID.randomUUID()); bad.putUUID("Owner", owner);
        bad.putString("Dimension", "bad id"); bad.putLong("Pos", 0); bad.putInt("Capacity", -1); records.add(bad);
        WarehouseDataSerializer.load(root);
        assertEquals(1, WarehouseManager.all().size());
        assertEquals(BlockPos.ZERO, WarehouseManager.get(id).blockPos());
    }

    @Test void legacyRootLoadsEmptyAndOverCapacityDoesNotDeleteCustody() {
        WarehouseDataSerializer.load(new CompoundTag());
        UUID owner = UUID.randomUUID();
        CommodityInventoryManager.setCommodity(owner, "iron", 200);
        WarehouseRecord record = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO, owner,
                null, 100, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseManager.restore(record); WarehouseManager.refreshOwnerStatus(owner);
        assertEquals(WarehouseStatus.OVER_CAPACITY, record.status());
        assertFalse(WarehouseManager.canDepositCapacity(owner, 1));
        assertEquals(200, CommodityInventoryManager.getCommodityAmount(owner, "iron"));
    }

    @Test void activeReplacementAtSamePositionRetainsDestroyedRecoveryRecord() {
        UUID owner = UUID.randomUUID();
        WarehouseRecord destroyed = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                owner, null, 100, WarehouseStatus.DISABLED, 0, 0, WarehousePermissionMode.OWNER_ONLY);
        WarehouseRecord replacement = new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", BlockPos.ZERO,
                owner, null, 100, WarehouseStatus.ACTIVE, 1, 1, WarehousePermissionMode.OWNER_ONLY);
        assertTrue(WarehouseManager.restore(destroyed));
        assertTrue(WarehouseManager.restore(replacement));
        assertSame(destroyed, WarehouseManager.get(destroyed.warehouseId()));
        assertSame(replacement, WarehouseManager.get(replacement.warehouseId()));
    }
}
