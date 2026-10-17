package finance.gameplay.company.factory;

import finance.company.CompanyType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static finance.gameplay.company.factory.FactoryEquipment.*;
import static finance.gameplay.company.factory.ProductionTier.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionMethodsTest {

    private static Map<FactoryEquipment, Integer> counts(Object... pairs) {
        Map<FactoryEquipment, Integer> map = new EnumMap<>(FactoryEquipment.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((FactoryEquipment) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }

    @Test void everyIndustryHasThreeTiersInOrder() {
        for (CompanyType type : CompanyType.values()) {
            var methods = ProductionMethods.forType(type);
            assertEquals(3, methods.size(), type.name());
            assertEquals(MANUAL, methods.get(0).tier());
            assertEquals(IMPROVED, methods.get(1).tier());
            assertEquals(INDUSTRIAL, methods.get(2).tier());
            for (ProductionMethod m : methods) {
                assertFalse(m.required().isEmpty(), m.key());
                assertFalse(m.outputs().isEmpty(), m.key());
                assertTrue(m.jobsPerDevice() > 0, m.key());
            }
        }
    }

    @Test void manualTierMatchesLegacyCompanyTypeOutputs() {
        for (CompanyType type : CompanyType.values()) {
            ProductionMethod manual = ProductionMethods.get(type, MANUAL);
            assertEquals(type.getDailyProduction(), manual.outputs(), type.name() + " 手工档产出应与旧规则一致");
        }
        // 铁矿手工档新增 20 木头燃料，其余行业手工档投入与旧规则一致
        for (CompanyType type : CompanyType.values()) {
            if (type == CompanyType.RAW_MATERIALS) continue;
            assertEquals(type.getDailyConsumption(), ProductionMethods.get(type, MANUAL).inputs(), type.name());
        }
    }

    @Test void higherTiersProduceMoreWithFewerJobsPerDevice() {
        for (CompanyType type : CompanyType.values()) {
            var m = ProductionMethods.forType(type);
            long manualOut = m.get(0).outputs().values().stream().mapToLong(Integer::longValue).sum();
            long industrialOut = m.get(2).outputs().values().stream().mapToLong(Integer::longValue).sum();
            assertTrue(industrialOut > manualOut, type.name());
            assertTrue(m.get(2).jobsPerDevice() < m.get(0).jobsPerDevice(), type.name());
        }
    }

    @Test void resolvePicksHighestSatisfiedTierAtOrBelowTarget() {
        var food = CompanyType.FOOD;
        Map<FactoryEquipment, Integer> all = counts(COMPOSTER, 2, HAY_BLOCK, 4, BARREL, 1, PISTON, 4, OBSERVER, 2, HOPPER, 2);
        assertEquals(INDUSTRIAL, ProductionMethods.resolve(food, 3, all, INDUSTRIAL));
        assertEquals(IMPROVED, ProductionMethods.resolve(food, 3, all, IMPROVED), "不会超过目标档");
        assertEquals(IMPROVED, ProductionMethods.resolve(food, 2, all, INDUSTRIAL), "工业档需要 3 级");
        assertEquals(MANUAL, ProductionMethods.resolve(food, 1, all, INDUSTRIAL), "改良档需要 2 级");
        // 设备不够时退化
        assertEquals(MANUAL, ProductionMethods.resolve(food, 3, counts(COMPOSTER, 2, HAY_BLOCK, 3, BARREL, 1), INDUSTRIAL));
        assertNull(ProductionMethods.resolve(food, 3, counts(COMPOSTER, 1), MANUAL), "连手工档都不满足");
        assertNull(ProductionMethods.resolve(food, 3, Map.of(), null));
    }

    @Test void jobsComeFromDevicesAndAreCappedByLevel() {
        ProductionMethod manual = ProductionMethods.get(CompanyType.FOOD, MANUAL);
        assertEquals(10, ProductionMethods.jobs(manual, counts(COMPOSTER, 2), 1, 16, 20));
        assertEquals(20, ProductionMethods.jobs(manual, counts(COMPOSTER, 6), 1, 16, 20), "1 级上限 20");
        assertEquals(30, ProductionMethods.jobs(manual, counts(COMPOSTER, 6), 2, 16, 20));
        assertEquals(40, ProductionMethods.jobs(manual, counts(COMPOSTER, 64), 3, 8, 20), "每种设备最多计 8 台");
        ProductionMethod industrial = ProductionMethods.get(CompanyType.FOOD, INDUSTRIAL);
        // 4 活塞 + 2 侦测器 + 2 漏斗 = 8 台 × 2
        assertEquals(16, ProductionMethods.jobs(industrial, counts(PISTON, 4, OBSERVER, 2, HOPPER, 2, COMPOSTER, 9), 3, 16, 20),
                "不属于本档的设备不提供岗位");
        assertEquals(0, ProductionMethods.jobs(null, counts(COMPOSTER, 2), 3, 16, 20));
    }

    @Test void equipmentLookupCoversBlockVariants() {
        assertEquals(PISTON, FactoryEquipment.fromBlockId("minecraft:sticky_piston"));
        assertEquals(RAIL, FactoryEquipment.fromBlockId("minecraft:powered_rail"));
        assertEquals(ANVIL, FactoryEquipment.fromBlockId("minecraft:damaged_anvil"));
        assertEquals(CAULDRON, FactoryEquipment.fromBlockId("minecraft:water_cauldron"));
        assertNull(FactoryEquipment.fromBlockId("minecraft:stone"));
        assertNull(FactoryEquipment.fromBlockId(null));
    }

    @Test void methodKeysAreUnique() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (CompanyType type : CompanyType.values()) {
            for (ProductionMethod m : ProductionMethods.forType(type)) assertTrue(keys.add(m.key()), m.key());
        }
        assertEquals(21, keys.size());
    }
}
