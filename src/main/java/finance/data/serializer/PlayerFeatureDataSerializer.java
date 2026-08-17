package finance.data.serializer;

import finance.alert.PriceAlert;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertManager;
import finance.alert.PriceAlertType;
import finance.stock.ConditionalStockOrder;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.ConditionalStockOrderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Persists price alerts and conditional stock orders owned by players. */
public final class PlayerFeatureDataSerializer {

    private PlayerFeatureDataSerializer() {
    }

    public static void save(CompoundTag root) {
        ListTag alertsTag = new ListTag();
        for (PriceAlert alert : PriceAlertManager.getAlerts()) {
            CompoundTag alertTag = new CompoundTag();
            alertTag.putUUID("AlertId", alert.getAlertId());
            alertTag.putUUID("PlayerUUID", alert.getPlayerId());
            alertTag.putString("Type", alert.getType().name());
            alertTag.putString("TargetId", alert.getTargetId());
            alertTag.putString("Direction", alert.getDirection().name());
            alertTag.putLong("TargetPrice", alert.getTargetPrice());
            alertTag.putLong("CreatedAt", alert.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            alertsTag.add(alertTag);
        }
        root.put("PriceAlerts", alertsTag);

        ListTag conditionalOrdersTag = new ListTag();
        for (ConditionalStockOrder order : ConditionalStockOrderManager.getOrders()) {
            CompoundTag orderTag = new CompoundTag();
            orderTag.putUUID("OrderId", order.getOrderId());
            orderTag.putUUID("PlayerUUID", order.getPlayerId());
            orderTag.putString("Symbol", order.getSymbol());
            orderTag.putString("Type", order.getType().name());
            orderTag.putLong("TriggerPrice", order.getTriggerPrice());
            orderTag.putLong("Quantity", order.getQuantity());
            orderTag.putLong("CreatedAt", order.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            conditionalOrdersTag.add(orderTag);
        }
        root.put("ConditionalStockOrders", conditionalOrdersTag);
    }

    public static void load(CompoundTag root) {
        loadAlerts(root);
        loadConditionalStockOrders(root);
    }

    private static void loadAlerts(CompoundTag root) {
        if (!root.contains("PriceAlerts")) {
            return;
        }
        for (Tag rawTag : root.getList("PriceAlerts", Tag.TAG_COMPOUND)) {
            CompoundTag alertTag = (CompoundTag) rawTag;
            UUID alertId = NbtDataSupport.readUuidOrNull(alertTag, "AlertId");
            UUID playerId = NbtDataSupport.readUuidOrNull(alertTag, "PlayerUUID");
            PriceAlertType type = NbtDataSupport.safeEnum(
                    PriceAlertType.class, alertTag.getString("Type"), null);
            PriceAlertDirection direction = NbtDataSupport.safeEnum(
                    PriceAlertDirection.class, alertTag.getString("Direction"), null);
            if (alertId == null || playerId == null || type == null || direction == null) {
                continue;
            }
            PriceAlertManager.addAlertDirect(new PriceAlert(alertId, playerId, type,
                    alertTag.getString("TargetId"), direction, alertTag.getLong("TargetPrice"),
                    LocalDateTime.ofEpochSecond(alertTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
        }
    }

    private static void loadConditionalStockOrders(CompoundTag root) {
        if (!root.contains("ConditionalStockOrders")) {
            return;
        }
        for (Tag rawTag : root.getList("ConditionalStockOrders", Tag.TAG_COMPOUND)) {
            CompoundTag orderTag = (CompoundTag) rawTag;
            UUID orderId = NbtDataSupport.readUuidOrNull(orderTag, "OrderId");
            UUID playerId = NbtDataSupport.readUuidOrNull(orderTag, "PlayerUUID");
            ConditionalStockOrderType type = NbtDataSupport.safeEnum(
                    ConditionalStockOrderType.class, orderTag.getString("Type"), null);
            if (orderId == null || playerId == null || type == null) {
                continue;
            }
            ConditionalStockOrderManager.addOrderDirect(new ConditionalStockOrder(orderId, playerId,
                    orderTag.getString("Symbol"), type, orderTag.getLong("TriggerPrice"),
                    orderTag.getLong("Quantity"),
                    LocalDateTime.ofEpochSecond(orderTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
        }
    }
}
