package finance.bank;

public record BankPolicy(int demandSpreadBps, int timeSpreadBps, int loanSpreadBps,
                         int targetReserveBps, int singleBorrowerLimitBps) {
    public BankPolicy {
        if (demandSpreadBps < 0 || timeSpreadBps < 0 || loanSpreadBps < 0 || targetReserveBps < 0
                || targetReserveBps > 10_000 || singleBorrowerLimitBps <= 0 || singleBorrowerLimitBps > 10_000)
            throw new IllegalArgumentException("invalid bank policy");
    }
    public static BankPolicy standard() { return new BankPolicy(150, 75, 250, 1_500, 2_500); }
}
