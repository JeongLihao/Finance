package finance.commodity;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家的商品背包 —— 以商品 ID 为 key，存储各商品的持有数量。
 */
public class CommodityInventory {

    private final Map<String, Integer> commodities =
            new HashMap<>();

    /** 设置商品数量（数据加载时使用） */
    public boolean setCommodity(
            String commodityId,
            int amount
    ) {
        if (commodityId == null || commodityId.isBlank() || amount < 0) {
            return false;
        }
        commodities.put(
                commodityId,
                amount
        );
        return true;
    }

    /** 查询持有的商品数量 */
    public int getAmount(String commodityId) {

        return commodities.getOrDefault(
                commodityId,
                0
        );
    }

    /** 增加商品数量 */
    public boolean addCommodity(
            String commodityId,
            int amount
    ) {
        if (!canAddCommodity(commodityId, amount)) {
            return false;
        }
        commodities.put(
                commodityId,
                getAmount(commodityId) + amount
        );
        return true;
    }

    public boolean canAddCommodity(String commodityId, int amount) {
        if (commodityId == null || commodityId.isBlank() || amount <= 0) {
            return false;
        }
        int current = getAmount(commodityId);
        return current >= 0 && current <= Integer.MAX_VALUE - amount;
    }

    /** 扣除商品数量，不足返回 false */
    public boolean removeCommodity(
            String commodityId,
            int amount
    ) {

        if (commodityId == null || commodityId.isBlank() || amount <= 0) {
            return false;
        }
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
