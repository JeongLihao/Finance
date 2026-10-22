package finance.factory;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.gameplay.company.CompanyGameplayManager;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 机器和公司库存之间搬货：成品存进公司仓库（之后每天自动卖），原料从公司仓库取。
 * 只认能对应到商品的普通物品（改过名、附魔等带数据的不收）。
 */
public final class CompanyStock {

    /** 机器离公司仓库控制器多远以内算“连着仓库”。 */
    public static final int WAREHOUSE_RANGE = 16;

    private CompanyStock() {}

    /** 这家公司在机器附近有没有绑定的仓库。 */
    public static boolean nearWarehouse(Company company, ServerLevel level, BlockPos pos) {
        if (company == null || level == null) return false;
        String dimension = level.dimension().location().toString();
        for (var id : CompanyGameplayManager.profileFor(company).warehouseIds()) {
            WarehouseRecord warehouse = WarehouseManager.get(id);
            if (warehouse != null
                    && dimension.equals(warehouse.dimensionId())
                    && warehouse.blockPos().distSqr(pos) <= (double) WAREHOUSE_RANGE * WAREHOUSE_RANGE) return true;
        }
        return false;
    }

    /** 把整组物品存进公司库存，成功返回存进去的数量，存不下或不是商品返回 0（物品不动）。 */
    public static int deposit(Company company, ItemStack stack) {
        Commodity commodity = plainCommodity(stack);
        if (company == null || commodity == null) return 0;
        Map<String, Integer> goods = Map.of(commodity.getId(), stack.getCount());
        return CompanyInventoryFacade.addOutputAtomically(company, goods) ? stack.getCount() : 0;
    }

    /** 从公司库存取出最多 max 个这种物品，取不到返回空堆。 */
    public static ItemStack withdraw(Company company, Item item, int max) {
        Commodity commodity = plainCommodity(new ItemStack(item));
        if (company == null || commodity == null || max <= 0) return ItemStack.EMPTY;
        int quantity = Math.min(max, CompanyInventoryFacade.availableInput(company, commodity.getId()));
        if (quantity <= 0) return ItemStack.EMPTY;
        return CompanyInventoryFacade.consumeInputAtomically(company, Map.of(commodity.getId(), quantity)) == null
                ? ItemStack.EMPTY
                : new ItemStack(item, quantity);
    }

    private static Commodity plainCommodity(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.hasTag()) return null;
        return CommodityRegistry.forItem(stack);
    }
}
