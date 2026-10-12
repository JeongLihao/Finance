package finance.market.liquidity;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.market.MarketMakerLedger;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 熔断与统一报价入口的集成测试（攻击视角）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>股票：现价偏离 fairValue 超 ±30% → 熔断，做市商拒绝报价、订单转订单簿，
 *       偏离回归后自动恢复；</li>
 *   <li>商品：中间价偏离基准价超 ±30% → 熔断，npcBuy/npcSell 拒绝，回归后恢复；</li>
 *   <li>熔断时买入立刻卖回仍无利可图（根本无报价）；</li>
 *   <li>未知市场 / 未知标的 → 不熔断、报价为 null；</li>
 *   <li>商品 MarketPrice 神圣参数未被破坏：REFERENCE_STOCK=100_000、
 *       价格钳制 base×[0.35, 2.6]、动量每分钟×0.5 衰减。</li>
 * </ul>
 */
class MarketMakerCircuitBreakerTest {

    private static final UUID MM = new UUID(0, 0);
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID TRADER = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final String SYMBOL = "HALT";
    private static final String COMMODITY_ID = "halt_copper";

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        CompanyManager.clearCompaniesDirect();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        StockPortfolioManager.clearPortfolios();
        MarketMakerLedger.clearDirect();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        NpcMarketMaker.clearMarketPrices();

        Company company = new Company(COMPANY_ID, "Halt Test Inc", CompanyType.RAW_MATERIALS, 10_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        Stock stock = new Stock(SYMBOL, "Halt Test Inc", COMPANY_ID,
                10_000, 10_000, 0, 100, 100);
        StockMarketManager.putStockDirect(stock);
        AccountManager.getOrCreateSystemAccount(MM).deposit(10_000_000L);
        assertTrue(MarketMakerLedger.seedStockInventory(stock));

        CommodityRegistry.register(new Commodity(COMMODITY_ID, "minecraft:copper_ingot", "Copper",
                CommodityCategory.RAW_MATERIALS, 1_000));
        NpcMarketMaker.putMarketPrice(COMMODITY_ID, new MarketPrice(COMMODITY_ID, 1_000, 0.05));
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, COMMODITY_ID, 100_000);
    }

    // ---------- 股票熔断 ----------

    @Test
    void stockHaltTriggersAtThirtyPercentDeviationAndRecovers() {
        Stock stock = StockMarketManager.getStock(SYMBOL);

        // 未熔断：库存=目标、无波动、无偏离 → 标准 2% 价差
        assertFalse(MarketMakerQuotes.isHalted("stock", SYMBOL));
        QuoteView quote = MarketMakerQuotes.getQuote("stock", SYMBOL);
        assertNotNull(quote);
        assertFalse(quote.halted());
        assertEquals(98L, quote.bid());
        assertEquals(102L, quote.ask());

        // 现价 +40% → 熔断
        assertTrue(stock.setLastPrice(140));
        assertTrue(MarketMakerQuotes.isHalted("stock", SYMBOL));
        QuoteView halted = MarketMakerQuotes.getQuote("stock", SYMBOL);
        assertTrue(halted.halted());
        assertNotNull(halted.haltReason());
        assertEquals(0L, halted.bid());
        assertEquals(0L, halted.ask());

        // 熔断时做市商拒绝：买单转订单簿，玩家拿不到股票、资金保持锁定
        AccountManager.deposit(TRADER, 100_000L);
        long traderBefore = AccountManager.getBalance(TRADER);
        assertTrue(StockOrderManager.placeBuyOrder(TRADER, SYMBOL, 200, 5).success());
        assertEquals(0, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(traderBefore - 200 * 5, AccountManager.getBalance(TRADER));

        // 熔断时卖单同样转订单簿
        StockPortfolioManager.addHolding(TRADER, SYMBOL, 5, 100);
        assertTrue(StockOrderManager.placeSellOrder(TRADER, SYMBOL, 50, 5).success());
        assertEquals(2, StockOrderManager.getOrders().size());

        // 偏离回归（±30% 以内）→ 自动恢复，无需手动复位
        assertTrue(stock.setLastPrice(100));
        assertFalse(MarketMakerQuotes.isHalted("stock", SYMBOL));
        assertFalse(MarketMakerQuotes.getQuote("stock", SYMBOL).halted());

        // 恢复后做市商重新报价成交
        for (var order : new ArrayList<>(StockOrderManager.getOrders())) {
            assertTrue(StockOrderManager.cancelOrder(order.getOrderId(), TRADER));
        }
        long traderMid = AccountManager.getBalance(TRADER);
        assertTrue(StockOrderManager.placeBuyOrder(TRADER, SYMBOL, 102, 5).success());
        // 交易者此前已持有 5 股（第 115 行加仓，卖单取消后解冻），本次再买入 5 股
        assertEquals(10, StockPortfolioManager.getHolding(TRADER, SYMBOL).getQuantity());
        assertEquals(traderMid - 102 * 5, AccountManager.getBalance(TRADER));
    }

    @Test
    void stockHaltBoundaryIsNotTriggeredAtExactlyThirtyPercent() {
        Stock stock = StockMarketManager.getStock(SYMBOL);
        assertTrue(stock.setLastPrice(130)); // 恰好 +30%，严格大于才熔断
        assertFalse(MarketMakerQuotes.isHalted("stock", SYMBOL));
        assertTrue(stock.setLastPrice(131));
        assertTrue(MarketMakerQuotes.isHalted("stock", SYMBOL));
    }

    // ---------- 商品熔断 ----------

    @Test
    void commodityHaltTriggersOnMomentumSpikeAndRecovers() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(COMMODITY_ID);

        assertFalse(MarketMakerQuotes.isHalted("commodity", COMMODITY_ID));
        QuoteView quote = MarketMakerQuotes.getQuote("commodity", COMMODITY_ID);
        assertNotNull(quote);
        assertFalse(quote.halted());
        // 库存=目标、无波动 → 基础 5% 价差
        assertEquals(950L, quote.bid());
        assertEquals(1_050L, quote.ask());

        // 单边大额成交 → 成交动量 +36% 超过 30% 阈值 → 熔断
        // （锚定动能而非静态 basePrice：mid 随供需曲线偏离 basePrice 是模型正常行为）
        mp.onNpcTrade(100_000, false, 2_000);
        assertTrue(MarketMakerQuotes.isHalted("commodity", COMMODITY_ID));
        QuoteView halted = MarketMakerQuotes.getQuote("commodity", COMMODITY_ID);
        assertTrue(halted.halted());
        assertNotNull(halted.haltReason());

        // 熔断时 npcBuy/npcSell 拒绝，不发生任何资金/库存移动
        CommodityInventoryManager.addCommodity(TRADER, COMMODITY_ID, 5);
        long traderBefore = AccountManager.getBalance(TRADER);
        assertFalse(NpcMarketMaker.npcBuy(TRADER, COMMODITY_ID, 5));
        assertEquals(traderBefore, AccountManager.getBalance(TRADER));
        assertEquals(5, CommodityInventoryManager.getCommodityAmount(TRADER, COMMODITY_ID));
        assertFalse(NpcMarketMaker.npcSell(TRADER, COMMODITY_ID, 5));

        // 动能衰减后自动恢复：0.36 → 0.18 < 0.30
        mp.tickMomentum();
        assertFalse(MarketMakerQuotes.isHalted("commodity", COMMODITY_ID));
        long bidAfterRecovery = MarketMakerQuotes.getQuote("commodity", COMMODITY_ID).bid();
        assertTrue(bidAfterRecovery > 0);
        assertTrue(NpcMarketMaker.npcBuy(TRADER, COMMODITY_ID, 5));
        assertEquals(traderBefore + bidAfterRecovery * 5, AccountManager.getBalance(TRADER));
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(TRADER, COMMODITY_ID));
    }

    // ---------- 未知标的 ----------

    @Test
    void unknownMarketOrSymbolIsNotHaltedAndHasNoQuote() {
        assertFalse(MarketMakerQuotes.isHalted("commodity", "no_such_commodity"));
        assertFalse(MarketMakerQuotes.isHalted("stock", "NOPE"));
        assertFalse(MarketMakerQuotes.isHalted("bogus", "x"));
        assertNull(MarketMakerQuotes.getQuote("commodity", "no_such_commodity"));
        assertNull(MarketMakerQuotes.getQuote("stock", "NOPE"));
        assertNull(MarketMakerQuotes.getQuote("bogus", "x"));
        assertNull(MarketMakerQuotes.getQuote("stock", null));
    }

    // ---------- MarketPrice 神圣参数 ----------

    @Test
    void marketPriceSacredParamsUntouched() {
        assertEquals(100_000L, MarketPrice.REFERENCE_STOCK);

        // 动量每分钟 ×0.5 衰减
        MarketPrice mp = new MarketPrice("sacred", 1_000L, 0.05);
        mp.onNpcTrade(100_000L, false, 5_000); // 做市商卖出 → momentum += 5000/100000×18 = 0.9
        assertEquals(0.9, mp.getTradeMomentum(), 1e-9);
        mp.tickMomentum();
        assertEquals(0.45, mp.getTradeMomentum(), 1e-9);

        // 价格钳制 base×[0.35, 2.6]：极端库存下也不越界
        MarketPrice hi = new MarketPrice("sacred_hi", 1_000L, 0.05);
        for (int i = 0; i < 300; i++) {
            hi.recomputePrice(1L);
        }
        assertTrue(hi.getMidPrice() <= 2_600L, "mid=" + hi.getMidPrice());
        MarketPrice lo = new MarketPrice("sacred_lo", 1_000L, 0.05);
        for (int i = 0; i < 300; i++) {
            lo.recomputePrice(10_000_000_000L);
        }
        assertTrue(lo.getMidPrice() >= 350L, "mid=" + lo.getMidPrice());
    }
}
