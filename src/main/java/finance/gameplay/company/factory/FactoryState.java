package finance.gameplay.company.factory;

import finance.company.CompanyType;
import finance.config.FinanceConfig;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 一座设施的厂房状态：目标方式、当前生效方式、改造期、最近一次设备快照和老存档的临时作坊期。
 *
 * <p>每天由 {@link #resolveDay} 决定当天生效的方式，生产和雇工只读结果。同一天重复调用不会重复计算。</p>
 */
public final class FactoryState {
    /** 临时作坊截止日未初始化（老存档或旧代码建的记录），第一次日结时设为"当天 + 临时作坊天数"。 */
    public static final long PROVISIONAL_UNSET = Long.MIN_VALUE;
    /** 没有临时作坊（新建设施，或已转为正式档位）。 */
    public static final long PROVISIONAL_NONE = -1L;

    private ProductionTier targetTier = ProductionTier.MANUAL;
    private ProductionTier activeTier;
    private boolean everActive;
    private long retoolUntilDay = -1L;
    private final EnumMap<FactoryEquipment, Integer> equipment = new EnumMap<>(FactoryEquipment.class);
    private long equipmentScanDay = -1L;
    private long provisionalUntilDay = PROVISIONAL_UNSET;
    private long lastResolvedDay = -1L;
    /** 上一次判定后的岗位数（运行时，只用于周报算裁员人数；-1 表示未知）。 */
    private long lastJobs = -1L;

    public long lastJobs() { return lastJobs; }

    void setLastJobs(long jobs) { lastJobs = jobs; }

    public ProductionTier targetTier() { return targetTier; }
    public ProductionTier activeTier() { return activeTier; }
    public boolean everActive() { return everActive; }
    public long retoolUntilDay() { return retoolUntilDay; }
    public Map<FactoryEquipment, Integer> equipment() { return Collections.unmodifiableMap(equipment); }
    public long equipmentScanDay() { return equipmentScanDay; }
    public long provisionalUntilDay() { return provisionalUntilDay; }
    public long lastResolvedDay() { return lastResolvedDay; }

    /** 新放置的控制器：没有临时作坊，必须搭设备才能生产。 */
    public void markNewBuild() {
        provisionalUntilDay = PROVISIONAL_NONE;
    }

    /** 玩家选择目标方式，下一个日结生效。合法性（等级、设备）由调用方按最新扫描校验。 */
    public void setTargetTier(ProductionTier tier) {
        if (tier != null) targetTier = tier;
    }

    /** 记录一次设备扫描结果。每种设备按上限截断。 */
    public void recordScan(Map<FactoryEquipment, Integer> counts, long day, int maxPerType) {
        if (day < 0) return;
        // 先复制再清空：调用方可能传入的就是 equipment() 视图
        Map<FactoryEquipment, Integer> copy = counts == null || counts.isEmpty() ? Map.of() : new EnumMap<>(counts);
        equipment.clear();
        if (!copy.isEmpty()) {
            copy.forEach((e, n) -> {
                if (e != null && n != null && n > 0) equipment.put(e, Math.min(n, Math.max(1, maxPerType)));
            });
        }
        equipmentScanDay = day;
    }

    /** 快照是否仍可用：扫描过，且距今不超过过期天数。 */
    public boolean scanFresh(long day, int staleDays) {
        return equipmentScanDay >= 0 && day - equipmentScanDay <= Math.max(0, staleDays);
    }

    /** 生产用的设备：快照过期时按"缺少设备"处理。 */
    public Map<FactoryEquipment, Integer> usableEquipment(long day, int staleDays) {
        return scanFresh(day, staleDays) ? equipment() : Map.of();
    }

    public boolean provisional(long day) {
        return provisionalUntilDay != PROVISIONAL_UNSET && provisionalUntilDay >= 0 && day <= provisionalUntilDay;
    }

    public boolean retooling(long day) {
        return retoolUntilDay >= 0 && day < retoolUntilDay;
    }

    /** 当天的日结判定，参数用配置值。 */
    public void resolveDay(CompanyType type, int facilityLevel, long day) {
        resolveDay(type, facilityLevel, day, FinanceConfig.facilityProvisionalDays(),
                FinanceConfig.facilityScanStaleDays(), FinanceConfig.facilityRetoolDays());
    }

    /**
     * 当天的日结判定（同一天只算一次）：
     * <ul>
     *   <li>老记录第一次日结时开始临时作坊期；</li>
     *   <li>在不高于目标档的前提下取满足条件的最高档；一旦有档位满足，临时作坊结束；</li>
     *   <li>切换到目标档时进入改造期——第一次投产和设备不够导致的退化不进改造期，缺设备后恢复要进。</li>
     * </ul>
     */
    public void resolveDay(CompanyType type, int facilityLevel, long day, int provisionalDays, int staleDays, int retoolDays) {
        if (day < 0 || day <= lastResolvedDay) return;
        lastResolvedDay = day;
        if (provisionalUntilDay == PROVISIONAL_UNSET) provisionalUntilDay = day + Math.max(0, provisionalDays);
        ProductionTier previous = activeTier;
        ProductionTier resolved = ProductionMethods.resolve(type, facilityLevel, usableEquipment(day, staleDays), targetTier);
        if (resolved != null) {
            provisionalUntilDay = PROVISIONAL_NONE;
            boolean switching = resolved != previous && resolved == targetTier && (previous != null || everActive);
            if (switching && retoolDays > 0) retoolUntilDay = day + retoolDays;
            everActive = true;
        }
        activeTier = resolved;
    }

    /** 当天生效的生产方式：正式档位；临时作坊期沿用原有规则；否则 null（缺少设备）。 */
    public ProductionMethod effectiveMethod(CompanyType type, long day) {
        if (activeTier != null) return ProductionMethods.get(type, activeTier);
        if (provisional(day) || provisionalUntilDay == PROVISIONAL_UNSET) return ProductionMethods.legacy(type);
        return null;
    }

    /** 是否处于临时作坊（含尚未初始化的老记录）。 */
    public boolean provisionalOrUnset(long day) {
        return activeTier == null && (provisionalUntilDay == PROVISIONAL_UNSET || provisional(day));
    }

    /** 反序列化用。 */
    public void restore(ProductionTier target, ProductionTier active, boolean everActive, long retoolUntil,
                        Map<FactoryEquipment, Integer> counts, long scanDay, long provisionalUntil, long lastResolved) {
        targetTier = target == null ? ProductionTier.MANUAL : target;
        activeTier = active;
        this.everActive = everActive || active != null;
        retoolUntilDay = Math.max(-1, retoolUntil);
        equipment.clear();
        if (counts != null) counts.forEach((e, n) -> { if (e != null && n != null && n > 0) equipment.put(e, Math.min(n, 64)); });
        equipmentScanDay = Math.max(-1, scanDay);
        provisionalUntilDay = provisionalUntil == PROVISIONAL_UNSET ? PROVISIONAL_UNSET : Math.max(PROVISIONAL_NONE, provisionalUntil);
        lastResolvedDay = Math.max(-1, lastResolved);
    }
}
