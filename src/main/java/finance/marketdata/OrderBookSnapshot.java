package finance.marketdata;

import java.util.List;

public record OrderBookSnapshot(List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
    public OrderBookSnapshot {
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
    }
}
