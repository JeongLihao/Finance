package finance.bank;

import java.util.*;

public final class CommercialBank {
    public static final int MAX_SNAPSHOTS = 30;
    private final UUID id;
    private final String code;
    private final String name;
    private BankStatus status;
    private long paidInCapital;
    private BankPolicy policy;
    private final BankLedger ledger;
    private final List<BankBalanceSheetSnapshot> snapshots = new ArrayList<>();

    public CommercialBank(UUID id, String code, String name, BankStatus status, long paidInCapital, BankPolicy policy) {
        if (id == null
                || code == null
                || code.isBlank()
                || code.length() > 16
                || name == null
                || name.isBlank()
                || name.length() > 64
                || status == null
                || paidInCapital <= 0
                || policy == null) throw new IllegalArgumentException();
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
        this.paidInCapital = paidInCapital;
        this.policy = policy;
        this.ledger = new BankLedger(id);
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public BankStatus status() {
        return status;
    }

    public long paidInCapital() {
        return paidInCapital;
    }

    public BankPolicy policy() {
        return policy;
    }

    public BankLedger ledger() {
        return ledger;
    }

    public void setStatus(BankStatus v) {
        if (v != null) status = v;
    }

    public boolean acceptsNewBusiness() {
        return status == BankStatus.ACTIVE || status == BankStatus.WATCH;
    }
    /**
     * 调整账面实收资本（P1-17）：分红实质减资时与账本 EQUITY_PAID_IN 科目同步扣减，
     * 使 NBT `Capital` 标签与 {@link BankingManager#closeDay} 首次快照口径保持真实。
     * 亏损核销（如 {@link DepositInsuranceService} 的股权清零）不是减资，<b>不得</b>调用此方法。
     */
    public void adjustPaidInCapital(long delta) {
        if (delta == 0) return;
        long next;
        try {
            next = Math.addExact(paidInCapital, delta);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("paid-in capital overflow");
        }
        if (next < 0) throw new IllegalArgumentException("paid-in capital cannot go negative");
        paidInCapital = next;
    }

    public void updatePolicy(BankPolicy v) {
        if (v != null) policy = v;
    }

    public List<BankBalanceSheetSnapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    public void addSnapshot(BankBalanceSheetSnapshot s) {
        if (s != null) {
            snapshots.add(s);
            if (snapshots.size() > MAX_SNAPSHOTS)
                snapshots.subList(0, snapshots.size() - MAX_SNAPSHOTS).clear();
        }
    }
}
