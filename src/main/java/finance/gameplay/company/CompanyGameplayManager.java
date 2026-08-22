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

    public static Map<UUID, CompanyGameplayProfile> profiles() { return java.util.Collections.unmodifiableMap(PROFILES); }
    public static CompanyGameplayProfile get(UUID companyId) { return companyId == null ? null : PROFILES.get(companyId); }
    public static CompanyGameplayProfile profileFor(Company company) {
        if (company == null) return null;
        return PROFILES.computeIfAbsent(company.getCompanyId(), id ->
                new CompanyGameplayProfile(id, CompanyOperatingMode.LEGACY_AUTOMATIC));
    }
    public static CompanyGameplayProfile createForNewCompany(Company company) {
        if (company == null) return null;
        CompanyOperatingMode mode = !finance.config.FinanceConfig.playerDrivenCompanyProduction()
                ? CompanyOperatingMode.LEGACY_AUTOMATIC
                : finance.config.FinanceConfig.newCompaniesPlayerDrivenOnly()
                ? CompanyOperatingMode.PLAYER_DRIVEN : CompanyOperatingMode.HYBRID;
        CompanyGameplayProfile profile = new CompanyGameplayProfile(company.getCompanyId(), mode);
        PROFILES.put(company.getCompanyId(), profile);
        EconomySavedData.markDirty();
        return profile;
    }
    public static boolean restore(CompanyGameplayProfile profile) {
        if (profile == null || CompanyManager.getCompany(profile.companyId()) == null
                || PROFILES.containsKey(profile.companyId())) return false;
        PROFILES.put(profile.companyId(), profile); return true;
    }
    public static void ensureLegacyProfiles() {
        for (Company company : CompanyManager.getCompanies()) profileFor(company);
    }
    public static void removeCompany(UUID companyId) {
        PROFILES.remove(companyId);
        CompanyFacilityManager.removeCompany(companyId);
    }
    public static void clearDirect() { PROFILES.clear(); CompanyFacilityManager.clearDirect(); }
}
