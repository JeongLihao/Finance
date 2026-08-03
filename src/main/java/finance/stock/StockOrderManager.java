package finance.stock;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.util.MathUtil;

import java.util.*;

/**
 * 股票订单簿管理器 —— 玩家限价单撮合 + 做市商保底流动性。
 *
 * <h3>撮合流程</h3>
 * <ol>
 *   <li>玩家挂单时先冻结资产（BUY 冻结资金 / SELL 冻结股票）</li>
 *   <li>遍历已有订单，寻找同股票、反向的可匹配订单</li>
 *   <li>价格匹配：买方出价 ≥ 卖方要价即可成交</li>
 *   <li>成交价 = 卖方定价，差价作为买方价格改善退还</li>
 *   <li>部分成交：剩余数量作为新订单挂回订单簿</li>
 *   <li>无对手盘时，做市商按 fairValue ± spread 报价成交</li>
 * </ol>
 */
public class StockOrderManager {

    private static final List<StockOrder> ORDERS = new ArrayList<>();

    /** 按股票代码分组，加速撮合 */
    private static final Map<String, List<StockOrder>> ORDERS_BY_SYMBOL = new HashMap<>();

    /** 成交记录（保留最近 500 条） */
    private static final List<StockTrade> TRADE_HISTORY = new ArrayList<>();

    /** 做市商参数 */
    private static final double MARKET_MAKER_SPREAD = 0.02; // ±2%
    private static final UUID MARKET_MAKER_UUID = new UUID(0, 0); // nil UUID

    // ================================================================
    // 订单操作
    // ================================================================

    /**
     * 玩家挂买单 —— 冻结资金，尝试与卖单撮合，无对手盘时做市商成交。
     */
    public static OrderResult placeBuyOrder(UUID playerId, String symbol, long price, int quantity) {
        if (playerId == null || symbol == null || symbol.isEmpty()) {
            return OrderResult.fail("参数错误。");
        }
        symbol = StockMarketManager.normalizeSymbol(symbol);
        if (quantity <= 0 || price <= 0) {
            return OrderResult.fail("数量和价格必须为正。");
        }

        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) {
            return OrderResult.fail("未知股票: " + symbol);
        }

        // 冻结资金
        long totalCost = MathUtil.multiplyExactOrNegative1(price, quantity);
        if (totalCost <= 0) {
            return OrderResult.fail("交易金额过大。");
        }
        if (!AccountManager.withdraw(playerId, totalCost)) {
            return OrderResult.fail("余额不足，需要: " + totalCost);
        }

        // 创建订单并尝试撮合
        StockOrder order = new StockOrder(playerId, symbol, StockOrderType.BUY, price, quantity);
        return matchOrder(order, stock);
    }

    /**
     * 玩家挂卖单 —— 冻结股票，尝试与买单撮合，无对手盘时做市商成交。
     */
    public static OrderResult placeSellOrder(UUID playerId, String symbol, long price, int quantity) {
        if (playerId == null || symbol == null || symbol.isEmpty()) {
            return OrderResult.fail("参数错误。");
        }
        symbol = StockMarketManager.normalizeSymbol(symbol);
        if (quantity <= 0 || price <= 0) {
            return OrderResult.fail("数量和价格必须为正。");
        }

        Stock stock = StockMarketManager.getStock(symbol);
        if (stock == null) {
            return OrderResult.fail("未知股票: " + symbol);
        }

        // 检查玩家持股
        long holdings = StockPortfolioManager.getHolding(playerId, symbol).getQuantity();
        if (holdings < quantity) {
            return OrderResult.fail("持仓不足，拥有: " + holdings);
        }

        // 扣除股票（锁定）
        if (!StockPortfolioManager.removeHolding(playerId, symbol, quantity)) {
            return OrderResult.fail("持仓不足。");
        }

        // 创建订单并尝试撮合
        StockOrder order = new StockOrder(playerId, symbol, StockOrderType.SELL, price, quantity);
        return matchOrder(order, stock);
    }

    /**
     * 尝试撮合订单：优先与玩家对手盘撮合，无则做市商成交。
     */
    private static OrderResult matchOrder(StockOrder order, Stock stock) {
        int remainingQty = order.getQuantity();

        // 尝试与现有订单撮合：价格优先，同价时间优先。
        List<StockOrder> oppositeOrders = ORDERS_BY_SYMBOL.getOrDefault(order.getSymbol(), new ArrayList<>());
        sortOrderBook(oppositeOrders);
        Iterator<StockOrder> iterator = oppositeOrders.iterator();

        while (iterator.hasNext() && remainingQty > 0) {
            StockOrder opposite = iterator.next();

            // 方向必须相反
            if (opposite.getType() == order.getType()) {
                continue;
            }

            if (opposite.getPlayerId().equals(order.getPlayerId())) {
                continue;
            }

            // 价格必须匹配
            boolean priceMatch = order.getType() == StockOrderType.BUY
                    ? order.getPrice() >= opposite.getPrice()  // 买单出价 ≥ 卖单要价
                    : order.getPrice() <= opposite.getPrice(); // 卖单要价 ≤ 买单出价

            if (!priceMatch) {
                continue;
            }

            // 成交量 = min(订单剩余, 对手盘剩余)
            int tradeQty = Math.min(remainingQty, opposite.getQuantity());

            // 成交价取卖方定价
            long tradePrice = order.getType() == StockOrderType.BUY ? opposite.getPrice() : order.getPrice();
            long buyerLimitPrice = order.getType() == StockOrderType.BUY ? order.getPrice() : opposite.getPrice();
            UUID buyerId = order.getType() == StockOrderType.BUY ? order.getPlayerId() : opposite.getPlayerId();
            UUID sellerId = order.getType() == StockOrderType.BUY ? opposite.getPlayerId() : order.getPlayerId();

            // 执行成交。失败时不推进订单状态，避免账户/持仓和订单数量半成功。
            if (!executeTrade(buyerId, sellerId, order.getSymbol(),
                    tradePrice, tradeQty, stock, buyerLimitPrice, order.getType() == StockOrderType.BUY)) {
                order.setQuantity(remainingQty);
                addOrderDirect(order);
                EconomySavedData.markDirty();
                return OrderResult.ok("部分撮合遇到结算异常，剩余订单已挂入订单簿。");
            }

            remainingQty -= tradeQty;
            opposite.reduceQuantity(tradeQty);

            // 对手盘完全成交，移除
            if (opposite.getQuantity() <= 0) {
                iterator.remove();
                ORDERS.remove(opposite);
            }
        }

        // 还有剩余数量，尝试做市商成交；做市商不接则挂回订单簿。
        if (remainingQty > 0) {
            order.setQuantity(remainingQty);
            OrderResult marketMakerResult = marketMakerTrade(order, stock);
            if (marketMakerResult.success()) {
                return marketMakerResult;
            }
            if (marketMakerResult.terminal()) {
                EconomySavedData.markDirty();
                return marketMakerResult;
            }
            addOrderDirect(order);
            EconomySavedData.markDirty();
            return OrderResult.ok("订单已挂入订单簿，剩余数量: " + order.getQuantity());
        }

        EconomySavedData.markDirty();
        return OrderResult.ok("订单已全部成交。");
    }

    /**
     * 做市商成交 —— 保底流动性，按 fairValue ± spread 报价。
     */
    private static OrderResult marketMakerTrade(StockOrder order, Stock stock) {
        long fairValue = stock.getFairValue();
        if (fairValue <= 0) {
            refundOrderAssets(order);
            return OrderResult.terminalFail("市场行情异常，请稍后重试。");
        }

        // 做市商报价
        long bidPrice = Math.max(1, Math.round(fairValue * (1 - MARKET_MAKER_SPREAD)));
        long askPrice = Math.round(fairValue * (1 + MARKET_MAKER_SPREAD));

        long tradePrice;
        if (order.getType() == StockOrderType.BUY) {
            // 买单以 askPrice 成交
            tradePrice = askPrice;
            if (order.getPrice() < tradePrice) {
                return OrderResult.fail("做市商报价未满足。");
            }
        } else {
            // 卖单以 bidPrice 成交
            tradePrice = bidPrice;
            if (order.getPrice() > tradePrice) {
                return OrderResult.fail("做市商报价未满足。");
            }
        }

        // 执行成交
        if (order.getType() == StockOrderType.BUY) {
            if (!executeTrade(order.getPlayerId(), MARKET_MAKER_UUID, order.getSymbol(),
                    tradePrice, order.getQuantity(), stock, order.getPrice(), true)) {
                return OrderResult.fail("做市商成交结算失败。");
            }
        } else {
            if (!executeTrade(MARKET_MAKER_UUID, order.getPlayerId(), order.getSymbol(),
                    tradePrice, order.getQuantity(), stock, tradePrice, false)) {
                return OrderResult.fail("做市商成交结算失败。");
            }
        }

        EconomySavedData.markDirty();
        return OrderResult.ok("订单已与做市商成交，成交价: " + tradePrice);
    }

    /**
     * 执行成交 —— 更新账户、持仓、价格、交易记录。
     */
    private static boolean executeTrade(UUID buyerId, UUID sellerId, String symbol,
                                      long tradePrice, int tradeQty, Stock stock,
                                      long buyerLimitPrice, boolean buyInitiated) {

        // 计算成交金额
        long totalValue = MathUtil.multiplyExactOrNegative1(tradePrice, tradeQty);
        if (totalValue <= 0) return false;
        long reservedValue = MathUtil.multiplyExactOrNegative1(buyerLimitPrice, tradeQty);
        if (reservedValue <= 0 || reservedValue < totalValue) return false;

        // 更新账户：卖方收钱，买方已在挂单时冻结
        if (!buyerId.equals(MARKET_MAKER_UUID) && reservedValue > totalValue) {
            AccountManager.deposit(buyerId, reservedValue - totalValue);
        }
        if (!sellerId.equals(MARKET_MAKER_UUID)) {
            AccountManager.deposit(sellerId, totalValue);
        }

        // 更新持仓
        if (!buyerId.equals(MARKET_MAKER_UUID)) {
            StockPortfolioManager.addHolding(buyerId, symbol, tradeQty, tradePrice);
        }
        if (!sellerId.equals(MARKET_MAKER_UUID)) {
            // 玩家卖方的股票已在挂单时扣除，这里无需操作
        }

        // 通知定价引擎（推动价格）
        stock.recordTrade(tradePrice, tradeQty, buyInitiated);

        // 记录成交
        StockTrade trade = new StockTrade(buyerId, sellerId, symbol, tradePrice, tradeQty);
        addTradeToHistory(trade);
        if (!buyerId.equals(MARKET_MAKER_UUID)) {
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyerId,
                            sellerId,
                            totalValue,
                            TransactionType.STOCK_BUY,
                            buyerId,
                            symbol,
                            tradeQty
                    )
            );
        }
        if (!sellerId.equals(MARKET_MAKER_UUID)) {
            AccountManager.addTransactionRecord(
                    new TransactionRecord(
                            buyerId,
                            sellerId,
                            totalValue,
                            TransactionType.STOCK_SELL,
                            sellerId,
                            symbol,
                            tradeQty
                    )
            );
        }

        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 取消订单 —— 退还冻结资产。
     */
    public static boolean cancelOrder(UUID orderId, UUID playerId) {
        Iterator<StockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            StockOrder order = iterator.next();
            if (order.getOrderId().equals(orderId) && order.getPlayerId().equals(playerId)) {
                long amount = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());
                refundOrderAssets(order);
                iterator.remove();
                List<StockOrder> symbolOrders = ORDERS_BY_SYMBOL.get(order.getSymbol());
                if (symbolOrders != null) {
                    symbolOrders.remove(order);
                    if (symbolOrders.isEmpty()) {
                        ORDERS_BY_SYMBOL.remove(order.getSymbol());
                    }
                }
                AccountManager.addTransactionRecord(
                        new TransactionRecord(
                                playerId,
                                playerId,
                                Math.max(0, amount),
                                TransactionType.STOCK_ORDER_CANCEL,
                                playerId,
                                order.getSymbol(),
                                order.getQuantity()
                        )
                );
                EconomySavedData.markDirty();
                return true;
            }
        }
        return false;
    }

    public static int cancelOrdersForSymbol(String symbol) {
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        int cancelled = 0;
        Iterator<StockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            StockOrder order = iterator.next();
            if (!normalized.equals(order.getSymbol())) {
                continue;
            }
            refundOrderAssets(order);
            iterator.remove();
            cancelled++;
        }
        ORDERS_BY_SYMBOL.remove(normalized);
        if (cancelled > 0) {
            EconomySavedData.markDirty();
        }
        return cancelled;
    }

    private static void refundOrderAssets(StockOrder order) {
        if (order.getQuantity() <= 0) {
            return;
        }
        if (order.getType() == StockOrderType.BUY) {
            long refund = MathUtil.multiplyExactOrNegative1(order.getPrice(), order.getQuantity());
            if (refund > 0) {
                AccountManager.deposit(order.getPlayerId(), refund);
            }
        } else {
            StockPortfolioManager.addHolding(order.getPlayerId(), order.getSymbol(), order.getQuantity(), 0);
        }
    }

    // ================================================================
    // 查询
    // ================================================================

    public static List<StockOrder> getOrders() {
        return new ArrayList<>(ORDERS);
    }

    public static List<StockOrder> getOrdersBySymbol(String symbol) {
        return ORDERS_BY_SYMBOL.getOrDefault(symbol, new ArrayList<>());
    }

    public static List<StockTrade> getTradeHistory() {
        return new ArrayList<>(TRADE_HISTORY);
    }

    // ================================================================
    // 持久化支持
    // ================================================================

    public static void addOrderDirect(StockOrder order) {
        ORDERS.add(order);
        ORDERS_BY_SYMBOL.computeIfAbsent(order.getSymbol(), k -> new ArrayList<>()).add(order);
        sortOrderBook(ORDERS_BY_SYMBOL.get(order.getSymbol()));
    }

    public static void clearOrders() {
        ORDERS.clear();
        ORDERS_BY_SYMBOL.clear();
    }

    private static void addTradeToHistory(StockTrade trade) {
        TRADE_HISTORY.add(trade);
        if (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.subList(0, TRADE_HISTORY.size() - 500).clear();
        }
    }

    public static void clearTradeHistory() {
        TRADE_HISTORY.clear();
    }

    public static void addTradeDirectly(StockTrade trade) {
        TRADE_HISTORY.add(trade);
        if (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.subList(0, TRADE_HISTORY.size() - 500).clear();
        }
    }

    private static void sortOrderBook(List<StockOrder> orders) {
        orders.sort((a, b) -> {
            if (a.getType() != b.getType()) {
                return a.getType().compareTo(b.getType());
            }
            int priceCompare = a.getType() == StockOrderType.BUY
                    ? Long.compare(b.getPrice(), a.getPrice())
                    : Long.compare(a.getPrice(), b.getPrice());
            if (priceCompare != 0) {
                return priceCompare;
            }
            return a.getTimestamp().compareTo(b.getTimestamp());
        });
    }

    public record OrderResult(boolean success, String message, boolean terminal) {
        public static OrderResult ok(String message) {
            return new OrderResult(true, message, false);
        }

        public static OrderResult fail(String message) {
            return new OrderResult(false, message, false);
        }

        public static OrderResult terminalFail(String message) {
            return new OrderResult(false, message, true);
        }
    }
}
