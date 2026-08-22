package finance.contract;

public enum ContractStatus {
    OPEN,
    ACCEPTED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    QUARANTINED;

    public boolean terminal() {
        return this == COMPLETED || this == EXPIRED || this == CANCELLED || this == QUARANTINED;
    }
}
