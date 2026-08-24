package finance.exploration;

public enum ExplorationStatus {
    ACTIVE, COMPLETED, EXPIRED, CANCELLED, QUARANTINED;
    public boolean terminal(){return this!=ACTIVE;}
}
