package finance.market;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 成交记录 —— 买卖双方撮合成功的交易。
 */
public class Trade {

    private final UUID buyer;

    private final UUID seller;

    private final String commodityId;

    /** 成交单价（卖方定价） */
    private final long price;

    private final int quantity;

    private final LocalDateTime timestamp;

    /** 新建成交记录，时间戳自动设为当前时间 */
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

    /** 从持久化数据恢复成交记录，使用指定时间戳 */
    public Trade(
            UUID buyer,
            UUID seller,
            String commodityId,
            long price,
            int quantity,
            LocalDateTime timestamp
    ) {

        this.buyer = buyer;
        this.seller = seller;
        this.commodityId = commodityId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
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
