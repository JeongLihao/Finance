package finance.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 宏观经济的全部运行时状态（静态持有，存档由 {@code MacroEconomyDataSerializer} 落盘，
 * 世界切换和测试隔离时由 {@link #clearDirect()} 清空）。
 */
public final class MacroEconomyManager {
    public static final int MAX_SETTLEMENTS = 256;
    public static final int MAX_FACILITIES = 2_048;
    public static final int MAX_COMPANY_WAGES = 4_096;
    public static final int MAX_SNAPSHOTS = 120;
    public static final int MAX_LAW_HISTORY = 32;

    /** 每日宏观快照。CPI 以 1/100 为单位（10000 = 100.00）。 */
    public record Snapshot(long day, long cpiX100, long population, int avgSol, long employed,
                           long wages, long consumptionValue) { }

    /** 进行中的法律提案。 */
    public record Proposal(LawLevel level, UUID proposer, long startDay, long endDay) { }

    /** 已结束的提案。 */
    public record LawResult(LawLevel level, boolean passed, long support, long opposition, long day) { }

    private static final Map<UUID, SettlementEconomy> SETTLEMENTS = new LinkedHashMap<>();
    /** 设施 id → 在岗人数。 */
    private static final Map<UUID, Long> EMPLOYMENT = new LinkedHashMap<>();
    /** 设施 id → 劳动力来源聚落（当天招工时确定）。 */
    private static final Map<UUID, UUID> LABOR_SOURCE = new LinkedHashMap<>();
    /** 公司 id → 日工资。 */
    private static final Map<UUID, Long> WAGES = new LinkedHashMap<>();
    private static final EnumMap<LawGroup, LawLevel> LAWS = new EnumMap<>(LawGroup.class);
    private static final EnumMap<InterestGroup, Integer> LOBBY = new EnumMap<>(InterestGroup.class);
    private static final EnumMap<InterestGroup, Integer> APPROVAL = new EnumMap<>(InterestGroup.class);
    private static final EnumMap<InterestGroup, Integer> CLOUT_BPS = new EnumMap<>(InterestGroup.class);
    private static final ArrayDeque<Snapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final ArrayDeque<LawResult> LAW_HISTORY = new ArrayDeque<>();
    private static Proposal proposal;
    private static boolean strike;
    private static boolean unrest;
    private static long lastProcessedDay = Long.MIN_VALUE;
    private static long lastLaborDay = Long.MIN_VALUE;

    static { resetLaws(); }

    private MacroEconomyManager() { }

    // ---------------------------------------------------------------- 聚落

    public static SettlementEconomy settlement(UUID id) { return id == null ? null : SETTLEMENTS.get(id); }

    static SettlementEconomy getOrCreate(UUID id) {
        if (id == null) return null;
        SettlementEconomy e = SETTLEMENTS.get(id);
        if (e == null && SETTLEMENTS.size() < MAX_SETTLEMENTS) {
            e = new SettlementEconomy(id);
            SETTLEMENTS.put(id, e);
        }
        return e;
    }

    public static Collection<SettlementEconomy> settlements() { return Collections.unmodifiableCollection(SETTLEMENTS.values()); }

    static void retainSettlements(java.util.Set<UUID> ids) { SETTLEMENTS.keySet().retainAll(ids); }

    // ---------------------------------------------------------------- 劳动力

    public static long employment(UUID facilityId) { return facilityId == null ? 0 : EMPLOYMENT.getOrDefault(facilityId, 0L); }

    static void setEmployment(UUID facilityId, long employed, UUID source) {
        if (facilityId == null) return;
        if (employed <= 0) {
            EMPLOYMENT.remove(facilityId);
            LABOR_SOURCE.remove(facilityId);
            return;
        }
        if (!EMPLOYMENT.containsKey(facilityId) && EMPLOYMENT.size() >= MAX_FACILITIES) return;
        EMPLOYMENT.put(facilityId, employed);
        if (source != null) LABOR_SOURCE.put(facilityId, source);
    }

    public static UUID laborSource(UUID facilityId) { return facilityId == null ? null : LABOR_SOURCE.get(facilityId); }

    public static Map<UUID, Long> employmentDirect() { return Collections.unmodifiableMap(EMPLOYMENT); }

    public static Map<UUID, UUID> laborSourcesDirect() { return Collections.unmodifiableMap(LABOR_SOURCE); }

    static void retainFacilities(java.util.Set<UUID> ids) {
        EMPLOYMENT.keySet().retainAll(ids);
        LABOR_SOURCE.keySet().retainAll(ids);
    }

    /** 公司日工资；未设定时用配置默认值。 */
    public static long wage(UUID companyId, long defaultWage) {
        Long w = companyId == null ? null : WAGES.get(companyId);
        return w == null ? Math.max(0, defaultWage) : w;
    }

    public static boolean hasCustomWage(UUID companyId) { return companyId != null && WAGES.containsKey(companyId); }

    static boolean setWage(UUID companyId, long wage) {
        if (companyId == null || wage < 0) return false;
        if (!WAGES.containsKey(companyId) && WAGES.size() >= MAX_COMPANY_WAGES) return false;
        WAGES.put(companyId, wage);
        return true;
    }

    public static Map<UUID, Long> wagesDirect() { return Collections.unmodifiableMap(WAGES); }

    // ---------------------------------------------------------------- 政治

    public static LawLevel law(LawGroup group) { return LAWS.getOrDefault(group, group.defaultLevel()); }

    public static Map<LawGroup, LawLevel> laws() { return Collections.unmodifiableMap(new EnumMap<>(LAWS)); }

    static void setLaw(LawLevel level) { if (level != null) LAWS.put(level.group(), level); }

    static void resetLaws() {
        LAWS.clear();
        for (LawGroup g : LawGroup.values()) LAWS.put(g, g.defaultLevel());
    }

    public static int lobby(InterestGroup ig) { return LOBBY.getOrDefault(ig, 0); }

    static void setLobby(InterestGroup ig, int value) {
        if (ig == null) return;
        if (value == 0) LOBBY.remove(ig); else LOBBY.put(ig, value);
    }

    public static int approval(InterestGroup ig) { return APPROVAL.getOrDefault(ig, 0); }

    static void setApproval(InterestGroup ig, int value) { if (ig != null) APPROVAL.put(ig, value); }

    /** 影响力占比（基点，全部集团之和约为 10000）。 */
    public static int cloutBps(InterestGroup ig) { return CLOUT_BPS.getOrDefault(ig, 0); }

    static void setCloutBps(InterestGroup ig, int value) { if (ig != null) CLOUT_BPS.put(ig, value); }

    public static Proposal proposal() { return proposal; }

    static void setProposal(Proposal p) { proposal = p; }

    public static boolean strike() { return strike; }

    public static boolean unrest() { return unrest; }

    static void setUnrestFlags(boolean strikeNow, boolean unrestNow) {
        strike = strikeNow;
        unrest = unrestNow;
    }

    static void addLawResult(LawResult r) {
        if (r == null) return;
        LAW_HISTORY.addLast(r);
        while (LAW_HISTORY.size() > MAX_LAW_HISTORY) LAW_HISTORY.removeFirst();
    }

    public static List<LawResult> lawHistory() { return new ArrayList<>(LAW_HISTORY); }

    // ---------------------------------------------------------------- 指标

    static void addSnapshot(Snapshot s) {
        if (s == null) return;
        SNAPSHOTS.addLast(s);
        while (SNAPSHOTS.size() > MAX_SNAPSHOTS) SNAPSHOTS.removeFirst();
    }

    public static List<Snapshot> snapshots() { return new ArrayList<>(SNAPSHOTS); }

    public static Snapshot latestSnapshot() { return SNAPSHOTS.peekLast(); }

    // ---------------------------------------------------------------- 日结标记

    public static long lastProcessedDay() { return lastProcessedDay; }

    static void setLastProcessedDay(long day) { lastProcessedDay = day; }

    public static long lastLaborDay() { return lastLaborDay; }

    static void setLastLaborDay(long day) { lastLaborDay = day; }

    public static void clearDirect() {
        SETTLEMENTS.clear();
        EMPLOYMENT.clear();
        LABOR_SOURCE.clear();
        WAGES.clear();
        resetLaws();
        LOBBY.clear();
        APPROVAL.clear();
        CLOUT_BPS.clear();
        SNAPSHOTS.clear();
        LAW_HISTORY.clear();
        proposal = null;
        strike = false;
        unrest = false;
        lastProcessedDay = Long.MIN_VALUE;
        lastLaborDay = Long.MIN_VALUE;
        Treasury.clearDirect();
        EconomyAchievements.clearDirect();
        MacroWorldFeedback.clearDirect();
        NewsService.clearDirect();
    }
}
