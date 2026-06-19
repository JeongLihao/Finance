package finance.network;

import finance.gui.FinanceGuiOpener;
import finance.stock.StockMarketManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 股票买卖操作包。
 */
public class StockTradePacket {

    public enum ActionType {
        BUY, SELL
    }

    private final ActionType actionType;
    private final String symbol;
    private final int quantity;

    public StockTradePacket(ActionType actionType, String symbol, int quantity) {
        this.actionType = actionType;
        this.symbol = symbol;
        this.quantity = quantity;
    }

    public static void encode(StockTradePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.actionType);
        buffer.writeUtf(packet.symbol, 16);
        buffer.writeVarInt(packet.quantity);
    }

    public static StockTradePacket decode(FriendlyByteBuf buffer) {
        return new StockTradePacket(
                buffer.readEnum(ActionType.class),
                buffer.readUtf(16),
                buffer.readVarInt());
    }

    public static void handle(StockTradePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            StockMarketManager.TradeResult result = switch (packet.actionType) {
                case BUY -> StockMarketManager.buy(player.getUUID(), packet.symbol, packet.quantity);
                case SELL -> StockMarketManager.sell(player.getUUID(), packet.symbol, packet.quantity);
            };
            player.sendSystemMessage(Component.literal(result.message()));
            if (result.success()) {
                FinanceGuiOpener.open(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
