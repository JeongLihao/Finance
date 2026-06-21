package finance.market;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 市场订单 —— 玩家挂出的买入或卖出委托。
 * <p>
 * 挂单时资金/商品即被冻结：
 * <ul>
 *   <li>BUY 单：冻结购买资金（price × quantity）</li>
 *   <li>SELL 单：扣除库存商品</li>
 * </ul>
 * 订单成交或取消时解冻/退还。
 * </p>
 */
public class Order {

    private final UUID orderId;
    private final UUID playerId;

    /** 商品 ID，对应 CommodityRegistry 中注册的商品 */
    private final String commodityId;

    private final OrderType type;

    /** 单价 */
    private final long price;

    /** 剩余数量（支持部分成交） */
    private int quantity;

    private final LocalDateTime timestamp;

    /** 新建订单，时间戳和 ID 自动设为当前时间/随机 UUID */
    public Order(
            UUID playerId,
            String commodityId,
            OrderType type,
            long price,
            int quantity
    ) {

        this.orderId = UUID.randomUUID();
        this.playerId = playerId;
        this.commodityId = commodityId;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    /** 从持久化数据恢复订单，使用指定时间戳和 ID */
    public Order(
            UUID orderId,
            UUID playerId,
            String commodityId,
            OrderType type,
            long price,
            int quantity,
            LocalDateTime timestamp
    ) {

        this.orderId = orderId;
        this.playerId = playerId;
        this.commodityId = commodityId;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public UUID getOrderId() {
        return orderId;
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

    /** 部分成交后减少剩余数量 */
    public void reduceQuantity(int amount) {
        if (amount > 0) {
            this.quantity -= amount;
        }
    }

    /** 设置剩余数量（匹配后剩余部分挂回订单簿时使用） */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
