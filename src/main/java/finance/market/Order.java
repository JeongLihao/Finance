package finance.market;

import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    private final UUID playerId;

    private final String commodityId;

    private final OrderType type;

    private final long price;

    private final int quantity;

    private final LocalDateTime timestamp;

    public Order(
            UUID playerId,
            String commodityId,
            OrderType type,
            long price,
            int quantity
    ) {

        this.playerId = playerId;
        this.commodityId = commodityId;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public OrderType getType() {
        return type;
    }

    public long getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
