package finance.macro;

/** 利益集团及其对各法律级别的立场（−2..+2）。 */
public enum InterestGroup {
    RURAL_FOLK("乡村民众"),
    PETITE_BOURGEOISIE("小资产阶级"),
    ARMED_FORCES("军队"),
    TRADE_UNIONS("工会"),
    INDUSTRIALISTS("工业家"),
    FINANCIERS("金融家");

    private final String displayName;

    InterestGroup(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    public int stance(LawLevel law) {
        if (law == null) return 0;
        return switch (law) {
            case FREE_TRADE -> pick(1, -2, -1, 0, 1, 2);
            case MERCANTILISM -> pick(0, 0, 1, 0, 0, 0);
            case PROTECTIONISM -> pick(-1, 2, 1, 0, 1, -2);
            case NO_LABOR_PROTECTION -> pick(0, 0, 0, -2, 2, 1);
            case MINIMUM_WAGE -> pick(1, 0, 0, 1, -1, 0);
            case WORKER_PROTECTION -> pick(0, -1, 0, 2, -2, -1);
            case NO_WELFARE -> pick(-1, 0, 0, -1, 1, 1);
            case POOR_RELIEF -> pick(1, 0, 0, 1, 0, -1);
            case PUBLIC_HEALTH -> pick(2, 1, 0, 2, -1, -2);
            case LOW_TAX -> pick(-1, 1, 0, -1, 2, 1);
            case MEDIUM_TAX -> pick(0, 0, 0, 0, 0, 0);
            case HIGH_TAX -> pick(1, -1, 0, 1, -2, -2);
        };
    }

    /** 按声明顺序取本集团的立场：乡村民众、小资产阶级、军队、工会、工业家、金融家。 */
    private int pick(int rural, int petite, int armed, int unions, int industrialists, int financiers) {
        return switch (this) {
            case RURAL_FOLK -> rural;
            case PETITE_BOURGEOISIE -> petite;
            case ARMED_FORCES -> armed;
            case TRADE_UNIONS -> unions;
            case INDUSTRIALISTS -> industrialists;
            case FINANCIERS -> financiers;
        };
    }
}
