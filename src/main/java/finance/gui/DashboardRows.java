package finance.gui;

import static finance.gui.MenuCodec.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** 经济看板页的数据行和编解码。从 {@link FinanceMenu} 拆出。 */
public final class DashboardRows {

    static final int DASHBOARD_TREND_LIMIT = 30;
    private static final int MAX_DECODED_DASHBOARD_TRENDS = 256;

    private DashboardRows() {}

    public record EconomyTrendRow(long mcDay, long commodityVolume, long stockVolume, double priceIndex) {}

    public record EconomyDashboardRow(
            long playerCash,
            long playerFrozenFunds,
            long companyCash,
            long npcCash,
            long centralBankReserve,
            long totalMoney,
            long dailyCommodityVolume,
            long dailyStockVolume,
            double priceIndex,
            int bankruptcyRiskCompanies,
            String centralBankSummary,
            List<EconomyTrendRow> trends) {}

    static void writeDashboard(FriendlyByteBuf buffer, EconomyDashboardRow row) {
        EconomyDashboardRow safe =
                row != null ? row : new EconomyDashboardRow(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, "", List.of());
        buffer.writeLong(safe.playerCash());
        buffer.writeLong(safe.playerFrozenFunds());
        buffer.writeLong(safe.companyCash());
        buffer.writeLong(safe.npcCash());
        buffer.writeLong(safe.centralBankReserve());
        buffer.writeLong(safe.totalMoney());
        buffer.writeLong(safe.dailyCommodityVolume());
        buffer.writeLong(safe.dailyStockVolume());
        buffer.writeDouble(safe.priceIndex());
        buffer.writeVarInt(safe.bankruptcyRiskCompanies());
        buffer.writeUtf(limitString(safe.centralBankSummary(), 128), 128);
        List<EconomyTrendRow> trends = safe.trends() == null ? List.of() : safe.trends();
        buffer.writeVarInt(Math.min(DASHBOARD_TREND_LIMIT, trends.size()));
        int start = Math.max(0, trends.size() - DASHBOARD_TREND_LIMIT);
        for (int index = start; index < trends.size(); index++) {
            EconomyTrendRow trend = trends.get(index);
            buffer.writeLong(trend.mcDay());
            buffer.writeLong(trend.commodityVolume());
            buffer.writeLong(trend.stockVolume());
            buffer.writeDouble(trend.priceIndex());
        }
    }

    static EconomyDashboardRow readDashboard(FriendlyByteBuf buffer) {
        return new EconomyDashboardRow(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readUtf(128),
                readDashboardTrends(buffer));
    }

    private static List<EconomyTrendRow> readDashboardTrends(FriendlyByteBuf buffer) {
        int encodedSize = buffer.readVarInt();
        if (encodedSize < 0 || encodedSize > MAX_DECODED_DASHBOARD_TRENDS) {
            throw new IllegalArgumentException("Invalid dashboard trend count: " + encodedSize);
        }
        int retainedSize = Math.min(DASHBOARD_TREND_LIMIT, encodedSize);
        List<EconomyTrendRow> trends = new ArrayList<>(retainedSize);
        for (int index = 0; index < encodedSize; index++) {
            EconomyTrendRow trend =
                    new EconomyTrendRow(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readDouble());
            if (index >= encodedSize - retainedSize) {
                trends.add(trend);
            }
        }
        return trends;
    }
}
