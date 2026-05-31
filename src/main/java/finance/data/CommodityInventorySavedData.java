package finance.data;

import finance.commodity.CommodityInventory;
import finance.commodity.CommodityInventoryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Map;
import java.util.UUID;

public class CommodityInventorySavedData extends SavedData {

    public static final String DATA_NAME =
            "finance_inventory";

    private static CommodityInventorySavedData INSTANCE;

    @Override
    public CompoundTag save(CompoundTag tag) {

        ListTag playersTag = new ListTag();

        for (UUID playerId :
                CommodityInventoryManager
                        .getInventories()
                        .keySet()) {

            CompoundTag playerTag =
                    new CompoundTag();

            playerTag.putUUID(
                    "PlayerUUID",
                    playerId
            );

            CommodityInventory inventory =
                    CommodityInventoryManager
                            .getInventory(playerId);

            CompoundTag commoditiesTag =
                    new CompoundTag();

            for (Map.Entry<String, Integer> entry :
                    inventory.getAllCommodities()
                            .entrySet()) {

                commoditiesTag.putInt(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            playerTag.put(
                    "Commodities",
                    commoditiesTag
            );

            playersTag.add(playerTag);
        }

        tag.put(
                "Inventories",
                playersTag
        );

        return tag;
    }

    public static CommodityInventorySavedData load(
            CompoundTag tag
    ) {

        CommodityInventorySavedData data =
                new CommodityInventorySavedData();

        ListTag playersTag =
                tag.getList(
                        "Inventories",
                        Tag.TAG_COMPOUND
                );

        for (Tag rawTag : playersTag) {

            CompoundTag playerTag =
                    (CompoundTag) rawTag;

            UUID playerId =
                    playerTag.getUUID(
                            "PlayerUUID"
                    );

            CompoundTag commoditiesTag =
                    playerTag.getCompound(
                            "Commodities"
                    );

            CommodityInventory inventory =
                    CommodityInventoryManager
                            .getInventory(playerId);

            for (String key :
                    commoditiesTag.getAllKeys()) {

                inventory.setCommodity(
                        key,
                        commoditiesTag.getInt(key)
                );
            }
        }

        return data;
    }

    public static CommodityInventorySavedData get(
            MinecraftServer server
    ) {

        DimensionDataStorage storage =
                server.overworld()
                        .getDataStorage();

        INSTANCE =
                storage.computeIfAbsent(
                        CommodityInventorySavedData::load,
                        CommodityInventorySavedData::new,
                        DATA_NAME
                );

        return INSTANCE;
    }

    public static void markDirty() {

        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }
}
