package finance.gui;

import static finance.gui.MenuCodec.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** 股票页的数据行（股票、持仓、股票委托、条件单）和编解码。从 {@link FinanceMenu} 拆出。 */
public final class StockRows {

    static final int MAX_STOCK_ROWS = 512;
    static final int MAX_HOLDING_ROWS = 512;
    static final int MAX_STOCK_ORDER_ROWS = 1_024;
    static final int MAX_CONDITIONAL_ORDER_ROWS = 512;

    private StockRows() {}

    public record StockRow(
            String symbol,
            String name,
            long lastPrice,
            double dayChange,
            long dayVolume,
            long availableShares,
            long fairValue,
            long dayHigh,
            long dayLow,
            List<Long> priceHistory,
            long expectedDividendPerShare,
            long lastDividendPerShare,
            long lastDividendTotal,
            // ---- Phase 4：做市商状态（服务端随菜单快照下发，客户端只展示） ----
            /** 做市商买价（无报价时为 0）。 */
            long mmBid,
            /** 做市商卖价（无报价时为 0）。 */
            long mmAsk,
            /** 做市商是否熔断暂停（Phase 2 熔断）。 */
            boolean mmHalted,
            /** 库存水位：0=充足，1=紧张，2=熔断。 */
            int mmLevel,
            /** 按游戏小时合成的 K 线，开高低收四个数一组。 */
            List<Long> hourBars) {
        public StockRow(
                String symbol,
                String name,
                long lastPrice,
                double dayChange,
                long dayVolume,
                long availableShares,
                long fairValue,
                long dayHigh,
                long dayLow,
                List<Long> priceHistory,
                long expectedDividendPerShare,
                long lastDividendPerShare,
                long lastDividendTotal,
                long mmBid,
                long mmAsk,
                boolean mmHalted,
                int mmLevel) {
            this(
                    symbol,
                    name,
                    lastPrice,
                    dayChange,
                    dayVolume,
                    availableShares,
                    fairValue,
                    dayHigh,
                    dayLow,
                    priceHistory,
                    expectedDividendPerShare,
                    lastDividendPerShare,
                    lastDividendTotal,
                    mmBid,
                    mmAsk,
                    mmHalted,
                    mmLevel,
                    List.of());
        }
    }

    public record StockHoldingRow(String symbol, long quantity, long averageCost) {}

    public record StockOrderRow(
            UUID orderId, String symbol, String type, long price, int quantity, boolean ownedByPlayer) {}

    public record ConditionalStockOrderRow(
            UUID orderId, String symbol, String type, long triggerPrice, long quantity) {}

    static void writeStockRows(FriendlyByteBuf buffer, List<StockRow> rows) {
        List<StockRow> safe = bounded(rows, MAX_STOCK_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockRow row : safe) {
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.name(), 64), 64);
            buffer.writeLong(row.lastPrice());
            buffer.writeDouble(row.dayChange());
            buffer.writeLong(row.dayVolume());
            buffer.writeLong(row.availableShares());
            buffer.writeLong(row.fairValue());
            buffer.writeLong(row.dayHigh());
            buffer.writeLong(row.dayLow());
            writeLongList(buffer, row.priceHistory());
            buffer.writeLong(row.expectedDividendPerShare());
            buffer.writeLong(row.lastDividendPerShare());
            buffer.writeLong(row.lastDividendTotal());
            buffer.writeLong(row.mmBid());
            buffer.writeLong(row.mmAsk());
            buffer.writeBoolean(row.mmHalted());
            buffer.writeVarInt(row.mmLevel());
            writeLongList(buffer, row.hourBars());
        }
    }

    static List<StockRow> readStockRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stocks", MAX_STOCK_ROWS);
        List<StockRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockRow(
                    buffer.readUtf(16),
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    readLongList(buffer),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    readLongList(buffer)));
        }
        return rows;
    }

    static void writeStockHoldingRows(FriendlyByteBuf buffer, List<StockHoldingRow> rows) {
        List<StockHoldingRow> safe = bounded(rows, MAX_HOLDING_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockHoldingRow row : safe) {
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeLong(row.quantity());
            buffer.writeLong(row.averageCost());
        }
    }

    static List<StockHoldingRow> readStockHoldingRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stock holdings", MAX_HOLDING_ROWS);
        List<StockHoldingRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockHoldingRow(buffer.readUtf(16), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }

    static void writeStockOrderRows(FriendlyByteBuf buffer, List<StockOrderRow> rows) {
        List<StockOrderRow> safe = bounded(rows, MAX_STOCK_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockOrderRow row : safe) {
            buffer.writeUUID(row.orderId());
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeLong(row.price());
            buffer.writeVarInt(row.quantity());
            buffer.writeBoolean(row.ownedByPlayer());
        }
    }

    static List<StockOrderRow> readStockOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stock orders", MAX_STOCK_ORDER_ROWS);
        List<StockOrderRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockOrderRow(
                    buffer.readUUID(),
                    buffer.readUtf(16),
                    buffer.readUtf(16),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        return rows;
    }

    static void writeConditionalStockOrderRows(FriendlyByteBuf buffer, List<ConditionalStockOrderRow> rows) {
        List<ConditionalStockOrderRow> safe = bounded(rows, MAX_CONDITIONAL_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (ConditionalStockOrderRow row : safe) {
            buffer.writeUUID(row.orderId());
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeLong(row.triggerPrice());
            buffer.writeLong(row.quantity());
        }
    }

    static List<ConditionalStockOrderRow> readConditionalStockOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "conditional stock orders", MAX_CONDITIONAL_ORDER_ROWS);
        List<ConditionalStockOrderRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new ConditionalStockOrderRow(
                    buffer.readUUID(), buffer.readUtf(16), buffer.readUtf(16), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }
}
