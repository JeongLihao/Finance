package finance.insurance;

import finance.account.AccountManager;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.LoanStatus;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InsuranceManagerTest {
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000009901");
    private static final UUID COMPANY=UUID.fromString("00000000-0000-0000-0000-000000009902");
    private Company company;

    @BeforeEach void setup(){
        EconomySavedData.resetRuntimeState();
        company=new Company(COMPANY,"Insured Co",CompanyType.RAW_MATERIALS,100_000,OWNER);
        company.addInventory("iron",100);
        CompanyManager.registerDirect(company);
        NpcMarketMaker.putMarketPrice("iron",new MarketPrice("iron",100,0.05));
        AccountManager.deposit(OWNER,1_000_000);
    }
    @AfterEach void cleanup(){EconomySavedData.resetRuntimeState();}

    @Test void warehouseLossUsesRealInventoryAndCannotBeClaimedTwice(){
        var bought=InsuranceManager.purchase(OWNER,InsuranceProduct.INVENTORY_DISASTER,COMPANY,20_000,30,0,"buy-1");
        assertTrue(bought.success(),bought.message());
        assertFalse(InsuranceManager.purchase(OWNER,InsuranceProduct.INVENTORY_DISASTER,COMPANY,20_000,30,0,"buy-1").success());
        InsuranceManager.processDay(1);
        long before=AccountManager.getBalance(OWNER);
        assertTrue(InsuranceManager.createWarehouseAccident(COMPANY,"iron",25,2,7).success());
        assertEquals(75,company.getInventoryAmount("iron"));
        assertFalse(InsuranceManager.createWarehouseAccident(COMPANY,"iron",25,2,7).success());
        assertEquals(1,InsuranceManager.claims().size());
        assertEquals(1,InsuranceManager.processPayments(2));
        assertTrue(AccountManager.getBalance(OWNER)>before);
        assertEquals(ClaimStatus.PAID,InsuranceManager.claims().values().iterator().next().status());
    }

    @Test void insufficientPoolPreservesUnpaidClaimAcrossRestart(){
        assertTrue(InsuranceManager.purchase(OWNER,InsuranceProduct.INVENTORY_DISASTER,COMPANY,20_000,30,0,"buy-2").success());
        InsuranceManager.processDay(1);
        assertTrue(InsuranceManager.createWarehouseAccident(COMPANY,"iron",100,2,8).success());
        AccountManager.getAccount(InsurancePool.ACCOUNT_ID).setBalance(10);
        assertEquals(1,InsuranceManager.processPayments(2));
        InsuranceClaim claim=InsuranceManager.claims().values().iterator().next();
        assertEquals(ClaimStatus.PARTIALLY_PAID,claim.status());assertTrue(claim.unpaidAmount()>0);
        CompoundTag saved=new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();EconomySavedData.load(saved);
        InsuranceClaim restored=InsuranceManager.claims().values().iterator().next();
        assertEquals(ClaimStatus.PARTIALLY_PAID,restored.status());assertEquals(claim.unpaidAmount(),restored.unpaidAmount());
        AccountManager.deposit(InsurancePool.ACCOUNT_ID,restored.unpaidAmount());assertEquals(1,InsuranceManager.processPayments(2));assertEquals(ClaimStatus.PAID,restored.status());
    }

    @Test void cancellationStopsFutureCoverageAndDoesNotRefundPremium(){
        long before=AccountManager.getBalance(OWNER);var bought=InsuranceManager.purchase(OWNER,InsuranceProduct.INVENTORY_DISASTER,COMPANY,10_000,30,0,"cancel-1");assertTrue(bought.success());long afterPremium=AccountManager.getBalance(OWNER);assertTrue(afterPremium<before);
        assertTrue(InsuranceManager.cancel(OWNER,bought.id(),0).success());assertEquals(afterPremium,AccountManager.getBalance(OWNER));InsuranceManager.processDay(1);
        assertTrue(InsuranceManager.createWarehouseAccident(COMPANY,"iron",10,2,9).success());assertTrue(InsuranceManager.claims().isEmpty());
    }

    @Test void creditClaimReducesBothLoanContractAndBankLedger(){
        BankingManager.ensureDefaultBanks();CommercialBank bank=BankingManager.banks().values().iterator().next();
        var issued=CompanyLoanManager.applyCommercial(OWNER,COMPANY,bank.id(),20_000,0,20,2);assertTrue(issued.success(),issued.message());
        assertTrue(InsuranceManager.purchase(bank.id(),InsuranceProduct.BANK_LOAN_CREDIT,issued.id(),20_000,30,0,"credit-1").success());
        InsuranceManager.processDay(1);CompanyLoanManager.processDay(2);CompanyLoanManager.processDay(5);
        CompanyLoan loan=CompanyLoanManager.loans().get(issued.id());assertEquals(LoanStatus.DEFAULTED,loan.status());
        long contractBefore=loan.outstandingPrincipal(),ledgerBefore=bank.ledger().balanceSheet().companyLoans();
        InsuranceManager.processDay(5);
        assertTrue(loan.outstandingPrincipal()<contractBefore);
        assertEquals(contractBefore-loan.outstandingPrincipal(),ledgerBefore-bank.ledger().balanceSheet().companyLoans());
        assertTrue(bank.ledger().balanceSheet().balanced());
    }
}
