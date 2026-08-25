package finance.tutorial;

import java.util.Set;

/** The short, Minecraft-first route through the mod's existing systems. */
public enum TutorialStage {
    GET_LEDGER("has_ledger"),
    OPEN_LEDGER("wallet_opened"),
    GET_MARKET_TERMINAL("has_market_terminal"),
    BUILD_WAREHOUSE("warehouse_built"),
    DEPOSIT_GOODS("warehouse_deposit"),
    COMPLETE_TRADE("first_trade"),
    COMPLETE_CONTRACT("first_contract"),
    JOIN_COMPANY("company_member"),
    RUN_PRODUCTION("company_production"),
    COMPLETE(null);

    private final String requiredEvent;

    TutorialStage(String requiredEvent) {
        this.requiredEvent = requiredEvent;
    }

    public String translationId() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static TutorialStage next(Set<String> completedEvents) {
        for (TutorialStage stage : values()) {
            if (stage.requiredEvent != null && !completedEvents.contains(stage.requiredEvent)) return stage;
        }
        return COMPLETE;
    }
}
