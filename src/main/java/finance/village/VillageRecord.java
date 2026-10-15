package finance.village;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 村庄 sidecar 数据：与 {@code SettlementRecord} 按 {@code settlementId} 1:1 关联的
 * 抽象村庄模型（不读取真实村民实体）。
 *
 * <p>持有：人口与职业构成、玩家好感度、村民预算系统账户、上次集市结算日、
 * 行商撤退/零成交计数（倾销玩法的状态）。</p>
 */
public final class VillageRecord {
    /** 人口合法范围（与反刷钱上限铁律配套：需求总量 ≤ 人口 × 人均上限）。 */
    public static final int MIN_POPULATION = 1, MAX_POPULATION = 10_000;
    /** 好感度钳制区间。 */
    public static final int MIN_FAVOR = -100, MAX_FAVOR = 100;

    private final UUID settlementId;
    private final int population;
    private final EnumMap<VillageProfession, Integer> professionMix;
    private final Map<UUID, Integer> favor = new HashMap<>();
    private final UUID budgetAccountId;
    private long lastMarketDay = -1;
    private long caravanRetreatUntilDay;
    private int caravanZeroSaleDays;

    /**
     * @param settlementId          关联聚落 id，非 null
     * @param population          人口，1..10000
     * @param professionMix       职业人数：非空、各项 ≥ 0、总和必须 == population
     * @param budgetAccountId     村民预算系统账户 id，非 null
     * @param caravanRetreatUntilDay 行商撤退到的 MC 天（0 表示未撤退）
     * @param caravanZeroSaleDays 行商连续零成交天数
     * @throws IllegalArgumentException 校验失败时抛出（沿用 LocalDemand 风格）
     */
    public VillageRecord(UUID settlementId, int population, Map<VillageProfession, Integer> professionMix,
                         UUID budgetAccountId, long caravanRetreatUntilDay, int caravanZeroSaleDays) {
        if (settlementId == null || budgetAccountId == null)
            throw new IllegalArgumentException("settlementId/budgetAccountId 不能为空");
        if (population < MIN_POPULATION || population > MAX_POPULATION)
            throw new IllegalArgumentException("population 越界: " + population);
        if (professionMix == null || professionMix.isEmpty())
            throw new IllegalArgumentException("professionMix 不能为空");
        long sum = 0;
        EnumMap<VillageProfession, Integer> copy = new EnumMap<>(VillageProfession.class);
        for (Map.Entry<VillageProfession, Integer> e : professionMix.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() < 0)
                throw new IllegalArgumentException("professionMix 含非法项");
            sum += e.getValue();
            copy.put(e.getKey(), e.getValue());
        }
        if (sum != population) throw new IllegalArgumentException("professionMix 总和必须等于 population");
        this.settlementId = settlementId;
        this.population = population;
        this.professionMix = copy;
        this.budgetAccountId = budgetAccountId;
        this.caravanRetreatUntilDay = caravanRetreatUntilDay;
        this.caravanZeroSaleDays = caravanZeroSaleDays;
    }

    public UUID settlementId() { return settlementId; }

    public int population() { return population; }

    /** 职业人数（防御性拷贝，修改返回值不影响内部状态）。 */
    public Map<VillageProfession, Integer> professionMix() { return new EnumMap<>(professionMix); }

    public UUID budgetAccountId() { return budgetAccountId; }

    /** 玩家好感度，缺省 0（player 为 null 也返回 0）。 */
    public int favorOf(UUID player) {
        if (player == null) return 0;
        return favor.getOrDefault(player, 0);
    }

    /** 好感度快照（不可修改的拷贝，供持久化遍历）。 */
    public Map<UUID, Integer> favor() { return Collections.unmodifiableMap(new HashMap<>(favor)); }

    /**
     * 增减好感度并钳制到 [-100, 100]，返回新值。
     * player 为 null 时不做任何修改，返回 0。
     * 新值为 0 时直接移除条目（0 是缺省值，不落盘也不占内存，避免 0 值条目缓慢堆积）。
     */
    public int addFavor(UUID player, int delta) {
        if (player == null) return 0;
        int next = Math.max(MIN_FAVOR, Math.min(MAX_FAVOR, favorOf(player) + delta));
        if (next == 0) {
            favor.remove(player);
        } else {
            favor.put(player, next);
        }
        return next;
    }

    public long lastMarketDay() { return lastMarketDay; }

    /**
     * 推进集市结算日（幂等）：只有 {@code day > lastMarketDay} 时才推进并返回 true，
     * 否则不做任何修改返回 false。
     */
    public boolean markMarketDay(long day) {
        if (day > lastMarketDay) {
            lastMarketDay = day;
            return true;
        }
        return false;
    }

    public long caravanRetreatUntilDay() { return caravanRetreatUntilDay; }

    public void setCaravanRetreatUntilDay(long d) { this.caravanRetreatUntilDay = d; }

    public int caravanZeroSaleDays() { return caravanZeroSaleDays; }

    public void setCaravanZeroSaleDays(int n) { this.caravanZeroSaleDays = n; }
}
