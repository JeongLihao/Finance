package finance.account;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionRecord {

    private final UUID from;

    private final UUID to;

    private final long amount;

    private final String type;

    private final LocalDateTime timestamp;

    public TransactionRecord(UUID from, UUID to, long amount, String type) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

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