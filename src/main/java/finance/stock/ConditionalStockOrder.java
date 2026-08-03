package finance.stock;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConditionalStockOrder {

    private final UUID orderId;
    private final UUID playerId;
    private final String symbol;
    private final ConditionalStockOrderType type;
    private final long triggerPrice;
    private final long quantity;
    private final LocalDateTime createdAt;

    public ConditionalStockOrder(UUID playerId, String symbol, ConditionalStockOrderType type,
                                 long triggerPrice, long quantity) {
        this(UUID.randomUUID(), playerId, symbol, type, triggerPrice, quantity, LocalDateTime.now());
    }

    public ConditionalStockOrder(UUID orderId, UUID playerId, String symbol, ConditionalStockOrderType type,
                                 long triggerPrice, long quantity, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.playerId = playerId;
        this.symbol = StockMarketManager.normalizeSymbol(symbol);
        this.type = type;
        this.triggerPrice = triggerPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getSymbol() {
        return symbol;
    }

    public ConditionalStockOrderType getType() {
        return type;
    }

    public long getTriggerPrice() {
        return triggerPrice;
    }

    public long getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean shouldTrigger(long currentPrice) {
        return switch (type) {
            case TAKE_PROFIT -> currentPrice >= triggerPrice;
            case STOP_LOSS -> currentPrice <= triggerPrice;
        };
    }
}
