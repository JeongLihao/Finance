package finance.fixedincome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CentralBankBill {
    private final UUID id;
    private final int termDays;
    private final int annualRateBasisPoints;
    private final long issueDay;
    private final long maturityDay;
    private CentralBankBillStatus status;
    private final Map<UUID, Long> principalByPlayer = new LinkedHashMap<>();

    public CentralBankBill(UUID id, int termDays, int annualRateBasisPoints, long issueDay,
                           long maturityDay, CentralBankBillStatus status) {
        this.id = id; this.termDays = termDays; this.annualRateBasisPoints = annualRateBasisPoints;
        this.issueDay = issueDay; this.maturityDay = maturityDay; this.status = status;
    }
    public UUID id() { return id; } public int termDays() { return termDays; }
    public int annualRateBasisPoints() { return annualRateBasisPoints; }
    public long issueDay() { return issueDay; } public long maturityDay() { return maturityDay; }
    public CentralBankBillStatus status() { return status; }
    public Map<UUID, Long> principalByPlayer() { return Collections.unmodifiableMap(principalByPlayer); }
    boolean addPrincipal(UUID player, long amount) {
        long old = principalByPlayer.getOrDefault(player, 0L);
        if (player == null || amount <= 0 || old > Long.MAX_VALUE - amount) return false;
        principalByPlayer.put(player, old + amount); return true;
    }
    public void putPrincipalDirect(UUID player, long amount) { if (player != null && amount > 0) principalByPlayer.put(player, amount); }
    void setStatus(CentralBankBillStatus value) { status = value; }
}
