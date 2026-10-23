package finance.factory;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * 机器给漏斗和管道看的受限视图：哪些槽能往里放、哪些能往外取由机器决定，
 * 能不能放进某一槽仍以机器自己的 isItemValid 为准。
 *
 * @param insertable 这一槽外面能不能往里放
 * @param extractable 这一槽当前的东西外面能不能取走
 */
public record AutomationView(
        IItemHandlerModifiable items, IntPredicate insertable, BiPredicate<Integer, ItemStack> extractable)
        implements IItemHandler {

    @Override
    public int getSlots() {
        return items.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return items.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return insertable.test(slot) ? items.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return extractable.test(slot, items.getStackInSlot(slot))
                ? items.extractItem(slot, amount, simulate)
                : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return items.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return insertable.test(slot) && items.isItemValid(slot, stack);
    }
}
