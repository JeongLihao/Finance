package finance.debt;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import finance.bank.*;

class CompanyLoanManagerTest {
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000711");
    private final UUID companyId = UUID.fromString("00000000-0000-0000-0000-000000000712");
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); CompanyManager.registerDirect(new Company(companyId,"LoanCo",CompanyType.RAW_MATERIALS,1_000_000,owner)); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void newLoanLocksRateAndCanBeRepaidEarly() {
        var result = CompanyLoanManager.apply(owner, companyId, 100_000, 0, 10, 3);
        assertTrue(result.success(), result.message());
        CompanyLoan loan = CompanyLoanManager.loans().get(result.id());
        int lockedRate = loan.annualRateBasisPoints();
        finance.policy.MonetaryPolicyService.setBenchmarkRate(1, 900, "tighten");
        assertEquals(lockedRate, loan.annualRateBasisPoints());
        assertTrue(CompanyLoanManager.repay(owner, loan.id(), Long.MAX_VALUE, 1).success());
        assertEquals(LoanStatus.REPAID, loan.status());
    }

    @Test void unpaidInterestBecomesDelinquentThenDefaulted() {
        var result = CompanyLoanManager.apply(owner, companyId, 100_000, 0, 20, 2);
        assertTrue(result.success(), result.message());
        CompanyLoan loan = CompanyLoanManager.loans().get(result.id());
        CompanyLoanManager.processDay(2);
        assertEquals(LoanStatus.DELINQUENT, loan.status());
        CompanyLoanManager.processDay(5);
        assertEquals(LoanStatus.DEFAULTED, loan.status());
    }

    @Test void commercialLoanCreatesCompanyDepositAndRoutesPrincipalBackToBank() {
        BankingManager.ensureDefaultBanks();CommercialBank bank=BankingManager.banks().values().iterator().next();
        var result=CompanyLoanManager.applyCommercial(owner,companyId,bank.id(),100_000,0,10,3);
        assertTrue(result.success(),result.message());CompanyLoan loan=CompanyLoanManager.loans().get(result.id());
        assertEquals(LoanLenderType.COMMERCIAL_BANK,loan.lenderType());assertEquals(bank.id(),loan.lenderId());
        assertEquals(100_000,bank.ledger().balanceSheet().companyLoans());
        assertEquals(100_000,BankingManager.demandAccount(bank.id(),companyId,CustomerType.COMPANY,0,false).balance());
        assertTrue(CompanyLoanManager.repay(owner,loan.id(),100_000,0).success());
        assertEquals(LoanStatus.REPAID,loan.status());assertEquals(0,bank.ledger().balanceSheet().companyLoans());
        assertTrue(bank.ledger().balanceSheet().balanced());
    }

    @Test void commercialDefaultBuildsLossReserveAndReducesBankEquity() {
        BankingManager.ensureDefaultBanks();CommercialBank bank=BankingManager.banks().values().iterator().next();long before=bank.ledger().balanceSheet().equity();
        var result=CompanyLoanManager.applyCommercial(owner,companyId,bank.id(),100_000,0,20,2);assertTrue(result.success(),result.message());
        CompanyLoanManager.processDay(2);CompanyLoanManager.processDay(5);assertEquals(LoanStatus.DEFAULTED,CompanyLoanManager.loans().get(result.id()).status());
        assertEquals(100_000,bank.ledger().balanceSheet().loanLossReserve());assertEquals(before-100_000,bank.ledger().balanceSheet().equity());assertTrue(bank.ledger().balanceSheet().balanced());
    }

    @Test void bankruptcyRecoveryIsCreditedToCommercialBankLedgerNotCentralBankWallet(){BankingManager.ensureDefaultBanks();CommercialBank bank=BankingManager.banks().values().iterator().next();var result=CompanyLoanManager.applyCommercial(owner,companyId,bank.id(),100_000,0,20,2);assertTrue(result.success());long reserve=bank.ledger().balanceSheet().reserves();var plan=CorporateBondManager.planBankruptcyClaims(companyId,50_000);assertEquals(bank.id(),plan.creditorAllocations().get(0).id());var settlement=CorporateBondManager.settleBankruptcyClaims(plan,java.util.List.of());assertTrue(settlement.complete());assertEquals(50_000,CompanyLoanManager.loans().get(result.id()).outstandingPrincipal());assertEquals(50_000,bank.ledger().balanceSheet().companyLoans());assertEquals(reserve+50_000,bank.ledger().balanceSheet().reserves());assertTrue(bank.ledger().balanceSheet().balanced());}

    @Test void defaultRecoveryReleasesMatchingLossReserveAndRestoresEquity(){BankingManager.ensureDefaultBanks();CommercialBank bank=BankingManager.banks().values().iterator().next();long initialEquity=bank.ledger().balanceSheet().equity();var result=CompanyLoanManager.applyCommercial(owner,companyId,bank.id(),100_000,0,20,2);CompanyLoanManager.processDay(2);CompanyLoanManager.processDay(5);assertEquals(100_000,bank.ledger().balanceSheet().loanLossReserve());var plan=CorporateBondManager.planBankruptcyClaims(companyId,50_000);assertTrue(CorporateBondManager.settleBankruptcyClaims(plan,java.util.List.of()).complete());assertEquals(50_000,bank.ledger().balanceSheet().loanLossReserve());assertEquals(initialEquity-50_000,bank.ledger().balanceSheet().equity());assertTrue(bank.ledger().balanceSheet().balanced());}
}
