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
        return INVENTORIES.computeIfAbsent(playerId, k -> new CommodityInventory());
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
    public static boolean addCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {

        boolean success = getInventory(playerId)
                .addCommodity(
                        commodityId,
                        amount
                );
        if (success) {
            CommodityInventorySavedData.markDirty();
        }
        return success;
    }

    public static boolean canAddCommodity(UUID playerId, String commodityId, int amount) {
        return playerId != null && getInventory(playerId).canAddCommodity(commodityId, amount);
    }

    /** 扣除商品（下单 SELL 时调用），不足返回 false */
    public static boolean removeCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {

        if (finance.collateral.InventoryCollateralManager.pledged(playerId, commodityId) > 0
                && !finance.collateral.InventoryCollateralManager.canRemove(playerId, commodityId, amount)) {
            return false;
        }
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

    /** 直接设置库存并标脏，适合批量初始化时避免反复读取和加减计算。 */
    public static boolean setCommodity(
            UUID playerId,
            String commodityId,
            int amount
    ) {
        boolean success = getInventory(playerId).setCommodity(commodityId, amount);
        if (success) {
            CommodityInventorySavedData.markDirty();
        }
        return success;
    }

    public static Map<UUID, CommodityInventory>
    getInventories() {

        return INVENTORIES;
    }

    public static int removeCommodityFromAll(String commodityId) {
        if (finance.collateral.InventoryCollateralManager.hasReservationsForCommodity(commodityId)) {
            return 0;
        }
        int affectedInventories = 0;
        for (CommodityInventory inventory : INVENTORIES.values()) {
            if (inventory.getAllCommodities().remove(commodityId) != null) {
                affectedInventories++;
            }
        }
        if (affectedInventories > 0) {
            CommodityInventorySavedData.markDirty();
        }
        return affectedInventories;
    }

    public static void clearInventoriesDirect() {
        INVENTORIES.clear();
    }
}
