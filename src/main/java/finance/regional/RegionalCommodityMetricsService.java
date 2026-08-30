package finance.regional;

import finance.diagnostic.ModuleHealthRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.settlement.LocalDemand;
import finance.settlement.SettlementRecord;

import java.math.BigInteger;
import java.util.List;

public final class RegionalCommodityMetricsService {
    private RegionalCommodityMetricsService() {}

    public static void recordDemandOpened(SettlementRecord settlement, LocalDemand demand) {
        var accumulator = accumulator(settlement, demand, demand.createdDay());
        if (accumulator != null) accumulator.opened(demand.quantity());
    }
    public static void recordDemandCompleted(SettlementRecord settlement, LocalDemand demand, long day) {
        var accumulator = accumulator(settlement, demand, day);
        if (accumulator != null) accumulator.filled(demand.quantity(), demand.reward(), day <= demand.deadlineDay());
    }
    public static void recordDemandExpired(SettlementRecord settlement, LocalDemand demand, long day) {
        var accumulator = accumulator(settlement, demand, day);
        if (accumulator != null) accumulator.noteExpired();
    }

    private static RegionalCommodityMetricsManager.DailyAccumulator accumulator(SettlementRecord settlement,
                                                                                 LocalDemand demand,long day) {
        if (settlement == null || demand == null || day < 0
                || !ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.REGIONAL_RISK)) return null;
        return RegionalCommodityMetricsManager.accumulator(new RegionalCommodityKey(settlement.dimensionId(),
                settlement.id(), demand.commodityId()), day);
    }

    public static synchronized boolean closeDay(long day) {
        if (day < 0 || day <= RegionalCommodityMetricsManager.lastClosedDay()
                || !ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.REGIONAL_RISK)) return false;
        for (var entry : RegionalCommodityMetricsManager.liveForDay(day)) {
            RegionalCommodityKey key = entry.getKey();
            var value = entry.getValue();
            MarketPrice market = NpcMarketMaker.getMarketPrice(key.commodityId());
            long global = market == null ? 0 : Math.max(0, market.getMidPrice());
            long average = value.delivered() <= 0 ? 0 : value.paid().divide(BigInteger.valueOf(value.delivered()))
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            int rawShortage = shortage(value);
            List<RegionalCommoditySnapshot> history = RegionalCommodityMetricsManager.history(key);
            int smooth = smoothed(history, rawShortage);
            int desiredPremium = clamp(7_500, 14_000, 8_000 + smooth * 6 / 10);
            int previous = history.isEmpty() ? 10_000 : history.get(history.size() - 1).localPremiumBps();
            int premium = clamp(previous - 1_000, previous + 1_000, desiredPremium);
            int deliveries = value.deliveredOnTime() + value.deliveredLate();
            int onTime = deliveries == 0 ? 0 : (int)Math.min(10_000L,
                    (long)value.deliveredOnTime() * 10_000L / deliveries);
            boolean reliable = global > 0 && average > 0;
            RegionalCommodityMetricsManager.append(key, new RegionalCommoditySnapshot(day, value.opened(),
                    value.filled(), value.requested(), value.delivered(), average, global, premium, onTime,
                    value.activeShipments(), rawShortage, smooth, RegionalSupplyPressure.fromScore(smooth), reliable));
        }
        RegionalCommodityMetricsManager.restoreLastClosedDay(day);
        return true;
    }

    public static int quoteMultiplierBps(SettlementRecord settlement, String commodityId) {
        if (settlement == null || commodityId == null) return 10_000;
        RegionalCommoditySnapshot latest = RegionalCommodityMetricsManager.latest(new RegionalCommodityKey(
                settlement.dimensionId(), settlement.id(), commodityId));
        return latest == null ? 10_000 : latest.localPremiumBps();
    }

    private static int shortage(RegionalCommodityMetricsManager.DailyAccumulator value) {
        long requested = Math.max(1, value.requested());
        long unmetBps = Math.max(0, requested - value.delivered()) * 10_000L / requested;
        long expiryBps = (long)value.expiredCount() * 10_000L / Math.max(1, value.opened() + value.expiredCount());
        return clamp(0, 10_000, (int)Math.min(10_000L, unmetBps * 7 / 10 + expiryBps * 3 / 10));
    }
    private static int smoothed(List<RegionalCommoditySnapshot> history,int today){long sum=today;int count=1;for(int i=history.size()-1;i>=0&&count<7;i--,count++)sum+=history.get(i).shortageScore();return(int)(sum/count);}
    private static int clamp(int min,int max,int value){return Math.max(min,Math.min(max,value));}
}
