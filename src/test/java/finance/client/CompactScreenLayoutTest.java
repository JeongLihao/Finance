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
        // “更多操作”宽 384，只在常见的 426 以上宽度里放得下；高度必须放进 240，否则上下会被裁掉
        assertTrue(PlayerServicesScreen.PANEL_HEIGHT <= 240, "player services height");
        assertFitsMinimum(FinanceGuideScreen.PANEL_WIDTH, FinanceGuideScreen.PANEL_HEIGHT);
        assertFitsMinimum(NewspaperScreen.PANEL_WIDTH, NewspaperScreen.PANEL_HEIGHT);
    }

    @Test
    void financeCenterIsNotScaledAtCommonAutoGuiSizes() {
        // 854x480、1280x720、2560x1440、3840x2160 在“自动”界面尺寸下都是 426~427x240；1366x768 是 455x256。
        // 缩放必须正好是 1，否则像素字体会被非整数缩放弄糊
        for (int[] size : new int[][]{{427, 240}, {426, 240}, {455, 256}, {480, 270}, {533, 300}}) {
            assertEquals(1.0F, FinanceScreen.fitScale(size[0], size[1]), size[0] + "x" + size[1]);
        }
    }

    @Test
    void shiftedTabContentStaysBetweenHeaderAndPanelBottom() {
        // 页签内容按原坐标编写、整体上移 CONTENT_SHIFT：最上面（CONTENT_Y - 2）不能压到页签栏分隔线，
        // 最下面（宏观页签按钮底边 241）不能超出面板
        assertTrue(FinanceGui.CONTENT_Y - 2 - FinanceGui.CONTENT_SHIFT > FinanceGui.HEADER_H);
        assertTrue(241 - FinanceGui.CONTENT_SHIFT <= FinanceGui.PANEL_HEIGHT - 4);
        assertEquals(FinanceGui.PANEL_HEIGHT + FinanceGui.CONTENT_SHIFT, FinanceGui.LAYOUT_HEIGHT);
        assertTrue(FinanceGui.TAB_Y + FinanceGui.TAB_H <= FinanceGui.HEADER_H);
        assertTrue(FinanceScreen.MORE_BUTTON_Y + 13 <= FinanceGui.TAB_Y - 1, "更多操作按钮留在标题行内");
    }

    @Test
    void advancedScreenScalesInsideSmallGuiArea() {
        float scale = FinanceScreen.fitScale(320, 240);
        assertTrue(scale > 0.0F && scale < 1.0F);
        assertTrue(Math.round(400 * scale) <= 312);
        assertTrue(Math.round(FinanceGui.PANEL_HEIGHT * scale) <= 232);
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
        assertEquals(TutorialOptionalGoal.SETTLEMENT, TutorialHubScreen.optionalGoalAt(160, 45));
        assertEquals(TutorialOptionalGoal.ADVANCED_FINANCE, TutorialHubScreen.optionalGoalAt(160, 90));
        assertEquals(TutorialOptionalGoal.RISK_MANAGEMENT, TutorialHubScreen.optionalGoalAt(160, 135));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(154, 45));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(307, 45));
        assertEquals(null, TutorialHubScreen.optionalGoalAt(20, 185));
    }

    private void assertFitsMinimum(int width, int height) {
        assertTrue(width <= 320, "screen width " + width);
        assertTrue(height <= 240, "screen height " + height);
    }
}
