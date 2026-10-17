package finance.rival;

import finance.company.Company;
import finance.company.CompanyManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 竞争公司档案的运行时存储与全局状态（上次新建日、上次破产日）。 */
public final class RivalManager {
    /** 档案上限：防止坏存档无限膨胀。实际公司数受配置 maxCompanies 约束。 */
    public static final int MAX_PROFILES = 64;

    private static final Map<UUID, RivalProfile> PROFILES = new LinkedHashMap<>();
    private static long lastProcessedDay = Long.MIN_VALUE;
    private static long lastSpawnDay = Long.MIN_VALUE;
    private static long lastBankruptcyDay = Long.MIN_VALUE;

    private RivalManager() { }

    public static synchronized RivalProfile get(UUID companyId) { return companyId == null ? null : PROFILES.get(companyId); }
    public static synchronized Collection<RivalProfile> all() { return List.copyOf(PROFILES.values()); }
    public static synchronized int size() { return PROFILES.size(); }
    public static synchronized long lastProcessedDay() { return lastProcessedDay; }
    public static synchronized long lastSpawnDay() { return lastSpawnDay; }
    public static synchronized long lastBankruptcyDay() { return lastBankruptcyDay; }

    static synchronized void setLastProcessedDay(long day) { lastProcessedDay = day; }
    static synchronized void setLastSpawnDay(long day) { lastSpawnDay = day; }
    static synchronized void setLastBankruptcyDay(long day) { lastBankruptcyDay = day; }

    static synchronized boolean put(RivalProfile profile) {
        if (profile == null || PROFILES.size() >= MAX_PROFILES && !PROFILES.containsKey(profile.companyId())) return false;
        PROFILES.put(profile.companyId(), profile);
        return true;
    }

    /**
     * 迁移：给已有的无主公司（3 家系统公司、旧存档里的 NPC 公司）懒创建档案，人格按公司 ID 确定性派生。
     */
    static synchronized void ensureProfiles(long day) {
        for (Company c : CompanyManager.getCompanies()) {
            if (c.isPlayerOwned() || PROFILES.containsKey(c.getCompanyId())) continue;
            String commodity = c.getType().getCommodityIds().isEmpty() ? "" : c.getType().getCommodityIds().get(0);
            put(new RivalProfile(c.getCompanyId(), RivalPersonality.forCompany(c.getCompanyId()), commodity, day));
        }
    }

    /** 删除已不存在（破产退市）的公司的档案，返回删除数。 */
    static synchronized int pruneRemoved() {
        int before = PROFILES.size();
        PROFILES.keySet().removeIf(id -> CompanyManager.getCompany(id) == null);
        return before - PROFILES.size();
    }

    public static synchronized void restoreDirect(long processed, long spawn, long bankruptcy, List<RivalProfile> profiles) {
        clearDirect();
        lastProcessedDay = processed;
        lastSpawnDay = spawn;
        lastBankruptcyDay = bankruptcy;
        if (profiles != null) profiles.forEach(RivalManager::put);
    }

    public static synchronized void clearDirect() {
        PROFILES.clear();
        lastProcessedDay = Long.MIN_VALUE;
        lastSpawnDay = Long.MIN_VALUE;
        lastBankruptcyDay = Long.MIN_VALUE;
    }
}
