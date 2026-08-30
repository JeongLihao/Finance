package finance.regional;

/** Immutable, public daily summary. It deliberately contains no player or route identity. */
public record RegionalCommoditySnapshot(long day, int demandOpened, int demandFilled,
                                        long quantityRequested, long quantityDelivered,
                                        long averagePaidPrice, long globalMidPrice,
                                        int localPremiumBps, int onTimeDeliveryBps,
                                        int activeShipmentCount, int shortageScore,
                                        int smoothedShortageScore, RegionalSupplyPressure pressure,
                                        boolean priceReliable) {
    public RegionalCommoditySnapshot {
        if (day < 0 || demandOpened < 0 || demandFilled < 0 || quantityRequested < 0
                || quantityDelivered < 0 || averagePaidPrice < 0 || globalMidPrice < 0
                || localPremiumBps < 7_500 || localPremiumBps > 14_000
                || onTimeDeliveryBps < 0 || onTimeDeliveryBps > 10_000
                || activeShipmentCount < 0 || shortageScore < 0 || shortageScore > 10_000
                || smoothedShortageScore < 0 || smoothedShortageScore > 10_000 || pressure == null)
            throw new IllegalArgumentException("invalid regional snapshot");
    }
}
