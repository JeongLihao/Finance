package finance.bondmarket;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.debt.BondStatus;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BondMarketManagerTest {
    private final UUID companyId = UUID.fromString("00000000-0000-0000-0000-000000004201");
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000004202");
    private final UUID sellerA = UUID.fromString("00000000-0000-0000-0000-000000004203");
    private final UUID sellerB = UUID.fromString("00000000-0000-0000-0000-000000004204");
    private final UUID buyer = UUID.fromString("00000000-0000-0000-0000-000000004205");
    private CorporateBond bond;

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        CompanyManager.registerDirect(new Company(companyId, "Trade Co", CompanyType.FOOD, 1_000_000, owner));
        bond = new CorporateBond(UUID.randomUUID(), companyId, "TRD1", 100, 100, 1_000,
                0, 1, 30, 5, 6, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(sellerA, 10); bond.putHoldingDirect(sellerB, 10);
        CorporateBondManager.putDirect(bond);
        AccountManager.deposit(buyer, 10_000);
    }

    @Test void partialMatchUsesRestingPriceAndRefundsPriceImprovement() {
        long buyerBefore = AccountManager.getBalance(buyer);
        long sellerBefore = AccountManager.getBalance(sellerA);
        assertTrue(BondMarketManager.placeSell(sellerA, bond.id(), 90, 5).success());
        assertTrue(BondMarketManager.placeBuy(buyer, bond.id(), 100, 3).success());
        assertEquals(buyerBefore - 270, AccountManager.getBalance(buyer));
        assertEquals(sellerBefore + 270, AccountManager.getBalance(sellerA));
        assertEquals(3, BondPortfolioManager.total(bond.id(), buyer));
        assertEquals(2, BondMarketManager.orders().get(0).remainingQuantity());
        assertEquals(2, BondPortfolioManager.position(bond.id(), sellerA).frozenQuantity());
    }

    @Test void priceThenSequencePriorityAndSelfTradePrevention() {
        BondMarketManager.placeSell(sellerA, bond.id(), 95, 2);
        BondMarketManager.placeSell(sellerB, bond.id(), 90, 2);
        BondMarketManager.placeBuy(buyer, bond.id(), 100, 2);
        assertEquals(sellerB, BondMarketManager.trades().get(0).sellerId());

        BondMarketManager.placeBuy(sellerA, bond.id(), 100, 1);
        assertTrue(BondMarketManager.orders().stream().anyMatch(o -> o.playerId().equals(sellerA)));
    }

    @Test void equalPriceUsesOldestOrderFirst() {
        BondMarketManager.placeSell(sellerA, bond.id(), 90, 2);
        BondMarketManager.placeSell(sellerB, bond.id(), 90, 2);
        BondMarketManager.placeBuy(buyer, bond.id(), 90, 2);
        assertEquals(1, BondMarketManager.trades().size());
        assertEquals(sellerA, BondMarketManager.trades().get(0).sellerId());
        assertEquals(sellerB, BondMarketManager.orders().get(0).playerId());
    }

    @Test void multiplicationAndSequenceOverflowDoNotFreezeFundsOrCreateOrders() {
        long before = AccountManager.getBalance(buyer);
        assertFalse(BondMarketManager.placeBuy(buyer, bond.id(), Long.MAX_VALUE, 2).success());
        assertEquals(before, AccountManager.getBalance(buyer));
        assertEquals(0, AccountManager.getAccount(buyer).getFrozenBalance());

        BondMarketManager.restoreSequence(Long.MAX_VALUE);
        assertFalse(BondMarketManager.placeBuy(buyer, bond.id(), 1, 1).success());
        assertEquals(before, AccountManager.getBalance(buyer));
        assertTrue(BondMarketManager.orders().isEmpty());
    }

    @Test void cancellationReleasesBothKindsOfLockedAssets() {
        long balance = AccountManager.getBalance(buyer);
        UUID buyOrder = BondMarketManager.placeBuy(buyer, bond.id(), 80, 2).orderId();
        assertEquals(balance - 160, AccountManager.getBalance(buyer));
        assertTrue(BondMarketManager.cancel(buyer, buyOrder));
        assertEquals(balance, AccountManager.getBalance(buyer));
        UUID sellOrder = BondMarketManager.placeSell(sellerA, bond.id(), 120, 3).orderId();
        assertEquals(7, BondPortfolioManager.available(bond.id(), sellerA));
        assertTrue(BondMarketManager.cancel(sellerA, sellOrder));
        assertEquals(10, BondPortfolioManager.available(bond.id(), sellerA));
    }

    @Test void sellerOverflowDoesNotAdvanceOrdersOrAssets() {
        AccountManager.getAccount(sellerA).setBalance(Long.MAX_VALUE);
        BondMarketManager.placeSell(sellerA, bond.id(), 90, 2);
        BondMarketManager.placeBuy(buyer, bond.id(), 100, 2);
        assertTrue(BondMarketManager.trades().isEmpty());
        assertEquals(10, BondPortfolioManager.total(bond.id(), sellerA));
        assertEquals(0, BondPortfolioManager.total(bond.id(), buyer));
    }

    @Test void maturityCancelsOrdersAndReleasesLocks() {
        BondMarketManager.placeSell(sellerA, bond.id(), 120, 2);
        CorporateBondManager.processDay(30);
        assertTrue(BondMarketManager.orders().isEmpty());
        assertEquals(BondStatus.MATURED, bond.status());
    }
}
