package finance.data.serializer;

import finance.account.Account;
import finance.account.AccountManager;
import finance.commodity.CommodityRegistry;
import finance.contract.ContractIssuerType;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;
import finance.contract.ContractType;
import finance.contract.FinanceContract;
import finance.warehouse.WarehouseManager;
import finance.company.CompanyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

public final class ContractDataSerializer {
    public static final String ROOT = "FinanceContracts";
    private static final int MAX_RECORDS = ContractManager.MAX_RECORDS;
    private ContractDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag contractRoot = new CompoundTag();
        contractRoot.putInt("Version", 1);
        ListTag records = new ListTag();
        int count = 0;
        for (FinanceContract contract : ContractManager.contracts().values()) {
            if (count++ >= MAX_RECORDS) break;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", contract.id());
            tag.putString("Type", contract.type().name());
            tag.putString("IssuerType", contract.issuerType().name());
            tag.putUUID("Issuer", contract.issuerId());
            tag.putString("Commodity", contract.commodityId());
            tag.putInt("Required", contract.requiredQuantity());
            tag.putInt("Delivered", contract.deliveredQuantity());
            tag.putLong("Reward", contract.rewardAmount());
            tag.putUUID("Escrow", contract.escrowAccountId());
            if (contract.destinationWarehouseId() != null) tag.putUUID("Destination", contract.destinationWarehouseId());
            tag.putLong("CreatedDay", contract.createdDay());
            tag.putLong("DeadlineDay", contract.deadlineDay());
            if (contract.acceptedPlayerId() != null) tag.putUUID("AcceptedPlayer", contract.acceptedPlayerId());
            tag.putString("Status", contract.status().name());
            tag.putString("Failure", contract.failureReason());
            ListTag operations = new ListTag();
            for (String key : contract.operationKeys()) operations.add(StringTag.valueOf(key));
            tag.put("Operations", operations);
            records.add(tag);
        }
        contractRoot.put("Records", records);
        ListTag dailyKeys = new ListTag();
        for (String key : ContractManager.dailyKeys()) dailyKeys.add(StringTag.valueOf(key));
        contractRoot.put("DailyKeys", dailyKeys);
        root.put(ROOT, contractRoot);
    }

    public static void load(CompoundTag root) {
        ContractManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag contractRoot = root.getCompound(ROOT);
        ListTag records = contractRoot.getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_RECORDS, records.size()); i++) {
            CompoundTag tag = records.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
                UUID issuer = NbtDataSupport.readUuidOrNull(tag, "Issuer");
                UUID escrowId = NbtDataSupport.readUuidOrNull(tag, "Escrow");
                String commodity = tag.getString("Commodity");
                ContractType type = NbtDataSupport.safeEnum(ContractType.class, tag.getString("Type"), null);
                ContractIssuerType issuerType = NbtDataSupport.safeEnum(
                        ContractIssuerType.class, tag.getString("IssuerType"), null);
                ContractStatus status = NbtDataSupport.safeEnum(ContractStatus.class, tag.getString("Status"), null);
                Account escrow = AccountManager.getAccounts().get(escrowId);
                long reward = tag.getLong("Reward");
                if (id == null || issuer == null || escrowId == null || CommodityRegistry.getCommodity(commodity) == null
                        || type == null || issuerType == null || status == null || escrow == null || reward <= 0) continue;
                if (issuerType == ContractIssuerType.COMPANY && CompanyManager.getCompany(issuer) == null) continue;
                boolean live = status == ContractStatus.OPEN || status == ContractStatus.ACCEPTED;
                if ((live && escrow.getBalance() != reward) || (!live && escrow.getBalance() != 0)) continue;
                UUID destination = NbtDataSupport.readUuidOrNull(tag, "Destination");
                UUID acceptedPlayer = NbtDataSupport.readUuidOrNull(tag, "AcceptedPlayer");
                if (status == ContractStatus.OPEN && (destination != null || acceptedPlayer != null)) continue;
                if (status == ContractStatus.ACCEPTED && (destination == null || acceptedPlayer == null
                        || WarehouseManager.get(destination) == null
                        || !acceptedPlayer.equals(WarehouseManager.get(destination).ownerId()))) continue;
                FinanceContract contract = new FinanceContract(id, type, issuerType, issuer, commodity,
                        tag.getInt("Required"), tag.getInt("Delivered"), reward, escrowId,
                        destination, tag.getLong("CreatedDay"),
                        tag.getLong("DeadlineDay"), acceptedPlayer, status,
                        tag.getString("Failure"));
                ListTag operations = tag.getList("Operations", Tag.TAG_STRING);
                for (int op = Math.max(0, operations.size() - FinanceContract.MAX_OPERATION_KEYS);
                     op < operations.size(); op++) {
                    String key = operations.getString(op);
                    if (!key.isBlank() && key.length() <= 96) contract.restoreOperation(key);
                }
                ContractManager.restore(contract);
            } catch (RuntimeException ignored) {
                // 单个合同损坏时隔离，绝不猜测退款归属。
            }
        }
        ListTag dailyKeys = contractRoot.getList("DailyKeys", Tag.TAG_STRING);
        for (int i = Math.max(0, dailyKeys.size() - 512); i < dailyKeys.size(); i++) {
            ContractManager.restoreDailyKey(dailyKeys.getString(i));
        }
    }
}
