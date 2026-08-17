package finance.commodity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 商品注册表 —— 管理所有可交易的商品定义。
 * 商品在 {@link finance.FinanceMod} 构造阶段注册。
 */
public class CommodityRegistry {

    private static final Map<String, Commodity> COMMODITIES =
            new HashMap<>();

    private static final Map<String, Commodity> DEFAULT_COMMODITIES =
            new HashMap<>();

    /** 注册商品（模组初始化时调用） */
    public static void register(Commodity commodity) {

        COMMODITIES.put(
                commodity.getId(),
                commodity
        );
        finance.event.EventManager.markCommodityIdsDirty();
    }

    /** 注册模组默认商品。默认商品会在切换世界时保留，自定义商品会随世界存档重新加载。 */
    public static void registerDefault(Commodity commodity) {
        DEFAULT_COMMODITIES.put(
                commodity.getId(),
                commodity
        );
        register(commodity);
    }

    /** 清空世界级自定义商品，只恢复模组默认商品。 */
    public static void resetToDefaults() {
        COMMODITIES.clear();
        COMMODITIES.putAll(DEFAULT_COMMODITIES);
        finance.event.EventManager.markCommodityIdsDirty();
    }

    /** 根据 ID 查找商品，不存在返回 null */
    public static Commodity getCommodity(String id) {

        return COMMODITIES.get(id);
    }

    public static Collection<Commodity> getAllCommodities() {

        return COMMODITIES.values();
    }

    /** 移除商品（管理员操作），返回是否成功 */
    public static boolean removeCommodity(String id) {
        if (DEFAULT_COMMODITIES.containsKey(id)) {
            return false;
        }
        if (finance.futures.FuturesMarketManager.hasLiveContractForCommodity(id)) {
            return false;
        }
        boolean removed = COMMODITIES.remove(id) != null;
        if (removed) {
            finance.event.EventManager.markCommodityIdsDirty();
        }
        return removed;
    }

    /** 检查商品是否已注册 */
    public static boolean isRegistered(String id) {
        return COMMODITIES.containsKey(id);
    }

    public static boolean isDefaultCommodity(String id) {
        return DEFAULT_COMMODITIES.containsKey(id);
    }
}
