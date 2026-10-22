package finance.macro;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyOperatingMode;
import finance.settlement.SettlementManager;
import finance.settlement.SettlementRecord;
import finance.util.MathUtil;
import finance.village.VillageManager;
import finance.village.VillageRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 宏观经济第二步：雇工。
 *
 * <p>每个公司设施从附近聚落招工，按工资从高到低分配劳动力；每天发工资（公司现金 → 聚落预算账户），
 * 产量按在岗比例缩放（见 {@link #staffingBps}）。传统自动经营的公司不参与。</p>
 */
public final class MacroLaborService {
    private MacroLaborService() {}

    public static boolean active() {
        return MacroEconomyService.active() && FinanceConfig.macroLaborEnabled();
    }

    /** 设施岗位数：由厂房设备决定（见 FactoryService.jobs），老存档临时作坊期沿用"等级 × 每级岗位"。 */
    public static long jobs(CompanyFacilityRecord facility) {
        return finance.gameplay.company.factory.FactoryService.jobs(facility);
    }

    /** 工人接受工作的最低日工资。 */
    public static long reservationWage() {
        return Math.max(0, FinanceConfig.macroBaseReservationWage());
    }

    /** 公司当前日工资。 */
    public static long wageOf(UUID companyId) {
        return MacroEconomyManager.wage(companyId, FinanceConfig.macroDefaultWage());
    }

    /** 所有者设定工资。 */
    public static boolean setWage(UUID actor, UUID companyId, long wage) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || actor == null || !actor.equals(company.getOwnerId()) || wage < 0 || wage > 1_000_000)
            return false;
        if (!MacroEconomyManager.setWage(companyId, wage)) return false;
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 产量系数（基点）：在岗/岗位；劳动力来源在半径外（远距离通勤）时减半。
     * 未启用，或设施所在维度一个聚落都没有（劳动力市场不存在）时为 10000（满员）。
     */
    public static int staffingBps(CompanyFacilityRecord facility) {
        if (!active() || facility == null) return 10_000;
        long jobs = jobs(facility);
        if (jobs <= 0) return 10_000;
        SettlementRecord source = nearestSettlement(facility);
        if (source == null) return 10_000;
        long employed = Math.min(jobs, MacroEconomyManager.employment(facility.facilityId()));
        long bps = employed * 10_000 / jobs;
        if (!withinRadius(source, facility)) bps /= 2;
        return (int) Math.max(0, Math.min(10_000, bps));
    }

    /** 按产量系数缩放投入/产出，全部向下取整，只保留 &gt; 0 的项。 */
    public static Map<String, Integer> scale(Map<String, Integer> base, int bps) {
        if (bps >= 10_000) return base;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : base.entrySet()) {
            long v = (long) e.getValue() * Math.max(0, bps) / 10_000;
            if (v > 0) out.put(e.getKey(), (int) v);
        }
        return Map.copyOf(out);
    }

    // ================================================================ 日结

    /** 招工并发工资。必须在 {@code CompanyManager.tickAll}（生产）之前调用。 */
    public static void processDay(long day) {
        if (!active()) {
            // 宏观经济仍在运行但雇工被关掉：清空在岗人数，避免旧的就业数继续影响需求和工会
            if (MacroEconomyService.active() && !FinanceConfig.macroLaborEnabled()) clearEmployment();
            return;
        }
        if (day <= MacroEconomyManager.lastLaborDay()) return;
        MacroEconomyManager.setLastLaborDay(day);
        long reservation = reservationWage();

        // 1. 每个设施找劳动力来源
        Map<UUID, List<CompanyFacilityRecord>> bySettlement = new LinkedHashMap<>();
        Set<UUID> live = new HashSet<>();
        for (CompanyFacilityRecord facility : CompanyFacilityManager.all()) {
            Company company = CompanyManager.getCompany(facility.companyId());
            if (company == null
                    || facility.status() == CompanyFacilityStatus.DISABLED
                    || facility.status() == CompanyFacilityStatus.ORPHANED) continue;
            if (CompanyGameplayManager.profileFor(company).operatingMode() == CompanyOperatingMode.LEGACY_AUTOMATIC)
                continue;
            live.add(facility.facilityId());
            // 岗位取决于当天的生产方式：先判定（幂等，日结里 FactoryService 已判定过时直接跳过）
            finance.gameplay.company.factory.FactoryService.prepare(facility, day);
            SettlementRecord source = nearestSettlement(facility);
            if (source == null) {
                MacroEconomyManager.setEmployment(facility.facilityId(), 0, null);
                continue;
            }
            bySettlement.computeIfAbsent(source.id(), k -> new ArrayList<>()).add(facility);
        }
        MacroEconomyManager.retainFacilities(live);
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) econ.employed = 0;

        // 2. 每个聚落按工资从高到低分配劳动力，然后发工资
        for (Map.Entry<UUID, List<CompanyFacilityRecord>> entry : bySettlement.entrySet()) {
            VillageRecord village = VillageManager.get(entry.getKey());
            SettlementEconomy econ = MacroEconomyManager.getOrCreate(entry.getKey());
            if (village == null || econ == null) continue;
            long workforce = (long) village.population() * MacroEconomyService.WORKFORCE_BPS / 10_000;
            List<CompanyFacilityRecord> facilities = entry.getValue();
            facilities.sort(Comparator.<CompanyFacilityRecord>comparingLong(f -> -wageOf(f.companyId()))
                    .thenComparing(f -> f.facilityId().toString()));
            for (CompanyFacilityRecord facility : facilities) {
                long jobs = jobs(facility);
                long wage = wageOf(facility.companyId());
                long current = MacroEconomyManager.employment(facility.facilityId());
                long target = wage < reservation || wage <= 0 ? 0 : Math.min(jobs, workforce);
                long maxChange = Math.max(1, (jobs + 2) / 3);
                long next = target > current
                        ? Math.min(target, current + maxChange)
                        : Math.max(target, current - maxChange);
                next = Math.max(0, Math.min(next, workforce));
                next = pay(facility, village, econ, next, wage);
                MacroEconomyManager.setEmployment(facility.facilityId(), next, entry.getKey());
                workforce -= next;
                econ.employed += next;
            }
        }
        EconomySavedData.markDirty();
    }

    static void clearEmployment() {
        if (MacroEconomyManager.employmentDirect().isEmpty()) {
            boolean any = false;
            for (SettlementEconomy econ : MacroEconomyManager.settlements()) any |= econ.employed != 0;
            if (!any) return;
        }
        MacroEconomyManager.retainFacilities(java.util.Set.of());
        for (SettlementEconomy econ : MacroEconomyManager.settlements()) econ.employed = 0;
        EconomySavedData.markDirty();
    }

    /** 发工资；现金不够时只保留付得起的人数。返回实际在岗人数。 */
    private static long pay(
            CompanyFacilityRecord facility, VillageRecord village, SettlementEconomy econ, long workers, long wage) {
        if (workers <= 0 || wage <= 0) return 0;
        Company company = CompanyManager.getCompany(facility.companyId());
        UUID budget = village.budgetAccountId();
        if (company == null || budget == null) return 0;
        long affordable = Math.max(0, company.getCash()) / wage;
        long n = Math.min(workers, affordable);
        if (n <= 0 || n > Integer.MAX_VALUE) return 0;
        long amount = MathUtil.multiplyExactOrNegative1(wage, (int) n);
        if (amount <= 0 || !company.withdraw(amount)) return 0;
        if (!AccountManager.deposit(budget, amount)) {
            company.deposit(amount);
            return 0;
        }
        company.recordGameplayCost(amount);
        econ.addWages(amount);
        AccountManager.addTransactionRecord(new TransactionRecord(
                company.getCompanyId(),
                budget,
                amount,
                TransactionType.MACRO_WAGE,
                company.getOwnerId(),
                company.getName(),
                n));
        return n;
    }

    /** 劳动力来源是否在招工半径以内；半径外按远距离通勤处理（效率减半）。 */
    public static boolean withinRadius(SettlementRecord source, CompanyFacilityRecord facility) {
        long radius = FinanceConfig.macroLaborRadius();
        return source != null && facility != null && source.anchor().distSqr(facility.blockPos()) <= radius * radius;
    }

    /** 同一维度内最近的可用聚落（且已有村庄记录）；该维度没有聚落时返回 null。 */
    public static SettlementRecord nearestSettlement(CompanyFacilityRecord facility) {
        SettlementRecord best = null;
        double bestDist = Double.MAX_VALUE;
        for (SettlementRecord s : SettlementManager.settlements().values()) {
            if (!MacroEconomyService.usable(s) || !s.dimensionId().equals(facility.dimensionId())) continue;
            if (VillageManager.get(s.id()) == null) continue;
            double d = s.anchor().distSqr(facility.blockPos());
            if (d < bestDist || (d == bestDist && best != null && s.id().compareTo(best.id()) < 0)) {
                best = s;
                bestDist = d;
            }
        }
        return best;
    }
}
