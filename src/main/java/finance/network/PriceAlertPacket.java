package finance.network;

import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertManager;
import finance.alert.PriceAlertType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class PriceAlertPacket {

    public enum Action {
        ADD,
        CANCEL
    }

    private final Action action;
    private final PriceAlertType type;
    private final String targetId;
    private final PriceAlertDirection direction;
    private final long targetPrice;
    private final UUID alertId;

    public PriceAlertPacket(PriceAlertType type, String targetId,
                            PriceAlertDirection direction, long targetPrice) {
        this.action = Action.ADD;
        this.type = type;
        this.targetId = targetId;
        this.direction = direction;
        this.targetPrice = targetPrice;
        this.alertId = null;
    }

    public PriceAlertPacket(UUID alertId) {
        this.action = Action.CANCEL;
        this.type = null;
        this.targetId = "";
        this.direction = null;
        this.targetPrice = 0;
        this.alertId = alertId;
    }

    public static void encode(PriceAlertPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        if (packet.action == Action.ADD) {
            buffer.writeEnum(packet.type);
            int maxLength = packet.type == PriceAlertType.STOCK
                    ? NetworkValidation.MAX_SYMBOL_LENGTH
                    : NetworkValidation.MAX_COMMODITY_ID_LENGTH;
            buffer.writeUtf(packet.targetId, maxLength);
            buffer.writeEnum(packet.direction);
            buffer.writeLong(packet.targetPrice);
        } else {
            buffer.writeUUID(packet.alertId);
        }
    }

    public static PriceAlertPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        if (action == Action.ADD) {
            PriceAlertType type = buffer.readEnum(PriceAlertType.class);
            int maxLength = type == PriceAlertType.STOCK
                    ? NetworkValidation.MAX_SYMBOL_LENGTH
                    : NetworkValidation.MAX_COMMODITY_ID_LENGTH;
            return new PriceAlertPacket(
                    type,
                    buffer.readUtf(maxLength),
                    buffer.readEnum(PriceAlertDirection.class),
                    buffer.readLong());
        }
        return new PriceAlertPacket(buffer.readUUID());
    }

    public static void handle(PriceAlertPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (packet.action == Action.ADD) {
                if (packet.type == null || packet.direction == null || packet.targetPrice <= 0
                        || packet.targetId == null || packet.targetId.isBlank()) {
                    GuiFeedbackPacket.send(player, "提醒参数无效。");
                    return;
                }
                PriceAlertManager.AddResult result = PriceAlertManager.addAlert(
                        player.getUUID(),
                        packet.type,
                        packet.targetId,
                        packet.direction,
                        packet.targetPrice);
                GuiFeedbackPacket.send(player, result.message());
                return;
            }
            if (packet.alertId == null) {
                GuiFeedbackPacket.send(player, "提醒参数无效。");
                return;
            }
            boolean success = PriceAlertManager.cancelAlert(player.getUUID(), packet.alertId);
            GuiFeedbackPacket.send(player, success ? "提醒已取消。" : "取消失败，提醒不存在。");
        });
        ctx.get().setPacketHandled(true);
    }
}
