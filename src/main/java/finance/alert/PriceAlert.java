package finance.alert;

import java.time.LocalDateTime;
import java.util.UUID;

public class PriceAlert {

    private final UUID alertId;
    private final UUID playerId;
    private final PriceAlertType type;
    private final String targetId;
    private final PriceAlertDirection direction;
    private final long targetPrice;
    private final LocalDateTime createdAt;

    public PriceAlert(UUID playerId, PriceAlertType type, String targetId,
                      PriceAlertDirection direction, long targetPrice) {
        this(UUID.randomUUID(), playerId, type, targetId, direction, targetPrice, LocalDateTime.now());
    }

    public PriceAlert(UUID alertId, UUID playerId, PriceAlertType type, String targetId,
                      PriceAlertDirection direction, long targetPrice, LocalDateTime createdAt) {
        this.alertId = alertId;
        this.playerId = playerId;
        this.type = type;
        this.targetId = targetId == null ? "" : targetId;
        this.direction = direction;
        this.targetPrice = targetPrice;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public UUID getAlertId() { return alertId; }
    public UUID getPlayerId() { return playerId; }
    public PriceAlertType getType() { return type; }
    public String getTargetId() { return targetId; }
    public PriceAlertDirection getDirection() { return direction; }
    public long getTargetPrice() { return targetPrice; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean shouldTrigger(long currentPrice) {
        return switch (direction) {
            case ABOVE -> currentPrice >= targetPrice;
            case BELOW -> currentPrice <= targetPrice;
            case PREVIOUS_HIGH_BREAKOUT, PREVIOUS_LOW_BREAKDOWN -> false;
        };
    }
}
