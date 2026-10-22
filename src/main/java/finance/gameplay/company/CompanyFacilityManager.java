package finance.gameplay.company;

import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 公司机器登记表：每台公司机器一条记录（见 {@link CompanyFacilityRecord}）。 */
public final class CompanyFacilityManager {
    public static final int MAX_FACILITIES = 4_096;
    public static final int MAX_PER_COMPANY = 64;
    private static final Map<UUID, CompanyFacilityRecord> FACILITIES = new LinkedHashMap<>();

    private CompanyFacilityManager() {}

    public static Collection<CompanyFacilityRecord> all() {
        return java.util.List.copyOf(FACILITIES.values());
    }

    public static CompanyFacilityRecord get(UUID id) {
        return id == null ? null : FACILITIES.get(id);
    }

    public static java.util.List<CompanyFacilityRecord> forCompany(UUID companyId) {
        return FACILITIES.values().stream()
                .filter(f -> f.companyId().equals(companyId))
                .toList();
    }

    public static synchronized boolean restore(CompanyFacilityRecord record) {
        if (record == null
                || FACILITIES.size() >= MAX_FACILITIES
                || FACILITIES.containsKey(record.facilityId())
                || CompanyManager.getCompany(record.companyId()) == null
                        && record.status() != CompanyFacilityStatus.ORPHANED
                || incomingCount(record.companyId()) >= MAX_PER_COMPANY) return false;
        boolean incomingActive =
                record.status() != CompanyFacilityStatus.DISABLED && record.status() != CompanyFacilityStatus.ORPHANED;
        CompanyFacilityRecord conflict = FACILITIES.values().stream()
                .filter(existing -> incomingActive
                        && existing.status() != CompanyFacilityStatus.DISABLED
                        && existing.status() != CompanyFacilityStatus.ORPHANED
                        && existing.dimensionId().equals(record.dimensionId())
                        && existing.blockPos().equals(record.blockPos()))
                .findFirst()
                .orElse(null);
        if (conflict != null) return false;
        FACILITIES.put(record.facilityId(), record);
        return true;
    }

    public static synchronized boolean register(CompanyFacilityRecord record) {
        boolean result = restore(record);
        if (result) EconomySavedData.markDirty();
        return result;
    }

    public static void removeCompany(UUID companyId) {
        FACILITIES.values().stream()
                .filter(f -> f.companyId().equals(companyId))
                .forEach(f -> f.setStatus(CompanyFacilityStatus.ORPHANED));
    }

    /** 全服公司机器按档次计数：[1 档, 2 档, 3 档, 停工]（停工 = 缺料、仓满、挖到底）。 */
    public static synchronized java.util.List<Integer> tierCounts() {
        int[] counts = new int[4];
        for (CompanyFacilityRecord f : FACILITIES.values()) {
            if (f.status() == CompanyFacilityStatus.ORPHANED) continue;
            boolean working = f.status() == CompanyFacilityStatus.ACTIVE;
            counts[working ? Math.max(1, Math.min(3, f.productionLevel())) - 1 : 3]++;
        }
        return java.util.List.of(counts[0], counts[1], counts[2], counts[3]);
    }

    /** 机器被拆掉时注销。 */
    public static synchronized void remove(UUID facilityId) {
        if (facilityId != null && FACILITIES.remove(facilityId) != null) EconomySavedData.markDirty();
    }

    public static void clearDirect() {
        FACILITIES.clear();
    }

    private static long incomingCount(UUID companyId) {
        return FACILITIES.values().stream()
                .filter(f -> f.companyId().equals(companyId)
                        && f.status() != CompanyFacilityStatus.DISABLED
                        && f.status() != CompanyFacilityStatus.ORPHANED)
                .count();
    }
}
