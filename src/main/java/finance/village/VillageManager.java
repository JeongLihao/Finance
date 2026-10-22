package finance.village;

import finance.account.AccountManager;
import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 村庄与摊位的内存注册表：静态持有，存档时由 {@code VillageDataSerializer} 落盘，
 * 世界切换/测试隔离时由 {@link #clearDirect()} 清空。
 */
public final class VillageManager {
    /** 持久化/内存上限（损坏存档防护，参考 SettlementManager 的 MAX_SETTLEMENTS/MAX_DEMANDS）。 */
    public static final int MAX_VILLAGES = 4096, MAX_STALLS = 65_536;
    /** 配置未加载时的默认人口（与 FinanceConfig 默认值一致）。 */
    private static final int FALLBACK_POPULATION = 30;

    private static final Map<UUID, VillageRecord> VILLAGES = new HashMap<>();
    private static final Map<UUID, VillageStall> STALLS = new HashMap<>();
    /** (settlementId|ownerId|commodityId) → stallId，保证每玩家每聚落每种商品只有一个摊位。 */
    private static final Map<String, UUID> STALL_KEYS = new HashMap<>();
    /**
     * 聚落 → 摊位 id 索引：每日结算按聚落扫描摊位时只扫本聚落，
     * 避免 O(村庄×商品×全服摊位) 的全表扫描。
     */
    private static final Map<UUID, Set<UUID>> STALLS_BY_SETTLEMENT = new HashMap<>();

    private VillageManager() {}

    /**
     * 获取聚落的村庄记录，不存在则懒创建：人口取 {@code FinanceConfig.villagePopulation()}
     *（配置未加载时回退 30），按 {@link VillageProfession#defaultRatioBps()} 分配职业
     *（末项凑整使总和 == population），预算账户为新建的系统账户。
     */
    public static VillageRecord getOrCreate(UUID settlementId) {
        if (settlementId == null) throw new IllegalArgumentException("settlementId 不能为空");
        VillageRecord existing = VILLAGES.get(settlementId);
        if (existing != null) return existing;
        if (VILLAGES.size() >= MAX_VILLAGES) throw new IllegalStateException("村庄数量已达上限");
        int population = FALLBACK_POPULATION;
        try {
            population = FinanceConfig.villagePopulation();
        } catch (RuntimeException ex) {
            population = FALLBACK_POPULATION; // 配置未加载（测试/早期启动）时回退默认值
        }
        if (population < VillageRecord.MIN_POPULATION || population > VillageRecord.MAX_POPULATION)
            population = FALLBACK_POPULATION;
        // 按职业占比分配，前 n-1 项向下取整，末项凑整保证总和 == population
        EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        VillageProfession[] professions = VillageProfession.values();
        int assigned = 0;
        for (int i = 0; i < professions.length - 1; i++) {
            int count = (int) ((long) population * professions[i].defaultRatioBps() / 10_000L);
            mix.put(professions[i], count);
            assigned += count;
        }
        mix.put(professions[professions.length - 1], population - assigned);
        UUID budgetAccountId =
                AccountManager.getOrCreateSystemAccount(UUID.randomUUID()).getPlayerId();
        VillageRecord created = new VillageRecord(settlementId, population, mix, budgetAccountId, 0L, 0);
        VILLAGES.put(settlementId, created);
        EconomySavedData.markDirty();
        return created;
    }

    public static VillageRecord get(UUID settlementId) {
        if (settlementId == null) return null;
        return VILLAGES.get(settlementId);
    }

    public static Collection<VillageRecord> villages() {
        return Collections.unmodifiableCollection(VILLAGES.values());
    }

    public static Collection<VillageStall> stalls() {
        return Collections.unmodifiableCollection(STALLS.values());
    }

    /**
     * 登记摊位。若该玩家在该聚落已有同商品摊位则返回 null；
     * commodity 必须已在 {@code CommodityRegistry} 注册，否则抛 IllegalArgumentException。
     */
    public static VillageStall registerStall(UUID settlementId, UUID ownerId, String commodityId, long unitPrice) {
        if (settlementId == null || ownerId == null) throw new IllegalArgumentException("settlementId/ownerId 不能为空");
        if (commodityId == null || commodityId.isBlank() || commodityId.length() > 64)
            throw new IllegalArgumentException("commodityId 非法");
        if (unitPrice < 1) throw new IllegalArgumentException("unitPrice 必须 ≥ 1");
        if (CommodityRegistry.getCommodity(commodityId) == null)
            throw new IllegalArgumentException("商品未注册: " + commodityId);
        String key = stallKey(settlementId, ownerId, commodityId);
        if (STALL_KEYS.containsKey(key)) return null;
        if (STALLS.size() >= MAX_STALLS) throw new IllegalStateException("摊位数量已达上限");
        VillageStall stall = new VillageStall(UUID.randomUUID(), settlementId, ownerId, commodityId, unitPrice);
        STALLS.put(stall.id(), stall);
        STALL_KEYS.put(key, stall.id());
        STALLS_BY_SETTLEMENT.computeIfAbsent(settlementId, k -> new HashSet<>()).add(stall.id());
        EconomySavedData.markDirty();
        return stall;
    }

    public static VillageStall stall(UUID stallId) {
        if (stallId == null) return null;
        return STALLS.get(stallId);
    }

    /** 按聚落取摊位：走聚落索引，只扫描本聚落摊位（O(本聚落摊位数)）。 */
    public static List<VillageStall> stallsForSettlement(UUID settlementId) {
        List<VillageStall> out = new ArrayList<>();
        if (settlementId == null) return out;
        Set<UUID> ids = STALLS_BY_SETTLEMENT.get(settlementId);
        if (ids == null) return out;
        for (UUID id : ids) {
            VillageStall s = STALLS.get(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    /**
     * 移除摊位并返回 true；不存在返回 false。
     * 调用方保证先清空摊位库存（下架撤回）。
     */
    public static boolean removeStall(UUID stallId) {
        if (stallId == null) return false;
        VillageStall removed = STALLS.remove(stallId);
        if (removed == null) return false;
        STALL_KEYS.remove(stallKey(removed.settlementId(), removed.ownerId(), removed.commodityId()));
        unindexStall(removed.settlementId(), stallId);
        EconomySavedData.markDirty();
        return true;
    }

    /** 存档 load 恢复用：不触发 markDirty，id 重复/超上限返回 false。 */
    public static boolean restoreVillage(VillageRecord record) {
        if (record == null || VILLAGES.size() >= MAX_VILLAGES) return false;
        return VILLAGES.putIfAbsent(record.settlementId(), record) == null;
    }

    /** 存档 load 恢复用：不触发 markDirty，id/摊位键重复或超上限返回 false。 */
    public static boolean restoreStall(VillageStall stall) {
        if (stall == null || STALLS.size() >= MAX_STALLS) return false;
        String key = stallKey(stall.settlementId(), stall.ownerId(), stall.commodityId());
        if (STALL_KEYS.containsKey(key)) return false;
        if (STALLS.putIfAbsent(stall.id(), stall) != null) return false;
        STALL_KEYS.put(key, stall.id());
        STALLS_BY_SETTLEMENT
                .computeIfAbsent(stall.settlementId(), k -> new HashSet<>())
                .add(stall.id());
        return true;
    }

    /** 清空全部运行时状态（世界切换/测试隔离/存档 load 前调用；不触发 markDirty）。 */
    public static void clearDirect() {
        VILLAGES.clear();
        STALLS.clear();
        STALL_KEYS.clear();
        STALLS_BY_SETTLEMENT.clear();
    }

    /** 从聚落索引摘除摊位；索引为空的聚落直接删键，避免空集合堆积。 */
    private static void unindexStall(UUID settlementId, UUID stallId) {
        Set<UUID> ids = STALLS_BY_SETTLEMENT.get(settlementId);
        if (ids == null) return;
        ids.remove(stallId);
        if (ids.isEmpty()) STALLS_BY_SETTLEMENT.remove(settlementId);
    }

    private static String stallKey(UUID settlementId, UUID ownerId, String commodityId) {
        return settlementId + "|" + ownerId + "|" + commodityId;
    }
}
