package finance.commodity;

import java.util.HashMap;
import java.util.Map;

public class CommodityInventory {

    private final Map<String, Integer> commodities =
            new HashMap<>();

    public int getAmount(String commodityId) {

        return commodities.getOrDefault(
                commodityId,
                0
        );
    }

    public void addCommodity(
            String commodityId,
            int amount
    ) {

        commodities.put(
                commodityId,
                getAmount(commodityId) + amount
        );
    }

    public boolean removeCommodity(
            String commodityId,
            int amount
    ) {

        int current = getAmount(commodityId);

        if (current < amount) {
            return false;
        }

        commodities.put(
                commodityId,
                current - amount
        );

        return true;
    }

    public Map<String, Integer> getAllCommodities() {
        return commodities;
    }
}