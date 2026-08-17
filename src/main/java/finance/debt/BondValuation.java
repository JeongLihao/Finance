package finance.debt;

/** Safe, side-effect-free fixed-income valuation result. */
public record BondValuation(long remainingPrincipal, long daysToNextCoupon, long accruedInterest,
                            long remainingCashFlows, int referenceYieldBasisPoints,
                            long referencePricePerUnit, long marketPricePerUnit, int marketYieldBasisPoints,
                            long marketValue, long unrealizedProfit) { }
