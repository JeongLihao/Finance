package finance.gui;

import static finance.gui.MenuCodec.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** 市场、交易页的数据行（商品行情、商品订单）和编解码。从 {@link FinanceMenu} 拆出。 */
public final class MarketRows {

    static final int MAX_MARKET_ROWS = 512;
    static final int MAX_ORDER_ROWS = 1_024;

    private MarketRows() {}

    /** hourBars：按游戏小时合成的 K 线，开高低收四个数一组（见 {@link finance.chart.HourBars}）。 */
    public record MarketRow(
            String commodityId,
            long midPrice,
            long bidPrice,
            long askPrice,
            double dayChange,
            int dayVolume,
            int marketStock,
            long dayHigh,
            long dayLow,
            List<Long> priceHistory,
            List<Long> hourBars) {
        public MarketRow(
                String commodityId,
                long midPrice,
                long bidPrice,
                long askPrice,
                double dayChange,
                int dayVolume,
                int marketStock,
                long dayHigh,
                long dayLow,
                List<Long> priceHistory) {
            this(
                    commodityId,
                    midPrice,
                    bidPrice,
                    askPrice,
                    dayChange,
                    dayVolume,
                    marketStock,
                    dayHigh,
                    dayLow,
                    priceHistory,
                    List.of());
        }
    }

    public record OrderRow(
            UUID orderId, String commodityId, String type, long price, int quantity, boolean ownedByPlayer) {}

    static void writeMarketData(FriendlyByteBuf buffer, List<MarketRow> list) {
        List<MarketRow> safe = bounded(list, MAX_MARKET_ROWS);
        buffer.writeVarInt(safe.size());
        for (MarketRow r : safe) {
            buffer.writeUtf(limitString(r.commodityId(), 64), 64);
            buffer.writeLong(r.midPrice());
            buffer.writeLong(r.bidPrice());
            buffer.writeLong(r.askPrice());
            buffer.writeDouble(r.dayChange());
            buffer.writeVarInt(r.dayVolume());
            buffer.writeVarInt(r.marketStock());
            buffer.writeLong(r.dayHigh());
            buffer.writeLong(r.dayLow());
            writeLongList(buffer, r.priceHistory());
            writeLongList(buffer, r.hourBars());
        }
    }

    static List<MarketRow> readMarketData(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "market rows", MAX_MARKET_ROWS);
        List<MarketRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MarketRow(
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readLong(),
                    readLongList(buffer),
                    readLongList(buffer)));
        }
        return list;
    }

    static void writeOrderRows(FriendlyByteBuf buffer, List<OrderRow> list) {
        List<OrderRow> safe = bounded(list, MAX_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (OrderRow r : safe) {
            buffer.writeUUID(r.orderId());
            buffer.writeUtf(limitString(r.commodityId(), 64), 64);
            buffer.writeUtf(limitString(r.type(), 16), 16);
            buffer.writeLong(r.price());
            buffer.writeVarInt(r.quantity());
            buffer.writeBoolean(r.ownedByPlayer());
        }
    }

    static List<OrderRow> readOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "commodity orders", MAX_ORDER_ROWS);
        List<OrderRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new OrderRow(
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        return list;
    }
}
