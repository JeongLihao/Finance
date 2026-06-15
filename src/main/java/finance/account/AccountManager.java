package finance.account;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import finance.data.EconomySavedData;

public class AccountManager {

    private static final Map<UUID, Account> ACCOUNTS = new HashMap<>();
    private static final List<TransactionRecord> TRANSACTIONS = new ArrayList<>();

    public static Account getAccount(UUID playerId) {

        if (!ACCOUNTS.containsKey(playerId)) {
            ACCOUNTS.put(playerId, new Account(playerId));
        }

        return ACCOUNTS.get(playerId);
    }

    public static long getBalance(UUID playerId) {

        return getAccount(playerId).getBalance();
    }

    public static void deposit(UUID playerId, long amount) {

        getAccount(playerId).deposit(amount);
        EconomySavedData.markDirty();
    }

    public static boolean withdraw(UUID playerId, long amount) {

        boolean success = getAccount(playerId).withdraw(amount);

        if (success) {
            EconomySavedData.markDirty();
        }

        return success;
    }

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
    public static List<TransactionRecord> getTransactions() {
        return TRANSACTIONS;
    }
    public static Map<UUID, Account> getAccounts() {
        return ACCOUNTS;
    }

    public static void addTransactionRecord(TransactionRecord record) {
        TRANSACTIONS.add(record);
        while (TRANSACTIONS.size() > 500) {
            TRANSACTIONS.remove(0);
        }
    }

    public static void clearTransactions() {
        TRANSACTIONS.clear();
    }

    public static boolean freezeFunds(UUID playerId, long amount) {
        boolean success = getAccount(playerId).freezeFunds(amount);
        if (success) {
            EconomySavedData.markDirty();
        }
        return success;
    }

    public static void unfreezeFunds(UUID playerId, long amount) {
        getAccount(playerId).unfreezeFunds(amount);
        EconomySavedData.markDirty();
    }


}

