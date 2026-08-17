package finance.account;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSafetyTest {

    @Test
    void depositOverflowLeavesBalanceUnchanged() {
        Account account = new Account(UUID.randomUUID());
        assertTrue(account.setBalance(Long.MAX_VALUE));

        assertFalse(account.deposit(1));
        assertEquals(Long.MAX_VALUE, account.getBalance());
    }

    @Test
    void frozenBalanceOverflowLeavesBothBalancesUnchanged() {
        Account account = new Account(UUID.randomUUID());
        assertTrue(account.setBalance(Long.MAX_VALUE));
        assertTrue(account.freezeFunds(Long.MAX_VALUE));
        assertTrue(account.deposit(1));

        assertFalse(account.freezeFunds(1));
        assertEquals(1, account.getBalance());
        assertEquals(Long.MAX_VALUE, account.getFrozenBalance());
    }

    @Test
    void rollbackSettlementRestoresExactFrozenStateWithoutDoubleCreditingPayment() {
        Account account = new Account(UUID.randomUUID());
        assertTrue(account.setBalance(100));
        assertTrue(account.freezeFunds(80));
        assertTrue(account.settleFrozenFunds(80, 60));
        assertEquals(40, account.getBalance());
        assertEquals(0, account.getFrozenBalance());

        assertTrue(account.rollbackFrozenSettlement(80, 60));
        assertEquals(20, account.getBalance());
        assertEquals(80, account.getFrozenBalance());
    }
}
