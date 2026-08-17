package finance.money;

import finance.account.Account;
import finance.account.AccountManager;
import finance.company.Company;

import java.util.UUID;

public final class MoneyEndpoints {
    private MoneyEndpoints() {
    }

    public static MoneyEndpoint account(UUID playerId) {
        if (playerId == null) return null;
        Account account = AccountManager.getAccount(playerId);
        return new MoneyEndpoint() {
            public String id() { return "account:" + playerId; }
            public long balance() { return account.getBalance(); }
            public boolean canDebit(long amount) { return amount > 0 && account.getBalance() >= amount; }
            public boolean canCredit(long amount) { return account.canDeposit(amount); }
            public boolean debit(long amount) { return account.withdraw(amount); }
            public boolean credit(long amount) { return account.deposit(amount); }
        };
    }

    public static MoneyEndpoint company(Company company) {
        if (company == null) return null;
        return new MoneyEndpoint() {
            public String id() { return "company:" + company.getCompanyId(); }
            public long balance() { return company.getCash(); }
            public boolean canDebit(long amount) { return amount > 0 && company.getCash() >= amount; }
            public boolean canCredit(long amount) { return company.canDeposit(amount); }
            public boolean debit(long amount) { return company.withdraw(amount); }
            public boolean credit(long amount) { return company.deposit(amount); }
        };
    }
}
