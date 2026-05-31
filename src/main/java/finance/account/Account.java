package finance.account;

import java.util.UUID;

public class Account {

    private final UUID playerId;

    private long balance;

    private long frozenBalance;

    public Account(UUID playerId) {
        this.playerId = playerId;
        this.balance = 0;
        this.frozenBalance = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getBalance() {
        return balance;
    }

    public long getFrozenBalance() {
        return frozenBalance;
    }

    public void deposit(long amount) {
        if (amount <= 0) {
            return;
        }

        balance += amount;
    }

    public boolean withdraw(long amount) {
        if (amount <= 0) {
            return false;
        }

        if (balance < amount) {
            return false;
        }

        balance -= amount;
        return true;
    }
    public void setBalance(long balance) {
        this.balance = balance;
    }
}
