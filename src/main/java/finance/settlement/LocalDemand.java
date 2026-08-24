package finance.settlement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class LocalDemand {
    public static final int MAX_OPERATION_KEYS = 64;
    private final UUID id, settlementId, escrowAccountId;
    private final String commodityId, theme;
    private final int quantity;
    private final long reward, createdDay, deadlineDay;
    private DemandStatus status;
    private UUID acceptedPlayerId;
    private final LinkedHashSet<String> operations = new LinkedHashSet<>();

    public LocalDemand(UUID id, UUID settlementId, String commodityId, String theme, int quantity, long reward,
                       UUID escrowAccountId, long createdDay, long deadlineDay, DemandStatus status,
                       UUID acceptedPlayerId) {
        if (id == null || settlementId == null || escrowAccountId == null || commodityId == null || commodityId.isBlank()
                || commodityId.length() > 64 || theme == null || theme.isBlank() || theme.length() > 32
                || quantity <= 0 || quantity > 4096 || reward <= 0 || createdDay < 0 || deadlineDay < createdDay
                || status == null) throw new IllegalArgumentException("invalid local demand");
        if ((status == DemandStatus.ACCEPTED || status == DemandStatus.COMPLETED) && acceptedPlayerId == null)
            throw new IllegalArgumentException("missing accepter");
        this.id=id; this.settlementId=settlementId; this.commodityId=commodityId; this.theme=theme;
        this.quantity=quantity; this.reward=reward; this.escrowAccountId=escrowAccountId;
        this.createdDay=createdDay; this.deadlineDay=deadlineDay; this.status=status; this.acceptedPlayerId=acceptedPlayerId;
    }
    public boolean accept(UUID player) { if (status != DemandStatus.OPEN || player == null) return false; status=DemandStatus.ACCEPTED; acceptedPlayerId=player; return true; }
    public boolean complete(UUID player) { if (status != DemandStatus.ACCEPTED || !player.equals(acceptedPlayerId)) return false; status=DemandStatus.COMPLETED; return true; }
    public boolean expire() { if (status != DemandStatus.OPEN && status != DemandStatus.ACCEPTED) return false; status=DemandStatus.EXPIRED; return true; }
    public void refunded() { status=DemandStatus.REFUNDED; }
    public void quarantine() { status=DemandStatus.QUARANTINED; }
    public boolean hasOperation(String key){ return operations.contains(key); }
    public void recordOperation(String key){ if(key==null||key.isBlank()||key.length()>128)return; operations.add(key); while(operations.size()>MAX_OPERATION_KEYS)operations.remove(operations.iterator().next()); }
    public void restoreOperation(String key){ recordOperation(key); }
    public Set<String> operations(){ return Set.copyOf(operations); }
    public UUID id(){return id;} public UUID settlementId(){return settlementId;} public UUID escrowAccountId(){return escrowAccountId;}
    public String commodityId(){return commodityId;} public String theme(){return theme;} public int quantity(){return quantity;}
    public long reward(){return reward;} public long createdDay(){return createdDay;} public long deadlineDay(){return deadlineDay;}
    public DemandStatus status(){return status;} public UUID acceptedPlayerId(){return acceptedPlayerId;}
}
