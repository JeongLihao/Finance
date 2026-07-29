package finance.stock;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 股票成交记录 —— 买卖双方订单撮合成功的交易。
 */
public class StockTrade {

    private final UUID buyer;
    private final UUID seller;
    private final String symbol;

    /** 成交单价 */
    private final long price;
    private final int quantity;
    private final LocalDateTime timestamp;

    public StockTrade(UUID buyer, UUID seller, String symbol, long price, int quantity) {
        this.buyer = buyer;
        this.seller = seller;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    public StockTrade(UUID buyer, UUID seller, String symbol, long price, int quantity, LocalDateTime timestamp) {
        this.buyer = buyer;
        this.seller = seller;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public UUID getBuyer() { return buyer; }
    public UUID getSeller() { return seller; }
    public String getSymbol() { return symbol; }
    public long getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
