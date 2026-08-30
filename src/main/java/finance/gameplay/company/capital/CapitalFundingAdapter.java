package finance.gameplay.company.capital;

import finance.company.Company;

import java.util.UUID;

/**
 * Adapter between the capital project state machine and one real funding
 * source. Adapters never mint money: initiation calls the existing manager
 * entry points and sync only reacts to that manager's persisted state.
 */
public interface CapitalFundingAdapter {

    /** Attempts to start funding. Keeps the project in DRAFT when retryable. */
    CapitalProjectActionResult initiate(WorldCapitalProject project, Company company,
                                        UUID bankId, long day);

    /** Daily reconciliation while the project is in FUNDING. */
    FundingSync sync(WorldCapitalProject project, Company company, long day);

    enum SyncState { PENDING, FUNDED, FAILED }

    record FundingSync(SyncState state, String messageKey) {
        static FundingSync pending() { return new FundingSync(SyncState.PENDING, ""); }
        static FundingSync funded() { return new FundingSync(SyncState.FUNDED, ""); }
        static FundingSync failed(String messageKey) { return new FundingSync(SyncState.FAILED, messageKey); }
    }
}
