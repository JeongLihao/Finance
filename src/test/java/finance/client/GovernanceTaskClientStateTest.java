package finance.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceTaskClientStateTest {
    @AfterEach void clear() { GovernanceTaskClientState.clear(); }

    @Test void failedResponseKeepsTaskRetryable() {
        UUID id=UUID.randomUUID();
        assertTrue(GovernanceTaskClientState.begin(id));
        assertTrue(GovernanceTaskClientState.isPending(id));
        GovernanceTaskClientState.complete(id,false);
        assertFalse(GovernanceTaskClientState.isPending(id));
        assertFalse(GovernanceTaskClientState.hasSucceeded(id));
        assertTrue(GovernanceTaskClientState.begin(id));
    }

    @Test void successfulResponseHidesTaskAndPreventsDuplicateSubmission() {
        UUID id=UUID.randomUUID();
        assertTrue(GovernanceTaskClientState.begin(id));
        GovernanceTaskClientState.complete(id,true);
        assertTrue(GovernanceTaskClientState.hasSucceeded(id));
        assertFalse(GovernanceTaskClientState.begin(id));
    }
}
