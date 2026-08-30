package finance.gameplay.company;

import finance.company.Company;
import finance.commodity.CommodityInventoryManager;
import finance.warehouse.WarehouseManager;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CompanyInventoryFacade {
    public record Consumption(Map<String, Integer> custody, Map<String, Integer> legacy) {}
    private CompanyInventoryFacade() {}

    public static UUID custodyId(UUID companyId) {
        if (companyId == null) throw new IllegalArgumentException("companyId");
        return UUID.nameUUIDFromBytes(("finance-company-custody:" + companyId).getBytes(StandardCharsets.UTF_8));
    }
    public static int availableInput(Company company, String commodityId) {
        if (company == null) return 0;
        CompanyOperatingMode mode = CompanyGameplayManager.profileFor(company).operatingMode();
        int legacy = mode == CompanyOperatingMode.PLAYER_DRIVEN ? 0 : company.getInventoryAmount(commodityId);
        int custody = mode == CompanyOperatingMode.LEGACY_AUTOMATIC ? 0
                : finance.collateral.InventoryCollateralManager.available(custodyId(company.getCompanyId()), commodityId);
        long sum = (long) legacy + custody;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, sum));
    }
    /** Total authority-visible inventory, including pledged custody that is not spendable. */
    public static int totalInventory(Company company,String commodityId){
        if(company==null||commodityId==null)return 0;CompanyGameplayProfile profile=CompanyGameplayManager.get(company.getCompanyId());
        if(profile==null)return company.getInventoryAmount(commodityId);
        int legacy=profile.operatingMode()==CompanyOperatingMode.PLAYER_DRIVEN?0:company.getInventoryAmount(commodityId);
        int custody=profile.operatingMode()==CompanyOperatingMode.LEGACY_AUTOMATIC?0:CommodityInventoryManager.getCommodityAmount(custodyId(company.getCompanyId()),commodityId);
        return (int)Math.min(Integer.MAX_VALUE,(long)legacy+custody);
    }
    public static int availableInsurableInventory(Company company,String commodityId){
        if(company==null||commodityId==null)return 0;CompanyGameplayProfile profile=CompanyGameplayManager.get(company.getCompanyId());
        if(profile==null)return company.getInventoryAmount(commodityId);
        int legacy=profile.operatingMode()==CompanyOperatingMode.PLAYER_DRIVEN?0:company.getInventoryAmount(commodityId);
        int custody=profile.operatingMode()==CompanyOperatingMode.LEGACY_AUTOMATIC?0:finance.collateral.InventoryCollateralManager.available(custodyId(company.getCompanyId()),commodityId);
        return (int)Math.min(Integer.MAX_VALUE,(long)legacy+custody);
    }
    /** Removes only unpledged stock and returns a rollback token for event-level atomicity. */
    public static Consumption consumeInsurableLoss(Company company,String commodityId,int quantity){
        if(company==null||commodityId==null||commodityId.isBlank()||quantity<=0)return null;
        CompanyGameplayProfile profile=CompanyGameplayManager.get(company.getCompanyId());
        if(profile==null){if(!company.removeInventory(commodityId,quantity))return null;return new Consumption(Map.of(),Map.of(commodityId,quantity));}
        return consumeInputAtomically(company,Map.of(commodityId,quantity));
    }
    public static Consumption consumeInputAtomically(Company company, Map<String, Integer> requirements) {
        if (company == null || requirements == null) return null;
        for (Map.Entry<String, Integer> entry : requirements.entrySet())
            if (entry.getValue() == null || entry.getValue() <= 0 || availableInput(company, entry.getKey()) < entry.getValue()) return null;
        CompanyOperatingMode mode = CompanyGameplayManager.profileFor(company).operatingMode();
        UUID custodyId = custodyId(company.getCompanyId());
        Map<String, Integer> custody = new LinkedHashMap<>(), legacy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            int remaining = entry.getValue();
            if (mode != CompanyOperatingMode.LEGACY_AUTOMATIC) {
                int fromCustody = Math.min(remaining, CommodityInventoryManager.getCommodityAmount(custodyId, entry.getKey()));
                if (fromCustody > 0) { CommodityInventoryManager.removeCommodity(custodyId, entry.getKey(), fromCustody); custody.put(entry.getKey(), fromCustody); remaining -= fromCustody; }
            }
            if (remaining > 0 && mode != CompanyOperatingMode.PLAYER_DRIVEN) {
                if (!company.removeInventory(entry.getKey(), remaining)) { rollback(company, new Consumption(custody, legacy)); return null; }
                legacy.put(entry.getKey(), remaining);
            }
        }
        return new Consumption(Map.copyOf(custody), Map.copyOf(legacy));
    }
    public static boolean rollback(Company company, Consumption consumption) {
        if (company == null || consumption == null) return false;
        UUID custodyId = custodyId(company.getCompanyId());
        for (var e : consumption.custody().entrySet()) if (!CommodityInventoryManager.canAddCommodity(custodyId, e.getKey(), e.getValue())) return false;
        for (var e : consumption.legacy().entrySet()) if (!company.canAddInventory(e.getKey(), e.getValue())) return false;
        consumption.custody().forEach((id, amount) -> CommodityInventoryManager.addCommodity(custodyId, id, amount));
        consumption.legacy().forEach(company::addInventory); return true;
    }
    public static boolean canAddOutput(Company company, Map<String, Integer> outputs) {
        if (company == null || outputs == null) return false;
        CompanyOperatingMode mode = CompanyGameplayManager.profileFor(company).operatingMode();
        if (mode == CompanyOperatingMode.LEGACY_AUTOMATIC) return outputs.entrySet().stream().allMatch(e -> company.canAddInventory(e.getKey(), e.getValue()));
        UUID custody = custodyId(company.getCompanyId()); long total = outputs.values().stream().mapToLong(Integer::longValue).sum();
        return total > 0 && WarehouseManager.canDepositCapacity(custody, (int) Math.min(Integer.MAX_VALUE, total))
                && outputs.entrySet().stream().allMatch(e -> CommodityInventoryManager.canAddCommodity(custody, e.getKey(), e.getValue()));
    }
    public static boolean addOutputAtomically(Company company, Map<String, Integer> outputs) {
        if (!canAddOutput(company, outputs)) return false;
        CompanyOperatingMode mode = CompanyGameplayManager.profileFor(company).operatingMode();
        UUID custody = custodyId(company.getCompanyId());
        for (var e : outputs.entrySet()) {
            boolean added = mode == CompanyOperatingMode.LEGACY_AUTOMATIC ? company.addInventory(e.getKey(), e.getValue())
                    : CommodityInventoryManager.addCommodity(custody, e.getKey(), e.getValue());
            if (!added) throw new IllegalStateException("prevalidated company output failed");
        }
        return true;
    }
}
