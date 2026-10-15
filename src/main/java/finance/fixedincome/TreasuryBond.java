package finance.fixedincome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 国债 —— 央行发行的长期限无风险债券。同一天同期限的认购并入同一只债券，
 * 收益率在认购瞬间锁定，之后基准利率变动不追溯。
 */
public final class TreasuryBond {
    private final UUID id;
    private final int termDays;
    private final int annualRateBasisPoints;
    private final long issueDay;
    private final long maturityDay;
    private TreasuryBondStatus status;
    private final Map<UUID, Long> principalByPlayer = new LinkedHashMap<>();

    public TreasuryBond(UUID id, int termDays, int annualRateBasisPoints, long issueDay,
                        long maturityDay, TreasuryBondStatus status) {
        this.id = id; this.termDays = termDays; this.annualRateBasisPoints = annualRateBasisPoints;
        this.issueDay = issueDay; this.maturityDay = maturityDay; this.status = status;
    }
    public UUID id() { return id; } public int termDays() { return termDays; }
    public int annualRateBasisPoints() { return annualRateBasisPoints; }
    public long issueDay() { return issueDay; } public long maturityDay() { return maturityDay; }
    public TreasuryBondStatus status() { return status; }
    public Map<UUID, Long> principalByPlayer() { return Collections.unmodifiableMap(principalByPlayer); }
    boolean addPrincipal(UUID player, long amount) {
        long old = principalByPlayer.getOrDefault(player, 0L);
        if (player == null || amount <= 0 || old > Long.MAX_VALUE - amount) return false;
        principalByPlayer.put(player, old + amount); return true;
    }
    /** 提前赎回：移除玩家持仓并返回本金；无持仓返回 -1。 */
    long removePrincipal(UUID player) {
        if (player == null) return -1;
        Long removed = principalByPlayer.remove(player);
        return removed == null ? -1 : removed;
    }
    public void putPrincipalDirect(UUID player, long amount) { if (player != null && amount > 0) principalByPlayer.put(player, amount); }
    void setStatus(TreasuryBondStatus value) { status = value; }
}
