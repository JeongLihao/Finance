package finance.data.serializer;

import finance.cycle.FinancialCycleService;
import finance.index.MarketIndexPoint;
import finance.index.MarketIndexService;
import finance.index.MarketIndexState;
import finance.policy.MonetaryPolicyService;
import finance.policy.PolicyRateRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Persistence for the financial calendar, policy rate and market indices. */
public final class FinancialDataSerializer {
    private static final int MAX_STATES = 64;

    private FinancialDataSerializer() {
    }

    public static void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("LastProcessedDay", FinancialCycleService.lastProcessedDay());
        tag.putLong("LastClosedMarketDay", FinancialCycleService.lastClosedMarketDay());
        tag.putLong("ObservedMarketDay", FinancialCycleService.observedMarketDay());
        tag.putInt("BenchmarkRateBps", MonetaryPolicyService.benchmarkRateBasisPoints());
        ListTag rates = new ListTag();
        for (PolicyRateRecord rate : MonetaryPolicyService.history()) {
            CompoundTag item = new CompoundTag();
            item.putLong("Day", rate.mcDay()); item.putInt("Bps", rate.basisPoints()); item.putString("Reason", rate.reason());
            rates.add(item);
        }
        tag.put("RateHistory", rates);
        ListTag states = new ListTag();
        int count = 0;
        for (MarketIndexState state : MarketIndexService.states().values()) {
            if (++count > MAX_STATES) break;
            CompoundTag item = new CompoundTag();
            item.putString("Id", state.id());
            item.putString("Divisor", state.divisor() == null ? "" : state.divisor().toPlainString());
            item.putString("Fingerprint", state.constituentFingerprint());
            ListTag points = new ListTag();
            for (MarketIndexPoint point : state.history()) {
                CompoundTag pointTag = new CompoundTag();
                pointTag.putLong("Day", point.mcDay()); pointTag.putDouble("Value", point.value()); points.add(pointTag);
            }
            item.put("Points", points); states.add(item);
        }
        tag.put("Indices", states);
        root.put("FinancialState", tag);
    }

    public static void load(CompoundTag root) {
        FinancialCycleService.clearDirect(); MonetaryPolicyService.clearDirect(); MarketIndexService.clearDirect();
        if (!root.contains("FinancialState", Tag.TAG_COMPOUND)) return;
        CompoundTag tag = root.getCompound("FinancialState");
        FinancialCycleService.restoreLastProcessedDay(tag.contains("LastProcessedDay") ? tag.getLong("LastProcessedDay") : -1);
        FinancialCycleService.restoreLastClosedMarketDay(tag.contains("LastClosedMarketDay") ? tag.getLong("LastClosedMarketDay") : -1);
        FinancialCycleService.restoreObservedMarketDay(tag.contains("ObservedMarketDay") ? tag.getLong("ObservedMarketDay") : -1);
        List<PolicyRateRecord> rates = new ArrayList<>();
        for (Tag raw : tag.getList("RateHistory", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) raw;
            rates.add(new PolicyRateRecord(item.getLong("Day"), item.getInt("Bps"), item.getString("Reason")));
        }
        MonetaryPolicyService.restore(tag.contains("BenchmarkRateBps") ? tag.getInt("BenchmarkRateBps")
                : finance.config.FinanceConfig.defaultBenchmarkRateBasisPoints(), rates);
        int count = 0;
        for (Tag raw : tag.getList("Indices", Tag.TAG_COMPOUND)) {
            if (++count > MAX_STATES) break;
            CompoundTag item = (CompoundTag) raw;
            String id = item.getString("Id");
            if (id.isBlank() || id.length() > 64) continue;
            try {
                String divisorText = item.getString("Divisor");
                BigDecimal divisor = divisorText.isBlank() ? null : new BigDecimal(divisorText);
                List<MarketIndexPoint> points = new ArrayList<>();
                for (Tag rawPoint : item.getList("Points", Tag.TAG_COMPOUND)) {
                    CompoundTag p = (CompoundTag) rawPoint;
                    points.add(new MarketIndexPoint(p.getLong("Day"), p.getDouble("Value")));
                }
                MarketIndexState state = new MarketIndexState(id);
                state.restore(divisor, item.getString("Fingerprint"), points);
                MarketIndexService.putDirect(state);
            } catch (RuntimeException ignored) { }
        }
    }
}
