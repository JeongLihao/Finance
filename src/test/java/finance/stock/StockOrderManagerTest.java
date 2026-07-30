package finance.stock;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StockOrderManagerTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final String SYMBOL = "BUG";

    @BeforeEach
    void resetState() {
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        CompanyManager.clearCompaniesDirect();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        StockPortfolioManager.clearPortfolios();

        Company company = new Company(COMPANY_ID, "Bug Regression Inc", CompanyType.RAW_MATERIALS, 10_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);

        Stock stock = new Stock(SYMBOL, "Bug Regression Inc", COMPANY_ID,
                10_000, 10_000, 0, 10, 1);
        stock.setFairValue(0);
        StockMarketManager.putStockDirect(stock);
    }

    @Test
    void buyOrderWithInvalidFairValueRefundsAndDoesNotEnterOrderBook() {
        long balanceBefore = AccountManager.getBalance(PLAYER_ID);

        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(PLAYER_ID, SYMBOL, 10, 5);

        assertFalse(result.success());
        assertEquals(balanceBefore, AccountManager.getBalance(PLAYER_ID));
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    @Test
    void sellOrderWithInvalidFairValueRefundsAndDoesNotEnterOrderBook() {
        StockPortfolioManager.addHolding(PLAYER_ID, SYMBOL, 5, 10);

        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(PLAYER_ID, SYMBOL, 10, 5);

        assertFalse(result.success());
        assertEquals(5, StockPortfolioManager.getHolding(PLAYER_ID, SYMBOL).getQuantity());
        assertEquals(0, StockOrderManager.getOrders().size());
    }
}
