package finance.settlement;

public enum DemandStatus {
    OPEN, ACCEPTED, COMPLETED, EXPIRED, REFUNDED, QUARANTINED;
    public boolean terminal() { return this == COMPLETED || this == EXPIRED || this == REFUNDED || this == QUARANTINED; }
}
