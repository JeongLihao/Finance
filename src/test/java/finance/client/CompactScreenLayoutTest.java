package finance.client;

import finance.tutorial.TutorialOptionalGoal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactScreenLayoutTest {
    @Test
    void physicalEntryScreensFitMinecraftMinimumGuiArea() {
        assertFitsMinimum(WalletScreen.PANEL_WIDTH, WalletScreen.PANEL_HEIGHT);
        assertFitsMinimum(MarketOverviewScreen.PANEL_WIDTH, MarketOverviewScreen.PANEL_HEIGHT);
        assertFitsMinimum(WarehouseScreen.PANEL_WIDTH, WarehouseScreen.PANEL_HEIGHT);
        assertFitsMinimum(CompanyGameplayScreen.PANEL_WIDTH, CompanyGameplayScreen.PANEL_HEIGHT);
        assertFitsMinimum(SettlementScreen.PANEL_WIDTH, SettlementScreen.PANEL_HEIGHT);
        assertFitsMinimum(TutorialHubScreen.PANEL_WIDTH, TutorialHubScreen.PANEL_HEIGHT);
    }

    @Test
    void advancedScreenScalesInsideSmallGuiArea() {
        float scale = FinanceScreen.fitScale(320, 240);
        assertTrue(scale > 0.0F && scale < 1.0F);
        assertTrue(Math.round(400 * scale) <= 312);
        assertTrue(Math.round(250 * scale) <= 232);
        assertEquals(1.0F, FinanceScreen.fitScale(854, 480));
    }

    @Test
    void scrollingKeepsOffsetsAndSelectionsInsideVisibleWindow() {
        assertEquals(0, WarehouseScreen.clampOffset(-5, 20, 5));
        assertEquals(15, WarehouseScreen.clampOffset(99, 20, 5));
        assertEquals(7, WarehouseScreen.keepVisible(0, 7, 20, 5));
        assertEquals(11, WarehouseScreen.keepVisible(19, 7, 20, 5));

        assertEquals(3, CompanyGameplayScreen.clampOffset(3, 8, 5));
        assertEquals(5, CompanyGameplayScreen.keepVisible(1, 5, 8, 5));
        assertEquals(7, CompanyGameplayScreen.keepVisible(12, 5, 8, 5));
    }

    @Test
    void optionalTutorialCardsHaveCompactNonOverlappingClickAreas() {
        assertEquals(TutorialOptionalGoal.LOGISTICS, TutorialHubScreen.optionalGoalAt(8, 45));
        assertEquals(TutorialOptionalGoal.SETTLEMENT, TutorialHubScreen.optionalGoalAt(20, 80));
        assertEquals(TutorialOptionalGoal.ADVANCED_FINANCE, TutorialHubScreen.optionalGoalAt(301, 150));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(20, 77));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(307, 45));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(20, 185));
    }

    private void assertFitsMinimum(int width, int height) {
        assertTrue(width <= 320, "screen width " + width);
        assertTrue(height <= 240, "screen height " + height);
    }
}
