package finance.account;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易记录 —— 记录每笔转账或市场交易的流水。
 */
public class TransactionRecord {

    /** 付款方 UUID */
    private final UUID from;

    /** 收款方 UUID */
    private final UUID to;

    private final long amount;

    private final TransactionType type;

    private final LocalDateTime timestamp;

    /** 主要操作玩家 UUID，用于 GUI 权限过滤。 */
    private final UUID playerId;

    /** 交易对象名称：商品 ID、股票代码、公司名或订单说明。 */
    private final String objectName;

    /** 交易数量。没有数量概念的操作用 0。 */
    private final long quantity;

    /** 新建交易记录，时间戳自动设为当前时间 */
    public TransactionRecord(UUID from, UUID to, long amount, TransactionType type) {
        this(from, to, amount, type, LocalDateTime.now());
    }

    /** 从持久化数据恢复交易记录，使用指定时间戳 */
    public TransactionRecord(
            UUID from, UUID to, long amount, TransactionType type,
            LocalDateTime timestamp
    ) {
        this(from, to, amount, type, timestamp, inferPlayerId(from, to), "", 0);
    }

    public TransactionRecord(
            UUID from, UUID to, long amount, TransactionType type,
            UUID playerId, String objectName, long quantity
    ) {
        this(from, to, amount, type, LocalDateTime.now(), playerId, objectName, quantity);
    }

    public TransactionRecord(
            UUID from, UUID to, long amount, TransactionType type,
            LocalDateTime timestamp, UUID playerId, String objectName, long quantity
    ) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.playerId = playerId != null ? playerId : inferPlayerId(from, to);
        this.objectName = objectName == null ? "" : objectName;
        this.quantity = Math.max(0, quantity);
    }

    public UUID getFrom() {
        return from;
    }

    public UUID getTo() {
        return to;
    }

    public long getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getObjectName() {
        return objectName;
    }

    public long getQuantity() {
        return quantity;
    }

    private static UUID inferPlayerId(UUID from, UUID to) {
        UUID system = new UUID(0L, 0L);
        if (from != null && !from.equals(system)) {
            return from;
        }
        if (to != null && !to.equals(system)) {
            return to;
        }
        return from != null ? from : to;
    }
}
