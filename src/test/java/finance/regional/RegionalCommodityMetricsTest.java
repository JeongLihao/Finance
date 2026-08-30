package finance.regional;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.serializer.RegionalCommodityDataSerializer;
import finance.settlement.DemandStatus;
import finance.settlement.LocalDemand;
import finance.settlement.SettlementRecord;
import finance.settlement.SettlementStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionalCommodityMetricsTest {
    private UUID settlementId;
    private SettlementRecord settlement;

    @BeforeEach void setup() {
        RegionalCommodityMetricsManager.clearDirect();
        CommodityRegistry.register(new Commodity("regional_test", "minecraft:iron_ingot", "Regional Test",
                CommodityCategory.RAW_MATERIALS, 100));
        settlementId = UUID.randomUUID();
        settlement = new SettlementRecord(settlementId, "minecraft:overworld", BlockPos.ZERO,
                "Test Region", SettlementStatus.ACTIVE, -1, -1, "");
    }

    @AfterEach void cleanup() { RegionalCommodityMetricsManager.clearDirect(); }

    @Test void realDemandEventsCloseExactlyOnceAndDoNotExposeIdentity() {
        LocalDemand demand = demand(2, 40, 4_000, 3);
        RegionalCommodityMetricsService.recordDemandOpened(settlement, demand);
        RegionalCommodityMetricsService.recordDemandCompleted(settlement, demand, 2);
        assertTrue(RegionalCommodityMetricsService.closeDay(2));
        assertFalse(RegionalCommodityMetricsService.closeDay(2));
        RegionalCommoditySnapshot row = RegionalCommodityMetricsManager.latest(key());
        assertNotNull(row);
        assertEquals(1, row.demandOpened());
        assertEquals(1, row.demandFilled());
        assertEquals(40, row.quantityRequested());
        assertEquals(40, row.quantityDelivered());
        assertEquals(100, row.averagePaidPrice());
        assertEquals(10_000, row.onTimeDeliveryBps());
        assertTrue(row.localPremiumBps() >= 7_500 && row.localPremiumBps() <= 14_000);
    }

    @Test void expiredDemandRaisesShortageAndDailyPremiumMovementIsBounded() {
        LocalDemand first = demand(2, 100, 10_000, 2);
        RegionalCommodityMetricsService.recordDemandOpened(settlement, first);
        RegionalCommodityMetricsService.recordDemandExpired(settlement, first, 2);
        RegionalCommodityMetricsService.closeDay(2);
        RegionalCommoditySnapshot dayTwo = RegionalCommodityMetricsManager.latest(key());
        assertEquals(8_500, dayTwo.shortageScore());
        assertEquals(11_000, dayTwo.localPremiumBps());
    }

    @Test void historyAndLiveAccumulatorSurviveRoundTrip() {
        LocalDemand completed = demand(2, 20, 2_000, 3);
        RegionalCommodityMetricsService.recordDemandOpened(settlement, completed);
        RegionalCommodityMetricsService.recordDemandCompleted(settlement, completed, 2);
        RegionalCommodityMetricsService.closeDay(2);
        LocalDemand open = demand(3, 30, Long.MAX_VALUE, 5);
        RegionalCommodityMetricsService.recordDemandOpened(settlement, open);
        CompoundTag root = new CompoundTag();
        RegionalCommodityDataSerializer.save(root);
        RegionalCommodityMetricsManager.clearDirect();
        RegionalCommodityDataSerializer.load(root);
        assertEquals(1, RegionalCommodityMetricsManager.history(key()).size());
        assertEquals(1, RegionalCommodityMetricsManager.liveCopy().get(key()).opened());
        RegionalCommodityMetricsService.recordDemandExpired(settlement, open, 3);
        assertTrue(RegionalCommodityMetricsService.closeDay(3));
        assertEquals(2, RegionalCommodityMetricsManager.history(key()).size());
    }

    @Test void networkHistoryViewIsBoundedWithoutTruncatingStoredHistory() {
        UUID firstRegion = null;
        for (int keyIndex = 0; keyIndex < 12; keyIndex++) {
            UUID region = UUID.randomUUID();
            if (firstRegion == null) firstRegion = region;
            RegionalCommodityKey key = new RegionalCommodityKey("minecraft:overworld", region, "regional_test");
            for (int day = 0; day < 10; day++) {
                RegionalCommodityMetricsManager.restoreSnapshot(key, snapshot(day, keyIndex * 100 + day));
            }
        }
        List<RegionalCommodityMetricsManager.HistoryView> views =
                RegionalCommodityMetricsManager.recentHistories(5, 3);
        assertEquals(5, views.size());
        assertTrue(views.stream().allMatch(view -> view.rows().size() == 3));
        assertTrue(views.stream().allMatch(view -> view.rows().get(0).day() == 7));
        assertEquals(10, RegionalCommodityMetricsManager.history(views.get(0).key()).size(),
                "bounded transport must not prune canonical history");
        assertEquals(9, RegionalCommodityMetricsManager.maxSmoothedShortage(firstRegion));
    }

    private RegionalCommoditySnapshot snapshot(long day, int shortage) {
        int bounded = Math.min(10_000, shortage);
        return new RegionalCommoditySnapshot(day, 1, 1, 1, 1, 100, 100, 10_000,
                10_000, 0, bounded, bounded, RegionalSupplyPressure.BALANCED, true);
    }

    private RegionalCommodityKey key() {
        return new RegionalCommodityKey("minecraft:overworld", settlementId, "regional_test");
    }
    private LocalDemand demand(long created, int quantity, long reward, long deadline) {
        return new LocalDemand(UUID.randomUUID(), settlementId, "regional_test", "materials", quantity,
                reward, UUID.randomUUID(), created, deadline, DemandStatus.OPEN, null);
    }
}
