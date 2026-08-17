package finance.debt;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorporateBondManagerTest {
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private final UUID companyId = UUID.fromString("00000000-0000-0000-0000-000000000702");
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); CompanyManager.registerDirect(new Company(companyId,"BondCo",CompanyType.FOOD,1_000_000,owner)); AccountManager.deposit(owner,500_000); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void subscriptionLocksCashThenActivationFundsCompany() {
        var issued = CorporateBondManager.issue(owner, companyId, "BC1", 100_000, 2, 2_000, 0, 1, 10, 2);
        assertTrue(issued.success(), issued.message());
        long before = AccountManager.getBalance(owner);
        assertTrue(CorporateBondManager.subscribe(owner, issued.id(), 1).success());
        CorporateBond bond = CorporateBondManager.bonds().get(issued.id());
        assertEquals(before - 100_000, AccountManager.getBalance(owner));
        assertEquals(100_000, bond.escrowCash());
        CorporateBondManager.processDay(1);
        assertEquals(BondStatus.ACTIVE, bond.status());
        assertEquals(0, bond.escrowCash());
        assertEquals(1_100_000, CompanyManager.getCompany(companyId).getCash());
    }

    @Test void insufficientCompanyCashDefaultsWithoutPartialCouponPayment() {
        var issued = CorporateBondManager.issue(owner, companyId, "BC2", 100_000, 2, 10_000, 0, 1, 10, 2);
        assertTrue(issued.success(), issued.message());
        assertTrue(CorporateBondManager.subscribe(owner, issued.id(), 2).success());
        CorporateBondManager.processDay(1);
        Company company = CompanyManager.getCompany(companyId);
        assertTrue(company.withdraw(company.getCash()));
        long playerBefore = AccountManager.getBalance(owner);
        CorporateBondManager.processDay(3);
        assertEquals(BondStatus.DEFAULTED, CorporateBondManager.bonds().get(issued.id()).status());
        assertEquals(playerBefore, AccountManager.getBalance(owner));
    }

    @Test void maturityRepaysPrincipalAndClosesHoldings() {
        var issued = CorporateBondManager.issue(owner, companyId, "BC3", 100_000, 2, 2_000, 0, 1, 5, 2);
        assertTrue(issued.success(), issued.message());
        assertTrue(CorporateBondManager.subscribe(owner, issued.id(), 1).success());
        CorporateBondManager.processDay(1);
        CorporateBond bond = CorporateBondManager.bonds().get(issued.id());
        long before = AccountManager.getBalance(owner);
        CorporateBondManager.processDay(5);
        assertEquals(BondStatus.MATURED, bond.status());
        assertEquals(before + 100_219, AccountManager.getBalance(owner));
        assertTrue(bond.holdings().isEmpty());
    }

    @Test void maturityPaysFinalStubCouponWhenIntervalExceedsWholeTerm() {
        var issued = CorporateBondManager.issue(owner, companyId, "BC4", 100_000, 1, 2_000, 0, 1, 5, 10);
        assertTrue(issued.success(), issued.message());
        assertTrue(CorporateBondManager.subscribe(owner, issued.id(), 1).success());
        CorporateBondManager.processDay(1);
        long before = AccountManager.getBalance(owner);
        CorporateBondManager.processDay(5);
        assertEquals(before + 100_219, AccountManager.getBalance(owner));
        assertEquals(BondStatus.MATURED, CorporateBondManager.bonds().get(issued.id()).status());
    }

    @Test void subscriptionOffersReserveDebtCapacityForBondsAndLoans() {
        var first = CorporateBondManager.issue(owner, companyId, "CAP1", 100_000, 4, 2_000, 0, 1, 10, 2);
        assertTrue(first.success(), first.message());
        var second = CorporateBondManager.issue(owner, companyId, "CAP2", 100_000, 4, 2_000, 0, 1, 10, 2);
        assertFalse(second.success());
        assertFalse(CompanyLoanManager.apply(owner, companyId, 400_000, 0, 10, 2).success());
    }
}
