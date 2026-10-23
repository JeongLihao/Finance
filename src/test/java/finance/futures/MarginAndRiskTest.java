package finance.futures;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.data.EconomySavedData;
import java.util.UUID;
import org.junit.jupiter.api.*;

class MarginAndRiskTest {
    private final UUID longOwner = UUID.fromString("00000000-0000-0000-0000-000000005401"),
            shortOwner = UUID.fromString("00000000-0000-0000-0000-000000005402");
    private FuturesContract contract;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.register(new Commodity("risk_future", "Risk", CommodityCategory.RAW_MATERIALS, 100));
        var r = FuturesMarketManager.createStandard("risk_future", 0, 10, 11);
        contract = FuturesMarketManager.contract(r.id());
        AccountManager.deposit(longOwner, 2_000);
        AccountManager.deposit(shortOwner, 2_000);
        MarginManager.deposit(longOwner, 1_000);
        MarginManager.deposit(shortOwner, 1_000);
    }

    private void open(long qty) {
        FuturesMarketManager.place(longOwner, contract.id(), FuturesOrderSide.BUY, 100, qty);
        FuturesMarketManager.place(shortOwner, contract.id(), FuturesOrderSide.SELL, 100, qty);
    }

    @Test
    void transfersConserveCashAndPositionsPreventExcessWithdrawal() {
        long ordinary = AccountManager.getBalance(longOwner),
                margin = MarginManager.account(longOwner).cashBalance();
        assertTrue(MarginManager.withdraw(longOwner, 100));
        assertEquals(ordinary + 100, AccountManager.getBalance(longOwner));
        assertEquals(margin - 100, MarginManager.account(longOwner).cashBalance());
        open(1);
        assertFalse(MarginManager.withdraw(longOwner, 801));
    }

    @Test
    void closedLossCannotBeWithdrawnBeforeDailySettlement() {
        open(1);
        // 多头在 90 平仓：亏的差价先记在待结算里，日终才从保证金扣
        FuturesMarketManager.place(longOwner, contract.id(), FuturesOrderSide.SELL, 90, 1);
        FuturesMarketManager.place(shortOwner, contract.id(), FuturesOrderSide.BUY, 90, 1);
        assertNull(MarginManager.findPosition(longOwner, contract.id()), "已经平仓");
        long loss = -MarginManager.pendingVariation(longOwner, contract.id());
        assertTrue(loss > 0, "平仓亏损还没结算");
        long cash = MarginManager.account(longOwner).cashBalance();
        assertEquals(cash - loss, MarginManager.availableToWithdraw(longOwner), "结算前亏的那部分不能取走");
        assertFalse(MarginManager.withdraw(longOwner, cash), "不能先把保证金全取走、让担保基金替你付亏损");
        assertTrue(MarginManager.withdraw(longOwner, cash - loss));
    }

    @Test
    void frozenOrderMarginCannotBeWithdrawn() {
        FuturesMarketManager.place(longOwner, contract.id(), FuturesOrderSide.BUY, 90, 2);
        assertTrue(MarginManager.account(longOwner).frozenForOrders() > 0);
        assertFalse(MarginManager.withdraw(longOwner, 900));
    }

    @Test
    void marginCallBlocksRiskIncreaseAndRecoveryReturnsNormal() {
        open(1);
        MarginManager.account(longOwner).forceDebit(900);
        assertEquals(MarginRiskStatus.MARGIN_CALL, FuturesRiskService.evaluate(longOwner, 1));
        assertFalse(FuturesMarketManager.place(longOwner, contract.id(), FuturesOrderSide.BUY, 100, 1)
                .success());
        assertTrue(MarginManager.account(longOwner).credit(100));
        assertEquals(MarginRiskStatus.NORMAL, FuturesRiskService.evaluate(longOwner, 2));
    }

    @Test
    void liquidationUsesAuditedSystemTakeoverAndStopsWhenRiskRemoved() {
        open(1);
        MarginManager.account(longOwner).forceDebit(950);
        long fund = FuturesClearingService.guaranteeFund();
        assertEquals(MarginRiskStatus.NORMAL, FuturesRiskService.evaluate(longOwner, 1));
        assertNull(MarginManager.findPosition(longOwner, contract.id()));
        assertNotNull(MarginManager.findPosition(FuturesClearingService.CLEARING_MEMBER_ID, contract.id()));
        assertTrue(FuturesClearingService.guaranteeFund() < fund);
    }

    @Test
    void insufficientGuaranteeFundDefaultsWithoutDeletingPosition() {
        open(1);
        MarginManager.account(longOwner).forceDebit(950);
        FuturesClearingService.restoreFund(0, 0, -1);
        assertEquals(MarginRiskStatus.DEFAULTED, FuturesRiskService.evaluate(longOwner, 1));
        assertNotNull(MarginManager.findPosition(longOwner, contract.id()));
    }
}
