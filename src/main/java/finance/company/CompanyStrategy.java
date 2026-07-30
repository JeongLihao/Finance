package finance.company;

/**
 * 玩家公司经营策略。
 */
public enum CompanyStrategy {
    STABLE("稳健经营", 1.0, 1.0, 1.0),
    EXPAND("扩张生产", 1.35, 1.25, 1.10),
    COST_CONTROL("控制成本", 0.85, 0.75, 0.90),
    HOLD_INVENTORY("囤货待涨", 0.95, 1.05, 0.45),
    FAST_CASH("快速变现", 1.05, 1.10, 1.35);

    private final String displayName;
    private final double productionMultiplier;
    private final double operatingCostMultiplier;
    private final double sellRatioMultiplier;

    CompanyStrategy(String displayName, double productionMultiplier,
                    double operatingCostMultiplier, double sellRatioMultiplier) {
        this.displayName = displayName;
        this.productionMultiplier = productionMultiplier;
        this.operatingCostMultiplier = operatingCostMultiplier;
        this.sellRatioMultiplier = sellRatioMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public double getProductionMultiplier() { return productionMultiplier; }
    public double getOperatingCostMultiplier() { return operatingCostMultiplier; }
    public double getSellRatioMultiplier() { return sellRatioMultiplier; }
}
