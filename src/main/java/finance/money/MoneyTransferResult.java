package finance.money;

public record MoneyTransferResult(boolean success, long amount, Failure failure) {
    public enum Failure {
        NONE,
        INVALID_ENDPOINT,
        SAME_ENDPOINT,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        RECEIVER_OVERFLOW,
        DEBIT_FAILED,
        CREDIT_FAILED,
        ROLLBACK_FAILED
    }

    public static MoneyTransferResult success(long amount) {
        return new MoneyTransferResult(true, amount, Failure.NONE);
    }

    public static MoneyTransferResult failure(long amount, Failure failure) {
        return new MoneyTransferResult(false, amount, failure);
    }
}
