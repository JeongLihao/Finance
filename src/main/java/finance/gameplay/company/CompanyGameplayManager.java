package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CompanyGameplayManager {
    private static final Map<UUID, CompanyGameplayProfile> PROFILES = new LinkedHashMap<>();

    private CompanyGameplayManager() {}

    public static Map<UUID, CompanyGameplayProfile> profiles() {
        return java.util.Collections.unmodifiableMap(PROFILES);
    }

    public static CompanyGameplayProfile get(UUID companyId) {
        return companyId == null ? null : PROFILES.get(companyId);
    }

    public static CompanyGameplayProfile profileFor(Company company) {
        if (company == null) return null;
        return PROFILES.computeIfAbsent(company.getCompanyId(), CompanyGameplayProfile::new);
    }

    /**
     * 读档后调用：玩家公司留在旧库存里的货全部并进托管账户。两份存档（经济数据、商品库存）都读完后才能调，
     * 可以重复调用（已经搬完的公司不再动）。返回搬了货的公司数。
     */
    public static int migratePlayerLegacyInventory() {
        int companies = 0;
        for (Company company : CompanyManager.getCompanies()) {
            if (CompanyInventoryFacade.migrateLegacyToCustody(company) > 0) companies++;
        }
        if (companies > 0) {
            EconomySavedData.markDirty();
            finance.data.CommodityInventorySavedData.markDirty();
        }
        return companies;
    }

    public static CompanyGameplayProfile createForNewCompany(Company company) {
        if (company == null) return null;
        CompanyGameplayProfile profile = new CompanyGameplayProfile(company.getCompanyId());
        PROFILES.put(company.getCompanyId(), profile);
        EconomySavedData.markDirty();
        return profile;
    }

    public static boolean restore(CompanyGameplayProfile profile) {
        if (profile == null
                || CompanyManager.getCompany(profile.companyId()) == null
                || PROFILES.containsKey(profile.companyId())) return false;
        PROFILES.put(profile.companyId(), profile);
        return true;
    }

    public static void ensureLegacyProfiles() {
        for (Company company : CompanyManager.getCompanies()) profileFor(company);
    }

    public static void removeCompany(UUID companyId) {
        PROFILES.remove(companyId);
        CompanyFacilityManager.removeCompany(companyId);
    }

    public static void clearDirect() {
        PROFILES.clear();
        CompanyFacilityManager.clearDirect();
    }
}
