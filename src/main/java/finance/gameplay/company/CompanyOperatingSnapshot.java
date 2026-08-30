package finance.gameplay.company;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, read-only aggregate describing how a company's physical Minecraft
 * operation is doing right now. It is computed on demand from the authoritative
 * managers and never persists a second mirror copy.
 *
 * <p>All money and quantity totals are saturated into the {@code long} range so
 * extreme inputs can never wrap negative. Inventory valuation explicitly records
 * whether every line had a market price; a missing price degrades the valuation
 * instead of silently counting as zero.</p>
 *
 * <p>The snapshot deliberately omits other players' UUIDs, exact warehouse
 * coordinates and admin-only diagnostics so it is safe to send to any member
 * who may view the company screen.</p>
 */
public record CompanyOperatingSnapshot(
        UUID companyId,
        long mcDay,
        long cash,
        long smoothedDailyProfit,
        long retainedEarnings,
        long lastReportDay,
        int boundWarehouseCount,
        long warehouseCapacity,
        long warehouseUsed,
        boolean overCapacity,
        int facilityCount,
        int highestFacilityLevel,
        long lastProductionDay,
        boolean materialShortage,
        boolean capacityBlocked,
        boolean bankruptcyRisk,
        Map<String, Long> custodyInventory,
        boolean inventoryValuationDegraded,
        long inventoryValue,
        int openContractCount,
        int activeShipmentCount,
        int abnormalShipmentCount,
        long outstandingLoanPrincipal,
        long outstandingBondPrincipal,
        long amountDueWithinSevenDays,
        boolean listed,
        long totalShares,
        long treasuryShares,
        long marketCapitalization,
        CompanyOperatingHealth health) {

    public CompanyOperatingSnapshot {
        if (companyId == null) throw new IllegalArgumentException("companyId");
        custodyInventory = custodyInventory == null ? Map.of() : Map.copyOf(custodyInventory);
    }

    /** Total debt principal used by the bridge; saturated to long. */
    public long totalDebtPrincipal() {
        return saturate(BigInteger.valueOf(Math.max(0, outstandingLoanPrincipal))
                .add(BigInteger.valueOf(Math.max(0, outstandingBondPrincipal))));
    }

    /**
     * Company asset estimate used for display and risk hints. Cash plus valued
     * inventory; degraded inventory contributes only its priced portion, so an
     * unknown price never inflates or silently zeroes the figure.
     */
    public long displayAssetValue() {
        return saturate(BigInteger.valueOf(Math.max(0, cash))
                .add(BigInteger.valueOf(Math.max(0, inventoryValue))));
    }

    static long saturate(BigInteger value) {
        if (value == null) return 0;
        if (value.signum() < 0) return 0;
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
        return value.longValue();
    }

    static long saturateAdd(long a, long b) {
        BigInteger sum = BigInteger.valueOf(Math.max(0, a)).add(BigInteger.valueOf(Math.max(0, b)));
        return saturate(sum);
    }

    static long saturateMultiply(long price, long quantity) {
        if (price <= 0 || quantity <= 0) return 0;
        return saturate(BigInteger.valueOf(price).multiply(BigInteger.valueOf(quantity)));
    }
}
