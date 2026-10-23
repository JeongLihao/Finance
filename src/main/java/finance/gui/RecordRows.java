package finance.gui;

import static finance.gui.MenuCodec.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** 记录、资产、提醒页的数据行（流水、资产汇总和明细、价格提醒）和编解码。从 {@link FinanceMenu} 拆出。 */
public final class RecordRows {

    static final int MAX_TRANSACTION_ROWS = 500;
    static final int MAX_ASSET_ROWS = 1_024;
    static final int MAX_ALERT_ROWS = 512;

    private RecordRows() {}

    public record TransactionRow(
            long timestamp, UUID playerId, String type, long amount, long quantity, String objectName) {}

    public record AssetSummary(
            long cash, long frozenCash, long commodityValue, long stockValue, long totalAsset, long todayProfit) {}

    public record AssetRow(
            String category,
            String name,
            long quantity,
            long value,
            double percent,
            long cost,
            long currentPrice,
            long floatingProfit) {}

    public record PriceAlertRow(UUID alertId, String type, String targetId, String direction, long targetPrice) {}

    static void writeTransactionRows(FriendlyByteBuf buffer, List<TransactionRow> rows) {
        List<TransactionRow> safe = tail(rows, MAX_TRANSACTION_ROWS);
        buffer.writeVarInt(safe.size());
        for (TransactionRow row : safe) {
            buffer.writeLong(row.timestamp());
            buffer.writeUUID(row.playerId());
            buffer.writeUtf(limitString(row.type(), 32), 32);
            buffer.writeLong(row.amount());
            buffer.writeLong(row.quantity());
            buffer.writeUtf(limitString(row.objectName(), 96), 96);
        }
    }

    static List<TransactionRow> readTransactionRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "transactions", MAX_TRANSACTION_ROWS);
        List<TransactionRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new TransactionRow(
                    buffer.readLong(),
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readUtf(96)));
        }
        return rows;
    }

    static void writeAssetSummary(FriendlyByteBuf buffer, AssetSummary summary) {
        AssetSummary safe = summary != null ? summary : new AssetSummary(0, 0, 0, 0, 0, 0);
        buffer.writeLong(safe.cash());
        buffer.writeLong(safe.frozenCash());
        buffer.writeLong(safe.commodityValue());
        buffer.writeLong(safe.stockValue());
        buffer.writeLong(safe.totalAsset());
        buffer.writeLong(safe.todayProfit());
    }

    static AssetSummary readAssetSummary(FriendlyByteBuf buffer) {
        return new AssetSummary(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong());
    }

    static void writeAssetRows(FriendlyByteBuf buffer, List<AssetRow> rows) {
        List<AssetRow> safe = bounded(rows, MAX_ASSET_ROWS);
        buffer.writeVarInt(safe.size());
        for (AssetRow row : safe) {
            buffer.writeUtf(limitString(row.category(), 16), 16);
            buffer.writeUtf(limitString(row.name(), 64), 64);
            buffer.writeLong(row.quantity());
            buffer.writeLong(row.value());
            buffer.writeDouble(row.percent());
            buffer.writeLong(row.cost());
            buffer.writeLong(row.currentPrice());
            buffer.writeLong(row.floatingProfit());
        }
    }

    static List<AssetRow> readAssetRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "asset rows", MAX_ASSET_ROWS);
        List<AssetRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new AssetRow(
                    buffer.readUtf(16),
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return rows;
    }

    static void writePriceAlertRows(FriendlyByteBuf buffer, List<PriceAlertRow> rows) {
        List<PriceAlertRow> safe = bounded(rows, MAX_ALERT_ROWS);
        buffer.writeVarInt(safe.size());
        for (PriceAlertRow row : safe) {
            buffer.writeUUID(row.alertId());
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeUtf(limitString(row.targetId(), 64), 64);
            buffer.writeUtf(limitString(row.direction(), 16), 16);
            buffer.writeLong(row.targetPrice());
        }
    }

    static List<PriceAlertRow> readPriceAlertRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "price alerts", MAX_ALERT_ROWS);
        List<PriceAlertRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new PriceAlertRow(
                    buffer.readUUID(), buffer.readUtf(16), buffer.readUtf(64), buffer.readUtf(16), buffer.readLong()));
        }
        return rows;
    }
}
