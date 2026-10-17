package finance.data.serializer;

import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.rival.RivalManager;
import finance.rival.RivalPersonality;
import finance.rival.RivalProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NPC 竞争公司档案持久化（DataVersion 41 新增）。旧存档没有该标签时，系统公司的档案会在第一次日结时懒创建。
 * 单条坏记录跳过并计数，有跳过时把 RIVALS 模块降为只读。
 */
public final class RivalDataSerializer {
    public static final String ROOT = "Rivals";
    public static final int VERSION = 1;

    private RivalDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putLong("LastProcessedDay", RivalManager.lastProcessedDay());
        data.putLong("LastSpawnDay", RivalManager.lastSpawnDay());
        data.putLong("LastBankruptcyDay", RivalManager.lastBankruptcyDay());
        ListTag profiles = new ListTag();
        for (RivalProfile p : RivalManager.all()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Company", p.companyId());
            t.putString("Personality", p.personality().name());
            t.putString("Commodity", p.mainCommodity());
            t.putLong("Created", p.createdDay());
            t.putLong("LastUpgrade", p.lastUpgradeDay());
            ListTag history = new ListTag();
            for (RivalProfile.Decision d : p.history()) {
                CompoundTag h = new CompoundTag();
                h.putLong("Day", d.day());
                h.putString("Action", d.action());
                h.putString("Detail", d.detail());
                history.add(h);
            }
            t.put("History", history);
            profiles.add(t);
        }
        data.put("Profiles", profiles);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        RivalManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        int skipped = 0;
        List<RivalProfile> profiles = new ArrayList<>();
        ListTag raw = data.getList("Profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < raw.size(); i++) {
            if (profiles.size() >= RivalManager.MAX_PROFILES) { skipped += raw.size() - i; break; }
            try {
                CompoundTag t = raw.getCompound(i);
                UUID id = t.getUUID("Company");
                RivalPersonality personality = RivalPersonality.parse(t.getString("Personality"));
                if (personality == null) { skipped++; continue; }
                RivalProfile profile = new RivalProfile(id, personality, t.getString("Commodity"), t.getLong("Created"));
                List<RivalProfile.Decision> history = new ArrayList<>();
                for (Tag h : t.getList("History", Tag.TAG_COMPOUND)) {
                    CompoundTag ht = (CompoundTag) h;
                    history.add(new RivalProfile.Decision(ht.getLong("Day"), ht.getString("Action"), ht.getString("Detail")));
                }
                profile.restoreDirect(t.getLong("LastUpgrade"), history);
                profiles.add(profile);
            } catch (Exception e) {
                skipped++;
            }
        }
        RivalManager.restoreDirect(longOr(data, "LastProcessedDay"), longOr(data, "LastSpawnDay"),
                longOr(data, "LastBankruptcyDay"), profiles);
        if (invalid || skipped > 0) {
            ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.RIVALS, ModuleRunState.READ_ONLY,
                    "竞争公司存档 " + skipped + " 条损坏已跳过", -1);
        }
    }

    private static long longOr(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_LONG) ? tag.getLong(key) : Long.MIN_VALUE;
    }
}
