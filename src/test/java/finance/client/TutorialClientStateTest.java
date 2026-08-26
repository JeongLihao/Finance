package finance.client;

import finance.tutorial.TutorialStage;
import finance.tutorial.TutorialOptionalGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TutorialClientStateTest {
    @Test
    void onlyLiveForwardProgressCreatesCompletionFeedback() {
        assertFalse(TutorialClientState.shouldCelebrate(null, TutorialStage.GET_LEDGER));
        assertFalse(TutorialClientState.shouldCelebrate(TutorialStage.COMPLETE, TutorialStage.GET_LEDGER));
        assertFalse(TutorialClientState.shouldCelebrate(TutorialStage.COMPLETE, TutorialStage.COMPLETE));
        assertTrue(TutorialClientState.shouldCelebrate(TutorialStage.GET_LEDGER, TutorialStage.OPEN_LEDGER));
        assertTrue(TutorialClientState.shouldCelebrate(TutorialStage.RUN_PRODUCTION, TutorialStage.COMPLETE));
    }

    @Test
    void completedOptionalRouteSelectsTheNextIncompleteRoute() {
        TutorialClientState.clear();
        TutorialClientState.update(TutorialStage.COMPLETE, TutorialOptionalGoal.LOGISTICS.bit());
        assertTrue(TutorialClientState.optionalComplete(TutorialOptionalGoal.LOGISTICS));
        assertEquals(TutorialOptionalGoal.SETTLEMENT, TutorialClientState.nextOptionalGoal());
        assertEquals("finance.tutorial.optional.settlement",
                TutorialClientState.objectiveTranslationBase());
        TutorialClientState.clear();
    }

    @Test
    void playerCanTrackAnyIncompleteOptionalRoute() {
        TutorialClientState.clear();
        TutorialClientState.update(TutorialStage.COMPLETE, TutorialOptionalGoal.LOGISTICS.bit());
        TutorialClientState.trackOptionalGoal(TutorialOptionalGoal.EXPLORATION);
        assertEquals(TutorialOptionalGoal.EXPLORATION, TutorialClientState.trackedOptionalGoal());
        assertEquals(TutorialOptionalGoal.EXPLORATION, TutorialClientState.nextOptionalGoal());

        TutorialClientState.trackOptionalGoal(TutorialOptionalGoal.LOGISTICS);
        assertEquals(TutorialOptionalGoal.EXPLORATION, TutorialClientState.nextOptionalGoal(),
                "completed routes cannot replace the tracked objective");
        TutorialClientState.clear();
    }
}
