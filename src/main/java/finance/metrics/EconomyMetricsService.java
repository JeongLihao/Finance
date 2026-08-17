package finance.metrics;

import finance.account.Account;
import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side aggregate economy metrics.  This is deliberately independent
 * from GUI data: trade services publish completed executions here, while the
 * daily cycle rolls the aggregates into persisted snapshots.
 */
public final class EconomyMetricsService {

    public static final int MAX_DAILY_SNAPSHOTS = 30;

    private static final List<DailySnapshot> DAILY_SNAPSHOTS = new ArrayList<>();
    private static long currentCommodityVolume;
    private static long currentStockVolume;
    private static long lastClosedMcDay = -1;

    private EconomyMetricsService() {
    }

    public static void recordCommodityTrade(long quantity) {
        if (quantity <= 0) {
            return;
        }
        currentCommodityVolume = saturatedAdd(currentCommodityVolume, quantity);
        EconomySavedData.markDirty();
    }

    public static void recordStockTrade(long quantity) {
        if (quantity <= 0) {
            return;
        }
        currentStockVolume = saturatedAdd(currentStockVolume, quantity);
        EconomySavedData.markDirty();
    }

    /** Saves the completed day before the price engines reset their day counters. */
    public static void closeDay(long completedMcDay) {
        if (completedMcDay < 0 || completedMcDay <= lastClosedMcDay) {
            return;
        }
        CurrentMetrics current = getCurrentMetrics();
        DAILY_SNAPSHOTS.add(new DailySnapshot(
                completedMcDay,
                current.playerCash(),
                current.playerFrozenFunds(),
                current.companyCash(),
                current.npcCash(),
                current.centralBankReserve(),
                current.totalMoney(),
                currentCommodityVolume,
                currentStockVolume,
                current.priceIndex(),
                current.bankruptcyRiskCompanies()));
        trimHistory();
        lastClosedMcDay = completedMcDay;
        currentCommodityVolume = 0;
        currentStockVolume = 0;
        EconomySavedData.markDirty();
    }

    public static CurrentMetrics getCurrentMetrics() {
        long playerCash = 0;
        long playerFrozen = 0;
        long npcCash = 0;
        long centralBankReserve = 0;

        for (Map.Entry<UUID, Account> entry : AccountManager.getAccounts().entrySet()) {
            long available = Math.max(0, entry.getValue().getBalance());
            long frozen = Math.max(0, entry.getValue().getFrozenBalance());
            UUID accountId = entry.getKey();
            if (NpcMarketMaker.NPC_UUID.equals(accountId)) {
                npcCash = saturatedAdd(npcCash, saturatedAdd(available, frozen));
            } else if (CentralBank.UUID.equals(accountId)) {
                centralBankReserve = saturatedAdd(centralBankReserve, saturatedAdd(available, frozen));
            } else {
                playerCash = saturatedAdd(playerCash, available);
                playerFrozen = saturatedAdd(playerFrozen, frozen);
            }
        }

        long companyCash = 0;
        int riskCompanies = 0;
        for (Company company : CompanyManager.getCompanies()) {
            companyCash = saturatedAdd(companyCash, Math.max(0, company.getCash()));
            if (company.isBankruptcyRisk()) {
                riskCompanies++;
            }
        }

        long total = saturatedAdd(playerCash, playerFrozen);
        total = saturatedAdd(total, companyCash);
        total = saturatedAdd(total, npcCash);
        total = saturatedAdd(total, centralBankReserve);
        return new CurrentMetrics(playerCash, playerFrozen, companyCash, npcCash,
                centralBankReserve, total, currentCommodityVolume, currentStockVolume,
                calculatePriceIndex(), riskCompanies, CentralBank.getLastInterventionSummary());
    }

    public static List<DailySnapshot> getDailySnapshots() {
        return List.copyOf(DAILY_SNAPSHOTS);
    }

    public static long getCurrentCommodityVolume() {
        return currentCommodityVolume;
    }

    public static long getCurrentStockVolume() {
        return currentStockVolume;
    }

    public static long getLastClosedMcDay() {
        return lastClosedMcDay;
    }

    public static void restore(long commodityVolume, long stockVolume, List<DailySnapshot> snapshots) {
        restore(commodityVolume, stockVolume, snapshots, -1);
    }

    public static void restore(long commodityVolume, long stockVolume, List<DailySnapshot> snapshots,
                               long persistedLastClosedMcDay) {
        currentCommodityVolume = Math.max(0, commodityVolume);
        currentStockVolume = Math.max(0, stockVolume);
        lastClosedMcDay = Math.max(-1, persistedLastClosedMcDay);
        DAILY_SNAPSHOTS.clear();
        if (snapshots != null) {
            for (DailySnapshot snapshot : snapshots) {
                if (snapshot != null && snapshot.mcDay() >= 0) {
                    DAILY_SNAPSHOTS.add(snapshot);
                }
            }
        }
        trimHistory();
        for (DailySnapshot snapshot : DAILY_SNAPSHOTS) {
            lastClosedMcDay = Math.max(lastClosedMcDay, snapshot.mcDay());
        }
    }

    public static void clearDirect() {
        currentCommodityVolume = 0;
        currentStockVolume = 0;
        lastClosedMcDay = -1;
        DAILY_SNAPSHOTS.clear();
    }

    private static double calculatePriceIndex() {
        double total = 0.0;
        int count = 0;
        for (MarketPrice price : NpcMarketMaker.getAllMarketPrices().values()) {
            if (price.getBasePrice() > 0) {
                total += (double) price.getMidPrice() / price.getBasePrice();
                count++;
            }
        }
        return count == 0 ? 100.0 : total / count * 100.0;
    }

    private static void trimHistory() {
        if (DAILY_SNAPSHOTS.size() > MAX_DAILY_SNAPSHOTS) {
            DAILY_SNAPSHOTS.subList(0, DAILY_SNAPSHOTS.size() - MAX_DAILY_SNAPSHOTS).clear();
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public record CurrentMetrics(long playerCash, long playerFrozenFunds, long companyCash,
                                 long npcCash, long centralBankReserve, long totalMoney,
                                 long dailyCommodityVolume, long dailyStockVolume,
                                 double priceIndex, int bankruptcyRiskCompanies,
                                 String centralBankSummary) {
    }

    public record DailySnapshot(long mcDay, long playerCash, long playerFrozenFunds,
                                long companyCash, long npcCash, long centralBankReserve,
                                long totalMoney, long commodityVolume, long stockVolume,
                                double priceIndex, int bankruptcyRiskCompanies) {
    }
}
