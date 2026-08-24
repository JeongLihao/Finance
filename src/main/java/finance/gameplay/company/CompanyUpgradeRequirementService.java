package finance.gameplay.company;

import finance.company.CompanyType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompanyUpgradeRequirementService {
    public record Requirement(long cash, Map<Item, Integer> materials) {}
    private CompanyUpgradeRequirementService() {}
    public static Requirement requirement(CompanyType type, CompanyFacilityType facilityType, int currentLevel) {
        if (type == null || facilityType == null || currentLevel < 1 || currentLevel >= CompanyFacilityRecord.MAX_LEVEL) return null;
        Map<Item, Integer> materials = new LinkedHashMap<>();
        if (currentLevel == 1) {
            materials.put(Items.IRON_INGOT, 12);
            materials.put(Items.COPPER_INGOT, 8);
            materials.put(Items.REDSTONE, 8);
            return new Requirement(2_000L, Map.copyOf(materials));
        }
        materials.put(Items.OBSIDIAN, 8);
        materials.put(Items.QUARTZ, 12);
        materials.put(Items.REDSTONE, 16);
        return new Requirement(8_000L, Map.copyOf(materials));
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
