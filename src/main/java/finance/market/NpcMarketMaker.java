package finance.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.commodity.Commodity;

/**
 * NPC 做市商引擎 —— 为所有注册商品提供双向报价，保证市场流动性。
 *
 * <h3>定价机制</h3>
 * 每种商品维护一个"中间价"，NPC 以中间价 ± spread 双向报价：
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — NPC 从玩家处购买的价格</li>
 *   <li>askPrice = midPrice × (1 + spread) — NPC 向玩家出售的价格</li>
 * </ul>
 *
 * <h3>NPC 身份</h3>
 * NPC 使用 nil UUID {@code 00000000-0000-0000-0000-000000000000}，
 * 账户和库存由系统自动管理。
 */
public class NpcMarketMaker {

    /** NPC 系统账户 UUID（nil UUID，不会与真实玩家冲突） */
    public static final UUID NPC_UUID = new UUID(0L, 0L);

    /** 初始注入资金：10 亿 */
    private static final long INITIAL_NPC_BALANCE = 1_000_000_000L;

    /** 每种商品初始库存 */
    private static final int INITIAL_NPC_STOCK = 100_000;

    /** 默认价差：5% */
    private static final double DEFAULT_SPREAD = 0.05;

    /** 商品中间价映射 */
    private static final Map<String, MarketPrice> MARKET_PRICES = new HashMap<>();

    /** 是否已完成 NPC 初始化 */
    private static boolean seeded = false;

    // ================================================================
    // NPC 交易操作
    // ================================================================

    /**
     * NPC 向玩家购买商品（玩家卖商品给 NPC，玩家获得钱）。
     *
     * @return 成功返回 true
     */
    public static boolean npcBuy(UUID playerId, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            return false;
        }

        MarketPrice price = getMarketPrice(commodityId);
        long bidPrice = price.getBidPrice();
        long totalPayment = bidPrice * quantity;

        // 1. 检查玩家库存
        int playerStock = CommodityInventoryManager.getCommodityAmount(playerId, commodityId);
        if (playerStock < quantity) {
            return false;
        }

        // 2. 检查 NPC 余额
        if (AccountManager.getBalance(NPC_UUID) < totalPayment) {
            return false;
        }

        // 3. 商品：玩家 → NPC
        CommodityInventoryManager.removeCommodity(playerId, commodityId, quantity);
        CommodityInventoryManager.addCommodity(NPC_UUID, commodityId, quantity);

        // 4. 资金：NPC → 玩家
        AccountManager.withdraw(NPC_UUID, totalPayment);
        AccountManager.deposit(playerId, totalPayment);

        // 5. 记录流水
        AccountManager.addTransactionRecord(
                new TransactionRecord(NPC_UUID, playerId, totalPayment, "NPC_BUY")
        );

        // 6. 记录成交（NPC 是买方，玩家是卖方）
        MarketManager.addTradeToHistory(
                new Trade(NPC_UUID, playerId, commodityId, bidPrice, quantity)
        );

        return true;
    }

    /**
     * NPC 向玩家出售商品（玩家从 NPC 买商品，玩家支付钱）。
     *
     * @return 成功返回 true
     */
    public static boolean npcSell(UUID playerId, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            return false;
        }

        MarketPrice price = getMarketPrice(commodityId);
        long askPrice = price.getAskPrice();
        long totalCost = askPrice * quantity;

        // 1. 检查 NPC 库存
        int npcStock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
        if (npcStock < quantity) {
            return false;
        }

        // 2. 检查玩家余额
        if (AccountManager.getBalance(playerId) < totalCost) {
            return false;
        }

        // 3. 资金：玩家 → NPC
        AccountManager.withdraw(playerId, totalCost);
        AccountManager.deposit(NPC_UUID, totalCost);

        // 4. 商品：NPC → 玩家
        CommodityInventoryManager.removeCommodity(NPC_UUID, commodityId, quantity);
        CommodityInventoryManager.addCommodity(playerId, commodityId, quantity);

        // 5. 记录流水
        AccountManager.addTransactionRecord(
                new TransactionRecord(playerId, NPC_UUID, totalCost, "NPC_SELL")
        );

        // 6. 记录成交（玩家是买方，NPC 是卖方）
        MarketManager.addTradeToHistory(
                new Trade(playerId, NPC_UUID, commodityId, askPrice, quantity)
        );

        return true;
    }

    // ================================================================
    // 价格管理
    // ================================================================

    /**
     * 获取商品的中间价，如果不存在则从 CommodityRegistry 懒创建。
     */
    public static MarketPrice getMarketPrice(String commodityId) {
        MarketPrice existing = MARKET_PRICES.get(commodityId);
        if (existing != null) {
            return existing;
        }

        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            return null;
        }

        MarketPrice mp = new MarketPrice(commodityId, commodity.getBasePrice(), DEFAULT_SPREAD);
        MARKET_PRICES.put(commodityId, mp);
        return mp;
    }

    public static Map<String, MarketPrice> getAllMarketPrices() {
        return MARKET_PRICES;
    }

    /** 直接写入价格映射（持久化加载时使用） */
    public static void putMarketPrice(String commodityId, MarketPrice mp) {
        MARKET_PRICES.put(commodityId, mp);
    }

    public static void clearMarketPrices() {
        MARKET_PRICES.clear();
    }

    // ================================================================
    // NPC 初始化
    // ================================================================

    /**
     * 给 NPC 注入初始资金和库存（仅在首次调用时执行）。
     * 在服务器启动、数据加载完成后调用。
     */
    public static void seedNpcIfNeeded() {
        if (seeded) {
            return;
        }
        seeded = true;

        // 注入初始资金
        long npcBalance = AccountManager.getBalance(NPC_UUID);
        if (npcBalance < INITIAL_NPC_BALANCE) {
            AccountManager.deposit(NPC_UUID, INITIAL_NPC_BALANCE - npcBalance);
        }

        // 预创建价格 + 注入初始库存（每种商品给 10 万）
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            String id = commodity.getId();

            // 确保价格条目存在（数据加载后可能已存在，跳过）
            if (!MARKET_PRICES.containsKey(id)) {
                MARKET_PRICES.put(id, new MarketPrice(
                        id, commodity.getBasePrice(), DEFAULT_SPREAD
                ));
            }

            int currentStock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, id);
            if (currentStock < INITIAL_NPC_STOCK) {
                CommodityInventoryManager.addCommodity(NPC_UUID, id, INITIAL_NPC_STOCK - currentStock);
            }
        }
    }
}
