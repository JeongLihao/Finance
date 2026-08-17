package finance.money;

/**
 * A bounded holder of non-negative money. Implementations must not mutate state
 * when a can* check fails.
 */
public interface MoneyEndpoint {
    String id();

    long balance();

    boolean canDebit(long amount);

    boolean canCredit(long amount);

    boolean debit(long amount);

    boolean credit(long amount);
}
