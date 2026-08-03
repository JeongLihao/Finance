package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyBankruptcyManagerTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000006101");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000006001");
    private static final UUID HOLDER_A = UUID.fromString("00000000-0000-0000-0000-000000006002");
    private static final UUID HOLDER_B = UUID.fromString("00000000-0000-0000-0000-000000006003");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void lowCashEntersRiskThenBankruptsAndLiquidatesShareholders() {
        Company company = publicCompany(0);
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 70, 10);
        StockPortfolioManager.addHolding(HOLDER_B, "BRK", 30, 10);

        CompanyBankruptcyManager.tick(1);
        assertTrue(company.isBankruptcyRisk());

        CompanyBankruptcyManager.tick(4);

        assertNull(CompanyManager.getCompany(COMPANY_ID));
        assertNull(StockMarketManager.getStock("BRK"));
        assertEquals(0, StockPortfolioManager.getHolding(HOLDER_A, "BRK").getQuantity());
        assertEquals(0, StockPortfolioManager.getHolding(HOLDER_B, "BRK").getQuantity());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.COMPANY_BANKRUPTCY));
    }

    @Test
    void riskClearsWhenCompanyCashRecoversBeforeDeadline() {
        Company company = publicCompany(0);
        CompanyBankruptcyManager.tick(1);
        assertTrue(company.isBankruptcyRisk());

        company.deposit(10_000);
        CompanyBankruptcyManager.tick(2);

        assertFalse(company.isBankruptcyRisk());
        assertEquals(company, CompanyManager.getCompany(COMPANY_ID));
    }

    @Test
    void bankruptcyCancelsStockOrdersAndStopsNewTrading() {
        Company company = publicCompany(0);
        long balanceBefore = AccountManager.getBalance(HOLDER_A);
        StockOrderManager.placeBuyOrder(HOLDER_A, "BRK", 1, 10);
        assertEquals(1, StockOrderManager.getOrders().size());

        CompanyBankruptcyManager.bankrupt(company, 5);

        assertEquals(0, StockOrderManager.getOrders().size());
        assertEquals(balanceBefore, AccountManager.getBalance(HOLDER_A));
        assertFalse(StockMarketManager.placeLimitBuy(HOLDER_A, "BRK", 1, 1).success());
    }

    private static Company publicCompany(long cash) {
        Company company = new Company(COMPANY_ID, "Bankrupt Inc", CompanyType.RAW_MATERIALS, cash, OWNER_ID);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("BRK", "Bankrupt Inc", COMPANY_ID,
                1_000, 1_000, 0, 10, 10));
        return company;
    }
}
