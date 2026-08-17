package finance.data.serializer;

import finance.metrics.EconomyMetricsService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** Persists the in-progress daily counters and the rolling economy dashboard history. */
public final class MetricsDataSerializer {

    private static final String ROOT_KEY = "EconomyMetrics";

    private MetricsDataSerializer() {
    }

    public static void save(CompoundTag root) {
        CompoundTag metricsTag = new CompoundTag();
        metricsTag.putLong("CurrentCommodityVolume", EconomyMetricsService.getCurrentCommodityVolume());
        metricsTag.putLong("CurrentStockVolume", EconomyMetricsService.getCurrentStockVolume());
        metricsTag.putLong("LastClosedMcDay", EconomyMetricsService.getLastClosedMcDay());
        ListTag snapshotsTag = new ListTag();
        for (EconomyMetricsService.DailySnapshot snapshot : EconomyMetricsService.getDailySnapshots()) {
            CompoundTag snapshotTag = new CompoundTag();
            snapshotTag.putLong("McDay", snapshot.mcDay());
            snapshotTag.putLong("PlayerCash", snapshot.playerCash());
            snapshotTag.putLong("PlayerFrozenFunds", snapshot.playerFrozenFunds());
            snapshotTag.putLong("CompanyCash", snapshot.companyCash());
            snapshotTag.putLong("NpcCash", snapshot.npcCash());
            snapshotTag.putLong("CentralBankReserve", snapshot.centralBankReserve());
            snapshotTag.putLong("TotalMoney", snapshot.totalMoney());
            snapshotTag.putLong("CommodityVolume", snapshot.commodityVolume());
            snapshotTag.putLong("StockVolume", snapshot.stockVolume());
            snapshotTag.putDouble("PriceIndex", snapshot.priceIndex());
            snapshotTag.putInt("BankruptcyRiskCompanies", snapshot.bankruptcyRiskCompanies());
            snapshotsTag.add(snapshotTag);
        }
        metricsTag.put("DailySnapshots", snapshotsTag);
        root.put(ROOT_KEY, metricsTag);
    }

    public static void load(CompoundTag root) {
        if (!root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            EconomyMetricsService.clearDirect();
            return;
        }
        CompoundTag metricsTag = root.getCompound(ROOT_KEY);
        List<EconomyMetricsService.DailySnapshot> snapshots = new ArrayList<>();
        for (Tag rawTag : metricsTag.getList("DailySnapshots", Tag.TAG_COMPOUND)) {
            CompoundTag snapshotTag = (CompoundTag) rawTag;
            snapshots.add(new EconomyMetricsService.DailySnapshot(
                    snapshotTag.getLong("McDay"),
                    snapshotTag.getLong("PlayerCash"),
                    snapshotTag.getLong("PlayerFrozenFunds"),
                    snapshotTag.getLong("CompanyCash"),
                    snapshotTag.getLong("NpcCash"),
                    snapshotTag.getLong("CentralBankReserve"),
                    snapshotTag.getLong("TotalMoney"),
                    snapshotTag.getLong("CommodityVolume"),
                    snapshotTag.getLong("StockVolume"),
                    snapshotTag.getDouble("PriceIndex"),
                    snapshotTag.getInt("BankruptcyRiskCompanies")));
        }
        EconomyMetricsService.restore(
                metricsTag.getLong("CurrentCommodityVolume"),
                metricsTag.getLong("CurrentStockVolume"),
                snapshots,
                metricsTag.contains("LastClosedMcDay") ? metricsTag.getLong("LastClosedMcDay") : -1);
    }
}
