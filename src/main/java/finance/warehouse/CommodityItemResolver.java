package finance.warehouse;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class CommodityItemResolver {
    public record Resolution(boolean valid, Item item, String messageKey) {}

    private CommodityItemResolver() {}

    public static Resolution resolve(String commodityId) {
        Commodity commodity = commodityId == null ? null : CommodityRegistry.getCommodity(commodityId);
        if (commodity == null) return new Resolution(false, null, "finance.warehouse.unknown_commodity");
        ResourceLocation id = ResourceLocation.tryParse(commodity.getItemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return new Resolution(false, null, "finance.warehouse.virtual_commodity");
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR || item.getDefaultInstance().isDamageableItem()) {
            return new Resolution(false, null, "finance.warehouse.virtual_commodity");
        }
        return new Resolution(true, item, "finance.warehouse.item_resolved");
    }
}
