package finance.commodity;
import finance.data.CommodityInventorySavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommodityInventoryManager {

    private static final Map<UUID, CommodityInventory>
            INVENTORIES = new HashMap<>();

    public static CommodityInventory getInventory(
            UUID playerId
    ) {

        if (!INVENTORIES.containsKey(playerId)) {

            INVENTORIES.put(
                    playerId,
                    new CommodityInventory()
            );
        }

        return INVENTORIES.get(playerId);
    }

    public static int getCommodityAmount(
            UUID playerId,
            String commodityId
    ) {

        return getInventory(playerId)
                .getAmount(commodityId);
    }

    public static void addCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {

        getInventory(playerId)
                .addCommodity(
                        commodityId,
                        amount
                );

        CommodityInventorySavedData.markDirty();
    }

    public static boolean removeCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {

        boolean success =
                getInventory(playerId)
                        .removeCommodity(
                                commodityId,
                                amount
                        );

        if (success) {
            CommodityInventorySavedData.markDirty();
        }

        return success;
    }
    public static Map<UUID, CommodityInventory>
    getInventories() {

        return INVENTORIES;
    }
}