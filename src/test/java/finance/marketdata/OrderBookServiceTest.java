package finance.marketdata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookServiceTest {
    @Test void aggregatesSamePriceAndUsesCorrectPricePriority() {
        List<OrderBookService.BookOrder> orders = List.of(
                new OrderBookService.BookOrder(OrderBookSide.BUY, 10, 3),
                new OrderBookService.BookOrder(OrderBookSide.BUY, 12, 2),
                new OrderBookService.BookOrder(OrderBookSide.BUY, 10, 4),
                new OrderBookService.BookOrder(OrderBookSide.SELL, 15, 5),
                new OrderBookService.BookOrder(OrderBookSide.SELL, 13, 1));
        OrderBookSnapshot result = OrderBookService.aggregate(orders, 5);
        assertEquals(new OrderBookLevel(12, 2), result.bids().get(0));
        assertEquals(new OrderBookLevel(10, 7), result.bids().get(1));
        assertEquals(new OrderBookLevel(13, 1), result.asks().get(0));
    }

    @Test void returnsOnlyRequestedDepthAndIgnoresInvalidOrders() {
        List<OrderBookService.BookOrder> orders = new ArrayList<>();
        for (int i = 1; i <= 8; i++) orders.add(new OrderBookService.BookOrder(OrderBookSide.BUY, i, 1));
        orders.add(new OrderBookService.BookOrder(OrderBookSide.SELL, 0, 2));
        assertEquals(5, OrderBookService.aggregate(orders, 5).bids().size());
    }

    @Test void quantityAggregationSaturatesWithoutWrapping() {
        OrderBookSnapshot result = OrderBookService.aggregate(List.of(
                new OrderBookService.BookOrder(OrderBookSide.BUY, 9, Long.MAX_VALUE),
                new OrderBookService.BookOrder(OrderBookSide.BUY, 9, 1)), 5);
        assertEquals(Long.MAX_VALUE, result.bids().get(0).quantity());
    }
}
