package finance.tutorial;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialStageTest {
    @Test
    void tutorialProgressAcceptsOnlyItsBoundedEventDomain() {
        assertTrue(TutorialProgressService.isRecognizedEvent("has_ledger"));
        assertTrue(TutorialProgressService.isRecognizedEvent("advanced_finance"));
        assertFalse(TutorialProgressService.isRecognizedEvent(null));
        assertFalse(TutorialProgressService.isRecognizedEvent(""));
        assertFalse(TutorialProgressService.isRecognizedEvent("unknown_or_corrupt_event"));
    }

    @Test
    void routeStopsAtFirstIncompleteMinecraftMilestone() {
        Set<String> events = new HashSet<>();
        assertEquals(TutorialStage.GET_LEDGER, TutorialStage.next(events));
        events.add("has_ledger");
        events.add("wallet_opened");
        events.add("has_market_terminal");
        assertEquals(TutorialStage.BUILD_WAREHOUSE, TutorialStage.next(events));
    }

    @Test
    void outOfOrderEventsAreRememberedButDoNotSkipPrerequisites() {
        Set<String> events = new HashSet<>(Set.of("first_trade", "company_member"));
        assertEquals(TutorialStage.GET_LEDGER, TutorialStage.next(events));
    }

    @Test
    void allNineMilestonesCompleteTheMainRoute() {
        Set<String> events = Set.of("has_ledger", "wallet_opened", "has_market_terminal",
                "warehouse_built", "warehouse_deposit", "first_trade", "first_contract",
                "company_member", "company_production");
        assertEquals(TutorialStage.COMPLETE, TutorialStage.next(events));
    }

    @Test
    void optionalRoutesProduceAStableBoundedBitMask() {
        assertEquals(0, TutorialOptionalGoal.completedMask(Set.of()));
        assertEquals(TutorialOptionalGoal.LOGISTICS.bit() | TutorialOptionalGoal.EXPLORATION.bit(),
                TutorialOptionalGoal.completedMask(Set.of("first_shipment", "field_survey", "unknown")));
        assertEquals(15, TutorialOptionalGoal.validMask());
    }
}
