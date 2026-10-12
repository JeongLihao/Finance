package finance.data.serializer;

import finance.commodity.CommodityRegistry;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.village.VillageManager;
import finance.village.VillageProfession;
import finance.village.VillageRecord;
import finance.village.VillageStall;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 村庄经济持久化：VillageRecord（含好感度）与 VillageStall 的存取。
 *
 * <p>沿用 {@code SettlementDataSerializer} 四步曲：损坏条目跳过＋标记 invalid，
 * 整体损坏时熔断对应模块。</p>
 */
public final class VillageDataSerializer {
    public static final String ROOT = "VillageEconomy";
    public static final int VERSION = 1;
    /** 单个村庄的好感度条目上限（损坏存档防护）。 */
    private static final int MAX_FAVOR_ENTRIES = 4096;
    /**
     * 行商撤退日相对上次结算日的最大未来偏移（天）：正常写入恒为结算日+7，
     * 取 30 留足余量，手改的超大值在此被截断。
     */
    private static final long MAX_RETREAT_FUTURE_DAYS = 30;
    /** 行商连续零成交计数合法上限（达到 3 即触发撤退并清零，正常存档只会有 0..2）。 */
    private static final int MAX_CARAVAN_ZERO_SALE_DAYS = 3;

    private VillageDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        ListTag villages = new ListTag();
        for (VillageRecord v : VillageManager.villages()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", v.settlementId());
            t.putInt("Population", v.population());
            CompoundTag mix = new CompoundTag();
            for (Map.Entry<VillageProfession, Integer> e : v.professionMix().entrySet())
                mix.putInt(e.getKey().name(), e.getValue());
            t.put("ProfessionMix", mix);
            ListTag favor = new ListTag();
            for (Map.Entry<UUID, Integer> e : v.favor().entrySet()) {
                if (e.getValue() == 0) continue; // 0 是缺省值，不落盘
                CompoundTag f = new CompoundTag();
                f.putUUID("Player", e.getKey());
                f.putInt("Points", e.getValue());
                favor.add(f);
            }
            t.put("Favor", favor);
            t.putUUID("BudgetAccount", v.budgetAccountId());
            t.putLong("LastMarketDay", v.lastMarketDay());
            t.putLong("CaravanRetreatUntilDay", v.caravanRetreatUntilDay());
            t.putInt("CaravanZeroSaleDays", v.caravanZeroSaleDays());
            villages.add(t);
        }
        data.put("Villages", villages);
        ListTag stalls = new ListTag();
        for (VillageStall s : VillageManager.stalls()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", s.id());
            t.putUUID("Settlement", s.settlementId());
            t.putUUID("Owner", s.ownerId());
            t.putString("Commodity", s.commodityId());
            t.putLong("UnitPrice", s.unitPrice());
            t.putInt("Stock", s.stock());
            stalls.add(t);
        }
        data.put("Stalls", stalls);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        VillageManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        ListTag villages = data.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(villages.size(), VillageManager.MAX_VILLAGES); i++) {
            CompoundTag t = villages.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(t, "Id");
                UUID budget = NbtDataSupport.readUuidOrNull(t, "BudgetAccount");
                int population = t.getInt("Population");
                CompoundTag mixTag = t.getCompound("ProfessionMix");
                EnumMap<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
                long mixSum = 0;
                boolean mixOk = true;
                for (VillageProfession p : VillageProfession.values()) {
                    if (!mixTag.contains(p.name(), Tag.TAG_INT)) { mixOk = false; break; }
                    int count = mixTag.getInt(p.name());
                    if (count < 0 || count > VillageRecord.MAX_POPULATION) { mixOk = false; break; }
                    mix.put(p, count);
                    mixSum += count;
                }
                long lastMarketDay = t.getLong("LastMarketDay");
                if (id == null || budget == null
                        || population < VillageRecord.MIN_POPULATION || population > VillageRecord.MAX_POPULATION
                        || !mixOk || mixSum != population || lastMarketDay < -1) {
                    invalid = true;
                    continue;
                }
                // 行商撤退字段钳制到合法范围（防手改存档：负数→0；撤退日不超过上次结算日+30 天，
                // 正常写入恒为结算日+7，超大值会导致行商"永久消失"）
                long retreatUntil = t.getLong("CaravanRetreatUntilDay");
                long maxRetreat = lastMarketDay < 0 ? 0
                        : (lastMarketDay > Long.MAX_VALUE - MAX_RETREAT_FUTURE_DAYS
                                ? Long.MAX_VALUE : lastMarketDay + MAX_RETREAT_FUTURE_DAYS);
                retreatUntil = Math.max(0, Math.min(retreatUntil, maxRetreat));
                int zeroSaleDays = Math.max(0,
                        Math.min(t.getInt("CaravanZeroSaleDays"), MAX_CARAVAN_ZERO_SALE_DAYS));
                VillageRecord v = new VillageRecord(id, population, mix, budget,
                        retreatUntil, zeroSaleDays);
                v.markMarketDay(lastMarketDay); // 恢复结算日（已校验 ≥ -1；幂等推进，精确还原）
                ListTag favor = t.getList("Favor", Tag.TAG_COMPOUND);
                if (favor.size() > MAX_FAVOR_ENTRIES) invalid = true;
                for (int f = 0; f < Math.min(favor.size(), MAX_FAVOR_ENTRIES); f++) {
                    CompoundTag ft = favor.getCompound(f);
                    UUID player = NbtDataSupport.readUuidOrNull(ft, "Player");
                    int points = ft.getInt("Points");
                    if (player == null || points == 0
                            || points < VillageRecord.MIN_FAVOR || points > VillageRecord.MAX_FAVOR) {
                        invalid = true;
                        continue;
                    }
                    v.addFavor(player, points); // 读到的值已在合法区间内，重放精确还原
                }
                if (!VillageManager.restoreVillage(v)) invalid = true;
            } catch (RuntimeException ex) {
                invalid = true;
            }
        }
        ListTag stalls = data.getList("Stalls", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(stalls.size(), VillageManager.MAX_STALLS); i++) {
            CompoundTag t = stalls.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(t, "Id");
                UUID settlement = NbtDataSupport.readUuidOrNull(t, "Settlement");
                UUID owner = NbtDataSupport.readUuidOrNull(t, "Owner");
                String commodity = t.getString("Commodity");
                long unitPrice = t.getLong("UnitPrice");
                int stock = t.getInt("Stock");
                if (id == null || settlement == null || owner == null
                        || commodity.isBlank() || commodity.length() > 64
                        || CommodityRegistry.getCommodity(commodity) == null
                        || unitPrice < 1 || stock < 0) {
                    invalid = true;
                    continue;
                }
                VillageStall s = new VillageStall(id, settlement, owner, commodity, unitPrice);
                if (stock > 0) s.addStock(stock);
                if (!VillageManager.restoreStall(s)) invalid = true;
            } catch (RuntimeException ex) {
                invalid = true;
            }
        }
        if (villages.size() > VillageManager.MAX_VILLAGES || stalls.size() > VillageManager.MAX_STALLS) invalid = true;
        if (invalid) ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.VILLAGE_ECONOMY,
                ModuleRunState.PAUSED, "village economy save invariant failed", 0);
    }
}
