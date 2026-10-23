package finance.network;

import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.commodity.CommodityRegistry;
import finance.cycle.EconomyCycleService;
import finance.marketdata.MarketRankingService;
import finance.marketdata.OrderBookService;
import finance.marketdata.RecentTradeService;
import finance.stock.StockMarketManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public record CandlestickRequestPacket(long requestId, MarketInstrumentType type, String id, int limit) {

    public static boolean isAllowedLimit(int limit) {
        return limit == 30 || limit == 60 || limit == 120;
    }

    public static void encode(CandlestickRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.requestId);
        buffer.writeEnum(packet.type);
        int maxLength = packet.type == MarketInstrumentType.STOCK
                ? NetworkValidation.MAX_SYMBOL_LENGTH
                : NetworkValidation.MAX_COMMODITY_ID_LENGTH;
        buffer.writeUtf(packet.id, maxLength);
        buffer.writeVarInt(packet.limit);
    }

    public static CandlestickRequestPacket decode(FriendlyByteBuf buffer) {
        long requestId = buffer.readLong();
        MarketInstrumentType type = buffer.readEnum(MarketInstrumentType.class);
        int maxLength = type == MarketInstrumentType.STOCK
                ? NetworkValidation.MAX_SYMBOL_LENGTH
                : NetworkValidation.MAX_COMMODITY_ID_LENGTH;
        return new CandlestickRequestPacket(requestId, type, buffer.readUtf(maxLength), buffer.readVarInt());
    }

    public static void handle(CandlestickRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() -> {
            ServerPlayer player = contextSupplier.get().getSender();
            if (player == null || packet.requestId <= 0 || packet.type == null || !isAllowedLimit(packet.limit)) return;
            String normalized = packet.type == MarketInstrumentType.STOCK
                    ? NetworkValidation.normalizeSymbol(packet.id)
                    : packet.id == null ? "" : packet.id.trim().toLowerCase(java.util.Locale.ROOT);
            boolean valid;
            if (packet.type == MarketInstrumentType.STOCK)
                valid = NetworkValidation.isValidSymbol(normalized) && StockMarketManager.getStock(normalized) != null;
            else if (packet.type == MarketInstrumentType.COMMODITY)
                valid = NetworkValidation.isValidCommodityId(normalized)
                        && CommodityRegistry.getCommodity(normalized) != null;
            else if (packet.type == MarketInstrumentType.FUTURES) {
                java.util.UUID contractId = finance.util.Uuids.parseOrNull(normalized);
                valid = contractId != null && finance.futures.FuturesMarketManager.contract(contractId) != null;
            } else if (packet.type == MarketInstrumentType.BOND) {
                java.util.UUID bondId = finance.util.Uuids.parseOrNull(normalized);
                valid = bondId != null
                        && finance.debt.CorporateBondManager.bonds().containsKey(bondId);
            } else valid = false;
            if (!valid) return;
            String requestKey = packet.type.name() + ":" + normalized + ":" + packet.limit;
            if (!MarketDataRequestLimiter.allow(player.getUUID(), player.server.getTickCount(), requestKey)) return;
            FinancePacketHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CandlestickResponsePacket(
                            packet.requestId,
                            packet.type,
                            normalized,
                            packet.limit,
                            EconomyCycleService.currentMcDay(player.server),
                            CandlestickService.getBars(packet.type, normalized, packet.limit),
                            OrderBookService.snapshot(packet.type, normalized),
                            RecentTradeService.get(packet.type, normalized),
                            MarketRankingService.current()));
        });
        contextSupplier.get().setPacketHandled(true);
    }
}
