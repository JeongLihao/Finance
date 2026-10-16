package finance.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 法律级别及其效果参数。 */
public enum LawLevel {
    FREE_TRADE(LawGroup.TRADE, "自由贸易", "free"),
    MERCANTILISM(LawGroup.TRADE, "重商主义", "mercantilism"),
    PROTECTIONISM(LawGroup.TRADE, "保护主义", "protectionism"),
    NO_LABOR_PROTECTION(LawGroup.LABOR, "无保护", "none"),
    MINIMUM_WAGE(LawGroup.LABOR, "最低工资", "minimum_wage"),
    WORKER_PROTECTION(LawGroup.LABOR, "劳工保护", "protection"),
    NO_WELFARE(LawGroup.WELFARE, "无福利", "none"),
    POOR_RELIEF(LawGroup.WELFARE, "济贫法", "poor_relief"),
    PUBLIC_HEALTH(LawGroup.WELFARE, "公共卫生", "public_health"),
    LOW_TAX(LawGroup.TAXATION, "低税", "low"),
    MEDIUM_TAX(LawGroup.TAXATION, "中税", "medium"),
    HIGH_TAX(LawGroup.TAXATION, "高税", "high");

    private final LawGroup group;
    private final String displayName;
    private final String key;

    LawLevel(LawGroup group, String displayName, String key) {
        this.group = group;
        this.displayName = displayName;
        this.key = key;
    }

    public LawGroup group() { return group; }
    public String displayName() { return displayName; }
    /** 命令里使用的短名（组内唯一）。 */
    public String key() { return key; }

    public static List<LawLevel> levelsOf(LawGroup group) {
        List<LawLevel> out = new ArrayList<>();
        for (LawLevel l : values()) if (l.group == group) out.add(l);
        return out;
    }

    public static LawLevel parse(LawGroup group, String key) {
        if (group == null || key == null) return null;
        String k = key.toLowerCase(Locale.ROOT);
        for (LawLevel l : levelsOf(group)) if (l.key.equals(k) || l.name().toLowerCase(Locale.ROOT).equals(k)) return l;
        return null;
    }

    /** 公司利润税率（基点）；非税制法律返回 0。 */
    public int profitTaxBps() {
        return switch (this) {
            case LOW_TAX -> 500;
            case MEDIUM_TAX -> 1_500;
            case HIGH_TAX -> 3_000;
            default -> 0;
        };
    }

    /** NPC 行商卖给聚落时的价格倍率（基点）。 */
    public int caravanPriceBps() {
        return switch (this) {
            case FREE_TRADE -> 9_000;
            case PROTECTIONISM -> 12_500;
            default -> 10_000;
        };
    }
}
