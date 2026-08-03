package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyDividendTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-000000002101");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-000000002102");

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void dividendsPayShareholdersByHoldingRatioAndWriteRecords() {
        Company company = new Company(COMPANY_ID, "Dividend Co", CompanyType.RAW_MATERIALS, 2_000, OWNER_ID);
        company.setPublic(true);
        company.addDistributableProfit(1_000);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("DIV", "Dividend Co", COMPANY_ID,
                1_000, 1_000, 0, 10, 10));
        StockPortfolioManager.addHolding(PLAYER_A, "DIV", 250, 10);
        StockPortfolioManager.addHolding(PLAYER_B, "DIV", 750, 10);
        CompanyManager.setDividendPolicy(0.50, 1);

        CompanyManager.tryDividends(1);
        CompanyManager.tryDividends(2);

        assertEquals(1_125, AccountManager.getBalance(PLAYER_A));
        assertEquals(1_375, AccountManager.getBalance(PLAYER_B));
        assertEquals(1_500, company.getCash());
        assertEquals(500, company.getDistributableProfit());
        assertEquals(1, company.getDividendHistory().size());
        assertEquals(500, company.getDividendHistory().get(0).totalAmount());
        assertEquals(2, AccountManager.getTransactions().stream()
                .filter(record -> record.getType() == TransactionType.DIVIDEND)
                .count());
    }
}
