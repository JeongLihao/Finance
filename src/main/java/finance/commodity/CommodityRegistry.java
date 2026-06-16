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

    /** 注册商品（模组初始化时调用） */
    public static void register(Commodity commodity) {

        COMMODITIES.put(
                commodity.getId(),
                commodity
        );
    }

    /** 根据 ID 查找商品，不存在返回 null */
    public static Commodity getCommodity(String id) {

        return COMMODITIES.get(id);
    }

    public static Collection<Commodity> getAllCommodities() {

        return COMMODITIES.values();
    }
}
