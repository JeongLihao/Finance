package finance.market;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.metrics.EconomyMetricsService;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketManagerMetricsTest {

    private static final String COMMODITY_ID = "metrics_test_commodity";
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000003001");
    private static final UUID SELLER_A = UUID.fromString("00000000-0000-0000-0000-000000003002");
    private static final UUID SELLER_B = UUID.fromString("00000000-0000-0000-0000-000000003003");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity(
                COMMODITY_ID, "minecraft:iron_ingot", "Metrics Commodity",
                CommodityCategory.RAW_MATERIALS, 10));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void peerToPeerAndPartialMultiFillCountOnlyExecutedQuantity() {
        CommodityInventoryManager.addCommodity(SELLER_A, COMMODITY_ID, 2);
        CommodityInventoryManager.addCommodity(SELLER_B, COMMODITY_ID, 3);
        assertTrue(MarketManager.placeOrder(new Order(SELLER_A, COMMODITY_ID, OrderType.SELL, 10, 2)));
        assertTrue(MarketManager.placeOrder(new Order(SELLER_B, COMMODITY_ID, OrderType.SELL, 11, 3)));

        assertTrue(MarketManager.placeOrder(new Order(BUYER, COMMODITY_ID, OrderType.BUY, 12, 5)));

        assertEquals(5, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(2, MarketManager.getTradeHistory().size());
        assertEquals(5, CommodityInventoryManager.getCommodityAmount(BUYER, COMMODITY_ID));
        assertEquals(10, CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).get(0).open());
        assertEquals(11, CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).get(0).close());
        assertEquals(5, CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).get(0).volume());
    }

    @Test
    void takeExistingOrderCountsItsExecutedQuantity() {
        CommodityInventoryManager.addCommodity(SELLER_A, COMMODITY_ID, 4);
        assertTrue(MarketManager.placeOrder(new Order(SELLER_A, COMMODITY_ID, OrderType.SELL, 5, 4)));

        MarketManager.TakeOrderResult result = MarketManager.takeOrder(
                MarketManager.getOrders().get(0).getOrderId(), BUYER);

        assertTrue(result.success());
        assertEquals(4, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(4, CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).get(0).volume());
    }

    @Test
    void npcBuyAndSellCountAcceptedQuantities() {
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(1_000);
        CommodityInventoryManager.addCommodity(SELLER_A, COMMODITY_ID, 3);
        assertTrue(NpcMarketMaker.npcBuy(SELLER_A, COMMODITY_ID, 3));

        CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, COMMODITY_ID, 2);
        assertTrue(NpcMarketMaker.npcSell(BUYER, COMMODITY_ID, 2));

        assertEquals(5, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(5, CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).get(0).volume());
    }

    @Test
    void failedAndCancelledOrdersDoNotIncreaseTradeVolume() {
        assertFalse(MarketManager.placeOrder(new Order(BUYER, COMMODITY_ID, OrderType.BUY, Long.MAX_VALUE, 2)));
        assertTrue(MarketManager.placeOrder(new Order(BUYER, COMMODITY_ID, OrderType.BUY, 9, 2)));
        assertTrue(MarketManager.cancelOrder(MarketManager.getOrders().get(0).getOrderId(), BUYER));

        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(0, MarketManager.getTradeHistory().size());
        assertTrue(CandlestickService.getBars(MarketInstrumentType.COMMODITY, COMMODITY_ID, 30).isEmpty());
    }

    @Test
    void matchingUsesBestCommodityPriceBeforeInsertionOrder() {
        CommodityInventoryManager.addCommodity(SELLER_A, COMMODITY_ID, 1);
        CommodityInventoryManager.addCommodity(SELLER_B, COMMODITY_ID, 1);
        assertTrue(MarketManager.placeOrder(new Order(SELLER_A, COMMODITY_ID, OrderType.SELL, 12, 1)));
        assertTrue(MarketManager.placeOrder(new Order(SELLER_B, COMMODITY_ID, OrderType.SELL, 10, 1)));

        assertTrue(MarketManager.placeOrder(new Order(BUYER, COMMODITY_ID, OrderType.BUY, 12, 1)));

        assertEquals(1_010, AccountManager.getBalance(SELLER_B));
        assertEquals(1_000, AccountManager.getBalance(SELLER_A));
        assertEquals(1, EconomyMetricsService.getCurrentCommodityVolume());
    }

    @Test
    void settlementOverflowDoesNotMoveFundsInventoryOrOrders() {
        CommodityInventoryManager.addCommodity(SELLER_A, COMMODITY_ID, 1);
        assertTrue(MarketManager.placeOrder(new Order(SELLER_A, COMMODITY_ID, OrderType.SELL, 10, 1)));
        AccountManager.getAccount(SELLER_A).setBalance(Long.MAX_VALUE);

        assertTrue(MarketManager.placeOrder(new Order(BUYER, COMMODITY_ID, OrderType.BUY, 10, 1)));

        assertEquals(Long.MAX_VALUE, AccountManager.getBalance(SELLER_A));
        assertEquals(990, AccountManager.getBalance(BUYER));
        assertEquals(10, AccountManager.getAccount(BUYER).getFrozenBalance());
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(BUYER, COMMODITY_ID));
        assertEquals(2, MarketManager.getOrders().size());
        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
    }
}
