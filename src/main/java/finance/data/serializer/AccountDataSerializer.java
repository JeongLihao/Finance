package finance.data.serializer;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.AssetSnapshotManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists account balances, asset baselines and the account transaction journal. */
public final class AccountDataSerializer {

    private static final int MAX_TRANSACTION_RECORDS = 500;

    private AccountDataSerializer() {
    }

    public static void save(CompoundTag root) {
        ListTag accountsTag = new ListTag();
        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("PlayerUUID", entry.getKey());
            accountTag.putLong("Balance", entry.getValue().getBalance());
            accountTag.putLong("FrozenBalance", entry.getValue().getFrozenBalance());
            accountsTag.add(accountTag);
        }
        root.put("Accounts", accountsTag);

        ListTag assetSnapshotsTag = new ListTag();
        for (Map.Entry<UUID, AssetSnapshotManager.AssetSnapshot> entry
                : AssetSnapshotManager.getSnapshots().entrySet()) {
            CompoundTag snapshotTag = new CompoundTag();
            snapshotTag.putUUID("PlayerUUID", entry.getKey());
            snapshotTag.putLong("McDay", entry.getValue().mcDay());
            snapshotTag.putLong("TotalAsset", entry.getValue().totalAsset());
            assetSnapshotsTag.add(snapshotTag);
        }
        root.put("AssetSnapshots", assetSnapshotsTag);

        ListTag transactionsTag = new ListTag();
        List<TransactionRecord> records = AccountManager.getTransactions();
        int start = Math.max(0, records.size() - MAX_TRANSACTION_RECORDS);
        for (int index = start; index < records.size(); index++) {
            TransactionRecord record = records.get(index);
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("From", record.getFrom());
            recordTag.putUUID("To", record.getTo());
            recordTag.putLong("Amount", record.getAmount());
            recordTag.putString("Type", record.getType().name());
            if (record.getPlayerId() != null) {
                recordTag.putUUID("PlayerUUID", record.getPlayerId());
            }
            recordTag.putString("ObjectName", record.getObjectName());
            recordTag.putLong("Quantity", record.getQuantity());
            recordTag.putLong("Timestamp", record.getTimestamp().toEpochSecond(ZoneOffset.UTC));
            transactionsTag.add(recordTag);
        }
        root.put("Transactions", transactionsTag);
    }

    public static void load(CompoundTag root) {
        loadAccounts(root);
        loadAssetSnapshots(root);
        loadTransactions(root);
    }

    private static void loadAccounts(CompoundTag root) {
        for (Tag rawTag : root.getList("Accounts", Tag.TAG_COMPOUND)) {
            CompoundTag accountTag = (CompoundTag) rawTag;
            UUID playerId = NbtDataSupport.readUuidOrNull(accountTag, "PlayerUUID");
            if (playerId == null) {
                continue;
            }
            long balance = accountTag.getLong("Balance");
            if (balance < 0) {
                continue;
            }
            if (!accountTag.contains("FrozenBalance")) {
                AccountManager.getAccount(playerId).setBalance(balance);
                continue;
            }
            long frozen = accountTag.getLong("FrozenBalance");
            if (frozen < 0 || balance > Long.MAX_VALUE - frozen) {
                continue;
            }
            Account account = AccountManager.getAccount(playerId);
            account.setBalance(balance + frozen);
            if (frozen > 0) {
                account.freezeFunds(frozen);
            }
        }
    }

    private static void loadAssetSnapshots(CompoundTag root) {
        if (!root.contains("AssetSnapshots")) {
            return;
        }
        for (Tag rawTag : root.getList("AssetSnapshots", Tag.TAG_COMPOUND)) {
            CompoundTag snapshotTag = (CompoundTag) rawTag;
            UUID playerId = NbtDataSupport.readUuidOrNull(snapshotTag, "PlayerUUID");
            if (playerId != null) {
                AssetSnapshotManager.putSnapshotDirect(playerId, new AssetSnapshotManager.AssetSnapshot(
                        snapshotTag.getLong("McDay"), snapshotTag.getLong("TotalAsset")));
            }
        }
    }

    private static void loadTransactions(CompoundTag root) {
        if (!root.contains("Transactions")) {
            return;
        }
        AccountManager.clearTransactions();
        for (Tag rawTag : root.getList("Transactions", Tag.TAG_COMPOUND)) {
            CompoundTag recordTag = (CompoundTag) rawTag;
            UUID from = NbtDataSupport.readUuidOrNull(recordTag, "From");
            UUID to = NbtDataSupport.readUuidOrNull(recordTag, "To");
            TransactionType type = NbtDataSupport.safeEnum(
                    TransactionType.class, recordTag.getString("Type"), null);
            if (from == null || to == null || type == null) {
                continue;
            }
            AccountManager.addTransactionRecord(new TransactionRecord(
                    from, to, recordTag.getLong("Amount"), type,
                    LocalDateTime.ofEpochSecond(recordTag.getLong("Timestamp"), 0, ZoneOffset.UTC),
                    NbtDataSupport.readUuidOrNull(recordTag, "PlayerUUID"),
                    recordTag.contains("ObjectName") ? recordTag.getString("ObjectName") : "",
                    recordTag.contains("Quantity") ? recordTag.getLong("Quantity") : 0));
        }
    }
}
