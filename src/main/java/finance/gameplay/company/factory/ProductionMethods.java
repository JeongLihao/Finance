package finance.gameplay.company.factory;

import static finance.gameplay.company.factory.FactoryEquipment.*;
import static finance.gameplay.company.factory.ProductionTier.*;

import finance.company.CompanyType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产方式数据表（见 docs/COMPANY_FACILITIES.md 第 4 节）与档位判定（纯函数）。
 *
 * <p>手工档的投入产出与 {@link CompanyType} 原有的日产量/日消耗一致，老存档切到手工档不会改变产量。</p>
 */
public final class ProductionMethods {
    private static final Map<CompanyType, List<ProductionMethod>> TABLE = new EnumMap<>(CompanyType.class);

    static {
        add(CompanyType.FOOD, MANUAL, "food.manual", "人力耕作", 5, eq(COMPOSTER, 2), Map.of(), Map.of("wheat", 80));
        add(
                CompanyType.FOOD,
                IMPROVED,
                "food.improved",
                "畜力耕作",
                4,
                eq(COMPOSTER, 2, HAY_BLOCK, 4, BARREL, 1),
                Map.of("iron", 2),
                Map.of("wheat", 120));
        add(
                CompanyType.FOOD,
                INDUSTRIAL,
                "food.industrial",
                "机械农场",
                2,
                eq(PISTON, 4, OBSERVER, 2, HOPPER, 2),
                Map.of("iron", 3, "coal", 6),
                Map.of("wheat", 180));

        // 木桶是辅助方块不提供岗位：工作台 2 台 × 5 = 10 岗，与旧规则 1 级 10 岗一致
        add(
                CompanyType.FOOD_PROCESSING,
                MANUAL,
                "food_processing.manual",
                "手工作坊",
                5,
                eq(CRAFTING_TABLE, 2, BARREL, 2),
                Map.of("wheat", 40),
                Map.of("bread", 30));
        add(
                CompanyType.FOOD_PROCESSING,
                IMPROVED,
                "food_processing.improved",
                "蒸汽烘焙",
                4,
                eq(SMOKER, 3, BARREL, 2),
                Map.of("wheat", 60, "coal", 8),
                Map.of("bread", 48));
        add(
                CompanyType.FOOD_PROCESSING,
                INDUSTRIAL,
                "food_processing.industrial",
                "流水线",
                2,
                eq(SMOKER, 4, BLAST_FURNACE, 1, HOPPER, 2, REDSTONE_LAMP, 2),
                Map.of("wheat", 80, "coal", 12, "iron", 2),
                Map.of("bread", 70));

        add(
                CompanyType.RAW_MATERIALS,
                MANUAL,
                "raw_materials.manual",
                "手工冶炼",
                5,
                eq(FURNACE, 3),
                Map.of("wood", 20),
                Map.of("iron", 100));
        add(
                CompanyType.RAW_MATERIALS,
                IMPROVED,
                "raw_materials.improved",
                "高炉冶炼",
                4,
                eq(BLAST_FURNACE, 3, CAULDRON, 1),
                Map.of("coal", 15),
                Map.of("iron", 150));
        add(
                CompanyType.RAW_MATERIALS,
                INDUSTRIAL,
                "raw_materials.industrial",
                "工业冶炼",
                2,
                eq(BLAST_FURNACE, 5, HOPPER, 3, REDSTONE_LAMP, 2),
                Map.of("coal", 25),
                Map.of("iron", 230));

        add(
                CompanyType.BUILDING_BLOCKS,
                MANUAL,
                "building_blocks.manual",
                "人工采石",
                6,
                eq(STONECUTTER, 2),
                Map.of(),
                Map.of("stone", 120));
        add(
                CompanyType.BUILDING_BLOCKS,
                IMPROVED,
                "building_blocks.improved",
                "爆破开采",
                4,
                eq(STONECUTTER, 3, ANVIL, 1),
                Map.of("iron", 3),
                Map.of("stone", 180));
        add(
                CompanyType.BUILDING_BLOCKS,
                INDUSTRIAL,
                "building_blocks.industrial",
                "机械采石",
                2,
                eq(STONECUTTER, 4, PISTON, 4, OBSERVER, 2),
                Map.of("iron", 4, "coal", 8),
                Map.of("stone", 270));

        add(
                CompanyType.FORESTRY,
                MANUAL,
                "forestry.manual",
                "斧头伐木",
                6,
                eq(CRAFTING_TABLE, 1, BARREL, 2),
                Map.of(),
                Map.of("wood", 100));
        add(
                CompanyType.FORESTRY,
                IMPROVED,
                "forestry.improved",
                "锯木作坊",
                4,
                eq(STONECUTTER, 2, BARREL, 2),
                Map.of("iron", 2),
                Map.of("wood", 150));
        add(
                CompanyType.FORESTRY,
                INDUSTRIAL,
                "forestry.industrial",
                "蒸汽锯木",
                2,
                eq(STONECUTTER, 3, FURNACE, 2, HOPPER, 2),
                Map.of("iron", 3, "coal", 8),
                Map.of("wood", 220));

        add(
                CompanyType.COAL_MINING,
                MANUAL,
                "coal_mining.manual",
                "手镐开采",
                6,
                eq(GRINDSTONE, 2, BARREL, 1),
                Map.of("iron", 5),
                Map.of("coal", 80));
        add(
                CompanyType.COAL_MINING,
                IMPROVED,
                "coal_mining.improved",
                "矿车运输",
                4,
                eq(GRINDSTONE, 2, RAIL, 8, CHEST, 2),
                Map.of("iron", 6),
                Map.of("coal", 120));
        // 蒸汽抽水自耗 10 煤：投入和产出分别结算，净产出 180
        add(
                CompanyType.COAL_MINING,
                INDUSTRIAL,
                "coal_mining.industrial",
                "蒸汽抽水",
                2,
                eq(GRINDSTONE, 3, RAIL, 8, FURNACE, 2, PISTON, 2),
                Map.of("iron", 8, "coal", 10),
                Map.of("coal", 190));

        add(
                CompanyType.TEXTILE,
                MANUAL,
                "textile.manual",
                "手工纺纱",
                5,
                eq(LOOM, 2),
                Map.of("wheat", 10),
                Map.of("wool", 40));
        add(
                CompanyType.TEXTILE,
                IMPROVED,
                "textile.improved",
                "水力纺纱",
                4,
                eq(LOOM, 3, CAULDRON, 2),
                Map.of("wheat", 15, "iron", 1),
                Map.of("wool", 64));
        add(
                CompanyType.TEXTILE,
                INDUSTRIAL,
                "textile.industrial",
                "机器纺织",
                2,
                eq(LOOM, 4, PISTON, 2, REDSTONE_LAMP, 2),
                Map.of("wheat", 20, "coal", 6, "iron", 2),
                Map.of("wool", 95));
    }

    private ProductionMethods() {}

    private static void add(
            CompanyType type,
            ProductionTier tier,
            String key,
            String name,
            int jobsPerDevice,
            Map<FactoryEquipment, Integer> required,
            Map<String, Integer> inputs,
            Map<String, Integer> outputs) {
        TABLE.computeIfAbsent(type, t -> new ArrayList<>())
                .add(new ProductionMethod(type, tier, key, name, required, jobsPerDevice, inputs, outputs));
    }

    private static Map<FactoryEquipment, Integer> eq(Object... pairs) {
        Map<FactoryEquipment, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((FactoryEquipment) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }

    /** 某行业的三档方式，按手工 → 工业排序。未知行业返回空表。 */
    public static List<ProductionMethod> forType(CompanyType type) {
        List<ProductionMethod> list = type == null ? null : TABLE.get(type);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * 临时作坊用的方式：完全沿用 {@link CompanyType} 原有的日产量和日消耗，保证老存档更新后产量不变
     * （手工档的铁矿要额外烧木头，直接套用会让没有木头的老工厂停产）。
     */
    public static ProductionMethod legacy(CompanyType type) {
        if (type == null) return null;
        return new ProductionMethod(
                type,
                MANUAL,
                type.name().toLowerCase(java.util.Locale.ROOT) + ".provisional",
                "临时作坊",
                Map.of(),
                0,
                type.getDailyConsumption(),
                type.getDailyProduction());
    }

    public static ProductionMethod get(CompanyType type, ProductionTier tier) {
        if (tier == null) return null;
        for (ProductionMethod m : forType(type)) if (m.tier() == tier) return m;
        return null;
    }

    /** 某档是否可用：设施等级达标，且设备满足最少数量。 */
    public static boolean available(ProductionMethod method, int facilityLevel, Map<FactoryEquipment, Integer> counts) {
        return method != null && facilityLevel >= method.tier().requiredLevel() && method.satisfiedBy(counts);
    }

    /**
     * 退化规则：在不高于目标档的前提下，取满足条件的最高档；连手工档都不满足返回 null（缺少设备）。
     * 目标档为 null 时按工业档处理（不限制）。
     */
    public static ProductionTier resolve(
            CompanyType type, int facilityLevel, Map<FactoryEquipment, Integer> counts, ProductionTier target) {
        ProductionTier ceiling = target == null ? INDUSTRIAL : target;
        ProductionTier best = null;
        for (ProductionMethod m : forType(type)) {
            if (m.tier().ordinal() > ceiling.ordinal()) continue;
            if (available(m, facilityLevel, counts)) best = m.tier();
        }
        return best;
    }

    /** 等级岗位上限：等级 × 每级上限（默认 20/40/60）。 */
    public static long jobsCap(int facilityLevel, int capPerLevel) {
        return (long) Math.max(0, facilityLevel) * Math.max(0, capPerLevel);
    }

    /** 某方式的岗位 = min(设备岗位, 等级上限)。 */
    public static long jobs(
            ProductionMethod method,
            Map<FactoryEquipment, Integer> counts,
            int facilityLevel,
            int maxPerType,
            int capPerLevel) {
        if (method == null) return 0;
        return Math.min(method.deviceJobs(counts, maxPerType), jobsCap(facilityLevel, capPerLevel));
    }
}
