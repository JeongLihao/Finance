package finance.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.data.EconomySavedData;
import java.util.UUID;
import finance.commodity.CommodityInventoryManager;

/**
 * 市场交易管理器 —— 订单簿撮合引擎。
 *
 * <h3>撮合流程</h3>
 * <ol>
 *   <li>挂单时先冻结资产（BUY 冻结资金 / SELL 扣除商品）</li>
 *   <li>遍历现有订单簿，寻找同商品、同方向的可匹配订单</li>
 *   <li>价格匹配：买方出价 ≥ 卖方要价即可成交</li>
 *   <li>成交价 = 卖方定价，差价作为买方价格改善退还</li>
 *   <li>部分成交：剩余数量作为新订单挂回订单簿</li>
 *   <li>取消订单时退还冻结的资产</li>
 * </ol>
 *
 * <h3>数据结构</h3>
 * ORDERS 是订单簿（当前所有未成交订单），TRADE_HISTORY 是成交历史（最多 500 条）。
 */
public class MarketManager {

    /** 订单簿：所有未成交的活跃订单 */
    private static final List<Order> ORDERS = new ArrayList<>();

    /** 成交历史：最多保留 500 条 */
    private static final List<Trade> TRADE_HISTORY = new ArrayList<>();

    // ================================================================
    // 挂单
    // ================================================================

    /**
     * 挂单入口。
     * BUY 单先冻结资金，SELL 单先扣除库存商品，然后尝试撮合。
     * 未能完全成交的剩余数量作为新订单加入订单簿。
     */
    public static void placeOrder(Order order) {

        // ---- 冻结资产 ----
        if (order.getType() == OrderType.BUY) {

            long totalCost = order.getPrice()
                    * order.getQuantity();

            if (!AccountManager.freezeFunds(
                    order.getPlayerId(),
                    totalCost
            )) {
                return;
            }

        } else {

            boolean removed =
                    CommodityInventoryManager
                            .removeCommodity(
                                    order.getPlayerId(),
                                    order.getCommodityId(),
                                    order.getQuantity()
                            );

            if (!removed) {
                return;
            }
        }

        // ---- 撮合 ----
        int remaining = matchOrders(order);

        // 未成交部分挂回订单簿
        if (remaining > 0) {
            order.setQuantity(remaining);
            ORDERS.add(order);
            EconomySavedData.markDirty();
        }
    }

    // ================================================================
    // 订单簿撮合引擎
    // ================================================================

    /**
     * 尝试将新订单与现有订单簿中的订单撮合。
     * <p>
     * 撮合规则：
     * <ul>
     *   <li>同商品、反方向（BUY ↔ SELL）</li>
     *   <li>不允许自成交</li>
     *   <li>买方出价 ≥ 卖方要价即可成交</li>
     *   <li>成交价 = 卖方定价（买方差价自动退还）</li>
     *   <li>支持部分成交，未成交数量返回给调用方</li>
     * </ul>
     *
     * @return 未成交的剩余数量
     */
    private static int matchOrders(Order newOrder) {

        int remaining = newOrder.getQuantity();

        Iterator<Order> iterator = ORDERS.iterator();

        while (iterator.hasNext() && remaining > 0) {

            Order existingOrder = iterator.next();

            // ---- 匹配检查 ----
            if (!existingOrder.getCommodityId()
                    .equals(newOrder.getCommodityId())) {
                continue;  // 不同商品
            }

            if (existingOrder.getType() == newOrder.getType()) {
                continue;  // 同方向，不撮合
            }

            if (existingOrder.getPlayerId()
                    .equals(newOrder.getPlayerId())) {
                continue;  // 禁止自成交
            }

            // 价格匹配：买方出价必须 ≥ 卖方要价
            boolean priceMatch;
            if (newOrder.getType() == OrderType.BUY) {
                priceMatch = newOrder.getPrice()
                        >= existingOrder.getPrice();
            } else {
                priceMatch = existingOrder.getPrice()
                        >= newOrder.getPrice();
            }

            if (!priceMatch) {
                continue;
            }

            // ---- 确定买卖双方 ----
            UUID buyer;
            UUID seller;
            Order buyOrder;
            Order sellOrder;

            if (newOrder.getType() == OrderType.BUY) {
                buyer = newOrder.getPlayerId();
                seller = existingOrder.getPlayerId();
                buyOrder = newOrder;
                sellOrder = existingOrder;
            } else {
                buyer = existingOrder.getPlayerId();
                seller = newOrder.getPlayerId();
                buyOrder = existingOrder;
                sellOrder = newOrder;
            }

            // 成交数量（支持部分成交）
            int tradeQty = Math.min(
                    remaining,
                    existingOrder.getQuantity()
            );

            // 成交价 = 卖方定价
            long tradePrice = sellOrder.getPrice();
            long paymentAmount = tradePrice * tradeQty;

            // 卖方商品已在下单时冻结，此处信任冻结量足够
            int frozenQty = (newOrder.getType() == OrderType.SELL)
                    ? remaining
                    : existingOrder.getQuantity();

            if (frozenQty < tradeQty) {
                continue;
            }

            // ============================================
            // 执行成交
            // ============================================

            // Step 1: 商品交割（卖方 → 买方）
            // 卖方的商品在下单时已从库存扣除，此处直接给买方
            CommodityInventoryManager.addCommodity(
                    buyer,
                    newOrder.getCommodityId(),
                    tradeQty
            );

            // Step 2: 资金结算（买方 → 卖方）
            long frozenAmount = buyOrder.getPrice() * tradeQty;

            // 解冻买方冻结的全部资金
            AccountManager.unfreezeFunds(buyer, frozenAmount);

            // 支付卖方（成交价 × 数量）
            AccountManager.deposit(seller, paymentAmount);

            // 差价（买方出价 - 成交价）由 unfreezeFunds 自动退回买方余额

            // Step 3: 记录交易
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyer,
                            seller,
                            paymentAmount,
                            "MARKET_TRADE"
                    )
            );

            addTradeToHistory(
                    new Trade(
                            buyer,
                            seller,
                            newOrder.getCommodityId(),
                            tradePrice,
                            tradeQty
                    )
            );

            // Step 4: 更新订单数量
            remaining -= tradeQty;
            existingOrder.reduceQuantity(tradeQty);

            // 已完全成交的订单从订单簿移除
            if (existingOrder.getQuantity() <= 0) {
                iterator.remove();
            }
        }

        return remaining;
    }

    // ================================================================
    // 取消订单
    // ================================================================

    /**
     * 取消指定索引的订单，退还冻结的资产。
     * <ul>
     *   <li>BUY 单：解冻资金</li>
     *   <li>SELL 单：退还商品</li>
     * </ul>
     */
    public static boolean cancelOrder(int index, UUID playerId) {

        if (index < 0 || index >= ORDERS.size()) {
            return false;
        }

        Order order = ORDERS.get(index);

        if (!order.getPlayerId().equals(playerId)) {
            return false;
        }

        // 退还冻结资产
        if (order.getType() == OrderType.BUY) {

            long totalCost = order.getPrice()
                    * order.getQuantity();

            AccountManager.unfreezeFunds(
                    order.getPlayerId(),
                    totalCost
            );

        } else {
            CommodityInventoryManager.addCommodity(
                    order.getPlayerId(),
                    order.getCommodityId(),
                    order.getQuantity()
            );
        }

        ORDERS.remove(index);
        EconomySavedData.markDirty();
        return true;
    }

    // ================================================================
    // 查询接口
    // ================================================================

    public static List<Order> getOrders() {
        return ORDERS;
    }

    public static List<Trade> getTradeHistory() {
        return TRADE_HISTORY;
    }

    // ================================================================
    // 数据管理（供持久化层使用）
    // ================================================================

    /** 添加成交记录并维护 500 条上限 */
    public static void addTradeToHistory(Trade trade) {
        TRADE_HISTORY.add(trade);
        while (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.remove(0);
        }
    }

    /** 清空成交历史（数据加载前调用） */
    public static void clearTradeHistory() {
        TRADE_HISTORY.clear();
    }

    /** 直接加入订单簿（从磁盘恢复时使用，跳过资产冻结） */
    public static void addOrderDirect(Order order) {
        ORDERS.add(order);
    }

    /** 清空订单簿（数据加载前调用） */
    public static void clearOrders() {
        ORDERS.clear();
    }
}
