package finance.gameplay.company.factory;

import finance.company.CompanyType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static finance.gameplay.company.factory.FactoryEquipment.*;
import static finance.gameplay.company.factory.ProductionTier.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryStateTest {
    private static final CompanyType FOOD = CompanyType.FOOD;
    private static final int PROVISIONAL = 14, STALE = 7, RETOOL = 3;

    private static Map<FactoryEquipment, Integer> manual() { return counts(COMPOSTER, 2); }

    private static Map<FactoryEquipment, Integer> allTiers() {
        return counts(COMPOSTER, 2, HAY_BLOCK, 4, BARREL, 1, PISTON, 4, OBSERVER, 2, HOPPER, 2);
    }

    private static Map<FactoryEquipment, Integer> counts(Object... pairs) {
        Map<FactoryEquipment, Integer> map = new EnumMap<>(FactoryEquipment.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((FactoryEquipment) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }

    private static void resolve(FactoryState s, int level, long day) {
        s.resolveDay(FOOD, level, day, PROVISIONAL, STALE, RETOOL);
    }

    @Test void legacyRecordGetsProvisionalWorkshopThenStops() {
        FactoryState s = new FactoryState();
        resolve(s, 1, 10);
        assertEquals(24, s.provisionalUntilDay(), "第一次日结开始 14 天兼容期");
        assertNull(s.activeTier());
        assertEquals(ProductionMethods.legacy(FOOD).outputs(), s.effectiveMethod(FOOD, 10).outputs(), "临时作坊沿用原有规则");
        resolve(s, 1, 24);
        assertNotNull(s.effectiveMethod(FOOD, 24), "最后一天仍可生产");
        resolve(s, 1, 25);
        assertNull(s.effectiveMethod(FOOD, 25), "到期仍没设备 → 缺少设备");
    }

    @Test void provisionalConvertsAsSoonAsEquipmentFitsWithoutRetool() {
        FactoryState s = new FactoryState();
        resolve(s, 1, 0);
        s.recordScan(manual(), 3, 16);
        resolve(s, 1, 3);
        assertEquals(MANUAL, s.activeTier());
        assertEquals(FactoryState.PROVISIONAL_NONE, s.provisionalUntilDay());
        assertFalse(s.retooling(3), "转正不进改造期");
    }

    @Test void newBuildHasNoProvisionalAndFirstActivationHasNoRetool() {
        FactoryState s = new FactoryState();
        s.markNewBuild();
        resolve(s, 1, 0);
        assertNull(s.effectiveMethod(FOOD, 0), "新建设施没设备就不生产");
        s.recordScan(manual(), 1, 16);
        resolve(s, 1, 1);
        assertEquals(MANUAL, s.activeTier());
        assertFalse(s.retooling(1));
    }

    @Test void switchingToTargetStartsRetoolAndDegradeDoesNot() {
        FactoryState s = new FactoryState();
        s.markNewBuild();
        s.recordScan(allTiers(), 0, 16);
        resolve(s, 3, 0);
        assertEquals(MANUAL, s.activeTier(), "默认目标是手工档");
        s.setTargetTier(INDUSTRIAL);
        s.recordScan(allTiers(), 1, 16);
        resolve(s, 3, 1);
        assertEquals(INDUSTRIAL, s.activeTier(), "次日生效");
        assertTrue(s.retooling(1) && s.retooling(3));
        assertFalse(s.retooling(4), "改造期 3 天：第 1、2、3 天");
        // 拆掉活塞：退化到改良档，不进新的改造期
        s.recordScan(counts(COMPOSTER, 2, HAY_BLOCK, 4, BARREL, 1), 10, 16);
        resolve(s, 3, 10);
        assertEquals(IMPROVED, s.activeTier());
        assertFalse(s.retooling(10));
        assertEquals(INDUSTRIAL, s.targetTier(), "仍记住玩家选的目标");
        // 补齐后自动恢复，并经历改造期
        s.recordScan(allTiers(), 11, 16);
        resolve(s, 3, 11);
        assertEquals(INDUSTRIAL, s.activeTier());
        assertTrue(s.retooling(11));
    }

    @Test void recoveryAfterMissingEquipmentRetools() {
        FactoryState s = new FactoryState();
        s.markNewBuild();
        s.recordScan(manual(), 0, 16);
        resolve(s, 1, 0);
        s.recordScan(Map.of(), 1, 16);
        resolve(s, 1, 1);
        assertNull(s.activeTier(), "设备拆光 → 停产");
        s.recordScan(manual(), 2, 16);
        resolve(s, 1, 2);
        assertEquals(MANUAL, s.activeTier());
        assertTrue(s.retooling(2), "缺设备后恢复要经历改造期");
    }

    @Test void staleScanCountsAsMissingAndResolveIsIdempotent() {
        FactoryState s = new FactoryState();
        s.markNewBuild();
        s.recordScan(manual(), 0, 16);
        resolve(s, 1, 7);
        assertEquals(MANUAL, s.activeTier(), "7 天内沿用旧扫描");
        s.setTargetTier(INDUSTRIAL);
        resolve(s, 1, 7);
        assertEquals(MANUAL, s.activeTier());
        resolve(s, 1, 8);
        assertNull(s.activeTier(), "超过 7 天未扫描按缺少设备");
        resolve(s, 1, 5);
        assertNull(s.activeTier(), "回拨日期不重算");
    }

    @Test void scanIsCappedPerType() {
        FactoryState s = new FactoryState();
        s.recordScan(counts(COMPOSTER, 40), 0, 16);
        assertEquals(16, s.equipment().get(COMPOSTER));
    }
}
