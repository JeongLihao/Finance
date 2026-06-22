package finance.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import java.util.UUID;
import finance.commodity.CommodityInventoryManager;
import finance.util.MathUtil;

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
 * ORDERS 是订单簿（当前所有未成交订单），ORDERS_BY_COMMODITY 是按商品 ID 索引的二级索引，
 * TRADE_HISTORY 是成交历史（最多 500 条）。
 *
 * <h3>已知限制</h3>
 * P2P 订单簿与国际市场是两套独立交易系统，同一商品在两处的价格可以不同，
 * 存在理论上的跨市场套利空间。未来可考虑统一报价或对 P2P 订单收取手续费来缩小价差。
 */
public class MarketManager {

    /** 订单簿：所有未成交的活跃订单 */
    private static final List<Order> ORDERS = new ArrayList<>();

    /** 商品索引：commodityId → 该商品的所有活跃订单，加速撮合 */
    private static final Map<String, List<Order>> ORDERS_BY_COMMODITY = new HashMap<>();

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
    public static boolean placeOrder(Order order) {

        if (CommodityRegistry.getCommodity(order.getCommodityId()) == null) {
            return false;
        }

        // ---- 冻结资产 ----
        if (order.getType() == OrderType.BUY) {

            long totalCost = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());

            if (totalCost <= 0) {
                return false;
            }

            if (!AccountManager.freezeFunds(
                    order.getPlayerId(),
                    totalCost
            )) {
                return false;
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
                return false;
            }
        }

        // ---- 撮合 ----
        int remaining = matchOrders(order);

        // 未成交部分挂回订单簿
        if (remaining > 0) {
            order.setQuantity(remaining);
            ORDERS.add(order);
            ORDERS_BY_COMMODITY
                    .computeIfAbsent(order.getCommodityId(), k -> new ArrayList<>())
                    .add(order);
            EconomySavedData.markDirty();
        }

        return true;
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

        // 只遍历同商品的订单，而非全部订单
        List<Order> commodityOrders = ORDERS_BY_COMMODITY.get(newOrder.getCommodityId());
        if (commodityOrders == null || commodityOrders.isEmpty()) {
            return remaining;
        }

        Iterator<Order> iterator = commodityOrders.iterator();

        while (iterator.hasNext() && remaining > 0) {

            Order existingOrder = iterator.next();

            // ---- 匹配检查 ----
            if (existingOrder.getType() == newOrder.getType()) {
                continue;  // 同方向，不撮合
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
            long paymentAmount = MathUtil.multiplyExactOrNegative1(tradePrice, tradeQty);
            if (paymentAmount < 0) {
                continue;
            }

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

            // Step 1: 资金结算（买方 → 卖方）
            long frozenAmount = MathUtil.multiplyExactOrNegative1(buyOrder.getPrice(), tradeQty);
            if (frozenAmount < 0) {
                continue;
            }

            if (!AccountManager.settleFrozenFunds(buyer, frozenAmount, paymentAmount)) {
                continue;
            }

            // 支付卖方（成交价 × 数量）
            AccountManager.deposit(seller, paymentAmount);

            // 差价（买方出价 - 成交价）由 settleFrozenFunds 自动退回买方余额

            // Step 2: 商品交割（卖方 → 买方）
            // 卖方的商品在下单时已从库存扣除，此处直接给买方
            CommodityInventoryManager.addCommodity(
                    buyer,
                    newOrder.getCommodityId(),
                    tradeQty
            );

            // Step 3: 记录交易
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyer,
                            seller,
                            paymentAmount,
                            TransactionType.MARKET_TRADE
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

            // 记录到行情统计（P2P 交易也贡献日内成交量和价格快照）
            MarketPrice mp = NpcMarketMaker.getMarketPrice(
                    newOrder.getCommodityId());
            if (mp != null) {
                mp.recordTrade(tradePrice, tradeQty);
            }

            // Step 4: 更新订单数量
            remaining -= tradeQty;
            existingOrder.reduceQuantity(tradeQty);

            // 已完全成交的订单从订单簿移除
            if (existingOrder.getQuantity() <= 0) {
                iterator.remove();
                ORDERS.remove(existingOrder);
            }
        }

        return remaining;
    }

    // ================================================================
    // 取消订单
    // ================================================================

    /**
     * 取消指定订单（按订单 ID 查找），退还冻结的资产。
     * <ul>
     *   <li>BUY 单：解冻资金</li>
     *   <li>SELL 单：退还商品</li>
     * </ul>
     */
    public static boolean cancelOrder(UUID orderId, UUID playerId) {

        Iterator<Order> iterator = ORDERS.iterator();
        Order order = null;

        while (iterator.hasNext()) {
            Order candidate = iterator.next();
            if (candidate.getOrderId().equals(orderId)) {
                if (!candidate.getPlayerId().equals(playerId)) {
                    return false;
                }
                order = candidate;
                iterator.remove();
                // 同步从商品索引中移除
                List<Order> commodityList = ORDERS_BY_COMMODITY.get(candidate.getCommodityId());
                if (commodityList != null) {
                    commodityList.remove(candidate);
                }
                break;
            }
        }

        if (order == null) {
            return false;
        }

        // 退还冻结资产
        if (order.getType() == OrderType.BUY) {

            long totalCost = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());

            if (totalCost <= 0) {
                return false;
            }

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

        EconomySavedData.markDirty();
        return true;
    }

    public static TakeOrderResult takeOrder(UUID orderId, UUID takerId) {
        Order target = null;
        for (Order order : ORDERS) {
            if (order.getOrderId().equals(orderId)) {
                target = order;
                break;
            }
        }
        if (target == null) {
            return TakeOrderResult.fail("订单不存在或已成交。");
        }

        int qty = target.getQuantity();
        if (qty <= 0) {
            return TakeOrderResult.fail("订单数量无效。");
        }

        long total = MathUtil.multiplyExactOrNegative1(target.getPrice(), qty);
        if (total <= 0) {
            return TakeOrderResult.fail("订单金额过大。");
        }

        UUID buyer;
        UUID seller;
        if (target.getType() == OrderType.SELL) {
            buyer = takerId;
            seller = target.getPlayerId();
            if (!AccountManager.withdraw(buyer, total)) {
                return TakeOrderResult.fail("余额不足，需要: " + total);
            }
            AccountManager.deposit(seller, total);
            CommodityInventoryManager.addCommodity(buyer, target.getCommodityId(), qty);
        } else {
            buyer = target.getPlayerId();
            seller = takerId;
            if (!CommodityInventoryManager.removeCommodity(seller, target.getCommodityId(), qty)) {
                return TakeOrderResult.fail("库存不足，需要: " + qty);
            }
            if (!AccountManager.settleFrozenFunds(buyer, total, total)) {
                CommodityInventoryManager.addCommodity(seller, target.getCommodityId(), qty);
                return TakeOrderResult.fail("买单资金结算失败。");
            }
            AccountManager.deposit(seller, total);
            CommodityInventoryManager.addCommodity(buyer, target.getCommodityId(), qty);
        }

        removeOrderDirect(target);
        AccountManager.addTransactionRecord(
                new TransactionRecord(buyer, seller, total, TransactionType.MARKET_TRADE)
        );
        addTradeToHistory(new Trade(buyer, seller, target.getCommodityId(), target.getPrice(), qty));
        MarketPrice mp = NpcMarketMaker.getMarketPrice(target.getCommodityId());
        if (mp != null) {
            mp.recordTrade(target.getPrice(), qty);
        }
        EconomySavedData.markDirty();
        String action = target.getType() == OrderType.SELL ? "买入" : "卖出";
        return TakeOrderResult.ok("已" + action + " " + qty + "x " + target.getCommodityId() + "，单价: " + target.getPrice());
    }

    private static void removeOrderDirect(Order order) {
        ORDERS.remove(order);
        List<Order> commodityList = ORDERS_BY_COMMODITY.get(order.getCommodityId());
        if (commodityList != null) {
            commodityList.remove(order);
            if (commodityList.isEmpty()) {
                ORDERS_BY_COMMODITY.remove(order.getCommodityId());
            }
        }
    }

    public record TakeOrderResult(boolean success, String message) {
        public static TakeOrderResult ok(String message) {
            return new TakeOrderResult(true, message);
        }

        public static TakeOrderResult fail(String message) {
            return new TakeOrderResult(false, message);
        }
    }

    // ================================================================
    // 查询接口
    // ================================================================

    public static List<Order> getOrders() {
        return ORDERS;
    }

    /** 取消指定索引的订单（供命令使用），索引越界或非本人订单返回 false */
    public static boolean cancelOrderByIndex(int index, UUID playerId) {
        if (index < 0 || index >= ORDERS.size()) {
            return false;
        }
        Order order = ORDERS.get(index);
        return cancelOrder(order.getOrderId(), playerId);
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
        if (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.subList(0, TRADE_HISTORY.size() - 500).clear();
        }
    }

    /** 清空成交历史（数据加载前调用） */
    public static void clearTradeHistory() {
        TRADE_HISTORY.clear();
    }

    /** 直接加入订单簿（从磁盘恢复时使用，跳过资产冻结） */
    public static void addOrderDirect(Order order) {
        ORDERS.add(order);
        ORDERS_BY_COMMODITY
                .computeIfAbsent(order.getCommodityId(), k -> new ArrayList<>())
                .add(order);
    }

    /** 清空订单簿（数据加载前调用） */
    public static void clearOrders() {
        ORDERS.clear();
        ORDERS_BY_COMMODITY.clear();
    }
}
