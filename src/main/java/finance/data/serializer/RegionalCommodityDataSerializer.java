package finance.data.serializer;

import finance.commodity.CommodityRegistry;
import finance.regional.RegionalCommodityKey;
import finance.regional.RegionalCommodityMetricsManager;
import finance.regional.RegionalCommoditySnapshot;
import finance.regional.RegionalSupplyPressure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.UUID;

public final class RegionalCommodityDataSerializer {
    public static final String ROOT = "RegionalCommodityMetrics";
    private static final int VERSION = 1;
    private RegionalCommodityDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putLong("LastClosedDay", RegionalCommodityMetricsManager.lastClosedDay());
        ListTag keys = new ListTag();
        RegionalCommodityMetricsManager.visitHistories((regionalKey, snapshots) -> {
            CompoundTag row = key(regionalKey);
            ListTag history = new ListTag();
            for (RegionalCommoditySnapshot snapshot : snapshots) history.add(snapshot(snapshot));
            row.put("History", history);
            keys.add(row);
        });
        data.put("Keys", keys);
        ListTag live = new ListTag();
        RegionalCommodityMetricsManager.visitLive((regionalKey, value) -> {
            CompoundTag row = key(regionalKey);
            row.putLong("Day", value.day()); row.putInt("Opened", value.opened());
            row.putInt("Filled", value.filled()); row.putInt("Expired", value.expiredCount());
            row.putInt("OnTime", value.deliveredOnTime()); row.putInt("Late", value.deliveredLate());
            row.putInt("Active", value.activeShipments()); row.putLong("Requested", value.requested());
            row.putLong("Delivered", value.delivered()); row.putString("Paid", value.paid().toString());
            live.add(row);
        });
        data.put("Live", live);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        RegionalCommodityMetricsManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        if (data.getInt("Version") != VERSION) return;
        ListTag keys = data.getList("Keys", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(keys.size(), RegionalCommodityMetricsManager.MAX_KEYS); i++) {
            try {
                CompoundTag row = keys.getCompound(i);
                RegionalCommodityKey key = readKey(row);
                if (key == null) continue;
                ListTag history = row.getList("History", Tag.TAG_COMPOUND);
                long previous = -1;
                for (int h = Math.max(0, history.size() - RegionalCommodityMetricsManager.MAX_HISTORY_DAYS);
                     h < history.size(); h++) {
                    RegionalCommoditySnapshot snapshot = readSnapshot(history.getCompound(h));
                    if (snapshot == null || snapshot.day() <= previous) continue;
                    RegionalCommodityMetricsManager.restoreSnapshot(key, snapshot);
                    previous = snapshot.day();
                }
            } catch (RuntimeException ignored) { }
        }
        ListTag live = data.getList("Live", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(live.size(), RegionalCommodityMetricsManager.MAX_KEYS); i++) {
            try {
                CompoundTag row = live.getCompound(i);
                RegionalCommodityKey key = readKey(row);
                if (key == null || row.getLong("Day") < 0) continue;
                var value = new RegionalCommodityMetricsManager.DailyAccumulator(row.getLong("Day"));
                value.restore(row.getInt("Opened"), row.getInt("Filled"), row.getInt("Expired"),
                        row.getInt("OnTime"), row.getInt("Late"), row.getInt("Active"),
                        row.getLong("Requested"), row.getLong("Delivered"), new BigInteger(row.getString("Paid")));
                RegionalCommodityMetricsManager.restoreLive(key, value);
            } catch (RuntimeException ignored) { }
        }
        RegionalCommodityMetricsManager.restoreLastClosedDay(data.getLong("LastClosedDay"));
    }

    private static CompoundTag key(RegionalCommodityKey key) {
        CompoundTag row = new CompoundTag(); row.putString("Dimension", key.dimensionId());
        row.putUUID("Region", key.regionId()); row.putString("Commodity", key.commodityId()); return row;
    }
    private static RegionalCommodityKey readKey(CompoundTag row) {
        UUID region = NbtDataSupport.readUuidOrNull(row, "Region"); String dimension = row.getString("Dimension");
        String commodity = row.getString("Commodity");
        if (region == null || dimension.isBlank() || dimension.length() > 128 || commodity.isBlank()
                || commodity.length() > 64 || CommodityRegistry.getCommodity(commodity) == null) return null;
        return new RegionalCommodityKey(dimension, region, commodity);
    }
    private static CompoundTag snapshot(RegionalCommoditySnapshot value) {
        CompoundTag row = new CompoundTag(); row.putLong("Day", value.day()); row.putInt("Opened", value.demandOpened());
        row.putInt("Filled", value.demandFilled()); row.putLong("Requested", value.quantityRequested());
        row.putLong("Delivered", value.quantityDelivered()); row.putLong("Average", value.averagePaidPrice());
        row.putLong("Global", value.globalMidPrice()); row.putInt("Premium", value.localPremiumBps());
        row.putInt("OnTime", value.onTimeDeliveryBps()); row.putInt("Active", value.activeShipmentCount());
        row.putInt("Shortage", value.shortageScore()); row.putInt("Smoothed", value.smoothedShortageScore());
        row.putString("Pressure", value.pressure().name()); row.putBoolean("Reliable", value.priceReliable()); return row;
    }
    private static RegionalCommoditySnapshot readSnapshot(CompoundTag row) {
        RegionalSupplyPressure pressure = NbtDataSupport.safeEnum(RegionalSupplyPressure.class,
                row.getString("Pressure"), null);
        try { return new RegionalCommoditySnapshot(row.getLong("Day"), row.getInt("Opened"), row.getInt("Filled"),
                row.getLong("Requested"), row.getLong("Delivered"), row.getLong("Average"), row.getLong("Global"),
                row.getInt("Premium"), row.getInt("OnTime"), row.getInt("Active"), row.getInt("Shortage"),
                row.getInt("Smoothed"), pressure, row.getBoolean("Reliable")); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
