package finance.fixedincome;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.policy.MonetaryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CentralBankBillManagerTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000004301");
    private final UUID blockedPlayer = UUID.fromString("00000000-0000-0000-0000-000000004302");
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); AccountManager.deposit(player, 100_000); }

    @Test void yieldCurveIsOrderedAndRateLocksAtSubscription() {
        assertTrue(YieldCurveService.yieldBasisPoints(7) < YieldCurveService.yieldBasisPoints(30));
        assertTrue(YieldCurveService.yieldBasisPoints(30) < YieldCurveService.yieldBasisPoints(90));
        var result = CentralBankBillManager.subscribe(player, 30, 10_000, 2);
        CentralBankBill bill = CentralBankBillManager.bills().get(result.billId());
        int locked = bill.annualRateBasisPoints();
        MonetaryPolicyService.restore(2_000, java.util.List.of());
        assertEquals(locked, bill.annualRateBasisPoints());
        assertTrue(YieldCurveService.yieldBasisPoints(30) > locked);
    }

    @Test void maturityPaysOnceAndCreatesOnlyReserveShortfall() {
        var result = CentralBankBillManager.subscribe(player, 7, 10_000, 0);
        CentralBankBill bill = CentralBankBillManager.bills().get(result.billId());
        long due = CentralBankBillManager.expectedMaturityValue(bill, player);
        long playerBefore = AccountManager.getBalance(player);
        long reserve = AccountManager.getBalance(CentralBank.UUID);
        AccountManager.withdraw(CentralBank.UUID, reserve);
        CentralBankBillManager.processDay(7);
        assertEquals(playerBefore + due, AccountManager.getBalance(player));
        assertEquals(due, CentralBankBillManager.cumulativePolicyIssuance());
        assertEquals(CentralBankBillStatus.MATURED, bill.status());
        CentralBankBillManager.processDay(8);
        assertEquals(playerBefore + due, AccountManager.getBalance(player));
        assertEquals(1, AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.CENTRAL_BANK_MONETARY_ISSUE).count());
    }

    @Test void availableReserveReducesPolicyIssuanceToExactGap() {
        var result = CentralBankBillManager.subscribe(player, 7, 10_000, 0);
        CentralBankBill bill = CentralBankBillManager.bills().get(result.billId());
        long due = CentralBankBillManager.expectedMaturityValue(bill, player);
        long reserve = AccountManager.getBalance(CentralBank.UUID);
        AccountManager.withdraw(CentralBank.UUID, reserve - 250);
        CentralBankBillManager.processDay(7);
        assertEquals(due - 250, CentralBankBillManager.lastPolicyIssuance());
    }

    @Test void maturityPrecheckPreventsPartialPaymentWhenAnyHolderCannotReceive() {
        CentralBankBillManager.subscribe(player, 7, 10_000, 0);
        AccountManager.deposit(blockedPlayer, 20_000);
        CentralBankBillManager.subscribe(blockedPlayer, 7, 10_000, 0);
        AccountManager.getAccount(blockedPlayer).setBalance(Long.MAX_VALUE);
        long playerBefore = AccountManager.getBalance(player);
        long reserveBefore = AccountManager.getBalance(CentralBank.UUID);

        CentralBankBillManager.processDay(7);

        assertEquals(playerBefore, AccountManager.getBalance(player));
        assertEquals(reserveBefore, AccountManager.getBalance(CentralBank.UUID));
        assertEquals(CentralBankBillStatus.ACTIVE, CentralBankBillManager.bills().values().iterator().next().status());
        assertEquals(0, CentralBankBillManager.cumulativePolicyIssuance());
    }

    @Test void restoredIssuanceCannotClaimLastAmountAboveCumulativeAmount() {
        CentralBankBillManager.restorePolicyIssuance(100, 200, 5);
        assertEquals(100, CentralBankBillManager.cumulativePolicyIssuance());
        assertEquals(100, CentralBankBillManager.lastPolicyIssuance());
        assertEquals(5, CentralBankBillManager.lastPolicyIssuanceDay());
    }
}
