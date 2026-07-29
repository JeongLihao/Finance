package finance.network;

import finance.gui.FinanceGuiOpener;
import finance.stock.StockMarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 股票订单操作数据包（P2）—— 支持挂买单、挂卖单、取消订单。
 */
public class StockOrderPacket {

    public enum ActionType {
        PLACE_BUY,      // 挂买单
        PLACE_SELL,     // 挂卖单
        CANCEL          // 取消订单
    }

    private final ActionType actionType;
    private final String symbol;
    private final long price;
    private final long quantity;
    private final java.util.UUID orderId; // 仅取消订单时用

    public StockOrderPacket(ActionType actionType, String symbol, long price, long quantity) {
        this.actionType = actionType;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.orderId = null;
    }

    public StockOrderPacket(ActionType actionType, java.util.UUID orderId) {
        this.actionType = actionType;
        this.orderId = orderId;
        this.symbol = null;
        this.price = 0;
        this.quantity = 0;
    }

    public static void encode(StockOrderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.actionType);
        if (packet.actionType == ActionType.CANCEL) {
            buffer.writeUUID(packet.orderId);
        } else {
            buffer.writeUtf(packet.symbol, 16);
            buffer.writeLong(packet.price);
            buffer.writeLong(packet.quantity);
        }
    }

    public static StockOrderPacket decode(FriendlyByteBuf buffer) {
        ActionType actionType = buffer.readEnum(ActionType.class);
        if (actionType == ActionType.CANCEL) {
            return new StockOrderPacket(actionType, buffer.readUUID());
        } else {
            return new StockOrderPacket(actionType, buffer.readUtf(16), buffer.readLong(), buffer.readLong());
        }
    }

    public static void handle(StockOrderPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            StockMarketManager.TradeResult result = null;

            switch (packet.actionType) {
                case PLACE_BUY:
                    result = StockMarketManager.buy(player.getUUID(), packet.symbol, packet.quantity);
                    break;
                case PLACE_SELL:
                    result = StockMarketManager.sell(player.getUUID(), packet.symbol, packet.quantity);
                    break;
                case CANCEL:
                    boolean success = StockMarketManager.cancelStockOrder(packet.orderId, player.getUUID());
                    result = new StockMarketManager.TradeResult(success,
                            success ? "订单已取消。" : "取消失败，请检查是否是你的订单。");
                    break;
            }

            if (result != null) {
                player.sendSystemMessage(Component.literal(result.message()));
                if (result.success()) {
                    FinanceGuiOpener.open(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
