package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyFinancingManagerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000003001");
    private static final UUID INVESTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000003002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final String SYMBOL = "FINC";

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(COMPANY_ID, "Financing Inc", CompanyType.RAW_MATERIALS, 1_000, OWNER_ID);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock(SYMBOL, "Financing Inc", COMPANY_ID,
                10_000, 10_000, 0, 100, 100));
    }

    @Test
    void subscriptionCompletesFinancingAndIssuesShares() {
        CompanyFinancingManager.Result start = CompanyFinancingManager.startProject(
                OWNER_ID, COMPANY_ID, 100, 10, 500, 3);
        assertTrue(start.success());
        long balanceBefore = AccountManager.getBalance(INVESTOR_ID);

        CompanyFinancingProject project = CompanyFinancingManager.getProjectByCompany(COMPANY_ID);
        CompanyFinancingManager.Result subscribe = CompanyFinancingManager.subscribe(
                INVESTOR_ID, project.getProjectId(), 50);

        assertTrue(subscribe.success());
        assertEquals(0, CompanyFinancingManager.getProjects().size());
        assertEquals(balanceBefore - 500, AccountManager.getBalance(INVESTOR_ID));
        assertEquals(1_500, CompanyManager.getCompany(COMPANY_ID).getCash());
        assertEquals(10_050, StockMarketManager.getStock(SYMBOL).getTotalShares());
        assertEquals(50, StockPortfolioManager.getHolding(INVESTOR_ID, SYMBOL).getQuantity());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.COMPANY_FINANCING_SUCCESS));
    }

    @Test
    void expiredUnfundedProjectRefundsSubscriptions() {
        CompanyFinancingManager.startProject(OWNER_ID, COMPANY_ID, 100, 10, 900, 3);
        CompanyFinancingProject project = CompanyFinancingManager.getProjectByCompany(COMPANY_ID);
        long balanceBefore = AccountManager.getBalance(INVESTOR_ID);
        CompanyFinancingManager.subscribe(INVESTOR_ID, project.getProjectId(), 50);

        CompanyFinancingManager.tick(10);

        assertEquals(0, CompanyFinancingManager.getProjects().size());
        assertEquals(balanceBefore, AccountManager.getBalance(INVESTOR_ID));
        assertEquals(1_000, CompanyManager.getCompany(COMPANY_ID).getCash());
        assertEquals(0, StockPortfolioManager.getHolding(INVESTOR_ID, SYMBOL).getQuantity());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.COMPANY_FINANCING_REFUND));
    }

    @Test
    void companyCannotStartSecondActiveFinancingProject() {
        assertTrue(CompanyFinancingManager.startProject(OWNER_ID, COMPANY_ID, 100, 10, 500, 3).success());

        CompanyFinancingManager.Result second = CompanyFinancingManager.startProject(
                OWNER_ID, COMPANY_ID, 100, 10, 500, 3);

        assertFalse(second.success());
        assertEquals(1, CompanyFinancingManager.getProjects().size());
    }
}
