package finance.warehouse;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plans and commits several real inventory removals as one compensatable operation. */
public final class PhysicalMaterialTransaction {
    private PhysicalMaterialTransaction() {}

    public static List<InventoryTransactionService.RemovalPlan> plan(ServerPlayer player,
                                                                     Map<Item, Integer> requirements) {
        if (player == null || requirements == null || requirements.isEmpty()) return null;
        List<InventoryTransactionService.RemovalPlan> plans = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : requirements.entrySet()) {
            InventoryTransactionService.RemovalPlan plan = InventoryTransactionService.planRemoval(
                    player.getInventory(), entry.getKey(), entry.getValue());
            if (plan == null) return null;
            plans.add(plan);
        }
        return List.copyOf(plans);
    }

    public static boolean commit(ServerPlayer player, List<InventoryTransactionService.RemovalPlan> plans) {
        if (player == null || plans == null) return false;
        List<InventoryTransactionService.RemovalPlan> committed = new ArrayList<>();
        for (InventoryTransactionService.RemovalPlan plan : plans) {
            if (!InventoryTransactionService.commitRemoval(player.getInventory(), plan)) {
                rollback(player, committed);
                return false;
            }
            committed.add(plan);
        }
        return true;
    }

    public static boolean rollback(ServerPlayer player, List<InventoryTransactionService.RemovalPlan> plans) {
        boolean exact = true;
        for (int i = plans.size() - 1; i >= 0; i--) {
            exact &= InventoryTransactionService.rollbackRemoval(player, plans.get(i));
        }
        return exact;
    }
}
