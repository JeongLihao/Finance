package finance.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.commodity.Commodity;
import finance.data.EconomySavedData;
import finance.event.MarketEvent;
import finance.util.MathUtil;

/**
 * 国际市场引擎 —— 为所有注册商品提供双向报价，保证市场流动性。
 *
 * <h3>定价机制</h3>
 * 每种商品维护一个"中间价"，国际市场以中间价 ± spread 双向报价：
 * <ul>
 *   <li>bidPrice = midPrice × (1 - spread) — 国际市场买入价</li>
 *   <li>askPrice = midPrice × (1 + spread) — 国际市场卖出价</li>
 * </ul>
 *
 * <h3>系统账户</h3>
 * 国际市场使用 nil UUID {@code 00000000-0000-0000-0000-000000000000}，
 * 账户和库存由系统流动性池自动管理。
 */
public class NpcMarketMaker {

    /** 国际市场系统账户 UUID（nil UUID，不会与真实玩家冲突） */
    public static final UUID NPC_UUID = new UUID(0L, 0L);

    /** 国际市场日常交易池初始资金：央行加入后，市场本身不再承担无限流动性。 */
    private static final long INITIAL_NPC_BALANCE = 2_000_000L;

    /** 每种商品日常流通库存。战略储备由中央银行持有。 */
    private static final int INITIAL_NPC_STOCK = 60_000;

    /** 国际市场低于参考库存时的每日基础补货比例 */
    private static final double DAILY_RESTOCK_RATIO = 0.035;

    /** 国际市场高于参考库存时的每日外部需求比例 */
    private static final double DAILY_DEMAND_RATIO = 0.035;

    /** 默认价差：5% */
    private static final double DEFAULT_SPREAD = 0.05;

    /** 单笔交易最多吃下参考库存的一部分，避免大手直接打穿国际市场。 */
    private static final double MAX_TRADE_REFERENCE_RATIO = 0.08;

    /** 商品中间价映射 */
    private static final Map<String, MarketPrice> MARKET_PRICES = new HashMap<>();

    /** 是否已完成国际市场初始化 */
    private static boolean seeded = false;

    // ================================================================
    // 国际市场交易操作
    // ================================================================

    /**
     * 国际市场向玩家购买商品（玩家卖出商品并获得资金）。
     *
     * @return 成功返回 true
     */
    public static boolean npcBuy(UUID playerId, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            return false;
        }

        MarketPrice price = getMarketPrice(commodityId);
        if (price == null) {
            return false;
        }
        long bidPrice = price.getBidPrice();
        int acceptedQuantity = Math.min(quantity, getMaxNpcBuyQuantity(commodityId, bidPrice));
        if (acceptedQuantity <= 0) {
            return false;
        }
        long totalPayment = MathUtil.multiplyExactOrNegative1(bidPrice, acceptedQuantity);
        if (totalPayment <= 0) {
            return false;
        }

        // 1. 检查玩家库存
        int playerStock = CommodityInventoryManager.getCommodityAmount(playerId, commodityId);
        if (playerStock < acceptedQuantity) {
            return false;
        }

        // 2. 检查国际市场余额
        if (AccountManager.getBalance(NPC_UUID) < totalPayment) {
            return false;
        }

        // 3. 商品：玩家 → 国际市场
        CommodityInventoryManager.removeCommodity(playerId, commodityId, acceptedQuantity);
        CommodityInventoryManager.addCommodity(NPC_UUID, commodityId, acceptedQuantity);

        // 4. 资金：国际市场 → 玩家
        AccountManager.withdraw(NPC_UUID, totalPayment);
        AccountManager.deposit(playerId, totalPayment);

        // 5. 记录流水
        AccountManager.addTransactionRecord(
                new TransactionRecord(NPC_UUID, playerId, totalPayment, TransactionType.COMMODITY_SELL,
                        playerId, commodityId, acceptedQuantity)
        );

        // 6. 记录成交（国际市场是买方，玩家是卖方）
        MarketManager.addTradeToHistory(
                new Trade(NPC_UUID, playerId, commodityId, bidPrice, acceptedQuantity)
        );

        // 7. 混合定价更新
        recordNpcTrade(commodityId, true, acceptedQuantity, bidPrice);

        return true;
    }

    /**
     * 国际市场向玩家出售商品（玩家买入商品并支付资金）。
     *
     * @return 成功返回 true
     */
    public static boolean npcSell(UUID playerId, String commodityId, int quantity) {
        Commodity commodity = CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) {
            return false;
        }

        MarketPrice price = getMarketPrice(commodityId);
        if (price == null) {
            return false;
        }
        long askPrice = price.getAskPrice();
        int acceptedQuantity = Math.min(quantity, getMaxNpcSellQuantity(commodityId));
        if (acceptedQuantity <= 0) {
            return false;
        }
        long totalCost = MathUtil.multiplyExactOrNegative1(askPrice, acceptedQuantity);
        if (totalCost <= 0) {
            return false;
        }

        // 1. 检查国际市场库存
        int npcStock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
        if (npcStock < acceptedQuantity) {
            return false;
        }

        // 2. 检查玩家余额
        if (AccountManager.getBalance(playerId) < totalCost) {
            return false;
        }

        // 3. 资金：玩家 → 国际市场
        AccountManager.withdraw(playerId, totalCost);
        AccountManager.deposit(NPC_UUID, totalCost);

        // 4. 商品：国际市场 → 玩家
        CommodityInventoryManager.removeCommodity(NPC_UUID, commodityId, acceptedQuantity);
        CommodityInventoryManager.addCommodity(playerId, commodityId, acceptedQuantity);

        // 5. 记录流水
        AccountManager.addTransactionRecord(
                new TransactionRecord(playerId, NPC_UUID, totalCost, TransactionType.COMMODITY_BUY,
                        playerId, commodityId, acceptedQuantity)
        );

        // 6. 记录成交（玩家是买方，国际市场是卖方）
        MarketManager.addTradeToHistory(
                new Trade(playerId, NPC_UUID, commodityId, askPrice, acceptedQuantity)
        );

        // 7. 混合定价更新
        recordNpcTrade(commodityId, false, acceptedQuantity, askPrice);

        return true;
    }

    // ================================================================
    // 价格管理
    // ================================================================

    /**
     * NPC 交易后统一更新行情：查询库存 → 更新动量 → 记录快照。
     *
     * @param commodityId  商品 ID
     * @param npcWasBuyer  true = 国际市场买入（玩家卖出），false = 国际市场卖出（玩家买入）
     * @param quantity     成交数量
     * @param tradePrice   成交单价
     */
    public static void recordNpcTrade(String commodityId, boolean npcWasBuyer,
                                       int quantity, long tradePrice) {
        long newNpcStock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
        MarketPrice price = getMarketPrice(commodityId);
        if (price != null) {
            price.onNpcTrade(newNpcStock, npcWasBuyer, quantity);
            price.recordTrade(tradePrice, quantity);
            EconomySavedData.markDirty();
        }
    }

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
        EconomySavedData.markDirty();
        return mp;
    }

    public static Map<String, MarketPrice> getAllMarketPrices() {
        return MARKET_PRICES;
    }

    public static long getInitialNpcBalance() {
        return INITIAL_NPC_BALANCE;
    }

    /** 直接写入价格映射（持久化加载时使用） */
    public static void putMarketPrice(String commodityId, MarketPrice mp) {
        MARKET_PRICES.put(commodityId, mp);
    }

    public static void clearMarketPrices() {
        MARKET_PRICES.clear();
        seeded = false;
    }

    public static void resetSeedState() {
        seeded = false;
    }

    // ================================================================
    // 国际市场初始化
    // ================================================================

    /**
     * 给国际市场注入初始资金和库存（仅在首次调用时执行）。
     * 在服务器启动、数据加载完成后调用。
     */
    public static void seedNpcIfNeeded() {
        if (seeded) {
            return;
        }
        seeded = true;

        boolean freshMarket = MARKET_PRICES.isEmpty();

        // 仅在全新市场注入初始资金，避免服务器重启反复补钱。
        if (freshMarket) {
            long npcBalance = AccountManager.getBalance(NPC_UUID);
            if (npcBalance < INITIAL_NPC_BALANCE) {
                AccountManager.deposit(NPC_UUID, INITIAL_NPC_BALANCE - npcBalance);
            }
        }
        CentralBank.seedIfNeeded();

        // 预创建价格 + 注入初始库存
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            String id = commodity.getId();
            long actualStock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, id);

            MarketPrice mp = MARKET_PRICES.get(id);
            if (mp == null) {
                // 新商品：创建 MarketPrice，注入初始库存，计算基础价格
                mp = new MarketPrice(id, commodity.getBasePrice(), DEFAULT_SPREAD);
                MARKET_PRICES.put(id, mp);

                if (actualStock < INITIAL_NPC_STOCK) {
                    CommodityInventoryManager.setCommodity(NPC_UUID, id, INITIAL_NPC_STOCK);
                }
                long stockAfterSeed = CommodityInventoryManager.getCommodityAmount(NPC_UUID, id);
                mp.recomputePrice(stockAfterSeed);
                mp.resetDayStats();
            } else {
                // 从磁盘恢复的已有商品只按实际库存重算价格，不在重启时补满库存。
                mp.recomputePrice(actualStock);
            }
        }
        EconomySavedData.markDirty();
    }

    // ================================================================
    // Tick（由 FinanceMod.onServerTick 驱动）
    // ================================================================

    /** 每个 MC 日执行国际市场外部需求和补货，使库存围绕参考值波动 */
    public static void naturalConsumeAll() {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            String commodityId = mp.getCommodityId();
            long stock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
            long target = MarketPrice.REFERENCE_STOCK;
            long newStock = stock;

            if (stock < target) {
                long gap = target - stock;
                int restockQty = (int) Math.max(1, Math.round(gap * DAILY_RESTOCK_RATIO));
                CommodityInventoryManager.addCommodity(NPC_UUID, commodityId, restockQty);
                newStock = stock + restockQty;
            } else if (stock > target) {
                long surplus = stock - target;
                int consumeQty = (int) Math.max(1, Math.round(surplus * DAILY_DEMAND_RATIO));
                consumeQty = (int) Math.min(consumeQty, stock);
                CommodityInventoryManager.removeCommodity(NPC_UUID, commodityId, consumeQty);
                newStock = stock - consumeQty;
            }

            mp.recomputePrice(newStock);
        }
        EconomySavedData.markDirty();
    }

    /** 失控时由中央银行介入，正常区间不出手。 */
    public static void centralBankIntervention() {
        CentralBank.dailyIntervention();
    }

    /** 每分钟衰减动能（不重算价格，由调用方统一 recalculate） */
    public static void tickAllMomentum() {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            mp.tickMomentum();
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    /** 每 3 分钟刷新噪音并重算价格 */
    public static void tickAllNoise() {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            mp.tickNoise();
            mp.recalculateFromCurrent();
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    /** 动量衰减后统一重算价格（每分钟调用，避免与噪音重叠） */
    public static void recalculateAll() {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            mp.recalculateFromCurrent();
            mp.recordPeriodicSnapshot();
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    public static int clearPriceHistory() {
        int cleared = 0;
        for (MarketPrice mp : MARKET_PRICES.values()) {
            cleared += mp.getSnapshots().size();
            mp.clearSnapshots();
        }
        if (cleared > 0) {
            EconomySavedData.markDirty();
        }
        return cleared;
    }

    /** 每个 MC 天结束时重置所有商品的日内统计 */
    public static void resetAllDayStats() {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            mp.newDayReset();
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    // ================================================================
    // 事件（由 EventManager 调用）
    // ================================================================

    public static void applyEvent(String commodityId, MarketEvent event) {
        MarketPrice mp = MARKET_PRICES.get(commodityId);
        if (mp != null) {
            mp.applyEvent(event);
            mp.recalculateFromCurrent();
            EconomySavedData.markDirty();
        }
    }

    public static void applyEventToAll(MarketEvent event) {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            mp.applyEvent(event);
            mp.recalculateFromCurrent();
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    public static void removeEvent(String commodityId, MarketEvent event) {
        MarketPrice mp = MARKET_PRICES.get(commodityId);
        if (mp != null && mp.getActiveEvent() == event) {
            mp.removeEvent();
            mp.recalculateFromCurrent();
            EconomySavedData.markDirty();
        }
    }

    public static void removeEventFromAll(MarketEvent event) {
        for (MarketPrice mp : MARKET_PRICES.values()) {
            if (mp.getActiveEvent() == event) {
                mp.removeEvent();
                mp.recalculateFromCurrent();
            }
        }
        if (!MARKET_PRICES.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    private static int getMaxNpcBuyQuantity(String commodityId, long bidPrice) {
        long cashCapacity = bidPrice <= 0 ? 0 : AccountManager.getBalance(NPC_UUID) / bidPrice;
        long stock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
        long target = MarketPrice.REFERENCE_STOCK;
        long stockCapacity = Math.max(1, Math.round(target * MAX_TRADE_REFERENCE_RATIO));
        if (stock > target) {
            stockCapacity = Math.max(1, Math.round(stockCapacity * 0.55));
        }
        return safeInt(Math.min(cashCapacity, stockCapacity));
    }

    private static int getMaxNpcSellQuantity(String commodityId) {
        long stock = CommodityInventoryManager.getCommodityAmount(NPC_UUID, commodityId);
        long target = MarketPrice.REFERENCE_STOCK;
        long capacity = Math.max(1, Math.round(target * MAX_TRADE_REFERENCE_RATIO));
        if (stock < target) {
            capacity = Math.max(1, Math.round(capacity * 0.55));
        }
        return safeInt(Math.min(stock, capacity));
    }

    private static int safeInt(long value) {
        if (value <= 0) return 0;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
