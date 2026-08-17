package finance.company;

import finance.account.AccountManager;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.data.EconomySavedData;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.metrics.EconomyMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyNpcTradeServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000009001");
    private MarketPrice price;

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        price = new MarketPrice("iron", 10, 0.05);
        NpcMarketMaker.putMarketPrice("iron", price);
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(10_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", 1_000);
    }

    @Test
    void normalCompanyPurchaseMovesAllFourBalancesAndPublishesTrade() {
        Company company = company(1_000, 0);
        long payment = price.getAskPrice() * 5;

        var result = CompanyNpcTradeService.buyForCompany(company, "iron", 5);

        assertTrue(result.success());
        assertEquals(1_000 - payment, company.getCash());
        assertEquals(5, company.getInventoryAmount("iron"));
        assertEquals(10_000 + payment, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(995, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron"));
        assertPublished(5);
    }

    @Test
    void normalCompanySaleMovesAllFourBalancesAndPublishesTrade() {
        Company company = company(100, 10);
        long payment = price.getBidPrice() * 4;

        var result = CompanyNpcTradeService.sellForCompany(company, "iron", 4);

        assertTrue(result.success());
        assertEquals(100 + payment, company.getCash());
        assertEquals(6, company.getInventoryAmount("iron"));
        assertEquals(10_000 - payment, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(1_004, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron"));
        assertPublished(4);
    }

    @Test
    void zeroNpcCashDoesNotMoveCompanySale() {
        Company company = company(100, 1);
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(0);
        assertSaleFailureUnchanged(company);
    }

    @Test
    void fullNpcInventoryDoesNotMoveCompanySale() {
        Company company = company(100, 1);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", Integer.MAX_VALUE);
        assertSaleFailureUnchanged(company);
    }

    @Test
    void fullCompanyCashDoesNotMoveCompanySale() {
        Company company = company(Long.MAX_VALUE, 1);
        assertSaleFailureUnchanged(company);
    }

    @Test
    void fullNpcAccountDoesNotMoveCompanyPurchase() {
        Company company = company(100, 0);
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(Long.MAX_VALUE);
        long marketStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron");
        int transactionCount = AccountManager.getTransactions().size();
        long midPrice = price.getMidPrice();

        var result = CompanyNpcTradeService.buyForCompany(company, "iron", 1);

        assertFalse(result.success());
        assertEquals(100, company.getCash());
        assertEquals(0, company.getInventoryAmount("iron"));
        assertEquals(Long.MAX_VALUE, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(marketStock, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron"));
        assertNoPublication(transactionCount, midPrice);
    }

    @Test
    void companyAndPlayerTradesAggregateIntoSameCandle() {
        Company company = company(100, 2);
        UUID seller = UUID.fromString("00000000-0000-0000-0000-000000009002");
        UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000009003");
        CommodityInventoryManager.addCommodity(seller, "iron", 3);

        assertTrue(MarketManager.placeOrder(new Order(seller, "iron", OrderType.SELL, 8, 3)));
        assertTrue(MarketManager.placeOrder(new Order(buyer, "iron", OrderType.BUY, 8, 3)));
        long companyTradePrice = price.getBidPrice();
        assertTrue(CompanyNpcTradeService.sellForCompany(company, "iron", 2).success());

        var candle = CandlestickService.getBars(MarketInstrumentType.COMMODITY, "iron", 30).get(0);
        assertEquals(5, candle.volume());
        assertEquals(8, candle.open());
        assertEquals(companyTradePrice, candle.close());
    }

    private Company company(long cash, int inventory) {
        Company company = new Company(COMPANY_ID, "Atomic Co", CompanyType.RAW_MATERIALS, cash);
        if (inventory > 0) company.addInventory("iron", inventory);
        return company;
    }

    private void assertSaleFailureUnchanged(Company company) {
        long companyCash = company.getCash();
        int companyStock = company.getInventoryAmount("iron");
        long npcCash = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);
        int npcStock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron");
        int transactionCount = AccountManager.getTransactions().size();
        long midPrice = price.getMidPrice();

        var result = CompanyNpcTradeService.sellForCompany(company, "iron", 1);

        assertFalse(result.success());
        assertEquals(companyCash, company.getCash());
        assertEquals(companyStock, company.getInventoryAmount("iron"));
        assertEquals(npcCash, AccountManager.getBalance(NpcMarketMaker.NPC_UUID));
        assertEquals(npcStock, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "iron"));
        assertNoPublication(transactionCount, midPrice);
    }

    private void assertNoPublication(int transactionCount, long midPrice) {
        assertEquals(transactionCount, AccountManager.getTransactions().size());
        assertEquals(0, MarketManager.getTradeHistory().size());
        assertEquals(0, EconomyMetricsService.getCurrentCommodityVolume());
        assertTrue(CandlestickService.getBars(MarketInstrumentType.COMMODITY, "iron", 30).isEmpty());
        assertEquals(0, price.getDayVolume());
        assertEquals(midPrice, price.getMidPrice());
    }

    private void assertPublished(long quantity) {
        assertEquals(1, AccountManager.getTransactions().size());
        assertEquals(1, MarketManager.getTradeHistory().size());
        assertEquals(quantity, EconomyMetricsService.getCurrentCommodityVolume());
        assertEquals(quantity, CandlestickService.getBars(
                MarketInstrumentType.COMMODITY, "iron", 30).get(0).volume());
        assertEquals(quantity, price.getDayVolume());
    }
}
