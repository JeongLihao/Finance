package finance.gameplay.company.factory;

import java.util.List;
import java.util.Locale;

/**
 * 厂房设备：厂区里会被计数的原版方块。一种设备可对应多个方块 ID（如各种铁轨、各种铁砧）。
 * 只认真实放置的方块，不认物品展示框或盔甲架里的物品。
 */
public enum FactoryEquipment {
    COMPOSTER("堆肥桶", "minecraft:composter"),
    HAY_BLOCK("干草块", "minecraft:hay_block"),
    BARREL("木桶", "minecraft:barrel"),
    PISTON("活塞", "minecraft:piston", "minecraft:sticky_piston"),
    OBSERVER("侦测器", "minecraft:observer"),
    HOPPER("漏斗", "minecraft:hopper"),
    CRAFTING_TABLE("工作台", "minecraft:crafting_table"),
    SMOKER("烟熏炉", "minecraft:smoker"),
    BLAST_FURNACE("高炉", "minecraft:blast_furnace"),
    REDSTONE_LAMP("红石灯", "minecraft:redstone_lamp"),
    FURNACE("熔炉", "minecraft:furnace"),
    CAULDRON("炼药锅", "minecraft:cauldron", "minecraft:water_cauldron", "minecraft:lava_cauldron",
            "minecraft:powder_snow_cauldron"),
    STONECUTTER("切石机", "minecraft:stonecutter"),
    ANVIL("铁砧", "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"),
    GRINDSTONE("砂轮", "minecraft:grindstone"),
    RAIL("铁轨", "minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail"),
    CHEST("箱子", "minecraft:chest", "minecraft:trapped_chest"),
    LOOM("织布机", "minecraft:loom");

    private final String displayName;
    private final List<String> blockIds;

    FactoryEquipment(String displayName, String... blockIds) {
        this.displayName = displayName;
        this.blockIds = List.of(blockIds);
    }

    public String displayName() { return displayName; }

    public List<String> blockIds() { return blockIds; }

    /** 本地化键：screen.finance.factory.equipment.&lt;小写名&gt;。 */
    public String translationKey() { return "screen.finance.factory.equipment." + name().toLowerCase(Locale.ROOT); }

    /** 按方块注册名找设备；不是设备返回 null。 */
    public static FactoryEquipment fromBlockId(String blockId) {
        if (blockId == null) return null;
        for (FactoryEquipment e : values()) if (e.blockIds.contains(blockId)) return e;
        return null;
    }
}
