package finance.futures;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.commodity.*;
import finance.data.EconomySavedData;
import finance.feedback.FeedbackSeverity;
import finance.feedback.WorldEconomyFeedbackService;
import finance.feedback.WorldFeedbackType;
import finance.market.MarketMakerLedger;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.policy.MonetaryPolicyService;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 期货做市商攻击视角回归用例（Phase 3）。
 * 纯逻辑测试：gradlew 无网络跑不起来是预期的，跑测试用纯 JUnit。
 */
class FuturesMarketMakerTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000006101"),
            player2 = UUID.fromString("00000000-0000-0000-0000-000000006102");
    private final UUID MM = FuturesMarketMaker.MARKET_MAKER_ID;
    private FuturesContract contract;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity("mm_test", "Test", CommodityCategory.RAW_MATERIALS, 100));
        var r = FuturesMarketManager.createStandard("mm_test", 0, 10, 365);
        assertTrue(r.success());
        contract = FuturesMarketManager.contract(r.id());
        AccountManager.deposit(player, 100_000);
        AccountManager.deposit(player2, 100_000);
        assertTrue(MarginManager.deposit(player, 50_000));
        assertTrue(MarginManager.deposit(player2, 50_000));
    }

    /** 做市商注资来源：做市商总账户（nil UUID）。 */
    private void fundMaster() {
        AccountManager.deposit(MarketMakerLedger.MASTER_UUID, 1_000_000);
    }

    @Test
    void quoteAnchorsOnTheoreticalFuturesPrice() {
        fundMaster();
        // 基准利率默认 500bps=5%，剩余 365 天：F = 100 × (1 + 0.05) = 105
        // spread 1.5%：bid = 105 × 0.985 = 103.425 → 103，ask = 105 × 1.015 = 106.575 → 107
        long[] quote = FuturesMarketMaker.getQuote(contract.id());
        assertNotNull(quote);
        assertArrayEquals(new long[]{103, 107}, quote);
    }

    @Test
    void quoteDegeneratesToSpotWhenRateIsZero() {
        assertTrue(MonetaryPolicyService.setBenchmarkRate(0, 0, "test"));
        long[] quote = FuturesMarketMaker.getQuote(contract.id());
        assertNotNull(quote);
        // 利率为 0 时退化：F = 现货中间价 = 100 → bid 98.5→98，ask 101.5→102
        assertArrayEquals(new long[]{98, 102}, quote);
    }

    @Test
    void noQuoteWithoutSpotPriceOrContract() {
        // 无现货价时不报价
        NpcMarketMaker.getAllMarketPrices().put("mm_test", new MarketPrice("mm_test", 0, 0.05));
        assertNull(FuturesMarketMaker.getQuote(contract.id()));
        // 未知合约 / 无净持仓
        assertNull(FuturesMarketMaker.getQuote(UUID.randomUUID()));
        assertEquals(0, FuturesMarketMaker.getNetPosition(UUID.randomUUID()));
    }

    @Test
    void marketMakerFillsAsCounterpartyAndCapEnforcedBothWays() {
        fundMaster();
        // 玩家买 10 手：无对手盘，做市商按 ask=107 吃下 1 手（冷启动上限 1），其余挂单
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 200, 10).success());
        assertEquals(1, FuturesMarketManager.trades().size());
        FuturesTrade t = FuturesMarketManager.trades().get(0);
        assertEquals(player, t.buyerId());
        assertEquals(MM, t.sellerId());
        assertEquals(107, t.price());
        assertEquals(1, t.quantity());
        assertEquals(-1, FuturesMarketMaker.getNetPosition(contract.id()));
        assertEquals(-1, MarginManager.findPosition(MM, contract.id()).signedQuantity());
        assertEquals(9, FuturesMarketManager.orders().stream().filter(o -> o.playerId().equals(player))
                .mapToLong(FuturesOrder::remainingQuantity).sum());
        // 做市商成交记了 MARKET_MAKER_SELL 审计记录（管理后台可审计做市商 P&L）
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(r -> r.getType() == TransactionType.MARKET_MAKER_SELL && MM.equals(r.getPlayerId())));

        // 再买 10 手：净持仓已达上限，加仓方向被拒绝，全部挂单
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 200, 10).success());
        assertEquals(1, FuturesMarketManager.trades().size());
        assertEquals(-1, FuturesMarketMaker.getNetPosition(contract.id()));

        // 玩家卖 5 手：减仓方向，做市商接 2 手（拉回零点再反向至上限），剩余挂单
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.SELL, 50, 5).success());
        assertEquals(2, FuturesMarketManager.trades().size());
        assertEquals(1, FuturesMarketMaker.getNetPosition(contract.id()));
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(r -> r.getType() == TransactionType.MARKET_MAKER_BUY && MM.equals(r.getPlayerId())));
    }

    @Test
    void fillRefusedWhenMarketMakerMarginInsufficient() {
        // 抽干做市商总账户（含 getAccount 自动生成的 1000 初始资金）→ 做市商保证金账户无资金
        // → 接单被拒绝，订单完整留订单簿
        long masterBalance = AccountManager.getBalance(MarketMakerLedger.MASTER_UUID);
        assertTrue(AccountManager.withdraw(MarketMakerLedger.MASTER_UUID, masterBalance));
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 200, 2).success());
        assertTrue(FuturesMarketManager.trades().isEmpty());
        assertEquals(1, FuturesMarketManager.orders().size());
        assertEquals(2, FuturesMarketManager.orders().get(0).remainingQuantity());
        assertEquals(0, FuturesMarketMaker.getNetPosition(contract.id()));
    }

    @Test
    void marketMakerNeverFillsItsOwnOrders() {
        fundMaster();
        FuturesOrder self = new FuturesOrder(UUID.randomUUID(), MM, contract.id(), FuturesOrderSide.BUY, 200, 1, 999, 0);
        FuturesMarketMaker.tryFill(self);
        assertTrue(FuturesMarketManager.trades().isEmpty());
        assertEquals(0, FuturesMarketMaker.getNetPosition(contract.id()));
    }

    @Test
    void washTradeTriggersAlertAndSingleSideCooldown() {
        fundMaster();
        // 交替买卖 7 次触发对敲（笔数阈值 6）
        for (int i = 0; i < 7; i++) {
            FuturesOrderSide side = i % 2 == 0 ? FuturesOrderSide.BUY : FuturesOrderSide.SELL;
            long limit = side == FuturesOrderSide.BUY ? 200 : 50;
            assertTrue(FuturesMarketManager.place(player, contract.id(), side, limit, 1).success(),
                    "fill " + i + " should succeed");
        }
        assertEquals(7, FuturesMarketManager.trades().size());
        assertEquals(1, FuturesMarketMaker.recentAlerts().size());
        FuturesMarketMaker.WashTradeAlert alert = FuturesMarketMaker.recentAlerts().get(0);
        assertEquals(player, alert.playerId());
        assertEquals(contract.id(), alert.contractId());
        assertEquals(7, alert.tradeCount());
        assertEquals(-1, FuturesMarketMaker.getNetPosition(contract.id()));
        // 告警同时走了仓库现有告警总线（管理后台可见）
        assertTrue(WorldEconomyFeedbackService.history().stream().anyMatch(e ->
                e.type() == WorldFeedbackType.MAJOR_MARKET_EVENT && e.severity() == FeedbackSeverity.WARNING));

        // 冷却期内：加仓方向（买）被拒绝挂单，只允许减仓方向（卖）
        int before = FuturesMarketManager.trades().size();
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 200, 1).success());
        assertEquals(before, FuturesMarketManager.trades().size());
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.SELL, 50, 1).success());
        assertEquals(before + 1, FuturesMarketManager.trades().size());
        assertEquals(0, FuturesMarketMaker.getNetPosition(contract.id()));
    }

    @Test
    void expiryShortfallIsCashSettledAndBounded() {
        // 做市商只有 300 保证金：1 手保证金刚好够，巨额亏损不够赔；
        // 先抽干总账户，避免 ensureCapital 注资干扰"资金不足"的测试意图
        long masterBalance = AccountManager.getBalance(MarketMakerLedger.MASTER_UUID);
        assertTrue(AccountManager.withdraw(MarketMakerLedger.MASTER_UUID, masterBalance));
        MarginManager.putAccountDirect(new MarginAccount(MM, 300, 0, MarginRiskStatus.NORMAL, -1));
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 200, 1).success());
        assertEquals(1, FuturesMarketManager.trades().size());
        assertEquals(-1, FuturesMarketMaker.getNetPosition(contract.id()));

        // 到期灾难价结算：做市商空头巨亏（玩家 +98930，做市商 -98930）
        assertTrue(FuturesClearingService.settle(contract, 10_000, 5, true));
        MarginAccount mm = MarginManager.account(MM);
        assertEquals(0, mm.cashBalance()); // 赔付有界：扣光但不为负，绝不倒贴印钱
        assertTrue(mm.cashBalance() >= 0);

        FuturesSettlementRecord record =
                FuturesClearingService.history().get(FuturesClearingService.history().size() - 1);
        assertEquals(98_930, record.grossGain());
        assertEquals(300, record.collectedLoss());
        assertEquals(98_630, record.guaranteeFundUsed());
        assertEquals(0, record.profitHaircut());
        // 永不印钱不变量：赢家实得 = 已收亏损 + 保证基金动用
        assertEquals(record.collectedLoss() + record.guaranteeFundUsed(),
                MarginManager.account(player).cashBalance() - 50_000);

        // 到期后清理子账本净持仓
        FuturesMarketMaker.onContractSettled(contract.id());
        assertEquals(0, FuturesMarketMaker.getNetPosition(contract.id()));
    }

    @Test
    void circuitBreakerHaltsQuotesOnExtremeDeviation() {
        fundMaster();
        assertNotNull(FuturesMarketMaker.getQuote(contract.id()));
        // 结算参考价被推到 10000，偏离理论价 105 超 ±30% → 熔断
        FuturesMarketManager.addTradeDirect(
                new FuturesTrade(player, player2, contract.id(), 10_000, 1, 0, LocalDateTime.now()));
        assertTrue(FuturesMarketMaker.isCircuitTripped(contract.id()));
        assertNull(FuturesMarketMaker.getQuote(contract.id()));
        // 熔断时不接单，订单留订单簿
        assertTrue(FuturesMarketManager.place(player, contract.id(), FuturesOrderSide.BUY, 20_000, 1).success());
        assertEquals(1, FuturesMarketManager.trades().size());
        assertEquals(1, FuturesMarketManager.orders().size());
        assertEquals(0, FuturesMarketMaker.getNetPosition(contract.id()));
    }

    @Test
    void manualPauseStopsQuotes() {
        fundMaster();
        assertNotNull(FuturesMarketMaker.getQuote(contract.id()));
        FuturesMarketMaker.setPaused(true);
        assertTrue(FuturesMarketMaker.isPaused());
        assertNull(FuturesMarketMaker.getQuote(contract.id()));
        FuturesMarketMaker.setPaused(false);
        assertFalse(FuturesMarketMaker.isPaused());
        assertNotNull(FuturesMarketMaker.getQuote(contract.id()));
    }
}
