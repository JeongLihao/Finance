package finance.gameplay.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CompanyProductionService {
    public record DayResult(int producedFacilities, boolean legacyFallback) {}
    private CompanyProductionService() {}

    public static DayResult processCompanyDay(Company company, long day) {
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY))return new DayResult(0,false);
        CompanyGameplayProfile profile = CompanyGameplayManager.profileFor(company);
        if (profile.operatingMode() == CompanyOperatingMode.LEGACY_AUTOMATIC) return new DayResult(0, false);
        int produced = 0;
        for (CompanyFacilityRecord facility : CompanyFacilityManager.forCompany(company.getCompanyId())) {
            if (processFacility(company, facility, day)) produced++;
        }
        boolean fallback = false;
        if (profile.operatingMode() == CompanyOperatingMode.HYBRID && produced == 0
                && profile.lastLegacyFallbackDay() < day) {
            fallback = legacyFallback(company);
            profile.setLastLegacyFallbackDay(day);
        }
        if (produced > 0) {
            // The same marker also guards the hybrid branch. Without this, a second
            // invocation on the same day saw no newly processed facility and ran the
            // legacy fallback in addition to the already committed facility output.
            profile.setLastLegacyFallbackDay(day);
            CompanyGameplayMarketService.autoSell(company);
        }
        EconomySavedData.markDirty();
        return new DayResult(produced, fallback);
    }

    public static boolean processFacility(Company company, CompanyFacilityRecord facility, long day) {
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.COMPANY_GAMEPLAY))return false;
        if (company == null || facility == null || day < 0 || facility.lastProcessedDay() >= day
                || facility.status() == CompanyFacilityStatus.DISABLED) return false;
        facility.setLastProcessedDay(day);
        if (company.isBankruptcyRisk()) { facility.setStatus(CompanyFacilityStatus.BANKRUPTCY_HOLD); return false; }
        if (facility.boundWarehouseId() == null || !CompanyGameplayManager.profileFor(company).warehouseIds()
                .contains(facility.boundWarehouseId())) { facility.setStatus(CompanyFacilityStatus.OUTPUT_FULL); return false; }
        int throughput = finance.config.FinanceConfig.factoryThroughput(facility.productionLevel());
        Map<String, Integer> inputs = scaled(company.getType().getDailyConsumption(), throughput);
        Map<String, Integer> outputs = scaled(company.getType().getDailyProduction(), throughput);
        if (!CompanyInventoryFacade.canAddOutput(company, outputs)) { facility.setStatus(CompanyFacilityStatus.OUTPUT_FULL); return false; }
        for (var e : inputs.entrySet()) if (CompanyInventoryFacade.availableInput(company, e.getKey()) < e.getValue()) {
            facility.setStatus(CompanyFacilityStatus.MISSING_INPUT); return false;
        }
        long baseMaintenance = Math.max(1L, company.estimateDailyOperatingCost() / 4L);
        long maintenance = Math.max(1L, java.math.BigInteger.valueOf(baseMaintenance)
                .multiply(java.math.BigInteger.valueOf(finance.config.FinanceConfig.factoryMaintenanceBasisPoints(
                        facility.productionLevel())))
                .divide(java.math.BigInteger.valueOf(10_000L))
                .min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue());
        if (company.getCash() < maintenance || !company.withdraw(maintenance)) {
            facility.setStatus(CompanyFacilityStatus.BANKRUPTCY_HOLD); return false;
        }
        CompanyInventoryFacade.Consumption consumed = CompanyInventoryFacade.consumeInputAtomically(company, inputs);
        if (consumed == null) { company.deposit(maintenance); facility.setStatus(CompanyFacilityStatus.MISSING_INPUT); return false; }
        if (!CompanyInventoryFacade.addOutputAtomically(company, outputs)) {
            if (!CompanyInventoryFacade.rollback(company, consumed) || !company.deposit(maintenance))
                throw new IllegalStateException("company production compensation failed");
            facility.setStatus(CompanyFacilityStatus.OUTPUT_FULL); return false;
        }
        facility.setStatus(CompanyFacilityStatus.ACTIVE);
        company.recordGameplayCost(maintenance);
        int total = outputs.values().stream().mapToInt(Integer::intValue).sum();
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(),
                CompanyInventoryFacade.custodyId(company.getCompanyId()), maintenance,
                TransactionType.FACILITY_PRODUCTION, company.getOwnerId(), company.getName(), total));
        return true;
    }

    private static boolean legacyFallback(Company company) {
        boolean any = false;
        for (var entry : company.getType().getDailyProduction().entrySet()) {
            int amount = (int) Math.floor(entry.getValue() * finance.config.FinanceConfig.hybridLegacyFallbackRatio());
            if (amount <= 0) continue;
            if (company.addInventory(entry.getKey(), amount)) any = true;
        }
        if (any) company.autoTrade();
        return any;
    }

    private static Map<String, Integer> scaled(Map<String, Integer> base, int facilityLevel) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : base.entrySet()) {
            BigInteger value = BigInteger.valueOf(entry.getValue()).multiply(BigInteger.valueOf(facilityLevel));
            if (entry.getValue() > 0 && value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0)
                result.put(entry.getKey(), value.intValue());
        }
        return Map.copyOf(result);
    }
}
