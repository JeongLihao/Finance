package finance.metrics;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.market.NpcMarketMaker;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class EconomyMetricsServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000002101");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void closesDayIntoSnapshotAndResetsOnlyDailyCounters() {
        EconomyMetricsService.recordCommodityTrade(12);
        EconomyMetricsService.recordStockTrade(7);

        EconomyMetricsService.closeDay(3);

        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, EconomyMetricsService.getCurrentStockVolume());
        assertEquals(1, EconomyMetricsService.getDailySnapshots().size());
        EconomyMetricsService.DailySnapshot snapshot = EconomyMetricsService.getDailySnapshots().get(0);
        assertEquals(3, snapshot.mcDay());
        assertEquals(12, snapshot.commodityVolume());
        assertEquals(7, snapshot.stockVolume());
    }

    @Test
    void persistsCurrentCountersAndDailyHistory() {
        EconomyMetricsService.recordCommodityTrade(9);
        EconomyMetricsService.recordStockTrade(4);
        EconomyMetricsService.closeDay(5);
        EconomyMetricsService.recordCommodityTrade(2);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);

        assertEquals(2, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, EconomyMetricsService.getCurrentStockVolume());
        assertEquals(1, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(5, EconomyMetricsService.getDailySnapshots().get(0).mcDay());
    }

    @Test
    void ignoresNonPositiveTradeQuantities() {
        EconomyMetricsService.recordCommodityTrade(0);
        EconomyMetricsService.recordCommodityTrade(-2);
        EconomyMetricsService.recordStockTrade(0);
        EconomyMetricsService.recordStockTrade(-3);

        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, EconomyMetricsService.getCurrentStockVolume());
    }

    @Test
    void saturatesTradeVolumeAtLongMaxValue() {
        EconomyMetricsService.recordCommodityTrade(Long.MAX_VALUE);
        EconomyMetricsService.recordCommodityTrade(1);
        EconomyMetricsService.recordStockTrade(Long.MAX_VALUE);
        EconomyMetricsService.recordStockTrade(1);

        assertEquals(Long.MAX_VALUE, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(Long.MAX_VALUE, EconomyMetricsService.getCurrentStockVolume());
    }

    @Test
    void keepsOnlyLatestThirtyDailySnapshots() {
        for (long day = 1; day <= 31; day++) {
            EconomyMetricsService.recordCommodityTrade(day);
            EconomyMetricsService.closeDay(day);
        }

        assertEquals(30, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(2, EconomyMetricsService.getDailySnapshots().get(0).mcDay());
        assertEquals(31, EconomyMetricsService.getDailySnapshots().get(29).mcDay());
    }

    @Test
    void restoreFiltersInvalidSnapshotsAndTrimsHistory() {
        List<EconomyMetricsService.DailySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(-1));
        for (long day = 1; day <= 31; day++) {
            snapshots.add(snapshot(day));
        }

        EconomyMetricsService.restore(-4, -5, snapshots);

        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, EconomyMetricsService.getCurrentStockVolume());
        assertEquals(30, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(2, EconomyMetricsService.getDailySnapshots().get(0).mcDay());
        assertEquals(31, EconomyMetricsService.getLastClosedMcDay());
    }

    @Test
    void oldSaveWithoutMetricsLoadsWithEmptyMetrics() {
        EconomyMetricsService.recordCommodityTrade(9);
        EconomyMetricsService.closeDay(2);

        EconomySavedData.load(new CompoundTag());

        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(-1, EconomyMetricsService.getLastClosedMcDay());
    }

    @Test
    void closeDayArchivesDayZeroAndIgnoresNegativeAndAlreadyCompletedDays() {
        EconomyMetricsService.recordCommodityTrade(7);
        EconomyMetricsService.closeDay(-1);
        EconomyMetricsService.closeDay(0);
        EconomyMetricsService.recordCommodityTrade(5);
        EconomyMetricsService.closeDay(0);
        EconomyMetricsService.closeDay(1);

        assertEquals(2, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(0, EconomyMetricsService.getDailySnapshots().get(0).mcDay());
        assertEquals(7, EconomyMetricsService.getDailySnapshots().get(0).commodityVolume());
        assertEquals(1, EconomyMetricsService.getDailySnapshots().get(1).mcDay());
        assertEquals(5, EconomyMetricsService.getDailySnapshots().get(1).commodityVolume());
        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
    }

    @Test
    void restartDoesNotArchiveDayZeroTwice() {
        EconomyMetricsService.recordCommodityTrade(4);
        EconomyMetricsService.closeDay(0);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);
        EconomyMetricsService.recordCommodityTrade(3);
        EconomyMetricsService.closeDay(0);

        assertEquals(1, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(4, EconomyMetricsService.getDailySnapshots().get(0).commodityVolume());
        assertEquals(3, EconomyMetricsService.getCurrentCommodityVolume());
    }

    @Test
    void restartPreservesInProgressVolumeAndPreventsDuplicateDayClose() {
        EconomyMetricsService.recordCommodityTrade(7);
        EconomyMetricsService.closeDay(4);
        EconomyMetricsService.recordCommodityTrade(3);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);

        EconomyMetricsService.closeDay(4);
        assertEquals(1, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(3, EconomyMetricsService.getCurrentCommodityVolume());

        EconomyMetricsService.closeDay(5);
        assertEquals(2, EconomyMetricsService.getDailySnapshots().size());
        assertEquals(3, EconomyMetricsService.getDailySnapshots().get(1).commodityVolume());
    }

    @Test
    void currentMetricsSeparatesPlayerNpcCentralBankAndCompanyCash() {
        AccountManager.getAccount(PLAYER_ID).setBalance(150);
        AccountManager.getAccount(PLAYER_ID).freezeFunds(50);
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(200);
        AccountManager.getAccount(CentralBank.UUID).setBalance(300);
        CompanyManager.registerDirect(new Company(
                COMPANY_ID, "Metrics Company", CompanyType.RAW_MATERIALS, 400, PLAYER_ID));

        EconomyMetricsService.CurrentMetrics metrics = EconomyMetricsService.getCurrentMetrics();

        assertEquals(100, metrics.playerCash());
        assertEquals(50, metrics.playerFrozenFunds());
        assertEquals(400, metrics.companyCash());
        assertEquals(200, metrics.npcCash());
        assertEquals(300, metrics.centralBankReserve());
        assertEquals(1_050, metrics.totalMoney());
    }

    @Test
    void priceIndexDefaultsToOneHundredWithoutMarketPrices() {
        assertEquals(100.0, EconomyMetricsService.getCurrentMetrics().priceIndex());
    }

    private static EconomyMetricsService.DailySnapshot snapshot(long day) {
        return new EconomyMetricsService.DailySnapshot(
                day, 0, 0, 0, 0, 0, 0, 0, 0, 100.0, 0);
    }
}
