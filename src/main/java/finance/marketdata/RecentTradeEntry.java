package finance.marketdata;

import java.time.LocalDateTime;

public record RecentTradeEntry(long mcDay, long price, long quantity,
                               LocalDateTime timestamp, TradeDirection direction) {
    public RecentTradeEntry {
        if (mcDay < 0 || price <= 0 || quantity <= 0 || timestamp == null || direction == null) {
            throw new IllegalArgumentException("Invalid recent trade");
        }
    }
}
