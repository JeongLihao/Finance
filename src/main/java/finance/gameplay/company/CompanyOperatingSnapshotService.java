package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyFinancialReport;
import finance.commodity.CommodityInventory;
import finance.commodity.CommodityInventoryManager;
import finance.contract.ContractIssuerType;
import finance.contract.ContractManager;
import finance.contract.FinanceContract;
import finance.debt.CompanyLoanManager;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.debt.BondStatus;
import finance.debt.LoanStatus;
import finance.debt.CompanyLoan;
import finance.logistics.Shipment;
import finance.logistics.ShipmentManager;
import finance.logistics.ShipmentStatus;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Pure-read aggregator that builds a {@link CompanyOperatingSnapshot} from the
 * authoritative managers. It never mutates state, never stores a mirror copy
 * and never forces chunk or block loading: every input comes from persisted
 * in-memory records, so snapshots remain available while blocks are unloaded.
 */
public final class CompanyOperatingSnapshotService {

    private static final int MAX_INVENTORY_LINES = 128;

    private CompanyOperatingSnapshotService() {
    }

    public static CompanyOperatingSnapshot snapshot(Company company, long mcDay) {
        if (company == null) return null;
        UUID companyId = company.getCompanyId();
        CompanyGameplayProfile profile = CompanyGameplayManager.profileFor(company);

        long capacity = WarehouseManager.totalCapacity(CompanyInventoryFacade.custodyId(companyId));
        long used = WarehouseManager.usedCapacity(CompanyInventoryFacade.custodyId(companyId));
        int boundWarehouses = 0;
        if (profile != null) {
            for (UUID warehouseId : profile.warehouseIds()) {
                WarehouseRecord record = WarehouseManager.get(warehouseId);
                if (record != null && companyId.equals(record.companyId())
                        && record.status() != WarehouseStatus.DISABLED
                        && record.status() != WarehouseStatus.ORPHANED) boundWarehouses++;
            }
        }

        int facilityCount = 0;
        int highestLevel = 0;
        long lastProductionDay = -1;
        boolean materialShortage = false;
        boolean capacityBlocked = false;
        for (CompanyFacilityRecord facility : CompanyFacilityManager.forCompany(companyId)) {
            facilityCount++;
            highestLevel = Math.max(highestLevel, facility.productionLevel());
            lastProductionDay = Math.max(lastProductionDay, facility.lastProcessedDay());
            if (facility.status() == CompanyFacilityStatus.MISSING_INPUT) materialShortage = true;
            if (facility.status() == CompanyFacilityStatus.OUTPUT_FULL) capacityBlocked = true;
        }

        Map<String, Long> inventory = aggregateInventory(company);
        boolean degraded = false;
        BigInteger value = BigInteger.ZERO;
        for (Map.Entry<String, Long> line : inventory.entrySet()) {
            MarketPrice price = NpcMarketMaker.getMarketPrice(line.getKey());
            if (price == null || price.getMidPrice() <= 0) {
                degraded = true;
                continue;
            }
            value = value.add(BigInteger.valueOf(price.getMidPrice())
                    .multiply(BigInteger.valueOf(line.getValue())));
        }

        int openContracts = 0;
        for (FinanceContract contract : ContractManager.contracts().values()) {
            if (companyId.equals(contract.issuerId())
                    && contract.issuerType() == ContractIssuerType.COMPANY
                    && !contract.status().terminal()) openContracts++;
        }

        int activeShipments = 0;
        int abnormalShipments = 0;
        boolean delayedShipment = false;
        for (Shipment shipment : ShipmentManager.all().values()) {
            if (!companyId.equals(shipment.companyId()) || shipment.status().terminal()) continue;
            activeShipments++;
            if (shipment.status() == ShipmentStatus.LOSS_PENDING
                    || shipment.status() == ShipmentStatus.QUARANTINED) abnormalShipments++;
            if (mcDay > shipment.deadlineDay()) delayedShipment = true;
        }

        long loanPrincipal = CompanyLoanManager.outstandingPrincipal(companyId);
        long bondPrincipal = CorporateBondManager.outstandingPrincipal(companyId);
        long dueSoon = amountDueWithinSevenDays(companyId, mcDay);

        Stock stock = StockMarketManager.getStockByCompanyId(companyId);
        boolean listed = company.isPublic() && stock != null;
        long totalShares = listed ? stock.getTotalShares() : 0;
        long treasuryShares = listed ? stock.getTreasuryShares() : 0;
        long marketCap = listed
                ? CompanyOperatingSnapshot.saturateMultiply(stock.getLastPrice(), stock.getTotalShares())
                : 0;

        long inventoryValue = CompanyOperatingSnapshot.saturate(value);
        BigInteger debtPrincipal = BigInteger.valueOf(Math.max(0, loanPrincipal))
                .add(BigInteger.valueOf(Math.max(0, bondPrincipal)));
        CompanyOperatingHealth health = classify(company, used, capacity, materialShortage,
                capacityBlocked, delayedShipment, debtPrincipal.signum() > 0, debtPrincipal);

        return new CompanyOperatingSnapshot(
                companyId, Math.max(0, mcDay),
                Math.max(0, company.getCash()),
                Math.max(0, company.getSmoothedDailyProfit()),
                Math.max(0, company.getRetainedEarnings()),
                latestReportDay(company),
                boundWarehouses, Math.max(0, capacity), Math.max(0, used), used > capacity,
                facilityCount, highestLevel, lastProductionDay,
                materialShortage, capacityBlocked, company.isBankruptcyRisk(),
                inventory, degraded, inventoryValue,
                openContracts, activeShipments, abnormalShipments,
                Math.max(0, loanPrincipal), Math.max(0, bondPrincipal), dueSoon,
                listed, totalShares, treasuryShares, marketCap, health);
    }

    private static Map<String, Long> aggregateInventory(Company company) {
        UUID custodyId = CompanyInventoryFacade.custodyId(company.getCompanyId());
        TreeSet<String> keys = new TreeSet<>();
        CommodityInventory custody = CommodityInventoryManager.getInventories().get(custodyId);
        if (custody != null) keys.addAll(custody.getAllCommodities().keySet());
        keys.addAll(company.getInventory().keySet());
        Map<String, Long> result = new LinkedHashMap<>();
        for (String commodityId : keys) {
            if (result.size() >= MAX_INVENTORY_LINES) break;
            int amount = CompanyInventoryFacade.availableInput(company, commodityId);
            if (amount > 0) result.put(commodityId, (long) amount);
        }
        return result;
    }

    private static long latestReportDay(Company company) {
        CompanyFinancialReport report = company.getLatestFinancialReport();
        return report == null ? -1 : Math.max(-1, report.mcDay());
    }

    private static long amountDueWithinSevenDays(UUID companyId, long mcDay) {
        long horizon = mcDay + 7;
        BigInteger due = BigInteger.ZERO;
        for (CompanyLoan loan : CompanyLoanManager.loans().values()) {
            if (!companyId.equals(loan.companyId())) continue;
            if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.DELINQUENT) continue;
            long nextDueDay = Math.min(loan.nextPaymentDay(), loan.maturityDay());
            if (nextDueDay < mcDay || nextDueDay > horizon) continue;
            if (nextDueDay == loan.maturityDay()) {
                due = due.add(BigInteger.valueOf(Math.max(0, loan.outstandingPrincipal()))
                        .add(BigInteger.valueOf(Math.max(0, loan.accruedInterest()))));
            } else {
                due = due.add(BigInteger.valueOf(Math.max(0, loan.accruedInterest())));
            }
        }
        for (CorporateBond bond : CorporateBondManager.bonds().values()) {
            if (!companyId.equals(bond.companyId()) || bond.status() != BondStatus.ACTIVE) continue;
            long totalFace = CompanyOperatingSnapshot.saturateMultiply(bond.faceValue(), bond.subscribedQuantity());
            if (bond.maturityDay() >= mcDay && bond.maturityDay() <= horizon) {
                due = due.add(BigInteger.valueOf(totalFace));
                continue;
            }
            if (bond.nextCouponDay() >= mcDay && bond.nextCouponDay() <= horizon) {
                long days = Math.max(0, bond.nextCouponDay() - bond.lastCouponDay());
                BigInteger coupon = BigInteger.valueOf(bond.faceValue())
                        .multiply(BigInteger.valueOf(Math.max(0, bond.subscribedQuantity())))
                        .multiply(BigInteger.valueOf(bond.couponBasisPoints()))
                        .multiply(BigInteger.valueOf(days))
                        .divide(BigInteger.valueOf(10_000L * Math.max(1,
                                finance.config.FinanceConfig.annualMcDays())));
                due = due.add(coupon);
            }
        }
        return CompanyOperatingSnapshot.saturate(due);
    }

    private static CompanyOperatingHealth classify(Company company, long used, long capacity,
                                                   boolean materialShortage, boolean capacityBlocked,
                                                   boolean delayedShipment, boolean hasDebt,
                                                   BigInteger debtPrincipal) {
        if (company.isBankruptcyRisk()) return CompanyOperatingHealth.BANKRUPTCY_RISK;
        if (hasDebt) {
            BigInteger assets = BigInteger.valueOf(Math.max(1, company.getReportBasedAssetValue()));
            if (debtPrincipal.multiply(BigInteger.TWO).compareTo(assets) > 0) {
                return CompanyOperatingHealth.DEBT_PRESSURE;
            }
        }
        if (materialShortage) return CompanyOperatingHealth.MATERIAL_SHORTAGE;
        if (capacityBlocked || used > capacity) return CompanyOperatingHealth.CAPACITY_BLOCKED;
        if (delayedShipment) return CompanyOperatingHealth.LOGISTICS_DELAY;
        return CompanyOperatingHealth.HEALTHY;
    }
}
