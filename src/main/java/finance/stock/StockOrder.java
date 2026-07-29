package finance.stock;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 股票订单 —— 玩家挂出的买入或卖出委托（限价单）。
 * 结构参考 MarketManager.Order。
 */
public class StockOrder {

    private final UUID orderId;
    private final UUID playerId;

    /** 股票代码（如 "IRON"） */
    private final String symbol;

    private final StockOrderType type;

    /** 限价单价 */
    private final long price;

    /** 剩余数量（支持部分成交） */
    private int quantity;

    private final LocalDateTime timestamp;

    public StockOrder(UUID playerId, String symbol, StockOrderType type, long price, int quantity) {
        this.orderId = UUID.randomUUID();
        this.playerId = playerId;
        this.symbol = symbol;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    /** 从持久化数据恢复订单 */
    public StockOrder(UUID orderId, UUID playerId, String symbol, StockOrderType type,
                      long price, int quantity, LocalDateTime timestamp) {
        this.orderId = orderId;
        this.playerId = playerId;
        this.symbol = symbol;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getPlayerId() { return playerId; }
    public String getSymbol() { return symbol; }
    public StockOrderType getType() { return type; }
    public long getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void reduceQuantity(int amount) {
        if (amount > 0) {
            this.quantity -= amount;
        }
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
