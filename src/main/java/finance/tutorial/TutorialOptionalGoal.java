package finance.tutorial;

import java.util.Locale;
import java.util.Set;

/** Optional Minecraft-facing routes offered after the main onboarding path. */
public enum TutorialOptionalGoal {
    LOGISTICS("first_shipment"),
    SETTLEMENT("first_village_help"),
    EXPLORATION("field_survey"),
    ADVANCED_FINANCE("advanced_finance"),
    INDUSTRIAL_FINANCE("capital_project_complete"),
    RISK_MANAGEMENT("risk_summary_view");

    private final String event;

    TutorialOptionalGoal(String event) {
        this.event = event;
    }

    public String translationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static int completedMask(Set<String> events) {
        int mask = 0;
        for (TutorialOptionalGoal goal : values()) {
            if (events.contains(goal.event)) mask |= goal.bit();
        }
        return mask;
    }

    public static int validMask() {
        return (1 << values().length) - 1;
    }
}
