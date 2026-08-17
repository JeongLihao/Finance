package finance.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTransferServiceTest {
    @Test
    void successfulTransferMovesExactlyOnce() {
        FakeEndpoint from = new FakeEndpoint("from", 100);
        FakeEndpoint to = new FakeEndpoint("to", 5);
        assertTrue(MoneyTransferService.transfer(from, to, 40).success());
        assertEquals(60, from.balance());
        assertEquals(45, to.balance());
    }

    @Test
    void overflowAndInsufficientFundsLeaveBothSidesUnchanged() {
        FakeEndpoint from = new FakeEndpoint("from", 100);
        FakeEndpoint full = new FakeEndpoint("full", Long.MAX_VALUE);
        assertEquals(MoneyTransferResult.Failure.RECEIVER_OVERFLOW,
                MoneyTransferService.transfer(from, full, 1).failure());
        assertEquals(100, from.balance());
        assertEquals(Long.MAX_VALUE, full.balance());
        assertEquals(MoneyTransferResult.Failure.INSUFFICIENT_FUNDS,
                MoneyTransferService.transfer(from, new FakeEndpoint("to", 0), 101).failure());
        assertEquals(100, from.balance());
    }

    @Test
    void unexpectedCreditFailureRollsDebitBack() {
        FakeEndpoint from = new FakeEndpoint("from", 100);
        FakeEndpoint to = new FakeEndpoint("to", 5);
        to.failNextCredit = true;
        assertEquals(MoneyTransferResult.Failure.CREDIT_FAILED,
                MoneyTransferService.transfer(from, to, 40).failure());
        assertEquals(100, from.balance());
        assertEquals(5, to.balance());
    }

    private static final class FakeEndpoint implements MoneyEndpoint {
        private final String id;
        private long balance;
        private boolean failNextCredit;

        private FakeEndpoint(String id, long balance) { this.id = id; this.balance = balance; }
        public String id() { return id; }
        public long balance() { return balance; }
        public boolean canDebit(long amount) { return amount > 0 && balance >= amount; }
        public boolean canCredit(long amount) { return amount > 0 && balance <= Long.MAX_VALUE - amount; }
        public boolean debit(long amount) { if (!canDebit(amount)) return false; balance -= amount; return true; }
        public boolean credit(long amount) {
            if (failNextCredit) { failNextCredit = false; return false; }
            if (!canCredit(amount)) return false;
            balance += amount;
            return true;
        }
    }
}
