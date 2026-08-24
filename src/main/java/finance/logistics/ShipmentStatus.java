package finance.logistics;

public enum ShipmentStatus {
    READY,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    LOSS_PENDING,
    QUARANTINED;

    public boolean carriesCargo() {
        return this == READY || this == IN_TRANSIT || this == LOSS_PENDING || this == QUARANTINED;
    }

    public boolean terminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
