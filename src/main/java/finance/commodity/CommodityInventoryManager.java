package finance.commodity;

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
    }

    public static boolean removeCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {

        return getInventory(playerId)
                .removeCommodity(
                        commodityId,
                        amount
                );
    }
}