package finance.money;

import finance.data.EconomySavedData;

/** Single entry point for moving available money between economic entities. */
public final class MoneyTransferService {
    private MoneyTransferService() {
    }

    public static MoneyTransferResult transfer(MoneyEndpoint from, MoneyEndpoint to, long amount) {
        if (from == null || to == null) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.INVALID_ENDPOINT);
        }
        if (from == to || from.id().equals(to.id())) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.SAME_ENDPOINT);
        }
        if (amount <= 0) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.INVALID_AMOUNT);
        }
        if (!from.canDebit(amount)) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.INSUFFICIENT_FUNDS);
        }
        if (!to.canCredit(amount)) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.RECEIVER_OVERFLOW);
        }
        if (!from.debit(amount)) {
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.DEBIT_FAILED);
        }
        if (!to.credit(amount)) {
            if (!from.credit(amount)) {
                return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.ROLLBACK_FAILED);
            }
            return MoneyTransferResult.failure(amount, MoneyTransferResult.Failure.CREDIT_FAILED);
        }
        EconomySavedData.markDirty();
        return MoneyTransferResult.success(amount);
    }
}
