package finance.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.data.EconomySavedData;
import java.util.UUID;
import finance.commodity.CommodityInventoryManager;

public class MarketManager {

    private static final List<Order> ORDERS = new ArrayList<>();
    private static final List<Trade> TRADE_HISTORY = new ArrayList<>();

    public static void placeOrder(Order order) {

        // Freeze assets when placing the order
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

        int remaining = matchOrders(order);

        if (remaining > 0) {
            order.setQuantity(remaining);
            ORDERS.add(order);
            EconomySavedData.markDirty();
        }
    }

    public static List<Order> getOrders() {
        return ORDERS;
    }

    public static List<Trade> getTradeHistory() {
        return TRADE_HISTORY;
    }

    public static void addTradeToHistory(Trade trade) {
        TRADE_HISTORY.add(trade);

        // Keep only the last 500 trades
        while (TRADE_HISTORY.size() > 500) {
            TRADE_HISTORY.remove(0);
        }
    }

    public static void clearTradeHistory() {
        TRADE_HISTORY.clear();
    }

    public static void addOrderDirect(Order order) {
        ORDERS.add(order);
    }

    public static void clearOrders() {
        ORDERS.clear();
    }

    public static boolean cancelOrder(int index, UUID playerId) {

        if (index < 0 || index >= ORDERS.size()) {
            return false;
        }

        Order order = ORDERS.get(index);

        if (!order.getPlayerId().equals(playerId)) {
            return false;
        }

        // Return frozen assets
        if (order.getType() == OrderType.BUY) {

            long totalCost = order.getPrice()
                    * order.getQuantity();

            AccountManager.unfreezeFunds(
                    order.getPlayerId(),
                    totalCost
            );

        } else {
            // SELL order — return commodity to seller
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

    private static int matchOrders(Order newOrder) {

        int remaining = newOrder.getQuantity();

        Iterator<Order> iterator = ORDERS.iterator();

        while (iterator.hasNext() && remaining > 0) {

            Order existingOrder = iterator.next();

            // Must be same commodity
            if (!existingOrder.getCommodityId()
                    .equals(newOrder.getCommodityId())) {
                continue;
            }

            // Must be opposite type (BUY matches SELL)
            if (existingOrder.getType() == newOrder.getType()) {
                continue;
            }

            // No self-trading
            if (existingOrder.getPlayerId()
                    .equals(newOrder.getPlayerId())) {
                continue;
            }

            // Price matching: buyer's price >= seller's price
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

            // Determine buyer, seller, buy order, sell order
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

            // Calculate trade quantity (support partial fill)
            int tradeQty = Math.min(
                    remaining,
                    existingOrder.getQuantity()
            );

            // Seller's price is the execution price
            long tradePrice = sellOrder.getPrice();
            long paymentAmount = tradePrice * tradeQty;

            // PRE-CHECK: seller has commodity frozen in the order
            // Commodity was already removed from inventory at
            // order placement. Trust the freeze — the order's
            // quantity represents the frozen commodity.
            int frozenQty = (newOrder.getType() == OrderType.SELL)
                    ? remaining
                    : existingOrder.getQuantity();

            if (frozenQty < tradeQty) {
                continue;
            }

            // ============================================
            // EXECUTE
            // ============================================

            // Step 1: Transfer commodity (seller → buyer)
            // Seller's commodity was frozen at order placement
            CommodityInventoryManager.addCommodity(
                    buyer,
                    newOrder.getCommodityId(),
                    tradeQty
            );

            // Step 2: Transfer money (buyer → seller)
            // Buyer's money was frozen at order placement
            long frozenAmount = buyOrder.getPrice() * tradeQty;
            long refund = frozenAmount - paymentAmount;

            // Unfreeze the buyer's frozen funds
            AccountManager.unfreezeFunds(buyer, frozenAmount);

            // Pay the seller
            AccountManager.deposit(seller, paymentAmount);

            // Price improvement refund stays in buyer's balance
            // (already returned by unfreezeFunds above)

            // Record the transaction and trade
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

            // Reduce quantities
            remaining -= tradeQty;
            existingOrder.reduceQuantity(tradeQty);

            if (existingOrder.getQuantity() <= 0) {
                iterator.remove();
            }
        }

        return remaining;
    }
}
