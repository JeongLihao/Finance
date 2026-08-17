package finance.bank;

public enum BankLedgerAccount {
    ASSET_RESERVE(Kind.ASSET), ASSET_COMPANY_LOAN(Kind.ASSET), ASSET_INTERBANK(Kind.ASSET),
    ASSET_BOND(Kind.ASSET), ASSET_OTHER(Kind.ASSET), CONTRA_LOAN_LOSS_RESERVE(Kind.CONTRA_ASSET),
    LIABILITY_DEMAND_DEPOSIT(Kind.LIABILITY), LIABILITY_TIME_DEPOSIT(Kind.LIABILITY),
    LIABILITY_INTERBANK(Kind.LIABILITY), LIABILITY_CENTRAL_BANK(Kind.LIABILITY),
    EQUITY_PAID_IN(Kind.EQUITY), EQUITY_RETAINED(Kind.EQUITY),
    EXPENSE_INTEREST(Kind.EXPENSE), EXPENSE_CREDIT_LOSS(Kind.EXPENSE), EXPENSE_INSURANCE(Kind.EXPENSE),
    INCOME_INTEREST(Kind.INCOME), INCOME_FEE(Kind.INCOME);

    public enum Kind { ASSET, CONTRA_ASSET, LIABILITY, EQUITY, EXPENSE, INCOME }
    private final Kind kind;
    BankLedgerAccount(Kind kind) { this.kind = kind; }
    public Kind kind() { return kind; }
    public boolean debitIncreases() { return kind == Kind.ASSET || kind == Kind.EXPENSE; }
}
