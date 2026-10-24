package finance.village;

import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.settlement.SettlementManager;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.InventoryTransactionService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家在聚落贸易站的摊位：登记、上货、改价、取货、关摊（供 {@link finance.network.VillageActionPacket} 调用）。
 * 每日 NPC 从摊位采购在 {@link VillageService}。
 */
public final class VillageStallService {

    private VillageStallService() {}

    /**
     * 登记摊位：每玩家每聚落每种商品限 1 个摊位。
     *
     * @return 摊位（已存在/非法返回 null）
     */
    public static VillageStall registerStall(ServerPlayer player, UUID settlementId, String commodityId, long price) {
        if (player == null || settlementId == null || commodityId == null || commodityId.isBlank() || price < 1) {
            return null;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return null;
        }
        if (SettlementManager.get(settlementId) == null) {
            return null;
        }
        if (CommodityRegistry.getCommodity(commodityId) == null) {
            return null;
        }
        // 摊位商品必须有 MC 实物映射（与需求篮子一致，纯虚拟商品不能上架）
        if (!CommodityItemResolver.resolve(commodityId).valid()) {
            return null;
        }
        final VillageStall stall;
        try {
            VillageManager.getOrCreate(settlementId);
            stall = VillageManager.registerStall(settlementId, player.getUUID(), commodityId, price);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalStateException：摊位/村庄数达上限（VillageManager 抛），同样走 register_failed 反馈，
            // 不能让异常逃到 Forge 网络线程
            return null;
        }
        if (stall != null) {
            EconomySavedData.markDirty();
        }
        return stall;
    }

    /**
     * 上架：从玩家背包扣除实物 → 摊位库存。没货不能卖（反刷钱核心）。
     */
    /** 玩家背包里能摆上这个摊位的普通物品数（不含改过名、附魔等带特殊数据的）。 */
    public static int plainCount(ServerPlayer player, VillageStall stall) {
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(stall.commodityId());
        if (player == null || !resolution.valid()) return 0;
        int count = 0;
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items)
            if (stack.is(resolution.item()) && !stack.hasTag()) count += stack.getCount();
        return count;
    }

    public static boolean stockStall(ServerPlayer player, UUID stallId, int quantity) {
        if (player == null || stallId == null || quantity <= 0) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(stall.commodityId());
        if (!resolution.valid()) {
            return false;
        }
        InventoryTransactionService.RemovalPlan plan =
                InventoryTransactionService.planRemoval(player.getInventory(), resolution.item(), quantity);
        if (plan == null) {
            return false;
        }
        if (!InventoryTransactionService.commitRemoval(player.getInventory(), plan)) {
            return false;
        }
        try {
            stall.addStock(quantity);
        } catch (IllegalArgumentException e) {
            InventoryTransactionService.rollbackRemoval(player, plan);
            return false;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 改价：仅摊主，单价 ≥ 1。
     */
    public static boolean setStallPrice(ServerPlayer player, UUID stallId, long price) {
        if (player == null || stallId == null || price < 1) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        try {
            stall.setUnitPrice(price);
        } catch (IllegalArgumentException e) {
            return false;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 下架：扣摊位库存 → 返还玩家背包实物；背包放不下则掉落脚下（自己的货，无刷钱风险）。
     */
    public static boolean withdrawStall(ServerPlayer player, UUID stallId, int quantity) {
        if (player == null || stallId == null || quantity <= 0) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(stall.commodityId());
        if (!resolution.valid()) {
            return false;
        }
        if (!stall.takeStock(quantity)) {
            return false;
        }
        int remaining = quantity;
        InventoryTransactionService.InsertionPlan plan =
                InventoryTransactionService.planInsertion(player.getInventory(), resolution.item(), quantity);
        if (plan != null && InventoryTransactionService.commitInsertion(player.getInventory(), plan)) {
            remaining = 0;
        }
        while (remaining > 0) {
            int n = Math.min(remaining, resolution.item().getMaxStackSize());
            player.drop(new ItemStack(resolution.item(), n), false);
            remaining -= n;
        }
        EconomySavedData.markDirty();
        return true;
    }

    /**
     * 关店：仅摊主；有库存先自动下架再关闭。
     */
    public static boolean closeStall(ServerPlayer player, UUID stallId) {
        if (player == null || stallId == null) {
            return false;
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.VILLAGE_ECONOMY)) {
            return false;
        }
        VillageStall stall = VillageManager.stall(stallId);
        if (stall == null || !player.getUUID().equals(stall.ownerId())) {
            return false;
        }
        int stock = stall.stock();
        if (stock > 0 && !withdrawStall(player, stallId, stock)) {
            return false;
        }
        boolean ok = VillageManager.removeStall(stallId);
        if (ok) {
            EconomySavedData.markDirty();
        }
        return ok;
    }
}
