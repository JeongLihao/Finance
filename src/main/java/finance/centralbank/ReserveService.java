package finance.centralbank;

import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.market.CentralBank;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 央行储备：市场上的货只有两个来源——央行按档投放的原材料，和玩家卖进来的货。
 *
 * <ul>
 *   <li>分档：第 1 档开服即有；第 2、3 档等全服累计成交额到了才解锁（市场越热闹，货越多，不是给某个玩家设门槛）。</li>
 *   <li>目标库存很小（默认 1000 / 500 / 200 件），每天最多补目标的 2%，玩家抢购时价格涨得上去，要过几天才回落。</li>
 *   <li>第 0 档是“玩家货”：央行不投放，有人卖了才上架，卖完就没了。</li>
 *   <li>底价：市场收购价跌破基准价的一半时，央行按底价收，每种每天有总量上限，防止自动农场无限取钱。</li>
 * </ul>
 * 商品分档为 -1 时（管理员自建商品、测试里临时注册的商品）沿用旧规则：参考库存 10 万、不参与这里的任何逻辑。
 */
public final class ReserveService {

    private static long tradedValue;
    private static boolean migrated;
    private static long floorDay = Long.MIN_VALUE;
    private static final Map<String, Integer> FLOOR_USED = new HashMap<>();

    private ReserveService() {}

    // ------------------------------------------------------------ 分档

    public static int tier(String commodityId) {
        Commodity c = commodityId == null ? null : CommodityRegistry.getCommodity(commodityId);
        return c == null ? -1 : c.getReserveTier();
    }

    /** 走新规则的商品（分档 0~3）。 */
    public static boolean managed(String commodityId) {
        return tier(commodityId) >= 0;
    }

    /** 现在央行投放到第几档。 */
    public static int unlockedTier() {
        if (tradedValue >= FinanceConfig.reserveTier3UnlockValue()) return 3;
        if (tradedValue >= FinanceConfig.reserveTier2UnlockValue()) return 2;
        return 1;
    }

    /** 解锁下一档还差多少成交额；已经全部解锁返回 0。 */
    public static long valueToNextTier() {
        int t = unlockedTier();
        if (t >= 3) return 0;
        long need = t == 1 ? FinanceConfig.reserveTier2UnlockValue() : FinanceConfig.reserveTier3UnlockValue();
        return Math.max(0, need - tradedValue);
    }

    /** 央行现在会给这种商品补货吗。 */
    public static boolean stocked(String commodityId) {
        int t = tier(commodityId);
        return t >= 1 && t <= unlockedTier();
    }

    /** 某一档的目标库存（件）。 */
    public static long tierTarget(int tier) {
        List<Long> targets = FinanceConfig.reserveTargets();
        if (tier < 1 || targets.isEmpty()) return 0;
        return targets.get(Math.min(tier, targets.size()) - 1);
    }

    /** 市场的目标库存：央行补货补到这里；玩家货和还没解锁的为 0；旧规则商品为 10 万。 */
    public static long target(String commodityId) {
        int t = tier(commodityId);
        if (t < 0) return MarketPrice.REFERENCE_STOCK;
        return stocked(commodityId) ? tierTarget(t) : 0;
    }

    /** 价格曲线的参考库存：库存等于它时价格就是基准价。 */
    public static long referenceStock(String commodityId) {
        int t = tier(commodityId);
        if (t < 0) return MarketPrice.REFERENCE_STOCK;
        if (t == 0) return FinanceConfig.reservePlayerGoodsReference();
        return Math.max(1, tierTarget(t));
    }

    /** 新建或读档后，把价格对象调成对应的曲线。 */
    public static void configure(MarketPrice mp) {
        if (mp == null || !managed(mp.getCommodityId())) return;
        mp.useReserveCurve(referenceStock(mp.getCommodityId()), FinanceConfig.reservePriceElasticity());
    }

    /** 新世界开服时市场里放多少。 */
    public static int initialStock(String commodityId) {
        return (int) Math.min(Integer.MAX_VALUE, target(commodityId));
    }

    /** 今天该补多少：最多目标库存的 restockBpsPerDay，补到目标为止。 */
    public static long dailyRestock(String commodityId, long stock) {
        long target = target(commodityId);
        if (target <= 0 || stock >= target) return 0;
        long step = Math.max(1, CentralBankService.bps(target, FinanceConfig.reserveRestockBpsPerDay()));
        return Math.min(target - stock, step);
    }

    /** 每天补货：先从央行储备里拿，不够再由央行新投放。返回补了多少。 */
    public static long restock(String commodityId) {
        long stock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId);
        long add = dailyRestock(commodityId, stock);
        if (add <= 0) return 0;
        int qty = (int) Math.min(Integer.MAX_VALUE, add);
        int fromReserve = Math.min(qty, CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, commodityId));
        if (fromReserve > 0 && CommodityInventoryManager.removeCommodity(CentralBank.UUID, commodityId, fromReserve)) {
            CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, fromReserve);
        } else {
            fromReserve = 0;
        }
        if (qty > fromReserve) CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, qty - fromReserve);
        return qty;
    }

    // ------------------------------------------------------------ 底价收购

    /**
     * 底价：基准价的 floorBps；旧规则商品没有底价（返回 0）。
     * 绿宝石也没有底价：刷怪塔的腐肉卖给牧师就能换绿宝石，有底价等于每天白送钱。
     */
    public static long floorPrice(String commodityId) {
        if (finance.cash.CashService.EMERALD.equals(commodityId)) return 0;
        Commodity c = CommodityRegistry.getCommodity(commodityId);
        if (c == null || c.getReserveTier() < 0 || c.getBasePrice() <= 0) return 0;
        return Math.max(1, CentralBankService.bps(c.getBasePrice(), FinanceConfig.reserveFloorBps()));
    }

    /** 今天这种商品还能按底价收多少件（全服合计）。 */
    public static int floorQuotaLeft(String commodityId, long day) {
        rollDay(day);
        return Math.max(0, FinanceConfig.reserveFloorDailyCap() - FLOOR_USED.getOrDefault(commodityId, 0));
    }

    public static void useFloorQuota(String commodityId, long day, int quantity) {
        rollDay(day);
        if (quantity > 0) FLOOR_USED.merge(commodityId, quantity, Integer::sum);
    }

    private static void rollDay(long day) {
        if (day != floorDay) {
            floorDay = day;
            FLOOR_USED.clear();
        }
    }

    // ------------------------------------------------------------ 成交额与旧存档

    public static void recordTrade(long value) {
        if (value > 0) tradedValue = MathUtil.saturatedAddNonNegative(tradedValue, value);
    }

    public static long tradedValue() { return tradedValue; }

    /** 某一档的商品名（经济天气里用）。 */
    public static List<String> namesOfTier(int tier) {
        List<String> out = new ArrayList<>();
        for (Commodity c : CommodityRegistry.getAllCommodities()) if (c.getReserveTier() == tier) out.add(c.getDisplayName());
        out.sort(String::compareTo);
        return out;
    }

    /**
     * 旧存档：做市商每种商品有 10 万件。超过目标两倍的部分搬进央行储备（不销毁），以后补货先用这些；
     * 只做一次。
     */
    public static void migrateIfNeeded() {
        if (migrated) return;
        migrated = true;
        for (Commodity c : new ArrayList<>(CommodityRegistry.getAllCommodities())) {
            String id = c.getId();
            if (!managed(id)) continue;
            int stock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id);
            long keep = Math.max(target(id), referenceStock(id)) * 2;
            if (stock <= keep) continue;
            int excess = (int) Math.min(Integer.MAX_VALUE, stock - keep);
            if (CommodityInventoryManager.canAddCommodity(CentralBank.UUID, id, excess)
                    && CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, id, excess)) {
                CommodityInventoryManager.addCommodity(CentralBank.UUID, id, excess);
            }
            MarketPrice mp = NpcMarketMaker.getMarketPrice(id);
            if (mp != null) mp.recomputePrice(CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, id));
        }
    }

    public static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("TradedValue", tradedValue);
        t.putBoolean("Migrated", migrated);
        return t;
    }

    public static void load(CompoundTag t) {
        clearDirect();
        if (t == null) return;
        tradedValue = Math.max(0, t.getLong("TradedValue"));
        migrated = t.getBoolean("Migrated");
    }

    public static void clearDirect() {
        tradedValue = 0;
        migrated = false;
        floorDay = Long.MIN_VALUE;
        FLOOR_USED.clear();
    }

    /** 新世界：库存本来就按目标放，不需要搬。 */
    public static void markMigrated() {
        migrated = true;
    }
}
