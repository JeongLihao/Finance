package finance.market;
import java.time.LocalDateTime;
import java.util.UUID;

public class Trade {

    private final UUID buyer;

    private final UUID seller;

    private final String commodityId;

    private final long price;

    private final int quantity;

    private final LocalDateTime timestamp;

    public Trade(
            UUID buyer,
            UUID seller,
            String commodityId,
            long price,
            int quantity
    ) {

        this.buyer = buyer;
        this.seller = seller;
        this.commodityId = commodityId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    public UUID getBuyer() {
        return buyer;
    }

    public UUID getSeller() {
        return seller;
    }

    public String getCommodityId() {
        return commodityId;
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

