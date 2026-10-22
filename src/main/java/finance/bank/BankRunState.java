package finance.bank;

import java.util.UUID;

/**
 * 单家银行的挤兑运行时状态（内存态，可持久化到 BankingDataSerializer 的 BankRuns 节）。
 * 所有资金仍以账本与客户账户为准；这里只保存恐慌/排队等统计量，不进账本。
 */
public final class BankRunState {
    private final UUID bankId;
    private int panic;
    private long lastPanicDay = -1;
    private long dailyWithdrawn;
    private long budgetDay = -1;
    private long budgetUsed;
    private long queued;
    private int illiquidDays;
    private long rumorCooldownDay = -1;
    private int peakPanic;
    private long cumulativeOutflow;
    private long lastRumorDay = -1;
    private String lastRumorText = "";
    private long npcLoanBook;

    public BankRunState(UUID bankId) {
        if (bankId == null) throw new IllegalArgumentException();
        this.bankId = bankId;
    }

    public UUID bankId() {
        return bankId;
    }

    public int panic() {
        return panic;
    }

    public long lastPanicDay() {
        return lastPanicDay;
    }

    public long dailyWithdrawn() {
        return dailyWithdrawn;
    }

    public long budgetDay() {
        return budgetDay;
    }

    public long budgetUsed() {
        return budgetUsed;
    }

    public long queued() {
        return queued;
    }

    public int illiquidDays() {
        return illiquidDays;
    }

    public long rumorCooldownDay() {
        return rumorCooldownDay;
    }

    public int peakPanic() {
        return peakPanic;
    }

    public long cumulativeOutflow() {
        return cumulativeOutflow;
    }

    public long lastRumorDay() {
        return lastRumorDay;
    }

    public String lastRumorText() {
        return lastRumorText;
    }

    public long npcLoanBook() {
        return npcLoanBook;
    }

    void setPanic(int v, long day) {
        panic = Math.max(0, Math.min(100, v));
        lastPanicDay = day;
        if (panic > peakPanic) peakPanic = panic;
    }

    void addWithdrawn(long v) {
        if (v > 0) dailyWithdrawn = safeAdd(dailyWithdrawn, v);
    }

    void resetDailyWithdrawn() {
        dailyWithdrawn = 0;
    }

    void addBudgetUsed(long v) {
        budgetUsed = v > 0 ? safeAdd(budgetUsed, v) : Math.max(0, budgetUsed + v);
    }

    void resetBudget(long day) {
        budgetDay = day;
        budgetUsed = 0;
    }

    void addQueued(long v) {
        if (v > 0) queued = safeAdd(queued, v);
    }

    void reduceQueued(long v) {
        if (v > 0) queued = Math.max(0, queued - v);
    }

    void setIlliquidDays(int v) {
        illiquidDays = Math.max(0, v);
    }

    void setRumorCooldownDay(long day) {
        rumorCooldownDay = day;
    }

    void addOutflow(long v) {
        if (v > 0) cumulativeOutflow = safeAdd(cumulativeOutflow, v);
    }

    void setLastRumor(long day, String text) {
        lastRumorDay = day;
        lastRumorText = text == null ? "" : text.substring(0, Math.min(256, text.length()));
    }

    void setNpcLoanBook(long v) {
        npcLoanBook = Math.max(0, v);
    }

    void reduceNpcLoanBook(long v) {
        if (v > 0) npcLoanBook = Math.max(0, npcLoanBook - v);
    }

    /** 持久化恢复用。 */
    void restore(
            int panic,
            long lastPanicDay,
            long queued,
            int illiquidDays,
            long rumorCooldownDay,
            int peakPanic,
            long cumulativeOutflow,
            long lastRumorDay,
            String lastRumorText,
            long budgetDay,
            long budgetUsed,
            long npcLoanBook) {
        this.panic = Math.max(0, Math.min(100, panic));
        this.lastPanicDay = Math.max(-1, lastPanicDay);
        this.queued = Math.max(0, queued);
        this.illiquidDays = Math.max(0, illiquidDays);
        this.rumorCooldownDay = rumorCooldownDay;
        this.peakPanic = Math.max(this.peakPanic, Math.max(0, Math.min(100, peakPanic)));
        this.cumulativeOutflow = Math.max(0, cumulativeOutflow);
        this.lastRumorDay = Math.max(-1, lastRumorDay);
        setLastRumor(this.lastRumorDay, lastRumorText);
        this.budgetDay = budgetDay;
        this.budgetUsed = Math.max(0, budgetUsed);
        this.npcLoanBook = Math.max(0, npcLoanBook);
    }

    private static long safeAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
