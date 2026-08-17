package finance.account;

import java.util.UUID;

/**
 * 玩家资金账户，管理可用余额和冻结余额。
 * 冻结余额用于市场挂单时锁定资金，防止重复使用。
 */
public class Account {

    private final UUID playerId;

    /** 可用余额 */
    private long balance;

    /** 市场挂单冻结的资金 */
    private long frozenBalance;

    public Account(UUID playerId) {
        this.playerId = playerId;
        this.balance = 0;
        this.frozenBalance = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getBalance() {
        return balance;
    }

    public long getFrozenBalance() {
        return frozenBalance;
    }

    // ================================================================
    // 资金操作
    // ================================================================

    /** 入账，amount 必须为正数 */
    public boolean deposit(long amount) {
        if (!canDeposit(amount)) {
            return false;
        }
        balance += amount;
        return true;
    }

    public boolean canDeposit(long amount) {
        return amount > 0 && balance >= 0 && balance <= Long.MAX_VALUE - amount;
    }

    /** 出账，余额不足返回 false */
    public boolean withdraw(long amount) {
        if (amount <= 0) {
            return false;
        }

        if (balance < amount) {
            return false;
        }

        balance -= amount;
        return true;
    }

    public boolean setBalance(long balance) {
        if (balance < 0) {
            return false;
        }
        this.balance = balance;
        return true;
    }

    // ================================================================
    // 冻结/解冻 —— 用于市场挂单的资金锁定
    // ================================================================

    /** 将可用余额中的 amount 转为冻结余额，余额不足返回 false */
    public boolean freezeFunds(long amount) {
        if (amount <= 0 || balance < amount || frozenBalance < 0
                || frozenBalance > Long.MAX_VALUE - amount) {
            return false;
        }
        balance -= amount;
        frozenBalance += amount;
        return true;
    }

    /** 将冻结余额中的 amount 解冻回可用余额 */
    public boolean unfreezeFunds(long amount) {
        if (amount <= 0) {
            return false;
        }
        long actual = Math.min(amount, frozenBalance);
        if (actual <= 0 || !canDeposit(actual)) {
            return false;
        }
        frozenBalance -= actual;
        balance += actual;
        return true;
    }

    /**
     * 从冻结资金中完成一笔结算。
     * reservedAmount 是本次成交对应的冻结资金，paymentAmount 是实际支付金额。
     * 两者的差额会退回可用余额。
     */
    public boolean settleFrozenFunds(long reservedAmount, long paymentAmount) {
        if (reservedAmount <= 0 || paymentAmount < 0 || paymentAmount > reservedAmount) {
            return false;
        }

        if (frozenBalance < reservedAmount || !canSettleFrozenFunds(reservedAmount, paymentAmount)) {
            return false;
        }

        frozenBalance -= reservedAmount;
        balance += reservedAmount - paymentAmount;
        return true;
    }

    public boolean canSettleFrozenFunds(long reservedAmount, long paymentAmount) {
        if (reservedAmount <= 0 || paymentAmount < 0 || paymentAmount > reservedAmount
                || frozenBalance < reservedAmount) {
            return false;
        }
        long refund = reservedAmount - paymentAmount;
        return refund == 0 || canDeposit(refund);
    }

    /** Reverses a previously completed frozen-funds settlement without transient overflow. */
    public boolean rollbackFrozenSettlement(long reservedAmount, long paymentAmount) {
        if (!canRollbackFrozenSettlement(reservedAmount, paymentAmount)) return false;
        long refund = reservedAmount - paymentAmount;
        balance -= refund;
        frozenBalance += reservedAmount;
        return true;
    }

    public boolean canRollbackFrozenSettlement(long reservedAmount, long paymentAmount) {
        if (reservedAmount <= 0 || paymentAmount < 0 || paymentAmount > reservedAmount) return false;
        long refund = reservedAmount - paymentAmount;
        return balance >= refund && frozenBalance >= 0 && frozenBalance <= Long.MAX_VALUE - reservedAmount;
    }
}
