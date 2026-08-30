package finance.contract;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class FinanceContract {
    public static final int MAX_OPERATION_KEYS = 128;
    private final UUID id;
    private final ContractType type;
    private final ContractIssuerType issuerType;
    private final UUID issuerId;
    private final String commodityId;
    private final int requiredQuantity;
    private int deliveredQuantity;
    private final long rewardAmount;
    private final UUID escrowAccountId;
    private UUID destinationWarehouseId;
    private final long createdDay;
    private final long deadlineDay;
    private UUID acceptedPlayerId;
    private UUID acceptedCompanyId;
    private ContractStatus status;
    private String failureReason;
    private final LinkedHashSet<String> operationKeys = new LinkedHashSet<>();

    public FinanceContract(UUID id, ContractType type, ContractIssuerType issuerType, UUID issuerId,
                           String commodityId, int requiredQuantity, int deliveredQuantity,
                           long rewardAmount, UUID escrowAccountId, UUID destinationWarehouseId,
                           long createdDay, long deadlineDay, UUID acceptedPlayerId,
                           ContractStatus status, String failureReason) {
        this(id, type, issuerType, issuerId, commodityId, requiredQuantity, deliveredQuantity,
                rewardAmount, escrowAccountId, destinationWarehouseId, createdDay, deadlineDay,
                acceptedPlayerId, null, status, failureReason);
    }

    public FinanceContract(UUID id, ContractType type, ContractIssuerType issuerType, UUID issuerId,
                           String commodityId, int requiredQuantity, int deliveredQuantity,
                           long rewardAmount, UUID escrowAccountId, UUID destinationWarehouseId,
                           long createdDay, long deadlineDay, UUID acceptedPlayerId, UUID acceptedCompanyId,
                           ContractStatus status, String failureReason) {
        if (id == null || type == null || issuerType == null || issuerId == null || commodityId == null
                || commodityId.isBlank() || commodityId.length() > 64 || requiredQuantity <= 0
                || deliveredQuantity < 0 || deliveredQuantity > requiredQuantity || rewardAmount <= 0
                || escrowAccountId == null || createdDay < 0 || deadlineDay <= createdDay || status == null) {
            throw new IllegalArgumentException("Invalid finance contract");
        }
        if (status == ContractStatus.ACCEPTED && (acceptedPlayerId == null) == (acceptedCompanyId == null))
            throw new IllegalArgumentException("Missing or ambiguous acceptor");
        if (acceptedCompanyId != null && issuerType != ContractIssuerType.COMPANY)
            throw new IllegalArgumentException("Company supplier requires company procurement");
        if (status == ContractStatus.COMPLETED && deliveredQuantity != requiredQuantity) throw new IllegalArgumentException("Incomplete completion");
        this.id = id; this.type = type; this.issuerType = issuerType; this.issuerId = issuerId;
        this.commodityId = commodityId; this.requiredQuantity = requiredQuantity;
        this.deliveredQuantity = deliveredQuantity; this.rewardAmount = rewardAmount;
        this.escrowAccountId = escrowAccountId; this.destinationWarehouseId = destinationWarehouseId;
        this.createdDay = createdDay; this.deadlineDay = deadlineDay; this.acceptedPlayerId = acceptedPlayerId;
        this.acceptedCompanyId = acceptedCompanyId;
        this.status = status; this.failureReason = limit(failureReason, 96);
    }

    public UUID id() { return id; }
    public ContractType type() { return type; }
    public ContractIssuerType issuerType() { return issuerType; }
    public UUID issuerId() { return issuerId; }
    public String commodityId() { return commodityId; }
    public int requiredQuantity() { return requiredQuantity; }
    public int deliveredQuantity() { return deliveredQuantity; }
    public long rewardAmount() { return rewardAmount; }
    public UUID escrowAccountId() { return escrowAccountId; }
    public UUID destinationWarehouseId() { return destinationWarehouseId; }
    public long createdDay() { return createdDay; }
    public long deadlineDay() { return deadlineDay; }
    public UUID acceptedPlayerId() { return acceptedPlayerId; }
    public UUID acceptedCompanyId() { return acceptedCompanyId; }
    public ContractStatus status() { return status; }
    public String failureReason() { return failureReason; }
    public Set<String> operationKeys() { return Set.copyOf(operationKeys); }

    public boolean accept(UUID playerId, UUID warehouseId) {
        if (status != ContractStatus.OPEN || playerId == null || warehouseId == null) return false;
        acceptedPlayerId = playerId; destinationWarehouseId = warehouseId; status = ContractStatus.ACCEPTED; return true;
    }
    public boolean acceptCompany(UUID companyId) {
        if (status != ContractStatus.OPEN || issuerType != ContractIssuerType.COMPANY || companyId == null
                || companyId.equals(issuerId)) return false;
        acceptedCompanyId = companyId; status = ContractStatus.ACCEPTED; return true;
    }
    public int remainingQuantity() { return requiredQuantity - deliveredQuantity; }
    public long paidAmount() { return proportionalReward(deliveredQuantity); }
    public long remainingReward() { return rewardAmount - paidAmount(); }
    public long paymentFor(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity()) return -1;
        return proportionalReward(deliveredQuantity + quantity) - proportionalReward(deliveredQuantity);
    }
    public boolean recordCompanyDelivery(int quantity) {
        if (status != ContractStatus.ACCEPTED || acceptedCompanyId == null || quantity <= 0
                || quantity > remainingQuantity()) return false;
        deliveredQuantity += quantity;
        if (deliveredQuantity == requiredQuantity) { status = ContractStatus.COMPLETED; failureReason = ""; }
        return true;
    }
    public void complete() { deliveredQuantity = requiredQuantity; status = ContractStatus.COMPLETED; failureReason = ""; }
    public void expire() { if (!status.terminal()) status = ContractStatus.EXPIRED; }
    public void cancel() { if (!status.terminal()) status = ContractStatus.CANCELLED; }
    public void quarantine(String reason) { status = ContractStatus.QUARANTINED; failureReason = limit(reason, 96); }
    public boolean hasOperation(String key) { return key != null && operationKeys.contains(key); }
    public void recordOperation(String key) {
        if (key == null || key.isBlank() || key.length() > 96) return;
        operationKeys.add(key);
        while (operationKeys.size() > MAX_OPERATION_KEYS) operationKeys.remove(operationKeys.iterator().next());
    }
    public void restoreOperation(String key) { recordOperation(key); }
    public void setFailureReason(String reason) { failureReason = limit(reason, 96); }
    private static String limit(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
    private long proportionalReward(int quantity) {
        return java.math.BigInteger.valueOf(rewardAmount).multiply(java.math.BigInteger.valueOf(quantity))
                .divide(java.math.BigInteger.valueOf(requiredQuantity)).longValueExact();
    }
}
