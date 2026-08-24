package finance.warehouse;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class WarehouseRecord {
    public static final int MAX_OPERATION_KEYS = 256;
    private final UUID warehouseId;
    private final String dimensionId;
    private final BlockPos blockPos;
    private final UUID ownerId;
    private UUID companyId;
    private int capacityUnits;
    private WarehouseTier tier;
    private WarehouseStatus status;
    private final long createdDay;
    private long lastAuditDay;
    private final WarehousePermissionMode permissionMode;
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();

    public WarehouseRecord(UUID warehouseId, String dimensionId, BlockPos blockPos, UUID ownerId,
                           UUID companyId, int capacityUnits, WarehouseStatus status, long createdDay,
                           long lastAuditDay, WarehousePermissionMode permissionMode) {
        if (warehouseId == null || dimensionId == null || dimensionId.isBlank() || blockPos == null
                || ownerId == null || capacityUnits <= 0 || status == null || createdDay < 0
                || lastAuditDay < -1 || permissionMode == null) {
            throw new IllegalArgumentException("Invalid warehouse record");
        }
        this.warehouseId = warehouseId;
        this.dimensionId = dimensionId;
        this.blockPos = blockPos.immutable();
        this.ownerId = ownerId;
        this.companyId = companyId;
        this.capacityUnits = capacityUnits;
        this.tier = WarehouseTier.fromLegacyCapacity(capacityUnits);
        this.status = status;
        this.createdDay = createdDay;
        this.lastAuditDay = lastAuditDay;
        this.permissionMode = permissionMode;
    }

    public WarehouseRecord(UUID warehouseId, String dimensionId, BlockPos blockPos, UUID ownerId,
                           UUID companyId, WarehouseTier tier, int capacityUnits, WarehouseStatus status,
                           long createdDay, long lastAuditDay, WarehousePermissionMode permissionMode) {
        this(warehouseId, dimensionId, blockPos, ownerId, companyId, capacityUnits, status,
                createdDay, lastAuditDay, permissionMode);
        if (tier == null) throw new IllegalArgumentException("Invalid warehouse tier");
        this.tier = tier;
    }

    public UUID warehouseId() { return warehouseId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos blockPos() { return blockPos; }
    public UUID ownerId() { return ownerId; }
    public UUID companyId() { return companyId; }
    public int capacityUnits() { return capacityUnits; }
    public WarehouseTier tier() { return tier; }
    public int transferLimit() { return tier.transferLimit(); }
    public WarehouseStatus status() { return status; }
    public long createdDay() { return createdDay; }
    public long lastAuditDay() { return lastAuditDay; }
    public WarehousePermissionMode permissionMode() { return permissionMode; }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }

    public void setStatus(WarehouseStatus status) { if (status != null) this.status = status; }
    public void setLastAuditDay(long day) { if (day >= -1) lastAuditDay = day; }
    public void bindCompany(UUID companyId) { this.companyId = companyId; }
    public boolean upgrade(WarehouseTier target, int newCapacity) {
        if (target == null || tier.next() != target || newCapacity < capacityUnits) return false;
        tier = target;
        capacityUnits = newCapacity;
        return true;
    }

    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }
    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) {
            operationKeys.remove(operationKeys.iterator().next());
        }
    }

    public void restoreOperation(String key) { recordOperation(key); }
}
