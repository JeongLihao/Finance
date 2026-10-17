package finance.macro;

/** 法律组。每组同时只有一个级别生效。 */
public enum LawGroup {
    TRADE("贸易政策"),
    LABOR("劳工权利"),
    WELFARE("社会福利"),
    TAXATION("税制");

    private final String displayName;

    LawGroup(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    public LawLevel defaultLevel() {
        return switch (this) {
            case TRADE -> LawLevel.MERCANTILISM;
            case LABOR -> LawLevel.NO_LABOR_PROTECTION;
            case WELFARE -> LawLevel.NO_WELFARE;
            case TAXATION -> LawLevel.MEDIUM_TAX;
        };
    }
}
