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
    public void deposit(long amount) {
        if (amount <= 0) {
            return;
        }

        balance += amount;
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

    public void setBalance(long balance) {
        this.balance = balance;
    }

    // ================================================================
    // 冻结/解冻 —— 用于市场挂单的资金锁定
    // ================================================================

    /** 将可用余额中的 amount 转为冻结余额，余额不足返回 false */
    public boolean freezeFunds(long amount) {
        if (amount <= 0 || balance < amount) {
            return false;
        }
        balance -= amount;
        frozenBalance += amount;
        return true;
    }

    /** 将冻结余额中的 amount 解冻回可用余额 */
    public void unfreezeFunds(long amount) {
        if (amount <= 0) {
            return;
        }
        long actual = Math.min(amount, frozenBalance);
        frozenBalance -= actual;
        balance += actual;
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

        if (frozenBalance < reservedAmount) {
            return false;
        }

        frozenBalance -= reservedAmount;
        balance += reservedAmount - paymentAmount;
        return true;
    }
}
