package finance.data.serializer;

import finance.chart.Candlestick;
import finance.chart.CandlestickSeries;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentKey;
import finance.chart.MarketInstrumentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

public final class CandlestickDataSerializer {

    private static final int MAX_SERIES = 4096;

    private CandlestickDataSerializer() {
    }

    public static void save(CompoundTag root) {
        ListTag seriesList = new ListTag();
        int count = 0;
        for (Map.Entry<MarketInstrumentKey, CandlestickSeries> entry
                : CandlestickService.getSeriesDirect().entrySet()) {
            if (count++ >= MAX_SERIES) break;
            CompoundTag seriesTag = new CompoundTag();
            seriesTag.putString("Type", entry.getKey().type().name());
            seriesTag.putString("Id", entry.getKey().id());
            ListTag bars = new ListTag();
            for (Candlestick bar : entry.getValue().getBars(CandlestickSeries.MAX_BARS)) {
                CompoundTag barTag = new CompoundTag();
                barTag.putLong("Day", bar.mcDay());
                barTag.putLong("Open", bar.open());
                barTag.putLong("High", bar.high());
                barTag.putLong("Low", bar.low());
                barTag.putLong("Close", bar.close());
                barTag.putLong("Volume", bar.volume());
                bars.add(barTag);
            }
            seriesTag.put("Bars", bars);
            seriesList.add(seriesTag);
        }
        root.put("Candlesticks", seriesList);
    }

    public static void load(CompoundTag root) {
        CandlestickService.clearDirect();
        if (!root.contains("Candlesticks")) return;
        ListTag list = root.getList("Candlesticks", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(MAX_SERIES, list.size()); index++) {
            CompoundTag seriesTag = list.getCompound(index);
            MarketInstrumentType type = NbtDataSupport.safeEnum(
                    MarketInstrumentType.class, seriesTag.getString("Type"), null);
            MarketInstrumentKey key = MarketInstrumentKey.tryCreate(type, seriesTag.getString("Id"));
            if (key == null) continue;
            CandlestickSeries series = new CandlestickSeries();
            ListTag bars = seriesTag.getList("Bars", Tag.TAG_COMPOUND);
            int start = Math.max(0, bars.size() - CandlestickSeries.MAX_BARS);
            for (int barIndex = start; barIndex < bars.size(); barIndex++) {
                CompoundTag barTag = bars.getCompound(barIndex);
                try {
                    series.addLoaded(new Candlestick(
                            barTag.getLong("Day"), barTag.getLong("Open"), barTag.getLong("High"),
                            barTag.getLong("Low"), barTag.getLong("Close"), barTag.getLong("Volume")));
                } catch (IllegalArgumentException ignored) {
                    // Skip corrupt bars without discarding the rest of the economy save.
                }
            }
            CandlestickService.putSeriesDirect(key, series);
        }
    }
}
