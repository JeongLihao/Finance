package finance.cycle;

import finance.data.EconomySavedData;
import finance.index.MarketIndexService;

/**
 * Persistent, idempotent calendar for financial contracts. It processes every
 * missed MC day once, ignores clock rollback, and includes day zero.
 */
public final class FinancialCycleService {
    private static long lastProcessedDay = -1;
    private static long lastClosedMarketDay = -1;
    private static long observedMarketDay = -1;

    private FinancialCycleService() {
    }

    public static long lastProcessedDay() { return lastProcessedDay; }
    public static long lastClosedMarketDay() { return lastClosedMarketDay; }
    public static long observedMarketDay() { return observedMarketDay; }

    public static int advanceTo(long currentMcDay) {
        if (currentMcDay < 0 || currentMcDay <= lastProcessedDay) return 0;
        int processed = 0;
        for (long day = lastProcessedDay + 1; day <= currentMcDay; day++) {
            processDay(day);
            lastProcessedDay = day;
            processed++;
            if (day == Long.MAX_VALUE) break;
        }
        EconomySavedData.markDirty();
        return processed;
    }

    private static void processDay(long day) {
        // Bond and loan schedules are attached here by their managers. Keeping
        // this as the only daily entry point prevents restart double execution.
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.DEBT)){
            finance.debt.CorporateBondManager.processDay(day);
            finance.debt.CompanyLoanManager.processDay(day);
        }
        finance.fixedincome.CentralBankBillManager.processDay(day);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.FUTURES))finance.futures.FuturesMarketManager.processDay(day);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.BANKING))finance.bank.BankingManager.processDay(day);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.FUND))finance.fund.FundManager.processDay(day);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.INSURANCE))finance.insurance.InsuranceManager.processDay(day);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.STOCK))finance.governance.CorporateActionManager.processDay(day);
    }

    /** Closes a fully completed market day before the new day's price reset. */
    public static boolean closeMarketDay(long completedMcDay) {
        if (completedMcDay < 0 || completedMcDay <= lastClosedMarketDay) return false;
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.FUTURES))finance.futures.FuturesClearingService.closeDay(completedMcDay);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.HISTORY))MarketIndexService.closeDay(completedMcDay);
        if(finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.BANKING))finance.bank.BankingManager.closeDay(completedMcDay);
        lastClosedMarketDay = completedMcDay;
        EconomySavedData.markDirty();
        return true;
    }

    /** Detects normal boundaries and time jumps without closing the current day early. */
    public static boolean observeMarketDay(long currentMcDay) {
        if (currentMcDay < 0) return false;
        if (observedMarketDay < 0) {
            observedMarketDay = currentMcDay;
            EconomySavedData.markDirty();
            return false;
        }
        if (currentMcDay <= observedMarketDay) return false;
        long completedDay = observedMarketDay;
        observedMarketDay = currentMcDay;
        boolean closed = closeMarketDay(completedDay);
        EconomySavedData.markDirty();
        return closed;
    }

    public static void restoreLastProcessedDay(long day) { lastProcessedDay = Math.max(-1, day); }
    public static void restoreLastClosedMarketDay(long day) { lastClosedMarketDay = Math.max(-1, day); }
    public static void restoreObservedMarketDay(long day) { observedMarketDay = Math.max(-1, day); }
    public static void clearDirect() { lastProcessedDay = -1; lastClosedMarketDay = -1; observedMarketDay = -1; }
}
