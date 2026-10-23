package finance.factory;

import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 加工机的生产逻辑（不依赖方块和世界，可直接测试）：两个原料槽 + 燃料槽 + 成品槽，
 * 像熔炉一样每 tick 推进；冶炼配方要烧燃料；成品槽放不下就停。
 */
public final class MachineCore {

    public static final int SLOT_INPUT = 0, SLOT_SECOND = 1, SLOT_FUEL = 2, SLOT_OUTPUT = 3, SLOT_COUNT = 4;

    /** 状态：工作中、没有能做的配方、成品槽满了、缺燃料。 */
    public static final int WORKING = 0, IDLE = 1, OUTPUT_FULL = 2, NO_FUEL = 3;

    /** 机器的物品槽：原料槽只收配方原料，燃料槽只收能烧的东西，成品槽外面放不进去。 */
    public static final class Items extends ItemStackHandler {
        private final Runnable onChanged;
        private final ToIntFunction<ItemStack> fuel;

        Items(Runnable onChanged, ToIntFunction<ItemStack> fuel) {
            super(SLOT_COUNT);
            this.onChanged = onChanged;
            this.fuel = fuel;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChanged.run();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> FactoryRecipe.isFirstInput(stack);
                case SLOT_SECOND -> FactoryRecipe.isSecondInput(stack);
                case SLOT_FUEL -> fuel.applyAsInt(stack) > 0;
                default -> false;
            };
        }
    }

    /** 游戏里的燃料表：和熔炉一样。 */
    public static final ToIntFunction<ItemStack> FURNACE_FUEL = s -> ForgeHooks.getBurnTime(s, RecipeType.SMELTING);

    public static boolean isFuel(ItemStack stack) {
        return FURNACE_FUEL.applyAsInt(stack) > 0;
    }

    /**
     * 烧掉这一槽里的一个燃料，返回它能烧多少 tick；不是燃料返回 0、什么也不动。
     * 桶类燃料留下空桶（空桶不能烧，漏斗可以取走）。
     */
    public static int burnOne(IItemHandlerModifiable items, int slot, ToIntFunction<ItemStack> fuel) {
        ItemStack stack = items.getStackInSlot(slot);
        int burn = fuel.applyAsInt(stack);
        if (burn <= 0) return 0;
        ItemStack left = stack.copy();
        left.shrink(1);
        items.setStackInSlot(slot, left.isEmpty() ? stack.getCraftingRemainingItem() : left);
        return burn;
    }

    private final Items items;
    private final ToIntFunction<ItemStack> fuel;
    private @Nullable String activeRecipe;
    private int progress, maxProgress = 1, burnTime, burnDuration, status = IDLE;

    /** fuel：一个物品能烧多少 tick，0 = 不是燃料。 */
    public MachineCore(Runnable onChanged, ToIntFunction<ItemStack> fuel) {
        this.fuel = fuel;
        items = new Items(onChanged, fuel);
    }

    public Items items() {
        return items;
    }

    /** 推进一 tick，返回状态有没有变。 */
    public boolean tick() {
        if (burnTime > 0) burnTime--;
        FactoryRecipe recipe = FactoryRecipe.find(items.getStackInSlot(SLOT_INPUT), items.getStackInSlot(SLOT_SECOND));
        int next;
        if (recipe == null) {
            activeRecipe = null;
            progress = 0;
            next = IDLE;
        } else {
            if (!recipe.id().equals(activeRecipe)) {
                activeRecipe = recipe.id();
                progress = 0;
            }
            maxProgress = recipe.cookTime();
            if (!canAcceptOutput(recipe)) next = OUTPUT_FULL;
            else if (recipe.needsFuel() && burnTime <= 0 && !igniteFuel()) next = NO_FUEL;
            else {
                next = WORKING;
                if (++progress >= maxProgress) {
                    craft(recipe);
                    progress = 0;
                }
            }
        }
        boolean changed = next != status;
        status = next;
        return changed;
    }

    /** 从燃料槽烧掉一个燃料，成功返回 true。 */
    private boolean igniteFuel() {
        int burn = burnOne(items, SLOT_FUEL, fuel);
        if (burn <= 0) return false;
        burnTime = burnDuration = burn;
        return true;
    }

    private boolean canAcceptOutput(FactoryRecipe recipe) {
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        return output.isEmpty()
                || (output.is(recipe.output()) && output.getCount() + recipe.outputCount() <= output.getMaxStackSize());
    }

    /** 扣原料、放成品；调用前已确认成品槽放得下。 */
    private void craft(FactoryRecipe recipe) {
        items.extractItem(SLOT_INPUT, recipe.inputCount(), false);
        if (recipe.second() != null) items.extractItem(SLOT_SECOND, recipe.secondCount(), false);
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        items.setStackInSlot(
                SLOT_OUTPUT,
                output.isEmpty()
                        ? recipe.resultStack()
                        : output.copyWithCount(output.getCount() + recipe.outputCount()));
    }

    public @Nullable String activeRecipe() {
        return activeRecipe;
    }

    public int progress() {
        return progress;
    }

    public int maxProgress() {
        return maxProgress;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnDuration() {
        return burnDuration;
    }

    public int status() {
        return status;
    }

    /** 给界面同步用（客户端收到数据时写回）。 */
    public void setSynced(int index, int value) {
        switch (index) {
            case 0 -> progress = value;
            case 1 -> maxProgress = Math.max(1, value);
            case 2 -> burnTime = value;
            case 3 -> burnDuration = value;
            case 4 -> status = value;
            default -> {}
        }
    }

    public void save(CompoundTag tag) {
        tag.put("Inventory", items.serializeNBT());
        if (activeRecipe != null) tag.putString("Active", activeRecipe);
        tag.putInt("Progress", progress);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
    }

    public void load(CompoundTag tag) {
        items.deserializeNBT(tag.getCompound("Inventory"));
        activeRecipe = tag.contains("Active") ? tag.getString("Active") : null;
        progress = Math.max(0, tag.getInt("Progress"));
        burnTime = Math.max(0, tag.getInt("BurnTime"));
        burnDuration = Math.max(0, tag.getInt("BurnDuration"));
    }
}
