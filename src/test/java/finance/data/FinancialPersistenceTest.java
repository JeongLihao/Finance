package finance.data;

import finance.cycle.FinancialCycleService;
import finance.debt.*;
import finance.index.MarketIndexService;
import finance.policy.MonetaryPolicyService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.account.AccountManager;
import finance.bondmarket.BondMarketManager;
import finance.bondmarket.BondPortfolioManager;
import finance.fixedincome.CentralBankBillManager;

class FinancialPersistenceTest {
    private final UUID company = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000802");
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void saveAndLoadPreservesCyclePolicyIndicesBondsAndLoans() {
        CompanyManager.registerDirect(new Company(company, "PersistCo", CompanyType.FOOD, 1_000_000, player));
        FinancialCycleService.restoreLastProcessedDay(9);
        FinancialCycleService.restoreLastClosedMarketDay(8);
        FinancialCycleService.restoreObservedMarketDay(9);
        MonetaryPolicyService.setBenchmarkRate(2, 650, "test");
        MarketIndexService.state("stock:composite").close(3, BigDecimal.valueOf(5000), "A:10;");
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), company, "T1", 100, 10, 900, 1, 2, 20, 5, 7, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(player, 4); CorporateBondManager.putDirect(bond);
        CompanyLoan loan = new CompanyLoan(UUID.randomUUID(), company, 500, 1_000, 1, 20, 5, 450, 10, 3, 6, -1, LoanStatus.ACTIVE);
        CompanyLoanManager.putDirect(loan);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);

        assertEquals(9, FinancialCycleService.lastProcessedDay());
        assertEquals(8, FinancialCycleService.lastClosedMarketDay());
        assertEquals(9, FinancialCycleService.observedMarketDay());
        assertEquals(650, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1000, MarketIndexService.state("stock:composite").latest().value(), 0.0001);
        assertEquals(4, CorporateBondManager.bonds().get(bond.id()).holdings().get(player));
        assertEquals(450, CompanyLoanManager.loans().get(loan.id()).outstandingPrincipal());
    }

    @Test void corruptFinancialRecordsAreSkipped() {
        CompoundTag root = new CompoundTag();
        net.minecraft.nbt.ListTag bonds = new net.minecraft.nbt.ListTag();
        CompoundTag bad = new CompoundTag(); bad.putString("Status", "NOT_A_STATUS"); bonds.add(bad); root.put("CorporateBonds", bonds);
        assertDoesNotThrow(() -> EconomySavedData.load(root));
        assertTrue(CorporateBondManager.bonds().isEmpty());
    }

    @Test void bondMarketOrdersPositionsAndSequenceSurviveRestart() {
        CompanyManager.registerDirect(new Company(company, "Market Persist", CompanyType.FOOD, 1_000_000, player));
        UUID seller = UUID.fromString("00000000-0000-0000-0000-000000000803");
        UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000000804");
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), company, "MKT1", 100, 20, 1_000,
                0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(seller, 10); CorporateBondManager.putDirect(bond);
        AccountManager.deposit(buyer, 1_000);
        BondMarketManager.placeSell(seller, bond.id(), 120, 2);
        BondMarketManager.placeBuy(buyer, bond.id(), 80, 2);
        long nextSequence = BondMarketManager.nextSequence();
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);

        assertEquals(2, BondMarketManager.orders().size());
        assertEquals(nextSequence, BondMarketManager.nextSequence());
        assertEquals(2, BondPortfolioManager.position(bond.id(), seller).frozenQuantity());
        assertEquals(160, AccountManager.getAccount(buyer).getFrozenBalance());
    }

    @Test void centralBankBillsAndPolicyIssuanceSurviveRestartWhileBadBillsAreSkipped() {
        AccountManager.deposit(player, 20_000);
        var subscribed = CentralBankBillManager.subscribe(player, 30, 10_000, 4);
        CentralBankBillManager.restorePolicyIssuance(500, 200, 3);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        CompoundTag bad = new CompoundTag(); bad.putUUID("Id", UUID.randomUUID()); bad.putInt("Term", 8);
        bad.putInt("RateBps", -1); bad.putLong("IssueDay", 1); bad.putLong("Maturity", 9);
        bad.putString("Status", "ACTIVE"); bad.put("Holdings", new ListTag());
        saved.getCompound("CentralBankBills").getList("Bills", Tag.TAG_COMPOUND).add(bad);

        EconomySavedData.resetRuntimeState(); EconomySavedData.load(saved);

        assertEquals(1, CentralBankBillManager.bills().size());
        assertNotNull(CentralBankBillManager.bills().get(subscribed.billId()));
        assertEquals(500, CentralBankBillManager.cumulativePolicyIssuance());
        assertEquals(200, CentralBankBillManager.lastPolicyIssuance());
    }

    @Test void debtLoaderRejectsBrokenEconomicInvariantsButKeepsValidContracts() {
        CompanyManager.registerDirect(new Company(company, "ValidateCo", CompanyType.FOOD, 1_000_000, player));
        CorporateBond validBond = new CorporateBond(UUID.randomUUID(), company, "GOOD", 100, 10, 1_000,
                0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        validBond.putHoldingDirect(player, 2); CorporateBondManager.putDirect(validBond);
        CompanyLoan validLoan = new CompanyLoan(UUID.randomUUID(), company, 500, 1_000, 1, 20, 5,
                450, 10, 3, 6, -1, LoanStatus.ACTIVE);
        CompanyLoanManager.putDirect(validLoan);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        CompoundTag badCoupon = bondTag(company, "BAD1", BondStatus.ACTIVE, 1, 1, 0);
        badCoupon.putInt("CouponBps", 0);
        saved.getList("CorporateBonds", Tag.TAG_COMPOUND).add(badCoupon);
        CompoundTag overHeld = bondTag(company, "BAD2", BondStatus.ACTIVE, 1, 2, 0);
        saved.getList("CorporateBonds", Tag.TAG_COMPOUND).add(overHeld);
        CompoundTag badEscrow = bondTag(company, "BAD3", BondStatus.SUBSCRIPTION, 100, 2, 1);
        badEscrow.putLong("Escrow", 99);
        saved.getList("CorporateBonds", Tag.TAG_COMPOUND).add(badEscrow);
        CompoundTag overflowingPrincipal = bondTag(company, "BAD4", BondStatus.ACTIVE, Long.MAX_VALUE, 1, 0);
        overflowingPrincipal.putLong("Face", 2);
        saved.getList("CorporateBonds", Tag.TAG_COMPOUND).add(overflowingPrincipal);
        CompoundTag badLoan = loanTag(company, LoanStatus.REPAID);
        badLoan.putLong("Outstanding", 10);
        saved.getList("CompanyLoans", Tag.TAG_COMPOUND).add(badLoan);

        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);

        assertEquals(1, CorporateBondManager.bonds().size());
        assertEquals(1, CompanyLoanManager.loans().size());
        assertNotNull(CorporateBondManager.bonds().get(validBond.id()));
        assertNotNull(CompanyLoanManager.loans().get(validLoan.id()));
    }

    private static CompoundTag bondTag(UUID company, String code, BondStatus status,
                                       long totalQuantity, long holdingQuantity, long escrowUnits) {
        CompoundTag t = new CompoundTag(); t.putUUID("Id", UUID.randomUUID()); t.putUUID("Company", company);
        t.putString("Code", code); t.putLong("Face", 100); t.putLong("Quantity", totalQuantity); t.putInt("CouponBps", 1_000);
        t.putLong("IssueDay", 0); t.putLong("SubscriptionEnd", 1); t.putLong("Maturity", 20);
        t.putInt("CouponInterval", 5); t.putLong("NextCoupon", 6); t.putLong("LastCoupon", 1);
        t.putString("Status", status.name()); t.putLong("Escrow", 100 * escrowUnits);
        ListTag holders = new ListTag(); CompoundTag h = new CompoundTag(); h.putUUID("Player", UUID.randomUUID());
        h.putLong("Quantity", holdingQuantity); holders.add(h); t.put("Holders", holders);
        return t;
    }

    private static CompoundTag loanTag(UUID company, LoanStatus status) {
        CompoundTag t = new CompoundTag(); t.putUUID("Id", UUID.randomUUID()); t.putUUID("Company", company);
        t.putLong("Original", 500); t.putInt("RateBps", 1_000); t.putLong("IssueDay", 1); t.putLong("Maturity", 20);
        t.putInt("PaymentInterval", 5); t.putLong("Outstanding", 0); t.putLong("Interest", 0);
        t.putLong("LastAccrual", 2); t.putLong("NextPayment", 6); t.putLong("DelinquentSince", -1);
        t.putString("Status", status.name()); return t;
    }
}
