package finance.gameplay.company;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CompanyFacilityRecord {
    public static final int MAX_LEVEL = 3;
    public static final int MAX_OPERATION_KEYS = 128;
    private final UUID facilityId;
    private final UUID companyId;
    private final String dimensionId;
    private final BlockPos blockPos;
    private final CompanyFacilityType type;
    private int productionLevel;
    private CompanyFacilityStatus status;
    private long lastProcessedDay;
    private UUID boundWarehouseId;
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();

    public CompanyFacilityRecord(UUID facilityId, UUID companyId, String dimensionId, BlockPos blockPos,
                                 CompanyFacilityType type, int productionLevel, CompanyFacilityStatus status,
                                 long lastProcessedDay, UUID boundWarehouseId) {
        if (facilityId == null || companyId == null || dimensionId == null || dimensionId.isBlank()
                || blockPos == null || type == null || productionLevel < 1 || productionLevel > MAX_LEVEL
                || status == null || lastProcessedDay < -1) throw new IllegalArgumentException("invalid company facility");
        this.facilityId = facilityId; this.companyId = companyId; this.dimensionId = dimensionId;
        this.blockPos = blockPos.immutable(); this.type = type; this.productionLevel = productionLevel;
        this.status = status; this.lastProcessedDay = lastProcessedDay; this.boundWarehouseId = boundWarehouseId;
    }
    public UUID facilityId() { return facilityId; }
    public UUID companyId() { return companyId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos blockPos() { return blockPos; }
    public CompanyFacilityType type() { return type; }
    public int productionLevel() { return productionLevel; }
    public CompanyFacilityStatus status() { return status; }
    public long lastProcessedDay() { return lastProcessedDay; }
    public UUID boundWarehouseId() { return boundWarehouseId; }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }
    public void setStatus(CompanyFacilityStatus value) { if (value != null) status = value; }
    public void setLastProcessedDay(long value) { if (value >= -1) lastProcessedDay = value; }
    public void bindWarehouse(UUID value) { boundWarehouseId = value; }
    public boolean upgrade() { if (productionLevel >= MAX_LEVEL) return false; productionLevel++; return true; }
    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }
    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) operationKeys.remove(operationKeys.iterator().next());
    }
}
