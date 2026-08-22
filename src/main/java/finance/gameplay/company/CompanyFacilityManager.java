package finance.gameplay.company;

import finance.company.CompanyManager;
import finance.data.EconomySavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CompanyFacilityManager {
    public static final int MAX_FACILITIES = 2_048;
    public static final int MAX_PER_COMPANY = 8;
    private static final Map<UUID, CompanyFacilityRecord> FACILITIES = new LinkedHashMap<>();
    private CompanyFacilityManager() {}
    public static Collection<CompanyFacilityRecord> all() { return java.util.List.copyOf(FACILITIES.values()); }
    public static CompanyFacilityRecord get(UUID id) { return id == null ? null : FACILITIES.get(id); }
    public static java.util.List<CompanyFacilityRecord> forCompany(UUID companyId) {
        return FACILITIES.values().stream().filter(f -> f.companyId().equals(companyId)).toList();
    }
    public static synchronized boolean restore(CompanyFacilityRecord record) {
        if (record == null || FACILITIES.size() >= MAX_FACILITIES || FACILITIES.containsKey(record.facilityId())
                || CompanyManager.getCompany(record.companyId()) == null && record.status() != CompanyFacilityStatus.ORPHANED
                || forCompany(record.companyId()).size() >= MAX_PER_COMPANY
                || FACILITIES.values().stream().anyMatch(existing -> existing.dimensionId().equals(record.dimensionId())
                && existing.blockPos().equals(record.blockPos()))) return false;
        FACILITIES.put(record.facilityId(), record); return true;
    }
    public static synchronized boolean register(CompanyFacilityRecord record) {
        boolean result = restore(record); if (result) EconomySavedData.markDirty(); return result;
    }
    public static void removeCompany(UUID companyId) { FACILITIES.values().stream().filter(f -> f.companyId().equals(companyId))
            .forEach(f -> f.setStatus(CompanyFacilityStatus.ORPHANED)); }
    public static void disable(UUID facilityId) { CompanyFacilityRecord f = get(facilityId); if (f != null) { f.setStatus(CompanyFacilityStatus.DISABLED); EconomySavedData.markDirty(); } }
    public static void clearDirect() { FACILITIES.clear(); }
}
