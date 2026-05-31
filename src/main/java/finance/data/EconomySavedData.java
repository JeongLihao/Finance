package finance.data;

import finance.account.Account;
import finance.account.AccountManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class EconomySavedData extends SavedData {

    public static final String DATA_NAME = "finance_data";

    @Override
    public CompoundTag save(CompoundTag tag) {

        ListTag accountsTag = new ListTag();

        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {

            CompoundTag accountTag = new CompoundTag();

            accountTag.putUUID("PlayerUUID", entry.getKey());

            accountTag.putLong(
                    "Balance",
                    entry.getValue().getBalance()
            );

            accountsTag.add(accountTag);
        }

        tag.put("Accounts", accountsTag);

        return tag;
    }

    public static EconomySavedData load(CompoundTag tag) {

        EconomySavedData data = new EconomySavedData();

        ListTag accountsTag = tag.getList("Accounts", Tag.TAG_COMPOUND);

        for (Tag rawTag : accountsTag) {

            CompoundTag accountTag = (CompoundTag) rawTag;

            UUID playerUUID = accountTag.getUUID("PlayerUUID");

            long balance = accountTag.getLong("Balance");

            Account account = AccountManager.getAccount(playerUUID);

            account.setBalance(balance);
        }

        return data;
    }
    public static EconomySavedData get(MinecraftServer server) {

        DimensionDataStorage storage =
                server.overworld().getDataStorage();

        INSTANCE = storage.computeIfAbsent(
                EconomySavedData::load,
                EconomySavedData::new,
                DATA_NAME
        );

        return INSTANCE;

    }

    private static EconomySavedData INSTANCE;

    public static void markDirty(){
        if (INSTANCE != null){
            INSTANCE.setDirty();
        }
    }
}
