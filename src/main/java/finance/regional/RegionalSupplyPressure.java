package finance.regional;

public enum RegionalSupplyPressure {
    SURPLUS, BALANCED, TIGHT, SHORTAGE;

    public static RegionalSupplyPressure fromScore(int score) {
        if (score >= 7_500) return SHORTAGE;
        if (score >= 5_500) return TIGHT;
        if (score >= 2_500) return BALANCED;
        return SURPLUS;
    }
}
