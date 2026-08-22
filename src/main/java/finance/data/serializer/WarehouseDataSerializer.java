package finance.data.serializer;

import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class WarehouseDataSerializer {
    public static final String ROOT = "Warehouses";
    private static final int MAX_RECORDS = WarehouseManager.MAX_RECORDS;
    private static final int MAX_CAPACITY = 1_000_000;

    private WarehouseDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag warehouseRoot = new CompoundTag();
        warehouseRoot.putInt("Version", 1);
        ListTag records = new ListTag();
        int count = 0;
        for (WarehouseRecord record : WarehouseManager.all()) {
            if (count++ >= MAX_RECORDS) break;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", record.warehouseId());
            tag.putString("Dimension", record.dimensionId());
            tag.putLong("Pos", record.blockPos().asLong());
            tag.putUUID("Owner", record.ownerId());
            if (record.companyId() != null) tag.putUUID("Company", record.companyId());
            tag.putInt("Capacity", record.capacityUnits());
            tag.putString("Status", record.status().name());
            tag.putLong("CreatedDay", record.createdDay());
            tag.putLong("LastAuditDay", record.lastAuditDay());
            tag.putString("Permission", record.permissionMode().name());
            ListTag operations = new ListTag();
            for (String key : record.operationKeys()) operations.add(StringTag.valueOf(key));
            tag.put("Operations", operations);
            records.add(tag);
        }
        warehouseRoot.put("Records", records);
        root.put(ROOT, warehouseRoot);
    }

    public static void load(CompoundTag root) {
        WarehouseManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag warehouseRoot = root.getCompound(ROOT);
        ListTag records = warehouseRoot.getList("Records", Tag.TAG_COMPOUND);
        int limit = Math.min(MAX_RECORDS, records.size());
        for (int i = 0; i < limit; i++) {
            CompoundTag tag = records.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
                UUID owner = NbtDataSupport.readUuidOrNull(tag, "Owner");
                String dimension = tag.getString("Dimension");
                BlockPos pos = BlockPos.of(tag.getLong("Pos"));
                int capacity = tag.getInt("Capacity");
                WarehouseStatus status = NbtDataSupport.safeEnum(WarehouseStatus.class, tag.getString("Status"), null);
                WarehousePermissionMode permission = NbtDataSupport.safeEnum(
                        WarehousePermissionMode.class, tag.getString("Permission"), null);
                long created = tag.getLong("CreatedDay");
                long audit = tag.getLong("LastAuditDay");
                if (id == null || owner == null || ResourceLocation.tryParse(dimension) == null
                        || Math.abs((long) pos.getX()) > 30_000_000L || Math.abs((long) pos.getZ()) > 30_000_000L
                        || pos.getY() < -2_048 || pos.getY() > 2_048 || capacity <= 0 || capacity > MAX_CAPACITY
                        || status == null || permission == null || created < 0 || audit < -1) continue;
                WarehouseRecord record = new WarehouseRecord(id, dimension, pos, owner,
                        NbtDataSupport.readUuidOrNull(tag, "Company"), capacity, status, created, audit, permission);
                ListTag operations = tag.getList("Operations", Tag.TAG_STRING);
                for (int op = Math.max(0, operations.size() - WarehouseRecord.MAX_OPERATION_KEYS);
                     op < operations.size(); op++) {
                    String key = operations.getString(op);
                    if (!key.isBlank() && key.length() <= 96) record.restoreOperation(key);
                }
                WarehouseManager.restore(record);
            } catch (RuntimeException ignored) {
                // 单条损坏仓库被隔离，不影响其余经济数据。
            }
        }
        for (WarehouseRecord record : WarehouseManager.all()) WarehouseManager.refreshOwnerStatus(record.ownerId());
    }
}
