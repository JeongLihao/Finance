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

    /** 交易类型：TRANSFER（玩家转账）或 MARKET_TRADE（市场成交） */
    private final String type;

    private final LocalDateTime timestamp;

    /** 新建交易记录，时间戳自动设为当前时间 */
    public TransactionRecord(UUID from, UUID to, long amount, String type) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    /** 从持久化数据恢复交易记录，使用指定时间戳 */
    public TransactionRecord(
            UUID from, UUID to, long amount, String type,
            LocalDateTime timestamp
    ) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp;
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

    public String getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}