package finance.market;

import finance.data.EconomySavedData;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 央行对做市商的紧急贷款账本（Phase 4）。
 *
 * <p>设计（npc-market-making-design.md §5）：期货做市商保证金风险指标跌破维持线时，
 * 央行注资不是白给 —— 记为央行对做市商的"紧急贷款"。本期（Phase 4）只做
 * 贷款记账＋ {@link CentralBank#dailyIntervention} 定期从做市商总账户现金余额
 * 划扣还款（余额健康时才扣），不碰成交路径的价差划转（与 Phase 3 并行，避免冲突）。</p>
 *
 * <p>内存账本 + NBT 持久化（见 {@code MarketDataSerializer}），不动
 * {@code AccountManager} 主账。贷款本金实际划转走真实账户
 * （央行账户 → f002 现金账户 → f002 保证金账户），此处只记债权债务。</p>
 */
public final class CentralBankLoanLedger {

    /** 单市场贷款台账。 */
    public record LoanBook(long outstanding, long totalLoaned, long totalRepaid, long lastLoanDay) {
        public static LoanBook empty() {
            return new LoanBook(0, 0, 0, -1);
        }
    }

    private static final Map<MarketMakerLedger.SubMarket, LoanBook> BOOKS =
            new EnumMap<>(MarketMakerLedger.SubMarket.class);

    static {
        for (MarketMakerLedger.SubMarket market : MarketMakerLedger.SubMarket.values()) {
            BOOKS.put(market, LoanBook.empty());
        }
    }

    private CentralBankLoanLedger() {
    }

    /** 记一笔紧急贷款发放（饱和加法防溢出）。 */
    public static synchronized void recordLoan(MarketMakerLedger.SubMarket market, long amount, long day) {
        if (market == null || amount <= 0) {
            return;
        }
        LoanBook book = BOOKS.get(market);
        BOOKS.put(market, new LoanBook(
                saturatedAdd(book.outstanding(), amount),
                saturatedAdd(book.totalLoaned(), amount),
                book.totalRepaid(),
                Math.max(0, day)));
        EconomySavedData.markDirty();
    }

    /** 未偿贷款余额。 */
    public static synchronized long outstanding(MarketMakerLedger.SubMarket market) {
        if (market == null) {
            return 0;
        }
        return Math.max(0, BOOKS.get(market).outstanding());
    }

    /**
     * 扣回还款：未偿 = max(0, 未偿 - amount)，返回实际扣减额。
     * 调用方负责真实资金划转（做市商总账户 → 央行账户）。
     */
    public static synchronized long repay(MarketMakerLedger.SubMarket market, long amount) {
        if (market == null || amount <= 0) {
            return 0;
        }
        LoanBook book = BOOKS.get(market);
        long actual = Math.min(book.outstanding(), amount);
        if (actual <= 0) {
            return 0;
        }
        BOOKS.put(market, new LoanBook(
                book.outstanding() - actual,
                book.totalLoaned(),
                saturatedAdd(book.totalRepaid(), actual),
                book.lastLoanDay()));
        EconomySavedData.markDirty();
        return actual;
    }

    /** 取单市场台账（管理后台展示用）。 */
    public static synchronized LoanBook book(MarketMakerLedger.SubMarket market) {
        if (market == null) {
            return LoanBook.empty();
        }
        return BOOKS.get(market);
    }

    /** 序列化快照（MarketDataSerializer 用）。 */
    public static synchronized Map<MarketMakerLedger.SubMarket, LoanBook> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(BOOKS));
    }

    /** 反序列化恢复（非法数据忽略）。 */
    public static synchronized void restore(MarketMakerLedger.SubMarket market, long outstanding,
                                           long totalLoaned, long totalRepaid, long lastLoanDay) {
        if (market == null || outstanding < 0 || totalLoaned < 0 || totalRepaid < 0) {
            return;
        }
        BOOKS.put(market, new LoanBook(
                Math.min(outstanding, totalLoaned), totalLoaned, totalRepaid, lastLoanDay));
        EconomySavedData.markDirty();
    }

    /** 清空（世界切换/测试重置用）。 */
    public static synchronized void clearDirect() {
        for (MarketMakerLedger.SubMarket market : MarketMakerLedger.SubMarket.values()) {
            BOOKS.put(market, LoanBook.empty());
        }
    }

    private static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
