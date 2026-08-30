package finance.gameplay.company;

import finance.company.Company;
import finance.gameplay.company.capital.CapitalProjectManager;
import finance.gameplay.company.capital.WorldCapitalProject;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;

import java.math.BigInteger;

/**
 * Single read-only bridge from physical operations to financial reporting.
 * Project escrow is reported as committed cash but is deliberately excluded
 * from free cash and report assets, preventing double counting.
 */
public final class CompanyFundamentalBridge {
    public record Inputs(long reportAssetValue, long physicalFacilityValue, long committedProjectCash,
                         long debtPrincipal, double capacityUtilization, int materialShortageFacilities,
                         int capacityBlockedFacilities, int delayedShipments, boolean valuationDegraded) {}

    private CompanyFundamentalBridge() {}

    public static Inputs inputs(Company company, long day) {
        if (company == null) return new Inputs(0, 0, 0, 0, 0, 0, 0, 0, true);
        CompanyOperatingSnapshot snapshot = CompanyOperatingSnapshotService.snapshot(company, day);
        if (snapshot == null) return new Inputs(0, 0, 0, 0, 0, 0, 0, 0, true);
        BigInteger physical = BigInteger.ZERO;
        CompanyGameplayProfile profile = CompanyGameplayManager.profileFor(company);
        if (profile != null) {
            for (java.util.UUID id : profile.warehouseIds()) {
                WarehouseRecord record = WarehouseManager.get(id);
                if (record == null || !company.getCompanyId().equals(record.companyId())) continue;
                physical = physical.add(switch (record.tier()) {
                    case BASIC -> BigInteger.ZERO;
                    case REINFORCED -> BigInteger.valueOf(250L);
                    case INDUSTRIAL -> BigInteger.valueOf(1_750L);
                });
            }
        }
        for (CompanyFacilityRecord facility : CompanyFacilityManager.forCompany(company.getCompanyId())) {
            if (facility.productionLevel() >= 2) physical = physical.add(BigInteger.valueOf(2_000L));
            if (facility.productionLevel() >= 3) physical = physical.add(BigInteger.valueOf(8_000L));
        }
        BigInteger committed = BigInteger.ZERO;
        for (WorldCapitalProject project : CapitalProjectManager.forCompany(company.getCompanyId())) {
            if (!project.status().terminal() && project.fundedAmount() > 0)
                committed = committed.add(BigInteger.valueOf(project.fundedAmount()));
        }
        BigInteger assets = BigInteger.valueOf(snapshot.displayAssetValue()).add(physical);
        double utilization = snapshot.warehouseCapacity() <= 0 ? 0.0
                : Math.max(0.0, Math.min(1.0, (double) snapshot.warehouseUsed() / snapshot.warehouseCapacity()));
        return new Inputs(saturate(assets), saturate(physical), saturate(committed),
                snapshot.totalDebtPrincipal(), utilization, snapshot.materialShortage() ? 1 : 0,
                snapshot.capacityBlocked() ? 1 : 0, snapshot.abnormalShipmentCount(),
                snapshot.inventoryValuationDegraded());
    }

    public static long reportAssetValue(Company company, long day) {
        return inputs(company, day).reportAssetValue();
    }

    private static long saturate(BigInteger value) {
        if (value == null || value.signum() <= 0) return 0;
        return value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : value.longValue();
    }
}
