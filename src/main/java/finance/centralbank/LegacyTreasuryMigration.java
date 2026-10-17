package finance.centralbank;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.market.CentralBank;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 国家财政已移除：旧存档里国库系统账户的余额一次性并入央行账户，国库对央行的债务随之注销（钱本来就是央行借出的）。
 * 账户为空或不存在时什么也不做，可以每天调用。
 */
public final class LegacyTreasuryMigration {
    /** 旧国库账户（与原 {@code finance.macro.Treasury.UUID} 相同）。 */
    public static final UUID LEGACY_TREASURY = UUID.nameUUIDFromBytes("finance-macro-treasury".getBytes(StandardCharsets.UTF_8));

    private LegacyTreasuryMigration() { }

    /** 返回并入的金额。 */
    public static long run() {
        if (!AccountManager.getAccounts().containsKey(LEGACY_TREASURY)) return 0;
        long balance = AccountManager.getBalance(LEGACY_TREASURY);
        if (balance <= 0) return 0;
        AccountManager.getOrCreateSystemAccount(CentralBank.UUID);
        if (!AccountManager.moveFunds(LEGACY_TREASURY, CentralBank.UUID, balance)) return 0;
        AccountManager.addTransactionRecord(new TransactionRecord(LEGACY_TREASURY, CentralBank.UUID, balance,
                TransactionType.LEGACY_TREASURY_MERGE, CentralBank.UUID, "国库并入央行", 0));
        return balance;
    }
}
