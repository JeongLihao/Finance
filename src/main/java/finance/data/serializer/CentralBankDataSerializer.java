package finance.data.serializer;

import finance.centralbank.CentralBankService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 中央银行状态持久化（DataVersion 42 新增）：动态准备金率、公开市场操作头寸、议息记录、物价稳定计数。
 * 旧存档没有该标签时全部取默认值（准备金率跟随配置），坏记录跳过。
 */
public final class CentralBankDataSerializer {
    public static final String ROOT = "CentralBank";
    public static final int VERSION = 1;

    private CentralBankDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putInt("DemandReserveBps", CentralBankService.storedReserveBps());
        data.putLong("LastDay", CentralBankService.lastProcessedDay());
        data.putLong("AbsorbedTotal", CentralBankService.absorbedTotal());
        data.putLong("InjectedTotal", CentralBankService.injectedTotal());
        data.putInt("StableStreak", CentralBankService.stableStreak());
        data.putBoolean("Runaway", CentralBankService.runaway());
        ListTag positions = new ListTag();
        CentralBankService.positions().forEach((bank, amount) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Bank", bank);
            t.putLong("Amount", amount);
            positions.add(t);
        });
        data.put("Positions", positions);
        ListTag decisions = new ListTag();
        for (CentralBankService.Decision d : CentralBankService.decisions()) {
            CompoundTag t = new CompoundTag();
            t.putLong("D", d.day());
            t.putInt("RateBefore", d.rateBefore());
            t.putInt("RateAfter", d.rateAfter());
            t.putInt("ReserveBefore", d.reserveBefore());
            t.putInt("ReserveAfter", d.reserveAfter());
            t.putInt("Inflation", d.inflationBps());
            t.putString("Note", d.note() == null ? "" : d.note());
            decisions.add(t);
        }
        data.put("Decisions", decisions);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        CentralBankService.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        Map<UUID, Long> positions = new LinkedHashMap<>();
        for (Tag raw : data.getList("Positions", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                positions.put(t.getUUID("Bank"), t.getLong("Amount"));
            } catch (Exception ignored) {
                // 坏记录跳过
            }
        }
        List<CentralBankService.Decision> decisions = new ArrayList<>();
        for (Tag raw : data.getList("Decisions", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            decisions.add(new CentralBankService.Decision(t.getLong("D"), t.getInt("RateBefore"), t.getInt("RateAfter"),
                    t.getInt("ReserveBefore"), t.getInt("ReserveAfter"), t.getInt("Inflation"), t.getString("Note")));
        }
        CentralBankService.restore(data.contains("DemandReserveBps", Tag.TAG_INT) ? data.getInt("DemandReserveBps") : -1,
                positions, decisions, data.contains("LastDay", Tag.TAG_LONG) ? data.getLong("LastDay") : Long.MIN_VALUE,
                data.getLong("AbsorbedTotal"), data.getLong("InjectedTotal"), data.getInt("StableStreak"), data.getBoolean("Runaway"));
    }
}
