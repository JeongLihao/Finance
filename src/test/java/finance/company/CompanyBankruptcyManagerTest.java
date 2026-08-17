package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockOrderManager;
import finance.stock.StockPortfolioManager;
import finance.debt.BondStatus;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.governance.CapitalActionStatus;
import finance.governance.CorporateActionManager;
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

    @Test
    void liquidationValuationSaturatesInsteadOfWrappingNegative() {
        CommodityRegistry.register(new Commodity("overflow_asset", "minecraft:diamond",
                "Overflow Asset", CommodityCategory.MISCELLANEOUS, 10));
        NpcMarketMaker.putMarketPrice("overflow_asset", new MarketPrice("overflow_asset", 10, 0.02));
        Company company = publicCompany(Long.MAX_VALUE - 5);
        company.addInventory("overflow_asset", 1);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 1);

        assertEquals(Long.MAX_VALUE, result.liquidationValue());
    }

    @Test
    void partialOwnershipOnlyReceivesDeclaredShareOfLiquidation() {
        Company company = publicCompany(1_000);
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 100, 10);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 1);

        assertEquals(100, result.paid());
        assertEquals(1_100, AccountManager.getBalance(HOLDER_A));
    }

    @Test
    void bondCreditorsArePaidBeforeShareholdersWithoutExceedingPool() {
        Company company = publicCompany(1_000);
        UUID creditor = UUID.fromString("00000000-0000-0000-0000-000000006004");
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 1_000, 10);
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "BRK1", 600, 1,
                1_000, 0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(creditor, 1);
        CorporateBondManager.putDirect(bond);
        long creditorBefore = AccountManager.getBalance(creditor);
        long shareholderBefore = AccountManager.getBalance(HOLDER_A);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertEquals(600, AccountManager.getBalance(creditor) - creditorBefore);
        assertEquals(400, AccountManager.getBalance(HOLDER_A) - shareholderBefore);
        assertEquals(1_000, result.paid());
    }

    @Test
    void creditorOverflowAbortsBankruptcyWithoutDeletingClaimsOrPayingShareholders() {
        Company company = publicCompany(1_000);
        UUID creditor = UUID.fromString("00000000-0000-0000-0000-000000006005");
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 1_000, 10);
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "BRK2", 600, 1,
                1_000, 0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(creditor, 1);
        CorporateBondManager.putDirect(bond);
        AccountManager.getAccount(creditor).setBalance(Long.MAX_VALUE);
        long shareholderBefore = AccountManager.getBalance(HOLDER_A);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertFalse(result.success());
        assertEquals(company, CompanyManager.getCompany(COMPANY_ID));
        assertEquals(1, bond.holdings().get(creditor));
        assertEquals(BondStatus.ACTIVE, bond.status());
        assertEquals(shareholderBefore, AccountManager.getBalance(HOLDER_A));
        assertEquals(Long.MAX_VALUE, AccountManager.getBalance(creditor));
    }

    @Test
    void shareholderOverflowAbortsBeforeAnyCreditorPaymentOrDelisting() {
        Company company = publicCompany(1_000);
        UUID creditor = UUID.fromString("00000000-0000-0000-0000-000000006006");
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "BRK4", 600, 1,
                1_000, 0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(creditor, 1);
        CorporateBondManager.putDirect(bond);
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 1_000, 10);
        AccountManager.getAccount(HOLDER_A).setBalance(Long.MAX_VALUE);
        long creditorBefore = AccountManager.getBalance(creditor);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertFalse(result.success());
        assertEquals(creditorBefore, AccountManager.getBalance(creditor));
        assertTrue(bond.recoveredPrincipal().isEmpty());
        assertEquals(BondStatus.ACTIVE, bond.status());
        assertEquals(company, CompanyManager.getCompany(COMPANY_ID));
        assertEquals(company.getCompanyId(), StockMarketManager.getStock("BRK").getCompanyId());
        assertEquals(1_000, StockPortfolioManager.getHolding(HOLDER_A, "BRK").getQuantity());
    }

    @Test
    void bankruptcyRecoveryNeverAttachesToSubscriptionBond() {
        Company company = publicCompany(600);
        UUID creditor = UUID.fromString("00000000-0000-0000-0000-000000006007");
        CorporateBond subscription = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "SUB1", 600, 1,
                1_000, 0, 5, 20, 5, 10, BondStatus.SUBSCRIPTION, 600);
        subscription.putHoldingDirect(creditor, 1);
        CorporateBond active = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "ACT1", 600, 1,
                1_000, 0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        active.putHoldingDirect(creditor, 1);
        CorporateBondManager.putDirect(subscription);
        CorporateBondManager.putDirect(active);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertTrue(result.success());
        assertTrue(subscription.recoveredPrincipal().isEmpty());
        assertEquals(BondStatus.SUBSCRIPTION, subscription.status());
        assertEquals(600, active.recoveredPrincipal().get(creditor));
        assertEquals(BondStatus.DEFAULTED, active.status());
    }

    @Test
    void zeroAssetBankruptcyMarksDebtDefaultButRetainsUnpaidClaimHistory() {
        Company company = publicCompany(0);
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), COMPANY_ID, "BRK3", 600, 1,
                1_000, 0, 1, 20, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(HOLDER_B, 1);
        CorporateBondManager.putDirect(bond);

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertTrue(result.success());
        assertNull(CompanyManager.getCompany(COMPANY_ID));
        assertEquals(BondStatus.DEFAULTED, bond.status());
        assertEquals(1, bond.holdings().get(HOLDER_B));
        assertEquals(600, CorporateBondManager.outstandingPrincipal(COMPANY_ID));
    }

    @Test
    void bankruptcyReleasesOpenBuybackEscrowAndLockedSharesBeforeDelisting() {
        Company company = publicCompany(10_000);
        StockPortfolioManager.addHolding(HOLDER_A, "BRK", 100, 25);
        long holderCash = AccountManager.getBalance(HOLDER_A);
        CompanyProposal proposal=new CompanyProposal(COMPANY_ID,OWNER_ID,CompanyProposalType.SHARE_BUYBACK,
                "approved","",20,50,10,0,20,.5);
        proposal.finish(CompanyProposalStatus.PASSED,"approved");
        CompanyProposalManager.addProposalDirect(proposal);
        var opened = CorporateActionManager.startBuyback(OWNER_ID, COMPANY_ID, 20, 50, 10, 0,
                "proposal-"+proposal.getProposalId());
        assertTrue(opened.success());
        assertTrue(CorporateActionManager.acceptBuyback(HOLDER_A, opened.id(), 50, 0, "brk-accept").success());

        CompanyBankruptcyManager.LiquidationResult result = CompanyBankruptcyManager.bankrupt(company, 2);

        assertTrue(result.success());
        assertEquals(CapitalActionStatus.CANCELLED, CorporateActionManager.buybacks().get(opened.id()).status());
        assertTrue(AccountManager.getBalance(HOLDER_A) >= holderCash);
        assertNull(StockMarketManager.getStock("BRK"));
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
