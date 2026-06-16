package finance.account;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import finance.data.EconomySavedData;

/**
 * 玩家账户管理器 —— 所有资金操作的入口。
 * <p>
 * 提供存款、取款、转账、冻结/解冻等核心金融操作。
 * 每次修改资金数据后自动标记持久化为脏数据。
 * </p>
 */
public class AccountManager {

    /** 所有玩家账户，key 为玩家 UUID */
    private static final Map<UUID, Account> ACCOUNTS = new HashMap<>();

    /** 交易记录列表，最多保留 500 条 */
    private static final List<TransactionRecord> TRANSACTIONS = new ArrayList<>();

    // ================================================================
    // 账户查询
    // ================================================================

    /** 获取或创建玩家账户（懒加载） */
    public static Account getAccount(UUID playerId) {

        if (!ACCOUNTS.containsKey(playerId)) {
            ACCOUNTS.put(playerId, new Account(playerId));
        }

        return ACCOUNTS.get(playerId);
    }

    public static long getBalance(UUID playerId) {

        return getAccount(playerId).getBalance();
    }

    public static Map<UUID, Account> getAccounts() {
        return ACCOUNTS;
    }

    // ================================================================
    // 基础资金操作
    // ================================================================

    /** 存款 */
    public static void deposit(UUID playerId, long amount) {

        getAccount(playerId).deposit(amount);
        EconomySavedData.markDirty();
    }

    /** 取款，余额不足返回 false */
    public static boolean withdraw(UUID playerId, long amount) {

        boolean success = getAccount(playerId).withdraw(amount);

        if (success) {
            EconomySavedData.markDirty();
        }

        return success;
    }

    /** 转账，不允许自转账或金额 ≤ 0 */
    public static boolean transfer(UUID from, UUID to, long amount) {

        if (amount <= 0 || from.equals(to)) {
            return false;
        }

        Account sender = getAccount(from);
        Account receiver = getAccount(to);

        if (!sender.withdraw(amount)) {
            return false;
        }

        receiver.deposit(amount);
        EconomySavedData.markDirty();

        addTransactionRecord(
                new TransactionRecord(
                        from,
                        to,
                        amount,
                        "TRANSFER"
                )
        );

        return true;
    }

    // ================================================================
    // 冻结/解冻 —— 市场挂单专用
    // ================================================================

    /** 冻结资金（市场挂 BUY 单时锁定购买资金） */
    public static boolean freezeFunds(UUID playerId, long amount) {
        boolean success = getAccount(playerId).freezeFunds(amount);
        if (success) {
            EconomySavedData.markDirty();
        }
        return success;
    }

    /** 解冻资金（取消 BUY 单或成交后退回余额） */
    public static void unfreezeFunds(UUID playerId, long amount) {
        getAccount(playerId).unfreezeFunds(amount);
        EconomySavedData.markDirty();
    }

    // ================================================================
    // 交易记录管理
    // ================================================================

    /** 添加交易记录，超过 500 条自动删除最早记录 */
    public static void addTransactionRecord(TransactionRecord record) {
        TRANSACTIONS.add(record);
        while (TRANSACTIONS.size() > 500) {
            TRANSACTIONS.remove(0);
        }
    }

    public static List<TransactionRecord> getTransactions() {
        return TRANSACTIONS;
    }

    /** 清空交易记录（数据加载时使用） */
    public static void clearTransactions() {
        TRANSACTIONS.clear();
    }
}
