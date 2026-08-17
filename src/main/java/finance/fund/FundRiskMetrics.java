package finance.fund;

public record FundRiskMetrics(double totalReturnPercent, double recentVolatilityPercent,
                              double maximumDrawdownPercent, double trackingDifferencePercent,
                              double stockWeightPercent, double bondWeightPercent,
                              double cashWeightPercent, String liquidityGrade, boolean sufficientHistory) { }
