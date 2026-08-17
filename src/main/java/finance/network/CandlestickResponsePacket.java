package finance.network;

import finance.chart.Candlestick;
import finance.chart.MarketInstrumentKey;
import finance.chart.MarketInstrumentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import finance.marketdata.OrderBookLevel;
import finance.marketdata.OrderBookSnapshot;
import finance.marketdata.RecentTradeEntry;
import finance.marketdata.TradeDirection;
import java.time.LocalDateTime;
import finance.marketdata.MarketRankingEntry;
import finance.marketdata.MarketRankingSnapshot;

public record CandlestickResponsePacket(long requestId, MarketInstrumentType type, String id, int limit,
                                        long serverCurrentMcDay, boolean latestBarComplete,
                                        List<Candlestick> bars, OrderBookSnapshot orderBook,
                                        List<RecentTradeEntry> recentTrades, MarketRankingSnapshot rankings) {

    public static final int MAX_BARS_PER_PACKET = 128;

    public CandlestickResponsePacket {
        if (type == null || MarketInstrumentKey.tryCreate(type, id) == null) {
            throw new IllegalArgumentException("Invalid candlestick response key");
        }
        if (requestId <= 0 || !CandlestickRequestPacket.isAllowedLimit(limit) || serverCurrentMcDay < 0) {
            throw new IllegalArgumentException("Invalid candlestick response metadata");
        }
        List<Candlestick> source = bars == null ? List.of() : bars;
        int start = Math.max(0, source.size() - Math.min(MAX_BARS_PER_PACKET, limit));
        bars = List.copyOf(source.subList(start, source.size()));
        OrderBookSnapshot sourceBook = orderBook == null ? new OrderBookSnapshot(List.of(), List.of()) : orderBook;
        orderBook = new OrderBookSnapshot(
                sourceBook.bids().subList(0, Math.min(5, sourceBook.bids().size())),
                sourceBook.asks().subList(0, Math.min(5, sourceBook.asks().size())));
        recentTrades = recentTrades == null ? List.of()
                : List.copyOf(recentTrades.subList(0, Math.min(20, recentTrades.size())));
        rankings = rankings == null ? emptyRankings() : rankings;
    }

    public CandlestickResponsePacket(long requestId, MarketInstrumentType type, String id, int limit,
                                     long serverCurrentMcDay, List<Candlestick> bars) {
        this(requestId, type, id, limit, serverCurrentMcDay,
                bars == null || bars.isEmpty() || bars.get(bars.size() - 1).mcDay() < serverCurrentMcDay, bars,
                new OrderBookSnapshot(List.of(), List.of()), List.of(), emptyRankings());
    }

    public CandlestickResponsePacket(long requestId, MarketInstrumentType type, String id, int limit,
                                     long serverCurrentMcDay, List<Candlestick> bars,
                                     OrderBookSnapshot orderBook, List<RecentTradeEntry> recentTrades) {
        this(requestId, type, id, limit, serverCurrentMcDay,
                bars == null || bars.isEmpty() || bars.get(bars.size() - 1).mcDay() < serverCurrentMcDay,
                bars, orderBook, recentTrades, emptyRankings());
    }

    public CandlestickResponsePacket(long requestId, MarketInstrumentType type, String id, int limit,
                                     long serverCurrentMcDay, List<Candlestick> bars,
                                     OrderBookSnapshot orderBook, List<RecentTradeEntry> recentTrades,
                                     MarketRankingSnapshot rankings) {
        this(requestId, type, id, limit, serverCurrentMcDay,
                bars == null || bars.isEmpty() || bars.get(bars.size() - 1).mcDay() < serverCurrentMcDay,
                bars, orderBook, recentTrades, rankings);
    }

    public static void encode(CandlestickResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.requestId);
        buffer.writeEnum(packet.type);
        buffer.writeUtf(packet.id, MarketInstrumentKey.MAX_ID_LENGTH);
        buffer.writeVarInt(packet.limit);
        buffer.writeLong(packet.serverCurrentMcDay);
        buffer.writeBoolean(packet.latestBarComplete);
        buffer.writeVarInt(packet.bars.size());
        for (Candlestick bar : packet.bars) {
            buffer.writeLong(bar.mcDay());
            buffer.writeLong(bar.open());
            buffer.writeLong(bar.high());
            buffer.writeLong(bar.low());
            buffer.writeLong(bar.close());
            buffer.writeLong(bar.volume());
        }
        writeLevels(buffer, packet.orderBook.bids());
        writeLevels(buffer, packet.orderBook.asks());
        buffer.writeVarInt(packet.recentTrades.size());
        for (RecentTradeEntry trade : packet.recentTrades) {
            buffer.writeLong(trade.mcDay());
            buffer.writeLong(trade.price());
            buffer.writeLong(trade.quantity());
            buffer.writeUtf(trade.timestamp().toString(), 32);
            buffer.writeEnum(trade.direction());
        }
        writeRanking(buffer, packet.rankings.commodityGainers());
        writeRanking(buffer, packet.rankings.commodityLosers());
        writeRanking(buffer, packet.rankings.stockGainers());
        writeRanking(buffer, packet.rankings.stockLosers());
        writeRanking(buffer, packet.rankings.commodityVolumeLeaders());
        writeRanking(buffer, packet.rankings.stockVolumeLeaders());
        writeRanking(buffer, packet.rankings.unusualVolume());
    }

    public static CandlestickResponsePacket decode(FriendlyByteBuf buffer) {
        long requestId = buffer.readLong();
        MarketInstrumentType type = buffer.readEnum(MarketInstrumentType.class);
        String id = buffer.readUtf(MarketInstrumentKey.MAX_ID_LENGTH);
        int limit = buffer.readVarInt();
        long serverCurrentMcDay = buffer.readLong();
        boolean latestBarComplete = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_BARS_PER_PACKET) {
            throw new IllegalArgumentException("Candlestick packet exceeds bar limit");
        }
        List<Candlestick> bars = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bars.add(new Candlestick(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        List<OrderBookLevel> bids = readLevels(buffer);
        List<OrderBookLevel> asks = readLevels(buffer);
        int tradeCount = buffer.readVarInt();
        if (tradeCount < 0 || tradeCount > 20) throw new IllegalArgumentException("Recent trade limit exceeded");
        List<RecentTradeEntry> recentTrades = new ArrayList<>(tradeCount);
        for (int index = 0; index < tradeCount; index++) {
            recentTrades.add(new RecentTradeEntry(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    LocalDateTime.parse(buffer.readUtf(32)), buffer.readEnum(TradeDirection.class)));
        }
        MarketRankingSnapshot rankings = new MarketRankingSnapshot(
                readRanking(buffer), readRanking(buffer), readRanking(buffer), readRanking(buffer),
                readRanking(buffer), readRanking(buffer), readRanking(buffer));
        return new CandlestickResponsePacket(requestId, type, id, limit,
                serverCurrentMcDay, latestBarComplete, bars,
                new OrderBookSnapshot(bids, asks), recentTrades, rankings);
    }

    public static void handle(CandlestickResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                finance.client.chart.CandlestickClientCache.accept(packet.requestId, packet.type, packet.id,
                        packet.limit, packet.serverCurrentMcDay, packet.latestBarComplete, packet.bars,
                        packet.orderBook, packet.recentTrades, packet.rankings)));
        contextSupplier.get().setPacketHandled(true);
    }

    private static void writeLevels(FriendlyByteBuf buffer, List<OrderBookLevel> levels) {
        buffer.writeVarInt(levels.size());
        for (OrderBookLevel level : levels) {
            buffer.writeLong(level.price());
            buffer.writeLong(level.quantity());
        }
    }

    private static List<OrderBookLevel> readLevels(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 5) throw new IllegalArgumentException("Order-book depth exceeded");
        List<OrderBookLevel> levels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) levels.add(new OrderBookLevel(buffer.readLong(), buffer.readLong()));
        return levels;
    }

    private static void writeRanking(FriendlyByteBuf buffer, List<MarketRankingEntry> entries) {
        int size = Math.min(5, entries.size());
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            MarketRankingEntry entry = entries.get(index);
            buffer.writeEnum(entry.type());
            buffer.writeUtf(entry.id(), MarketInstrumentKey.MAX_ID_LENGTH);
            buffer.writeDouble(entry.changePercent());
            buffer.writeLong(entry.volume());
            buffer.writeDouble(entry.volumeRatio());
        }
    }

    private static List<MarketRankingEntry> readRanking(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 5) throw new IllegalArgumentException("Ranking limit exceeded");
        List<MarketRankingEntry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(new MarketRankingEntry(
                buffer.readEnum(MarketInstrumentType.class), buffer.readUtf(MarketInstrumentKey.MAX_ID_LENGTH),
                buffer.readDouble(), buffer.readLong(), buffer.readDouble()));
        return result;
    }

    private static MarketRankingSnapshot emptyRankings() {
        return new MarketRankingSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }
}
