package finance.gameplay.company.factory;

import java.util.Locale;

/** 生产方式档位：手工 → 改良 → 工业，分别要求设施 1/2/3 级。 */
public enum ProductionTier {
    MANUAL(1, "手工"),
    IMPROVED(2, "改良"),
    INDUSTRIAL(3, "工业");

    private final int requiredLevel;
    private final String displayName;

    ProductionTier(int requiredLevel, String displayName) {
        this.requiredLevel = requiredLevel;
        this.displayName = displayName;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public String displayName() {
        return displayName;
    }

    public String translationKey() {
        return "screen.finance.factory.tier." + name().toLowerCase(Locale.ROOT);
    }

    public static ProductionTier parse(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
