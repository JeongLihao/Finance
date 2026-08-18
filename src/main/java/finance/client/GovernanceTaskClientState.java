package finance.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-only request state for cross-company restructuring tasks. */
public final class GovernanceTaskClientState {
    private static final Set<UUID> PENDING = new HashSet<>();
    private static final Set<UUID> SUCCEEDED = new HashSet<>();

    private GovernanceTaskClientState() {}

    public static synchronized boolean begin(UUID proposalId) {
        return proposalId != null && !SUCCEEDED.contains(proposalId) && PENDING.add(proposalId);
    }

    public static synchronized void complete(UUID proposalId, boolean success) {
        if (proposalId == null) return;
        PENDING.remove(proposalId);
        if (success) SUCCEEDED.add(proposalId);
    }

    public static synchronized boolean isPending(UUID proposalId) { return PENDING.contains(proposalId); }
    public static synchronized boolean hasSucceeded(UUID proposalId) { return SUCCEEDED.contains(proposalId); }
    public static synchronized void clear() { PENDING.clear(); SUCCEEDED.clear(); }
}
