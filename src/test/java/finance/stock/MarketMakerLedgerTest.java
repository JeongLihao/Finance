package finance.stock;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyIPOService;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.market.MarketMakerLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1（股票做市商资金/库存正规化）的攻击视角回归测试。
 *
 * <p>覆盖防线（对应设计文档 §6 反刷钱清单 1/2/3/7）：
 * <ul>
 *   <li>做市商买入从总账户真实扣款 —— 现金不足时拒绝，玩家得不到钱；</li>
 *   <li>做市商卖出从子账本扣减库存 —— 库存不足时拒绝，玩家得不到股票（无凭空交割）；</li>
 *   <li>往返必亏 —— 买入立刻卖回必亏，无刷钱循环；</li>
 *   <li>库存上限 —— 达上限后只卖不买；</li>
 *   <li>200 股终身熔断仍生效；</li>
 *   <li>单笔上限被强制执行（超限部分转订单簿）；</li>
 *   <li>IPO 预留 —— 发行量 5% 以发行价划入做市商库存，资金真实支付给公司，
 *       余额不足时跳过预留且不阻断 IPO。</li>
 * </ul>
 *
 * <p>测试设定：流通股 10_000，fairValue 100，价差 2%（bid 98 / ask 102），
 * 目标库存 500 股，上限 1000 股，单笔上限 100 股；做市商总账户初始 10_000_000，
 * 建仓花费 50_000 后余额 9_950_000。</p>
 */
class MarketMakerLedgerTest {

    private static final UUID MM = new UUID(0, 0);
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final String SYMBOL = "MMT";

    private static final long FAIR_VALUE = 100L;
    private static final long BID = 98L;  // fairValue × (1 - 2%)
    private static final long ASK = 102L; // fairValue × (1 + 2%)

    private static final long TARGET_INVENTORY = 500L;
    private static final long MAX_INVENTORY = 1000L;
    private static final long SINGLE_TRADE_CAP = 100L;
    private static final long MM_BALANCE_AFTER_SEED = 9_950_000L;

    @BeforeEach
    void resetState() {
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        CompanyManager.clearCompaniesDirect();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        StockPortfolioManager.clearPortfolios();
        MarketMakerLedger.clearDirect();

        Company company = new Company(COMPANY_ID, "Market Maker Test Inc", CompanyType.RAW_MATERIALS, 10_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        Stock stock = new Stock(SYMBOL, "Market Maker Test Inc", COMPANY_ID,
                10_000, 10_000, 0, FAIR_VALUE, FAIR_VALUE);
        StockMarketManager.putStockDirect(stock);

        // 做市商总账户注资并建仓至目标库存（500 股 × 100 = 50_000）
        AccountManager.getOrCreateSystemAccount(MM).deposit(10_000_000L);
        assertTrue(MarketMakerLedger.seedStockInventory(stock));
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(MM_BALANCE_AFTER_SEED, AccountManager.getBalance(MM));
    }

    // ---------- 反刷钱 1：做市商一切支付走真实账户扣减 ----------

    @Test
    void marketMakerBuyRefusedWhenCashInsufficient() {
        // 做市商只剩 50，连 1 股（98）都买不起
        assertTrue(AccountManager.withdraw(MM, MM_BALANCE_AFTER_SEED - 50));
        assertEquals(50L, AccountManager.getBalance(MM));

        StockPortfolioManager.addHolding(TRADER, SYMBOL, 5, 100);
        long traderBefore = AccountManager.getBalance(TRADER);

        // 旧漏洞：玩家直接 deposit 印钱；现在必须拒绝，转订单簿
        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(TRADER, SYMBOL, BID, 5);

        assertTrue(result.success(), "拒绝后应转入订单簿: " + result.message());
        assertEquals(traderBefore, AccountManager.getBalance(TRADER), "玩家不应得到任何钱");
        assertEquals(50L, AccountManager.getBalance(MM), "做市商余额不应变化");
        assertEquals(0, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity(), "股份应保持锁定");
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL), "做市商库存不应变化");
        assertEquals(0, StockMarketManager.getStock(SYMBOL).getMarketMakerBoughtShares());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(5, StockOrderManager.getOrders().get(0).getQuantity());
    }

    // ---------- 反刷钱 1：做市商一切交割走真实库存扣减 ----------

    @Test
    void marketMakerSellRefusedWhenInventoryEmpty() {
        MarketMakerLedger.clearDirect();
        assertEquals(0, MarketMakerLedger.getStockInventory(SYMBOL));

        long traderBefore = AccountManager.getBalance(TRADER);

        // 旧漏洞：凭空交割，无上限；现在必须拒绝，转订单簿
        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(TRADER, SYMBOL, ASK, 5);

        assertTrue(result.success(), "拒绝后应转入订单簿: " + result.message());
        assertEquals(0, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity(), "玩家不应得到任何股票");
        assertEquals(traderBefore - ASK * 5, AccountManager.getBalance(TRADER), "资金应保持锁定在订单簿");
        assertEquals(MM_BALANCE_AFTER_SEED, AccountManager.getBalance(MM), "做市商不应凭空收款");
        assertEquals(1, StockOrderManager.getOrders().size());
    }

    // ---------- 反刷钱 4：往返必亏 ----------

    @Test
    void roundTripWithMarketMakerAlwaysLoses() {
        AccountManager.deposit(TRADER, 100_000L);
        long traderBefore = AccountManager.getBalance(TRADER);
        long mmBefore = AccountManager.getBalance(MM);

        assertTrue(StockOrderManager.placeBuyOrder(TRADER, SYMBOL, ASK, 10).success());
        assertEquals(10, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        // Phase 2 动态报价：买入 10 股后做市商库存 490/500，skew = 0.5×(0.98-1) = -0.01，
        // 卖回价 bid = 100×(1-0.02+0.01) = 99（波动率/均值回归此时无作用）
        long sellBackPrice = 99L;
        assertTrue(StockOrderManager.placeSellOrder(TRADER, SYMBOL, sellBackPrice, 10).success());

        // 买入立刻卖回：支付 10×102，收回 10×99，净亏 10×3，无免费午餐
        long expectedLoss = 10 * (ASK - sellBackPrice);
        assertEquals(traderBefore - expectedLoss, AccountManager.getBalance(TRADER));
        assertEquals(0, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        // 做市商赚走价差，库存回到原点
        assertEquals(mmBefore + expectedLoss, AccountManager.getBalance(MM));
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));
        // 审计记录齐全
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(r -> r.getType() == TransactionType.MARKET_MAKER_SELL));
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(r -> r.getType() == TransactionType.MARKET_MAKER_BUY));
    }

    // ---------- 反刷钱 2：库存上限 —— 达上限后只卖不买 ----------

    @Test
    void inventoryCapAllowsOnlySelling() {
        MarketMakerLedger.addInventory(MarketMakerLedger.SubMarket.STOCK, SYMBOL, TARGET_INVENTORY);
        assertEquals(MAX_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));

        // 做市商不再接盘 → 卖单转订单簿，玩家拿不到钱
        StockPortfolioManager.addHolding(TRADER, SYMBOL, 5, 100);
        long traderBefore = AccountManager.getBalance(TRADER);
        StockOrderManager.OrderResult sellResult = StockOrderManager.placeSellOrder(TRADER, SYMBOL, BID, 5);

        assertTrue(sellResult.success());
        assertEquals(traderBefore, AccountManager.getBalance(TRADER));
        assertEquals(MAX_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(1, StockOrderManager.getOrders().size());

        // 但做市商仍可卖出（只卖不买）
        AccountManager.deposit(TRADER, 100_000L);
        StockOrderManager.OrderResult buyResult = StockOrderManager.placeBuyOrder(TRADER, SYMBOL, ASK, 5);

        assertTrue(buyResult.success());
        assertEquals(5, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        assertEquals(MAX_INVENTORY - 5, MarketMakerLedger.getStockInventory(SYMBOL));
    }

    // ---------- 终身熔断兜底仍生效 ----------

    @Test
    void lifetimeCircuitBreakerStillEnforced() {
        Stock stock = StockMarketManager.getStock(SYMBOL);
        stock.addMarketMakerBoughtShares(195); // 终身 200 股只剩 5 股

        StockPortfolioManager.addHolding(TRADER, SYMBOL, 20, 100);
        long traderBefore = AccountManager.getBalance(TRADER);

        // 卖 10 股：终身剩 5 → 只成交 5 股，剩余 5 股挂订单簿
        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(TRADER, SYMBOL, BID, 10);

        assertTrue(result.success());
        assertEquals(traderBefore + 5 * BID, AccountManager.getBalance(TRADER));
        assertEquals(200, stock.getMarketMakerBoughtShares());
        assertEquals(TARGET_INVENTORY + 5, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(5, StockOrderManager.getOrders().get(0).getQuantity());

        // 终身额度用尽后：新的卖单完全被拒，转订单簿
        long traderMid = AccountManager.getBalance(TRADER);
        StockOrderManager.OrderResult result2 = StockOrderManager.placeSellOrder(TRADER, SYMBOL, BID, 5);

        assertTrue(result2.success());
        assertEquals(traderMid, AccountManager.getBalance(TRADER), "熔断后玩家不应再从做市商拿到钱");
        assertEquals(200, stock.getMarketMakerBoughtShares());
        assertEquals(2, StockOrderManager.getOrders().size());
    }

    // ---------- 反刷钱 3：单笔上限被强制执行 ----------

    @Test
    void singleTradeCapEnforcedOnBothSides() {
        // 卖 500 股：单笔上限 100 → 只成交 100 股，其余 400 股挂订单簿
        StockPortfolioManager.addHolding(TRADER, SYMBOL, 500, 100);
        long traderBefore = AccountManager.getBalance(TRADER);

        StockOrderManager.OrderResult sellResult = StockOrderManager.placeSellOrder(TRADER, SYMBOL, BID, 500);

        assertTrue(sellResult.success());
        assertEquals(traderBefore + SINGLE_TRADE_CAP * BID, AccountManager.getBalance(TRADER));
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(400, StockOrderManager.getOrders().get(0).getQuantity());
        assertEquals(TARGET_INVENTORY + SINGLE_TRADE_CAP, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(SINGLE_TRADE_CAP, StockMarketManager.getStock(SYMBOL).getMarketMakerBoughtShares());

        // 买 500 股：同样只成交 100 股。注意卖出 100 股后做市商库存 600/500，
        // Phase 2 skew = 0.5×(1.2-1) = 0.10 → ask = 100×(1+0.02-0.10) = 92
        long dynamicAsk = 92L;
        AccountManager.deposit(TRADER, 1_000_000L);
        long traderMid = AccountManager.getBalance(TRADER);
        StockOrderManager.OrderResult buyResult = StockOrderManager.placeBuyOrder(TRADER, SYMBOL, ASK, 500);

        assertTrue(buyResult.success());
        assertEquals(SINGLE_TRADE_CAP, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        // 100 股以动态 ask(92)成交，剩余 400 股挂订单簿并冻结 400×102；
        // 账面可用余额 = traderMid - 9200 - 40800
        assertEquals(traderMid - SINGLE_TRADE_CAP * dynamicAsk - 400 * ASK,
                AccountManager.getBalance(TRADER), "400股挂单应冻结 400×102");
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(2, StockOrderManager.getOrders().size());
    }

    // ---------- IPO 预留 ----------

    @Test
    void ipoReservesFivePercentForMarketMaker() {
        UUID ipoCompanyId = UUID.fromString("00000000-0000-0000-0000-000000000704");
        Company company = new Company(ipoCompanyId, "MMTCorp", CompanyType.RAW_MATERIALS, 20_000L, OWNER); // 达到上市估值门槛
        CompanyManager.registerDirect(company);
        AccountManager.deposit(OWNER, 100_000L);

        long mmBefore = AccountManager.getBalance(MM);
        CompanyIPOService.IPOResult result = CompanyIPOService.ipo(OWNER, ipoCompanyId, 10L, 1000L);

        assertTrue(result.success(), result.message());
        // 发行量 1000 × 5% = 50 股，以发行价 10 划入做市商库存
        assertEquals(50, MarketMakerLedger.getStockInventory("MMTC"));
        assertEquals(mmBefore - 500, AccountManager.getBalance(MM), "做市商应真实支付 500");
        // 公司原有 20_000 + 募资 10_000 + 做市商认购款 500
        assertEquals(30_500L, company.getCash());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(r -> r.getType() == TransactionType.MARKET_MAKER_BUY
                        && "MMTC".equals(r.getObjectName())
                        && r.getQuantity() == 50));
    }

    @Test
    void ipoSkipsReserveWhenMarketMakerBroke() {
        assertTrue(AccountManager.withdraw(MM, AccountManager.getBalance(MM)));
        assertEquals(0, AccountManager.getBalance(MM));

        UUID ipoCompanyId = UUID.fromString("00000000-0000-0000-0000-000000000705");
        Company company = new Company(ipoCompanyId, "BrokeCorp", CompanyType.RAW_MATERIALS, 20_000L, OWNER); // 达到上市估值门槛
        CompanyManager.registerDirect(company);
        AccountManager.deposit(OWNER, 100_000L);

        CompanyIPOService.IPOResult result = CompanyIPOService.ipo(OWNER, ipoCompanyId, 10L, 1000L);

        // 预留被跳过，但 IPO 本身不受影响
        assertTrue(result.success(), result.message());
        assertEquals(0, MarketMakerLedger.getStockInventory("BROK"));
        assertEquals(30_000L, company.getCash(), "公司只收到募资，无认购款");
        assertEquals(0, AccountManager.getBalance(MM));
    }

    @Test
    void ipoRequiresMinimumValuation() {
        UUID ipoCompanyId = UUID.fromString("00000000-0000-0000-0000-000000000706");
        Company company = new Company(ipoCompanyId, "SmallCorp", CompanyType.RAW_MATERIALS, 1_000L, OWNER);
        CompanyManager.registerDirect(company);
        AccountManager.deposit(OWNER, 100_000L);
        long before = AccountManager.getBalance(OWNER);

        CompanyIPOService.IPOResult result = CompanyIPOService.ipo(OWNER, ipoCompanyId, 10L, 1000L);

        // 开公司只要 10 磅，没有估值门槛就能“开公司→上市→提现”刷钱
        assertFalse(result.success());
        assertTrue(result.message().contains("估值"), result.message());
        assertFalse(company.isPublic());
        assertEquals(before, AccountManager.getBalance(OWNER), "失败时不收上市费");
    }

    // ---------- 建仓幂等与失败语义 ----------

    @Test
    void seedIsIdempotentAndFailsGracefullyWhenBroke() {
        // 幂等：重复调用不重复扣款
        long mmBefore = AccountManager.getBalance(MM);
        assertTrue(MarketMakerLedger.seedStockInventory(StockMarketManager.getStock(SYMBOL)));
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.getStockInventory(SYMBOL));
        assertEquals(mmBefore, AccountManager.getBalance(MM));

        // 余额不足：建仓失败但不抛异常，库存保持为 0
        MarketMakerLedger.clearDirect();
        assertTrue(AccountManager.withdraw(MM, AccountManager.getBalance(MM)));
        assertFalse(MarketMakerLedger.seedStockInventory(StockMarketManager.getStock(SYMBOL)));
        assertEquals(0, MarketMakerLedger.getStockInventory(SYMBOL));
    }

    @Test
    void ledgerCapsFollowConfig() {
        assertEquals(TARGET_INVENTORY, MarketMakerLedger.targetInventory(10_000));
        assertEquals(MAX_INVENTORY, MarketMakerLedger.maxInventory(10_000));
        assertEquals(SINGLE_TRADE_CAP, MarketMakerLedger.maxSingleTradeQty(10_000));
        assertEquals(0, MarketMakerLedger.targetInventory(0));
        assertEquals(0, MarketMakerLedger.maxSingleTradeQty(0));
    }
}
