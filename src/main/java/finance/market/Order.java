package finance.market;

import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    private final UUID playerId;

    private final String commodityId;

    private final OrderType type;

    private final long price;

    private int quantity;

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

    public Order(
            UUID playerId,
            String commodityId,
            OrderType type,
            long price,
            int quantity,
            LocalDateTime timestamp
    ) {

        this.playerId = playerId;
        this.commodityId = commodityId;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
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

    public void reduceQuantity(int amount) {
        if (amount > 0) {
            this.quantity -= amount;
        }
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
