package finance.marketdata;

import finance.bondmarket.BondMarketManager;
import finance.bondmarket.BondOrderSide;
import finance.chart.MarketInstrumentType;
import finance.market.MarketManager;
import finance.market.OrderType;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockOrderType;
import finance.util.MathUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class OrderBookService {
    public static final int DEFAULT_DEPTH = 5;

    private OrderBookService() {}

    public record BookOrder(OrderBookSide side, long price, long quantity) {}

    public static OrderBookSnapshot snapshot(MarketInstrumentType type, String id) {
        if (type == null || id == null || id.isBlank()) return new OrderBookSnapshot(List.of(), List.of());
        List<BookOrder> orders = new ArrayList<>();
        if (type == MarketInstrumentType.COMMODITY) {
            for (finance.market.Order order : MarketManager.getOrders()) {
                if (id.equals(order.getCommodityId()))
                    orders.add(new BookOrder(
                            order.getType() == OrderType.BUY ? OrderBookSide.BUY : OrderBookSide.SELL,
                            order.getPrice(),
                            order.getQuantity()));
            }
        } else if (type == MarketInstrumentType.STOCK) {
            String symbol = StockMarketManager.normalizeSymbol(id);
            for (finance.stock.StockOrder order : StockOrderManager.getOrdersBySymbol(symbol)) {
                orders.add(new BookOrder(
                        order.getType() == StockOrderType.BUY ? OrderBookSide.BUY : OrderBookSide.SELL,
                        order.getPrice(),
                        order.getQuantity()));
            }
        } else if (type == MarketInstrumentType.BOND) {
            java.util.UUID bondId = finance.util.Uuids.parseOrNull(id);
            if (bondId != null) {
                for (finance.bondmarket.BondOrder order : BondMarketManager.orders()) {
                    if (bondId.equals(order.bondId()))
                        orders.add(new BookOrder(
                                order.side() == BondOrderSide.BUY ? OrderBookSide.BUY : OrderBookSide.SELL,
                                order.limitPricePerUnit(),
                                order.remainingQuantity()));
                }
            }
        } else if (type == MarketInstrumentType.FUTURES) {
            java.util.UUID contractId = finance.util.Uuids.parseOrNull(id);
            if (contractId != null) {
                for (finance.futures.FuturesOrder order : finance.futures.FuturesMarketManager.orders()) {
                    if (contractId.equals(order.contractId()))
                        orders.add(new BookOrder(
                                order.side() == finance.futures.FuturesOrderSide.BUY
                                        ? OrderBookSide.BUY
                                        : OrderBookSide.SELL,
                                order.limitPrice(),
                                order.remainingQuantity()));
                }
            }
        }
        return aggregate(orders, DEFAULT_DEPTH);
    }

    public static OrderBookSnapshot aggregate(List<BookOrder> orders, int depth) {
        int safeDepth = Math.max(0, Math.min(20, depth));
        Map<Long, Long> bids = new TreeMap<>(Comparator.reverseOrder());
        Map<Long, Long> asks = new TreeMap<>();
        if (orders != null)
            for (BookOrder order : orders) {
                if (order == null || order.side == null || order.price <= 0 || order.quantity <= 0) continue;
                Map<Long, Long> levels = order.side == OrderBookSide.BUY ? bids : asks;
                levels.merge(order.price, order.quantity, MathUtil::saturatedAddNonNegative);
            }
        return new OrderBookSnapshot(toLevels(bids, safeDepth), toLevels(asks, safeDepth));
    }

    private static List<OrderBookLevel> toLevels(Map<Long, Long> values, int depth) {
        return values.entrySet().stream()
                .limit(depth)
                .map(entry -> new OrderBookLevel(entry.getKey(), entry.getValue()))
                .toList();
    }
}
