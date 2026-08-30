package finance.gameplay.company.capital;

/**
 * Capital project lifecycle. All transitions are owned by
 * {@link CapitalProjectService}; GUI, packets and blocks never mutate status
 * directly.
 *
 * <pre>
 * create → AUTHORIZATION_REQUIRED (governance needed) or DRAFT
 * AUTHORIZATION_REQUIRED → DRAFT            (approved proposal consumed once)
 * DRAFT → FUNDING                            (bond issued / loan granted / issue linked)
 * DRAFT → FUNDED → MATERIALS_PENDING         (retained earnings transfer succeeds)
 * FUNDING → FUNDED → MATERIALS_PENDING       (real raised funds reach escrow)
 * FUNDING → FAILED_RECOVERABLE               (funding refunded / underfunded)
 * MATERIALS_PENDING ↔ READY                  (material availability re-checked)
 * MATERIALS_PENDING|READY → COMPLETED        (atomic world execution)
 * any non-terminal → CANCELLED               (unused escrow refunded)
 * FAILED_RECOVERABLE → MATERIALS_PENDING     (recover with escrow intact)
 * </pre>
 */
public enum CapitalProjectStatus {
    DRAFT,
    AUTHORIZATION_REQUIRED,
    FUNDING,
    FUNDED,
    MATERIALS_PENDING,
    READY,
    COMPLETED,
    CANCELLED,
    FAILED_RECOVERABLE;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
