package finance.network;

import finance.stock.StockMarketManager;
import net.minecraft.network.FriendlyByteBuf;
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
        buffer.writeUtf(packet.symbol, NetworkValidation.MAX_SYMBOL_LENGTH);
        buffer.writeVarInt(packet.quantity);
    }

    public static StockTradePacket decode(FriendlyByteBuf buffer) {
        return new StockTradePacket(
                buffer.readEnum(ActionType.class),
                buffer.readUtf(NetworkValidation.MAX_SYMBOL_LENGTH),
                buffer.readVarInt());
    }

    public static void handle(StockTradePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.STOCK)) { GuiFeedbackPacket.send(player,"股票市场已因一致性问题暂停。"); return; }
            if (!MarketDataRequestLimiter.allow(player.getUUID(),player.server.getTickCount(),"stock-trade:"+packet.actionType)) { GuiFeedbackPacket.send(player,"操作过于频繁。"); return; }
            if (packet.actionType == null
                    || !NetworkValidation.isValidSymbol(packet.symbol)
                    || !NetworkValidation.isPositive(packet.quantity)) {
                GuiFeedbackPacket.send(player, "股票交易请求参数无效。");
                return;
            }
            String symbol = NetworkValidation.normalizeSymbol(packet.symbol);

            StockMarketManager.TradeResult result = switch (packet.actionType) {
                case BUY -> StockMarketManager.buy(player.getUUID(), symbol, packet.quantity);
                case SELL -> StockMarketManager.sell(player.getUUID(), symbol, packet.quantity);
            };
            if(result.success()&&packet.actionType==ActionType.BUY)finance.advancement.FinanceAdvancementTriggers.trigger(player,"public_company");
            GuiFeedbackPacket.send(player, result.message());
        });
        ctx.get().setPacketHandled(true);
    }
}
