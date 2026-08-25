package finance.tutorial;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TutorialStageTest {
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
}
