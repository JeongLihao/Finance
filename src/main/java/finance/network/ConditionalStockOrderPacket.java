package finance.network;

import finance.stock.ConditionalStockOrderManager;
import finance.stock.ConditionalStockOrderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ConditionalStockOrderPacket {

    public enum Action {
        ADD,
        CANCEL
    }

    private final Action action;
    private final ConditionalStockOrderType type;
    private final String symbol;
    private final long triggerPrice;
    private final long quantity;
    private final UUID orderId;

    public ConditionalStockOrderPacket(ConditionalStockOrderType type, String symbol,
                                       long triggerPrice, long quantity) {
        this.action = Action.ADD;
        this.type = type;
        this.symbol = symbol;
        this.triggerPrice = triggerPrice;
        this.quantity = quantity;
        this.orderId = null;
    }

    public ConditionalStockOrderPacket(UUID orderId) {
        this.action = Action.CANCEL;
        this.type = null;
        this.symbol = "";
        this.triggerPrice = 0;
        this.quantity = 0;
        this.orderId = orderId;
    }

    public static void encode(ConditionalStockOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        if (packet.action == Action.ADD) {
            buffer.writeEnum(packet.type);
            buffer.writeUtf(packet.symbol, NetworkValidation.MAX_SYMBOL_LENGTH);
            buffer.writeLong(packet.triggerPrice);
            buffer.writeLong(packet.quantity);
        } else {
            buffer.writeUUID(packet.orderId);
        }
    }

    public static ConditionalStockOrderPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        if (action == Action.ADD) {
            return new ConditionalStockOrderPacket(
                    buffer.readEnum(ConditionalStockOrderType.class),
                    buffer.readUtf(NetworkValidation.MAX_SYMBOL_LENGTH),
                    buffer.readLong(),
                    buffer.readLong());
        }
        return new ConditionalStockOrderPacket(buffer.readUUID());
    }

    public static void handle(ConditionalStockOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (packet.action == null) {
                GuiFeedbackPacket.send(player, "条件委托请求无效。");
                return;
            }

            if (packet.action == Action.ADD) {
                if (packet.type == null
                        || !NetworkValidation.isValidSymbol(packet.symbol)
                        || !NetworkValidation.isPositive(packet.triggerPrice)
                        || !NetworkValidation.isPositive(packet.quantity)) {
                    GuiFeedbackPacket.send(player, "条件委托参数无效。");
                    return;
                }
                ConditionalStockOrderManager.OrderResult result =
                        ConditionalStockOrderManager.addOrder(
                                player.getUUID(),
                                NetworkValidation.normalizeSymbol(packet.symbol),
                                packet.type,
                                packet.triggerPrice,
                                packet.quantity);
                GuiFeedbackPacket.send(player, result.message());
                return;
            }

            if (packet.orderId == null) {
                GuiFeedbackPacket.send(player, "条件委托编号无效。");
                return;
            }
            boolean success = ConditionalStockOrderManager.cancelOrder(player.getUUID(), packet.orderId);
            GuiFeedbackPacket.send(player, success ? "条件委托已取消。" : "取消失败，请检查是否是你的条件委托。");
        });
        ctx.get().setPacketHandled(true);
    }
}
