package finance.macro;

import finance.village.VillageProfession;

import java.util.EnumMap;
import java.util.Map;

/**
 * 人口增减时职业人数的分配（纯函数）：新增人口按默认职业占比分配，减少时按当前占比扣除，
 * 两种情况都用最大余数法保证总数精确。
 */
public final class PopulationMix {
    private PopulationMix() { }

    public static EnumMap<VillageProfession, Integer> resized(Map<VillageProfession, Integer> current, int newTotal) {
        EnumMap<VillageProfession, Integer> out = new EnumMap<>(VillageProfession.class);
        long total = 0;
        for (VillageProfession p : VillageProfession.values()) {
            int v = current == null ? 0 : Math.max(0, current.getOrDefault(p, 0));
            out.put(p, v);
            total += v;
        }
        int target = Math.max(0, newTotal);
        long delta = target - total;
        if (delta == 0) return out;
        VillageProfession[] ps = VillageProfession.values();
        long[] weights = new long[ps.length];
        long weightSum = 0;
        for (int i = 0; i < ps.length; i++) {
            weights[i] = delta > 0 ? ps[i].defaultRatioBps() : out.get(ps[i]);
            weightSum += weights[i];
        }
        if (weightSum <= 0) {
            // 现有人口为 0 时只可能是增加；退回默认占比
            for (int i = 0; i < ps.length; i++) weights[i] = ps[i].defaultRatioBps();
            weightSum = 10_000;
        }
        long magnitude = Math.abs(delta);
        long[] parts = new long[ps.length];
        long[] remainders = new long[ps.length];
        long assigned = 0;
        for (int i = 0; i < ps.length; i++) {
            long scaled = magnitude * weights[i];
            parts[i] = scaled / weightSum;
            remainders[i] = scaled % weightSum;
            assigned += parts[i];
        }
        long left = magnitude - assigned;
        while (left > 0) {
            int best = -1;
            for (int i = 0; i < ps.length; i++) {
                if (delta < 0 && out.get(ps[i]) - parts[i] <= 0) continue;
                if (best < 0 || remainders[i] > remainders[best]) best = i;
            }
            if (best < 0) break;
            parts[best]++;
            remainders[best] = -1;
            left--;
        }
        for (int i = 0; i < ps.length; i++) {
            long v = out.get(ps[i]) + (delta > 0 ? parts[i] : -parts[i]);
            out.put(ps[i], (int) Math.max(0, v));
        }
        return out;
    }
}
