package finance.gameplay.company.factory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class FactoryZoneTest {
    private static final FactoryZone ZONE = FactoryZone.DEFAULT;
    private static final BlockPos A = new BlockPos(0, 64, 0);

    @Test
    void defaultZoneIsNineByNineByFive() {
        assertEquals(9 * 9 * 5, ZONE.volume());
        assertTrue(ZONE.contains(A, new BlockPos(4, 67, -4)));
        assertTrue(ZONE.contains(A, new BlockPos(-4, 63, 4)));
        assertFalse(ZONE.contains(A, new BlockPos(5, 64, 0)), "水平超出 4 格");
        assertFalse(ZONE.contains(A, new BlockPos(0, 62, 0)), "下方只到 1 格");
        assertFalse(ZONE.contains(A, new BlockPos(0, 68, 0)), "上方只到 3 格");
        long cells = 0;
        for (BlockPos ignored : ZONE.positions(A)) cells++;
        assertEquals(ZONE.volume() - 1, cells, "不含控制器本身");
    }

    @Test
    void sharedBlockGoesToNearestThenEarliestController() {
        BlockPos b = new BlockPos(6, 64, 0);
        List<BlockPos> controllers = List.of(A, b);
        assertEquals(0, ZONE.owner(new BlockPos(2, 64, 0), controllers), "离 A 更近");
        assertEquals(1, ZONE.owner(new BlockPos(4, 64, 0), controllers), "离 B 更近");
        assertEquals(0, ZONE.owner(new BlockPos(3, 64, 0), controllers), "距离相同算先建的 A");
        assertEquals(-1, ZONE.owner(new BlockPos(20, 64, 0), controllers));
    }

    @Test
    void countDeduplicatesAcrossFactoriesAndCapsPerType() {
        BlockPos b = new BlockPos(6, 64, 0);
        List<BlockPos> controllers = List.of(A, b);
        Map<BlockPos, FactoryEquipment> world = new HashMap<>();
        world.put(new BlockPos(3, 64, 0), FactoryEquipment.SMOKER); // 等距 → A
        world.put(new BlockPos(4, 64, 0), FactoryEquipment.SMOKER); // → B
        world.put(new BlockPos(-1, 64, 0), FactoryEquipment.SMOKER); // 只在 A
        for (int z = -4; z <= 4; z++) world.put(new BlockPos(-4, 65, z), FactoryEquipment.COMPOSTER); // 9 个，只在 A
        var countA = ZONE.count(0, controllers, world::get, 5);
        var countB = ZONE.count(1, controllers, world::get, 5);
        assertEquals(2, countA.get(FactoryEquipment.SMOKER));
        assertEquals(5, countA.get(FactoryEquipment.COMPOSTER), "每种最多计 5 台");
        assertEquals(1, countB.get(FactoryEquipment.SMOKER));
        assertNull(countB.get(FactoryEquipment.COMPOSTER));
    }
}
