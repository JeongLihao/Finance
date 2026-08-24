package finance.logistics;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Metadata only. Commodity units are held exclusively by {@link TransportCustodyManager}. */
public final class Shipment {
    public static final int MAX_OPERATION_KEYS = 64;
    private final UUID id;
    private final UUID sourceWarehouseId;
    private final UUID destinationWarehouseId;
    private final UUID contractId;
    private final String commodityId;
    private final int quantity;
    private final UUID creatorId;
    private UUID carrierId;
    private final UUID companyId;
    private ShipmentStatus status;
    private final long createdDay;
    private final long deadlineDay;
    private UUID tokenId;
    private String failureReason;
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();

    public Shipment(UUID id, UUID sourceWarehouseId, UUID destinationWarehouseId, UUID contractId,
                    String commodityId, int quantity, UUID creatorId, UUID carrierId, UUID companyId,
                    ShipmentStatus status, long createdDay, long deadlineDay, UUID tokenId,
                    String failureReason) {
        if (id == null || sourceWarehouseId == null || destinationWarehouseId == null
                || sourceWarehouseId.equals(destinationWarehouseId) || commodityId == null
                || commodityId.isBlank() || commodityId.length() > 64 || quantity <= 0
                || creatorId == null || carrierId == null || status == null || createdDay < 0
                || deadlineDay < createdDay || tokenId == null) {
            throw new IllegalArgumentException("Invalid shipment");
        }
        this.id = id;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.contractId = contractId;
        this.commodityId = commodityId;
        this.quantity = quantity;
        this.creatorId = creatorId;
        this.carrierId = carrierId;
        this.companyId = companyId;
        this.status = status;
        this.createdDay = createdDay;
        this.deadlineDay = deadlineDay;
        this.tokenId = tokenId;
        this.failureReason = limit(failureReason, 96);
    }

    public UUID id() { return id; }
    public UUID sourceWarehouseId() { return sourceWarehouseId; }
    public UUID destinationWarehouseId() { return destinationWarehouseId; }
    public UUID contractId() { return contractId; }
    public String commodityId() { return commodityId; }
    public int quantity() { return quantity; }
    public UUID creatorId() { return creatorId; }
    public UUID carrierId() { return carrierId; }
    public UUID companyId() { return companyId; }
    public ShipmentStatus status() { return status; }
    public long createdDay() { return createdDay; }
    public long deadlineDay() { return deadlineDay; }
    public UUID tokenId() { return tokenId; }
    public String failureReason() { return failureReason; }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }

    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }
    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) operationKeys.remove(operationKeys.iterator().next());
    }
    public void restoreOperation(String key) { recordOperation(key); }
    public boolean markDelivered() {
        if (status != ShipmentStatus.IN_TRANSIT) return false;
        status = ShipmentStatus.DELIVERED;
        failureReason = "";
        return true;
    }
    public boolean markLossPending(String reason) {
        if (status != ShipmentStatus.IN_TRANSIT) return false;
        status = ShipmentStatus.LOSS_PENDING;
        failureReason = limit(reason, 96);
        return true;
    }
    public boolean recover(UUID newCarrier, UUID newToken) {
        if (status != ShipmentStatus.LOSS_PENDING || newCarrier == null || newToken == null) return false;
        carrierId = newCarrier;
        tokenId = newToken;
        status = ShipmentStatus.IN_TRANSIT;
        failureReason = "";
        return true;
    }
    public void quarantine(String reason) {
        status = ShipmentStatus.QUARANTINED;
        failureReason = limit(reason, 96);
    }

    private static String limit(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
