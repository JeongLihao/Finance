package finance.gameplay.company.factory;

import java.util.List;
import java.util.Locale;

/**
 * 厂房设备：厂区里会被计数的原版方块。一种设备可对应多个方块 ID（如各种铁轨、各种铁砧）。
 * 只认真实放置的方块，不认物品展示框或盔甲架里的物品。
 */
public enum FactoryEquipment {
    COMPOSTER("堆肥桶", "minecraft:composter"),
    HAY_BLOCK(false, "干草块", "minecraft:hay_block"),
    BARREL(false, "木桶", "minecraft:barrel"),
    PISTON("活塞", "minecraft:piston", "minecraft:sticky_piston"),
    OBSERVER("侦测器", "minecraft:observer"),
    HOPPER(false, "漏斗", "minecraft:hopper"),
    CRAFTING_TABLE("工作台", "minecraft:crafting_table"),
    SMOKER("烟熏炉", "minecraft:smoker"),
    BLAST_FURNACE("高炉", "minecraft:blast_furnace"),
    REDSTONE_LAMP(false, "红石灯", "minecraft:redstone_lamp"),
    FURNACE("熔炉", "minecraft:furnace"),
    CAULDRON(
            "炼药锅",
            "minecraft:cauldron",
            "minecraft:water_cauldron",
            "minecraft:lava_cauldron",
            "minecraft:powder_snow_cauldron"),
    STONECUTTER("切石机", "minecraft:stonecutter"),
    ANVIL("铁砧", "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"),
    GRINDSTONE("砂轮", "minecraft:grindstone"),
    RAIL(
            false,
            "铁轨",
            "minecraft:rail",
            "minecraft:powered_rail",
            "minecraft:detector_rail",
            "minecraft:activator_rail"),
    CHEST(false, "箱子", "minecraft:chest", "minecraft:trapped_chest"),
    LOOM("织布机", "minecraft:loom");

    private final String displayName;
    private final List<String> blockIds;
    private final boolean providesJobs;

    FactoryEquipment(String displayName, String... blockIds) {
        this(true, displayName, blockIds);
    }

    /** providesJobs 为 false 的是辅助方块（储物、运输、照明）：只算设备门槛，不提供岗位。 */
    FactoryEquipment(boolean providesJobs, String displayName, String... blockIds) {
        this.providesJobs = providesJobs;
        this.displayName = displayName;
        this.blockIds = List.of(blockIds);
    }

    public boolean providesJobs() {
        return providesJobs;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> blockIds() {
        return blockIds;
    }

    /** 本地化键：screen.finance.factory.equipment.&lt;小写名&gt;。 */
    public String translationKey() {
        return "screen.finance.factory.equipment." + name().toLowerCase(Locale.ROOT);
    }

    /** 按方块注册名找设备；不是设备返回 null。 */
    public static FactoryEquipment fromBlockId(String blockId) {
        if (blockId == null) return null;
        for (FactoryEquipment e : values()) if (e.blockIds.contains(blockId)) return e;
        return null;
    }
}
