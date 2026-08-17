package finance.stock;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.metrics.EconomyMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionalStockOrderManagerTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000002101");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000002201");
    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000002202");
    private static final String SYMBOL = "COND";

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(COMPANY_ID, "Conditional Test Inc", CompanyType.RAW_MATERIALS, 10_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock(SYMBOL, "Conditional Test Inc", COMPANY_ID,
                10_000, 10_000, 0, 100, 100));
        StockPortfolioManager.addHolding(PLAYER_ID, SYMBOL, 5, 80);
    }

    @Test
    void takeProfitTriggerCreatesSellOrderAndWritesRecord() {
        StockMarketManager.getStock(SYMBOL).setLastPrice(120);
        ConditionalStockOrderManager.OrderResult result = ConditionalStockOrderManager.addOrder(
                PLAYER_ID, SYMBOL, ConditionalStockOrderType.TAKE_PROFIT, 110, 3);

        assertTrue(result.success());
        assertEquals(1, ConditionalStockOrderManager.checkOrdersForTest());

        assertEquals(0, ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).size());
        assertEquals(2, StockPortfolioManager.getHolding(PLAYER_ID, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(3, StockOrderManager.getOrders().get(0).getQuantity());
        assertEquals(TransactionType.CONDITIONAL_STOCK_TRIGGER,
                AccountManager.getTransactions().get(0).getType());
    }

    @Test
    void triggeredConditionalOrderCanImmediatelyMatchExistingBuyOrder() {
        StockMarketManager.getStock(SYMBOL).setFairValue(200);
        StockOrderManager.placeBuyOrder(BUYER_ID, SYMBOL, 125, 2);
        StockMarketManager.getStock(SYMBOL).setLastPrice(120);
        ConditionalStockOrderManager.addOrder(PLAYER_ID, SYMBOL, ConditionalStockOrderType.TAKE_PROFIT, 110, 2);

        assertEquals(1, ConditionalStockOrderManager.checkOrdersForTest());

        assertEquals(0, ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).size());
        assertEquals(2, StockPortfolioManager.getHolding(BUYER_ID, SYMBOL).getQuantity());
        assertEquals(3, StockPortfolioManager.getHolding(PLAYER_ID, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getTradeHistory().size());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.CONDITIONAL_STOCK_TRIGGER));
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.STOCK_SELL));
        assertEquals(2, EconomyMetricsService.getCurrentStockVolume());
    }

    @Test
    void insufficientHoldingCancelsConditionalOrderAndWritesRecord() {
        ConditionalStockOrderManager.addOrder(PLAYER_ID, SYMBOL, ConditionalStockOrderType.STOP_LOSS, 90, 5);
        assertTrue(StockPortfolioManager.removeHolding(PLAYER_ID, SYMBOL, 4));
        StockMarketManager.getStock(SYMBOL).setLastPrice(80);

        assertEquals(0, ConditionalStockOrderManager.checkOrdersForTest());

        assertEquals(0, ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).size());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.CONDITIONAL_STOCK_CANCEL));
    }

    @Test
    void cannotCreateConditionalOrderWithoutEnoughCurrentHolding() {
        ConditionalStockOrderManager.OrderResult result = ConditionalStockOrderManager.addOrder(
                PLAYER_ID, SYMBOL, ConditionalStockOrderType.TAKE_PROFIT, 110, 6);

        assertFalse(result.success());
        assertEquals(0, ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).size());
    }
}
