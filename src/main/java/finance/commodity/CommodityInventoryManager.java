package finance.commodity;

import finance.data.CommodityInventorySavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 商品背包管理器 —— 所有商品库存操作的入口。
 * 每次修改库存后自动标记持久化为脏数据。
 */
public class CommodityInventoryManager {

    private static final Map<UUID, CommodityInventory>
            INVENTORIES = new HashMap<>();

    /** 获取或创建玩家背包（懒加载） */
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

    /** 查询玩家持有某商品的数量 */
    public static int getCommodityAmount(
            UUID playerId,
            String commodityId
    ) {

        return getInventory(playerId)
                .getAmount(commodityId);
    }

    /** 增加商品（购买、管理命令等） */
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

    /** 扣除商品（下单 SELL 时调用），不足返回 false */
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