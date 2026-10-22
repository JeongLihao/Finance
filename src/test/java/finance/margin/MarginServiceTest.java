package finance.margin;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 融资融券（MarginService）回归测试 —— 覆盖设计文档 §9 的 12 个用例。
 */
class MarginServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000030001");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000030002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000030003");
    private static final String SYMBOL = "MRGN";

    private Stock stock;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(COMPANY_ID, "Margin Corp", CompanyType.RAW_MATERIALS, 500_000L, OWNER);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        // totalShares=10_000, floatShares=10_000, fairValue=100
        stock = new Stock(SYMBOL, "Margin Corp", COMPANY_ID, 10_000L, 10_000L, 0L, 100L, 100L);
        StockMarketManager.putStockDirect(stock);
        AccountManager.deposit(TRADER, 1_000_000L);
    }

    @AfterEach
    void clean() {
        EconomySavedData.resetRuntimeState();
    }

    private void depositMargin(long amount) {
        assertTrue(MarginService.depositCollateral(TRADER, amount).success());
    }

    // ---------- §9.1 融资买入 ----------

    @Test
    void marginBuyRespects2xLeverage() {
        depositMargin(10_000L);
        // 2x：10_000 保证金最多买 20_000（200 股 @100）
        MarginService.Result ok = MarginService.marginBuy(TRADER, SYMBOL, 200L);
        assertTrue(ok.success(), ok.message());
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        assertEquals(0L, acct.getCashCollateral()); // 首付 10_000 已扣
        assertEquals(10_000L, acct.getFinancedDebt()); // 券商垫付 10_000
        assertEquals(200L, acct.financedSharesOf(SYMBOL));
    }

    @Test
    void marginBuyRejectsBeyond2x() {
        depositMargin(10_000L);
        // 201 股 = 20_100，需首付 10_050 > 10_000，保证金不足
        MarginService.Result r = MarginService.marginBuy(TRADER, SYMBOL, 201L);
        assertFalse(r.success());
        assertEquals(10_000L, MarginPoolManager.getAccount(TRADER).getCashCollateral());
    }

    // ---------- §9.2 利息 ----------

    @Test
    void financeInterestAccruesDaily() {
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 债务 10_000，年利率 8.6% → 日息 = 10000*0.086/365 ≈ 2.35 → 2
        MarginService.processDay(1L);
        long interest = MarginPoolManager.getAccount(TRADER).getFinancedInterestOwed();
        assertEquals(2L, interest);
        // 同一天重复调用不重复计息
        MarginService.processDay(1L);
        assertEquals(2L, MarginPoolManager.getAccount(TRADER).getFinancedInterestOwed());
    }

    // ---------- §9.3 强平 ----------

    @Test
    void forcedLiquidationBelowMaintenanceRatio() {
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 股价跌至 60：权益 200×60=12_000，负债 10_002（含日息）→ 约 120% < 130%，触发强平
        // 强平所得 12_000 足以覆盖本息，负债清零
        setFairValue(60L);
        MarginService.processDay(2L);
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        assertEquals(0L, acct.getFinancedDebt(), "强平后负债应结清");
        assertEquals(0L, acct.financedSharesOf(SYMBOL), "强平后融资股应清零");
    }

    @Test
    void warningLineDoesNotLiquidate() {
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 股价跌至 70：权益 14_000，负债 10_000 → 140% < 150% 预警但 ≥130%，不强平
        setFairValue(70L);
        MarginService.processDay(2L);
        assertTrue(MarginService.isWarning(TRADER), "应处于预警状态");
        assertEquals(200L, MarginPoolManager.getAccount(TRADER).financedSharesOf(SYMBOL), "不应被强平");
    }

    // ---------- §9.4 融券闭环 ----------

    @Test
    void shortSellAndCoverRoundTrip() {
        depositMargin(10_000L);
        // 券池 = 10_000×5% = 500 股；单人上限 20% = 100 股
        MarginService.Result r = MarginService.shortSell(TRADER, SYMBOL, 100L);
        assertTrue(r.success(), r.message());
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        assertEquals(100L, acct.shortPositionOf(SYMBOL));
        assertEquals(10_000L, acct.shortProceedsOf(SYMBOL)); // 冻结

        // 股价跌到 80，买回：买回 100 股（1% 流通股）把价格推高 1.5% → 按 81 成交，成本 8_100，盈利 1_900
        long freeBefore = AccountManager.getBalance(TRADER);
        setFairValue(80L);
        MarginService.Result cover = MarginService.coverShort(TRADER, SYMBOL, 0L);
        assertTrue(cover.success(), cover.message());
        assertEquals(0L, acct.shortPositionOf(SYMBOL));
        assertEquals(500L, MarginPoolManager.poolAvailable(SYMBOL), "券池额度应全额归还");
        assertEquals(freeBefore + 1_900L, AccountManager.getBalance(TRADER), "盈利应解冻到自由账户");
    }

    // ---------- §9.5 反刷钱 ----------

    @Test
    void antiExploit_financedFundsCannotBeWithdrawn() {
        long freeBefore = AccountManager.getBalance(TRADER);
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 融资后自由资金 = 1_000_000 - 10_000（保证金）= 990_000，券商垫付的 10_000 不可见
        assertEquals(freeBefore - 10_000L, AccountManager.getBalance(TRADER));
        // 试图超额提现自由资金失败
        assertFalse(AccountManager.withdraw(TRADER, freeBefore));
    }

    @Test
    void antiExploit_shortProceedsFrozen() {
        long freeBefore = AccountManager.getBalance(TRADER);
        depositMargin(10_000L);
        assertTrue(MarginService.shortSell(TRADER, SYMBOL, 100L).success());
        // 卖空所得 10_000 冻结在信用账户，自由账户余额 = 存入前 - 保证金（无泄漏）
        assertEquals(freeBefore - 10_000L, AccountManager.getBalance(TRADER));
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        assertEquals(10_000L, acct.shortProceedsOf(SYMBOL));
        // 信用账户没有提现通道：只能通过 coverShort 解冻
        // （验证冻结资金不会泄漏到自由账户）
    }

    @Test
    void antiExploit_poolCapEnforced() {
        depositMargin(100_000L);
        // 券池总量 500 股，单人上限 100 股
        MarginService.Result overPool = MarginService.shortSell(TRADER, SYMBOL, 501L);
        assertFalse(overPool.success(), "超券池总量应被拒");
        MarginService.Result overPlayer = MarginService.shortSell(TRADER, SYMBOL, 101L);
        assertFalse(overPlayer.success(), "超单人上限应被拒");
        assertEquals(500L, MarginPoolManager.poolAvailable(SYMBOL), "券池应无借出");
    }

    @Test
    void antiExploit_badDebtBlocksNewPositions() {
        depositMargin(10_000L);
        assertTrue(MarginService.shortSell(TRADER, SYMBOL, 100L).success());
        // 股价暴涨到 300（轧空）：负债 30_000，权益 = 10_000保证金 + 10_000冻结 = 20_000
        // 保证金率 66% < 130% → 强平；缺口 10_000 记不良债务
        setFairValue(300L);
        MarginService.processDay(2L);
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        assertTrue(acct.getBadDebt() > 0, "穿仓缺口应记为不良债务，实际=" + acct.getBadDebt());
        // 有不良债务不可新开仓
        assertFalse(MarginService.marginBuy(TRADER, SYMBOL, 10L).success());
        assertFalse(MarginService.shortSell(TRADER, SYMBOL, 10L).success());
        // 还清后可恢复
        AccountManager.deposit(TRADER, 1_000_000L);
        assertTrue(MarginService.repayBadDebt(TRADER, acct.getBadDebt()).success());
        assertEquals(0L, MarginPoolManager.getAccount(TRADER).getBadDebt());
    }

    // ---------- §9.6 停牌 ----------

    @Test
    void haltedStockRejectsNewPositions() {
        depositMargin(10_000L);
        stock.setTradingHalted(true);
        assertFalse(MarginService.marginBuy(TRADER, SYMBOL, 10L).success());
        assertFalse(MarginService.shortSell(TRADER, SYMBOL, 10L).success());
        stock.setTradingHalted(false);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 10L).success());
    }

    @Test
    void haltedPositionsSkipLiquidation() {
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        setFairValue(50L); // 保证金率 100%，本应强平
        stock.setTradingHalted(true);
        MarginService.processDay(2L);
        // 停牌股跳过强平
        assertEquals(200L, MarginPoolManager.getAccount(TRADER).financedSharesOf(SYMBOL));
    }

    // ---------- §9.7 其他 ----------

    @Test
    void repayFinancingConvertsToRegularHolding() {
        depositMargin(10_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 还清本息
        MarginAccount acct = MarginPoolManager.getAccount(TRADER);
        long owed = acct.getFinancedDebt() + acct.getFinancedInterestOwed();
        assertTrue(MarginService.repayFinancing(TRADER, owed).success());
        assertEquals(0L, acct.financedSharesOf(SYMBOL));
        assertEquals(200L, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity(), "融资股应转为普通持仓");
    }

    @Test
    void collateralWithdrawBlockedBelowWarningLine() {
        depositMargin(12_000L);
        assertTrue(MarginService.marginBuy(TRADER, SYMBOL, 200L).success());
        // 买入后：保证金 2_000，负债 10_000
        // 股价 60 → 权益 2_000+12_000=14_000 → 140% < 150% 预警线，任何提取都会更低，应被拒
        setFairValue(60L);
        assertFalse(MarginService.withdrawCollateral(TRADER, 500L).success());
        // 补保证金后恢复：保证金 7_000 → 权益 19_000 → 190%，提取 1_000 → 180% 放行
        depositMargin(5_000L);
        assertTrue(MarginService.withdrawCollateral(TRADER, 1_000L).success());
    }

    // ---------- 工具 ----------

    /** 替换为不同 fairValue 的股票（测试用，模拟行情波动；持仓按 symbol 引用，不受影响） */
    private void setFairValue(long value) {
        Stock s = new Stock(SYMBOL, "Margin Corp", COMPANY_ID, 10_000L, 10_000L, 0L, value, value);
        s.setTradingHalted(stock.isTradingHalted());
        StockMarketManager.putStockDirect(s);
        stock = s;
    }
}
