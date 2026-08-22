package finance.warehouse;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class InventoryTransactionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAIN_INVENTORY_SLOTS = 36;

    public record SlotMutation(int slot, ItemStack expectedBefore, int amount) {}
    public record RemovalPlan(Item item, int total, List<SlotMutation> mutations) {}
    public record InsertionPlan(Item item, int total, List<SlotMutation> mutations) {}

    private InventoryTransactionService() {}

    public static boolean eligible(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && stack.is(item) && !stack.hasTag()
                && !stack.isDamageableItem();
    }

    public static int countEligible(Inventory inventory, Item item) {
        long count = 0;
        int slots = Math.min(MAIN_INVENTORY_SLOTS, inventory.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (eligible(stack, item)) count += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public static RemovalPlan planRemoval(Inventory inventory, Item item, int amount) {
        if (inventory == null || item == null || amount <= 0) return null;
        int slots = Math.min(MAIN_INVENTORY_SLOTS, inventory.getContainerSize());
        List<InventorySlotPlanner.SlotState> states = slotStates(inventory, item, slots);
        List<InventorySlotPlanner.Allocation> allocations = InventorySlotPlanner.removal(states, amount);
        if (allocations == null) return null;
        List<SlotMutation> mutations = allocations.stream().map(allocation ->
                new SlotMutation(allocation.slot(), inventory.getItem(allocation.slot()).copy(), allocation.amount())).toList();
        return new RemovalPlan(item, amount, mutations);
    }

    public static boolean commitRemoval(Inventory inventory, RemovalPlan plan) {
        if (inventory == null || plan == null) return false;
        for (SlotMutation mutation : plan.mutations()) {
            if (!sameExact(inventory.getItem(mutation.slot()), mutation.expectedBefore())) return false;
        }
        for (SlotMutation mutation : plan.mutations()) inventory.getItem(mutation.slot()).shrink(mutation.amount());
        inventory.setChanged();
        return true;
    }

    public static boolean rollbackRemoval(ServerPlayer player, RemovalPlan plan) {
        Inventory inventory = player.getInventory();
        boolean exact = true;
        for (SlotMutation mutation : plan.mutations()) {
            ItemStack before = mutation.expectedBefore();
            int afterCount = before.getCount() - mutation.amount();
            ItemStack current = inventory.getItem(mutation.slot());
            if ((afterCount == 0 && current.isEmpty())
                    || (afterCount > 0 && sameItemTags(current, before) && current.getCount() == afterCount)) {
                inventory.setItem(mutation.slot(), before.copy());
            } else {
                exact = false;
                ItemStack recovery = new ItemStack(plan.item(), mutation.amount());
                inventory.add(recovery);
                if (!recovery.isEmpty()) {
                    player.drop(recovery, false);
                    LOGGER.error("Warehouse rollback dropped {} recovered items for {}", recovery.getCount(), player.getUUID());
                }
            }
        }
        inventory.setChanged();
        return exact;
    }

    public static InsertionPlan planInsertion(Inventory inventory, Item item, int amount) {
        if (inventory == null || item == null || amount <= 0) return null;
        int slots = Math.min(MAIN_INVENTORY_SLOTS, inventory.getContainerSize());
        List<InventorySlotPlanner.SlotState> states = slotStates(inventory, item, slots);
        int maxStack = item.getMaxStackSize();
        List<InventorySlotPlanner.Allocation> allocations = InventorySlotPlanner.insertion(states, amount, maxStack);
        if (allocations == null) return null;
        List<SlotMutation> mutations = allocations.stream().map(allocation ->
                new SlotMutation(allocation.slot(), inventory.getItem(allocation.slot()).copy(), allocation.amount())).toList();
        return new InsertionPlan(item, amount, mutations);
    }

    public static boolean commitInsertion(Inventory inventory, InsertionPlan plan) {
        if (inventory == null || plan == null) return false;
        for (SlotMutation mutation : plan.mutations()) {
            if (!sameExact(inventory.getItem(mutation.slot()), mutation.expectedBefore())) return false;
        }
        for (SlotMutation mutation : plan.mutations()) {
            ItemStack before = mutation.expectedBefore();
            if (before.isEmpty()) inventory.setItem(mutation.slot(), new ItemStack(plan.item(), mutation.amount()));
            else inventory.getItem(mutation.slot()).grow(mutation.amount());
        }
        inventory.setChanged();
        return true;
    }

    public static boolean rollbackInsertion(Inventory inventory, InsertionPlan plan) {
        for (SlotMutation mutation : plan.mutations()) {
            ItemStack current = inventory.getItem(mutation.slot());
            ItemStack before = mutation.expectedBefore();
            int expected = before.getCount() + mutation.amount();
            if (!current.is(plan.item()) || current.getCount() != expected || current.hasTag()) return false;
        }
        for (SlotMutation mutation : plan.mutations()) inventory.setItem(mutation.slot(), mutation.expectedBefore().copy());
        inventory.setChanged();
        return true;
    }

    private static boolean sameExact(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() && second.isEmpty();
        return first.getCount() == second.getCount() && sameItemTags(first, second);
    }

    private static boolean sameItemTags(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameTags(first, second);
    }

    private static List<InventorySlotPlanner.SlotState> slotStates(Inventory inventory, Item item, int slots) {
        List<InventorySlotPlanner.SlotState> states = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            boolean empty = stack.isEmpty();
            boolean same = !empty && stack.is(item) && !stack.hasTag();
            states.add(new InventorySlotPlanner.SlotState(empty ? 0 : stack.getCount(),
                    empty ? item.getMaxStackSize() : stack.getMaxStackSize(), same, empty,
                    empty || eligible(stack, item)));
        }
        return states;
    }
}
