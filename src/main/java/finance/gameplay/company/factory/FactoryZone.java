package finance.gameplay.company.factory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;

/**
 * 厂区几何（纯函数）：以控制器为中心，水平 (2r+1)×(2r+1)，垂直从控制器下方 {@code below} 格到上方 {@code above} 格。
 * 默认 r=4、below=1、above=3，即 9×9×5。
 */
public record FactoryZone(int radius, int below, int above) {
    public static final FactoryZone DEFAULT = new FactoryZone(4, 1, 3);

    public FactoryZone {
        radius = Math.max(0, Math.min(16, radius));
        below = Math.max(0, Math.min(16, below));
        above = Math.max(0, Math.min(16, above));
    }

    public boolean contains(BlockPos controller, BlockPos pos) {
        if (controller == null || pos == null) return false;
        int dx = pos.getX() - controller.getX();
        int dy = pos.getY() - controller.getY();
        int dz = pos.getZ() - controller.getZ();
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius && dy >= -below && dy <= above;
    }

    /** 厂区内的所有格子（不含控制器本身）。 */
    public Iterable<BlockPos> positions(BlockPos controller) {
        BlockPos min = controller.offset(-radius, -below, -radius);
        BlockPos max = controller.offset(radius, above, radius);
        return () -> BlockPos.betweenClosedStream(min, max)
                .filter(p -> !p.equals(controller))
                .map(BlockPos::immutable)
                .iterator();
    }

    public int volume() {
        int side = radius * 2 + 1;
        return side * side * (below + above + 1);
    }

    /**
     * 一块只算一次：同一方块落在多座厂区内时，算给最近的控制器；距离相同算给列表中靠前（先建）的那座。
     *
     * @param controllers 同维度的全部控制器，按建造顺序排列
     * @return 拥有该方块的控制器下标；不在任何厂区内返回 -1
     */
    public int owner(BlockPos pos, List<BlockPos> controllers) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < controllers.size(); i++) {
            BlockPos c = controllers.get(i);
            if (!contains(c, pos)) continue;
            long d = (long) c.distSqr(pos);
            if (d < bestDistance) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * 统计某座厂区的设备（纯函数，测试和世界扫描共用）。
     *
     * @param self        当前控制器在 {@code controllers} 里的下标
     * @param equipmentAt 坐标 → 设备（不是设备返回 null）
     * @param maxPerType  每种设备最多计数
     */
    public Map<FactoryEquipment, Integer> count(
            int self, List<BlockPos> controllers, Function<BlockPos, FactoryEquipment> equipmentAt, int maxPerType) {
        Map<FactoryEquipment, Integer> counts = new EnumMap<>(FactoryEquipment.class);
        if (self < 0 || self >= controllers.size()) return counts;
        BlockPos controller = controllers.get(self);
        for (BlockPos pos : positions(controller)) {
            FactoryEquipment equipment = equipmentAt.apply(pos);
            if (equipment == null) continue;
            if (owner(pos, controllers) != self) continue;
            int current = counts.getOrDefault(equipment, 0);
            if (current < maxPerType) counts.put(equipment, current + 1);
        }
        return counts;
    }
}
