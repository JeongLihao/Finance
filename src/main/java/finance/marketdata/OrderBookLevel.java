package finance.marketdata;

public record OrderBookLevel(long price, long quantity) {
    public OrderBookLevel {
        if (price <= 0 || quantity <= 0) throw new IllegalArgumentException("Invalid order-book level");
    }
}
