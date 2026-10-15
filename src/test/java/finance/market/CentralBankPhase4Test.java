package finance.market;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.futures.FuturesClearingService;
import finance.futures.FuturesContract;
import finance.futures.FuturesContractStatus;
import finance.futures.FuturesMarketManager;
import finance.futures.FuturesMath;
import finance.futures.FuturesPosition;
import finance.futures.FuturesSettlementType;
import finance.futures.FuturesTrade;
import finance.futures.MarginAccount;
import finance.futures.MarginManager;
import finance.futures.MarginRiskStatus;
import finance.stock.StockMarketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4（央行跨市场联动）的回归测试。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>紧急贷款账本：记账 / 未偿 / 还款钳制 / 非法输入忽略；</li>
 *   <li>手动暂停状态：toggle / 市场键映射；</li>
 *   <li>贷款额与还款额的纯计算：缺口、上限钳制、健康线、溢出安全；</li>
 *   <li>dailyIntervention 集成：f002 不存在则跳过；风险跌破维持线时发放贷款并记账；
 *       总账户余额健康时扣回还款，余额不健康时不扣。</li>
 * </ul>
 *
 * <p>GUI 部分（股票/期货/管理后台）无 MC 客户端，只能做代码走读，见提交说明。</p>
 */
class CentralBankPhase4Test {

    private static final UUID MM = MarketMakerLedger.MASTER_UUID;
    private static final UUID F002 = MarketMakerLedger.FUTURES_MARGIN_UUID;
    private static final UUID CB = CentralBank.UUID;
    private static final MarketMakerLedger.SubMarket FUTURES = MarketMakerLedger.SubMarket.FUTURES;

    @BeforeEach
    void resetState() {
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        MarketMakerLedger.clearDirect();
        CentralBankLoanLedger.clearDirect();
        MarketMakerPauseState.clearDirect();
        MarginManager.clearDirect();
        FuturesMarketManager.clearDirect();
        FuturesClearingService.clearDirect();
        NpcMarketMaker.clearMarketPrices();
        CommodityRegistry.resetToDefaults();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        CandlestickService.clearDirect();
    }

    // ---------- 紧急贷款账本 ----------

    @Test
    void loanLedgerRecordAndRepay() {
        CentralBankLoanLedger.recordLoan(FUTURES, 1_000_000, 42);
        assertEquals(1_000_000, CentralBankLoanLedger.outstanding(FUTURES));

        assertEquals(600_000, CentralBankLoanLedger.repay(FUTURES, 600_000));
        assertEquals(400_000, CentralBankLoanLedger.outstanding(FUTURES));

        // 超额还款被钳制到未偿
        assertEquals(400_000, CentralBankLoanLedger.repay(FUTURES, 999_999_999));
        assertEquals(0, CentralBankLoanLedger.outstanding(FUTURES));

        var book = CentralBankLoanLedger.book(FUTURES);
        assertEquals(1_000_000, book.totalLoaned());
        assertEquals(1_000_000, book.totalRepaid());
        assertEquals(42, book.lastLoanDay());

        // 非法输入忽略
        CentralBankLoanLedger.recordLoan(FUTURES, -5, 1);
        CentralBankLoanLedger.recordLoan(FUTURES, 0, 1);
        CentralBankLoanLedger.recordLoan(null, 5, 1);
        assertEquals(0, CentralBankLoanLedger.repay(FUTURES, 10));
        assertEquals(0, CentralBankLoanLedger.repay(FUTURES, -10));
        assertEquals(0, CentralBankLoanLedger.repay(null, 10));
        assertEquals(0, CentralBankLoanLedger.outstanding(null));
    }

    @Test
    void loanLedgerRestoreAndClear() {
        CentralBankLoanLedger.restore(FUTURES, 300, 1_000, 700, 9);
        assertEquals(300, CentralBankLoanLedger.outstanding(FUTURES));
        // 非法恢复被忽略
        CentralBankLoanLedger.restore(FUTURES, -1, 1_000, 700, 9);
        assertEquals(300, CentralBankLoanLedger.outstanding(FUTURES));
        CentralBankLoanLedger.clearDirect();
        assertEquals(0, CentralBankLoanLedger.outstanding(FUTURES));
    }

    // ---------- 手动暂停状态 ----------

    @Test
    void pauseStateToggle() {
        assertFalse(MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.STOCK));
        assertTrue(MarketMakerPauseState.toggle(MarketMakerLedger.SubMarket.STOCK));
        assertTrue(MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.STOCK));
        assertFalse(MarketMakerPauseState.toggle(MarketMakerLedger.SubMarket.STOCK));
        assertFalse(MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.STOCK));
        MarketMakerPauseState.clearDirect();
        assertFalse(MarketMakerPauseState.isPaused(MarketMakerLedger.SubMarket.STOCK));
    }

    @Test
    void pauseStateMarketKeys() {
        assertEquals(MarketMakerLedger.SubMarket.COMMODITY, MarketMakerPauseState.fromKey("commodity"));
        assertEquals(MarketMakerLedger.SubMarket.STOCK, MarketMakerPauseState.fromKey("STOCK"));
        assertEquals(FUTURES, MarketMakerPauseState.fromKey(" futures "));
        assertNull(MarketMakerPauseState.fromKey("nope"));
        assertNull(MarketMakerPauseState.fromKey(null));
        assertEquals("commodity", MarketMakerPauseState.toKey(MarketMakerLedger.SubMarket.COMMODITY));
        assertEquals("stock", MarketMakerPauseState.toKey(MarketMakerLedger.SubMarket.STOCK));
        assertEquals("futures", MarketMakerPauseState.toKey(FUTURES));
    }

    // ---------- 纯计算：贷款额 ----------

    @Test
    void computeEmergencyLoanAmount() {
        // 缺口 500 → 贷 500
        assertEquals(500, CentralBank.computeEmergencyLoanAmount(1500, 2000, 10_000));
        // 缺口超上限 → 钳制
        assertEquals(10_000, CentralBank.computeEmergencyLoanAmount(0, 50_000, 10_000));
        // 无缺口 → 0
        assertEquals(0, CentralBank.computeEmergencyLoanAmount(3000, 2000, 10_000));
        assertEquals(0, CentralBank.computeEmergencyLoanAmount(2000, 2000, 10_000));
        // 上限为 0 → 0
        assertEquals(0, CentralBank.computeEmergencyLoanAmount(0, 2000, 0));
        // 保证金要求非法（-1 表示计算失败）→ 0
        assertEquals(0, CentralBank.computeEmergencyLoanAmount(0, -1, 10_000));
        // 权益为 Long.MIN_VALUE（穿仓计算错误）→ 溢出安全，按上限贷
        assertEquals(10_000, CentralBank.computeEmergencyLoanAmount(Long.MIN_VALUE, 100, 10_000));
    }

    // ---------- 纯计算：还款额 ----------

    @Test
    void computeRepayAmount() {
        // 余额 150 万，健康线 100 万，超出 50 万 ×20% = 10 万；未偿 100 万 → 还 10 万
        assertEquals(100_000, CentralBank.computeRepayAmount(1_500_000, 1_000_000, 1_000_000, 2_000));
        // 未偿更小 → 按未偿还
        assertEquals(50_000, CentralBank.computeRepayAmount(1_500_000, 1_000_000, 50_000, 2_000));
        // 余额不健康 → 不还
        assertEquals(0, CentralBank.computeRepayAmount(900_000, 1_000_000, 1_000_000, 2_000));
        assertEquals(0, CentralBank.computeRepayAmount(1_000_000, 1_000_000, 1_000_000, 2_000));
        // 比例/未偿为 0 → 不还
        assertEquals(0, CentralBank.computeRepayAmount(1_500_000, 1_000_000, 1_000_000, 0));
        assertEquals(0, CentralBank.computeRepayAmount(1_500_000, 1_000_000, 0, 2_000));
        // 溢出安全：巨额余额 ×100% → 按未偿还
        assertEquals(1_000_000, CentralBank.computeRepayAmount(Long.MAX_VALUE, 0, 1_000_000, 10_000));
    }

    // ---------- dailyIntervention 集成 ----------

    @Test
    void skipsFuturesCheckWhenNoMarginAccount() {
        CentralBank.dailyIntervention();
        assertEquals(0, CentralBankLoanLedger.outstanding(FUTURES));
    }

    @Test
    void issuesEmergencyLoanWhenBelowMaintenance() {
        UUID contractId = UUID.randomUUID();
        FuturesMarketManager.putContractDirect(new FuturesContract(contractId, "TST1", "iron", 10,
                0, 10, 20, FuturesSettlementType.CASH, FuturesContractStatus.TRADING));
        FuturesMarketManager.addTradeDirect(new FuturesTrade(UUID.randomUUID(), UUID.randomUUID(),
                contractId, 100, 5, 1, LocalDateTime.now()));
        long qty = 50;
        MarginManager.putPositionDirect(new FuturesPosition(F002, contractId, qty, 100, 100, 0));
        long cash = 1_000;
        MarginManager.putAccountDirect(new MarginAccount(F002, cash, 0, MarginRiskStatus.MARGIN_CALL, -1));

        long initialReq = FuturesMath.margin(100, 10, qty, FinanceConfig.futuresInitialMarginBps());
        long expectedLoan = Math.min(Math.max(0, initialReq - cash), FinanceConfig.cbMmEmergencyLoanCap());

        CentralBank.dailyIntervention();

        // 贷款记账
        assertEquals(expectedLoan, CentralBankLoanLedger.outstanding(FUTURES));
        // 保证金账户实际到账（央行→f002 现金→保证金，全程真实划转）
        assertEquals(cash + expectedLoan, MarginManager.accounts().get(F002).cashBalance());
        // 可审计：CENTRAL_BANK_MM_LOAN 记录
        assertTrue(AccountManager.getTransactions().stream().anyMatch(r ->
                r.getType() == TransactionType.CENTRAL_BANK_MM_LOAN && r.getAmount() == expectedLoan));
    }

    @Test
    void repaysLoanWhenMasterCashHealthy() {
        CentralBankLoanLedger.recordLoan(FUTURES, 1_000_000, 7);
        long target = NpcMarketMaker.getInitialNpcBalance();
        AccountManager.getOrCreateSystemAccount(MM).deposit((long) (target * 1.5));

        CentralBank.dailyIntervention();

        long surplus = (long) (target * 1.5) - target;
        long expectedRepay = Math.min(1_000_000, surplus * FinanceConfig.cbMmLoanRepaymentBps() / 10_000);
        assertEquals(1_000_000 - expectedRepay, CentralBankLoanLedger.outstanding(FUTURES));
        assertTrue(AccountManager.getTransactions().stream().anyMatch(r ->
                r.getType() == TransactionType.CENTRAL_BANK_MM_LOAN_REPAY && r.getAmount() == expectedRepay));
    }

    @Test
    void doesNotRepayWhenMasterCashUnhealthy() {
        CentralBankLoanLedger.recordLoan(FUTURES, 1_000_000, 7);
        // 做市商总账户余额为 0（不健康）→ 不扣还款
        // 注意 interveneMarketCash 会注资 (target-0)/3，仍低于健康线 target
        CentralBank.dailyIntervention();
        assertEquals(1_000_000, CentralBankLoanLedger.outstanding(FUTURES));
        assertTrue(AccountManager.getTransactions().stream().noneMatch(r ->
                r.getType() == TransactionType.CENTRAL_BANK_MM_LOAN_REPAY));
    }
}
