package finance.warehouse;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WarehouseUpgradeRequirementService {
    public record Requirement(WarehouseTier targetTier, long cash, Map<Item, Integer> materials) {}

    private WarehouseUpgradeRequirementService() {}

    public static Requirement requirement(WarehouseTier current) {
        if (current == null || current.next() == null) return null;
        Map<Item, Integer> materials = new LinkedHashMap<>();
        if (current == WarehouseTier.BASIC) {
            materials.put(Items.IRON_INGOT, 8);
            materials.put(Items.COPPER_INGOT, 8);
            materials.put(Items.REDSTONE, 4);
            return new Requirement(WarehouseTier.REINFORCED, 250, Map.copyOf(materials));
        }
        materials.put(Items.OBSIDIAN, 8);
        materials.put(Items.QUARTZ, 8);
        materials.put(Items.REDSTONE, 8);
        return new Requirement(WarehouseTier.INDUSTRIAL, 1_500, Map.copyOf(materials));
    }

    public static String summary(Requirement requirement) {
        if (requirement == null) return "MAX";
        StringBuilder result = new StringBuilder();
        requirement.materials().forEach((item, amount) -> {
            if (!result.isEmpty()) result.append(", ");
            result.append(amount).append('x').append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
        });
        return result.append(" + ").append(requirement.cash()).toString();
    }
}
