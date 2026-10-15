package finance.block.entity;

import finance.factory.FactoryRecipe;
import finance.gui.FactoryMenu;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工厂方块实体 —— 2 槽位（0=输入，1=输出）＋进度。
 *
 * <p>反 dup：产出前确认输出槽能<b>完全</b>容纳结果，否则不扣输入；
 * 扣减与放入在同一 tick 内原子完成。</p>
 */
public final class FactoryBlockEntity extends BlockEntity implements MenuProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactoryBlockEntity.class);

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    /**
     * BE 的物品 handler（具名类，单测可直接实例化走真实覆写路径）。
     *
     * <p>isItemValid 语义（vanilla 熔炉模式）：
     * <ul>
     *   <li>输出槽：玩家/料斗不可放入（恒 false），只能由生产逻辑内部直接写入（旁路 isItemValid）；</li>
     *   <li>输入槽：只接受有配方的物品（料斗塞垃圾直接拒收）。</li>
     * </ul>
     */
    static final class FactoryItemHandler extends ItemStackHandler {
        private final Runnable onChanged;

        FactoryItemHandler(Runnable onChanged) {
            super(SLOT_COUNT);
            this.onChanged = onChanged;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChanged.run();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == SLOT_INPUT && !stack.isEmpty() && FactoryRecipe.find(stack) != null;
        }
    }

    private final FactoryItemHandler items = new FactoryItemHandler(this::setChanged);
    private final LazyOptional<ItemStackHandler> itemHandler = LazyOptional.of(() -> items);

    private int progress;
    private int maxProgress;
    /** 进度所属配方 id（换配方清零，防蹭进度）；null = 无进行中配方。 */
    private @Nullable String activeRecipeId;

    /** GUI 同步用：0=progress，1=maxProgress。 */
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? progress : maxProgress; }
        @Override public void set(int index, int value) {
            if (index == 0) progress = Math.max(0, value);
            else maxProgress = Math.max(1, value);
        }
        @Override public int getCount() { return 2; }
    };

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY.get(), pos, state);
    }

    // ================================================================
    // 生产 tick（服务端）
    // ================================================================

    public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity factory) {
        if (level.isClientSide) return;
        int cookTime = currentCookTime(factory.items);
        if (cookTime < 0) {
            // 空闲（无有效输入）：进度/配方/maxProgress 复位，只在变化时脏标记，不再每 tick 空转。
            resetIdle(factory);
            return;
        }
        TickResult result = tickProduction(factory.items, factory.progress, factory.activeRecipeId);
        boolean changed = false;
        if (factory.maxProgress != cookTime) {
            factory.maxProgress = cookTime;
            changed = true;
        }
        if (result.progress() != factory.progress) {
            factory.progress = result.progress();
            changed = true;
        }
        if (!java.util.Objects.equals(result.recipeId(), factory.activeRecipeId)) {
            factory.activeRecipeId = result.recipeId();
            changed = true;
        }
        if (changed) factory.setChanged();
    }

    /**
     * 空闲复位：输入被拿走/无配方时进度归 0、配方解绑、maxProgress 复位为 1
     *（P2 ⑤：输入清空后 maxProgress 不再残留旧值）。
     */
    private static void resetIdle(FactoryBlockEntity factory) {
        boolean changed = false;
        if (factory.progress != 0) {
            factory.progress = 0;
            changed = true;
        }
        if (factory.activeRecipeId != null) {
            factory.activeRecipeId = null;
            changed = true;
        }
        if (factory.maxProgress != 1) {
            factory.maxProgress = 1;
            changed = true;
        }
        if (changed) factory.setChanged();
    }

    /** tick 结果：新进度 ＋ 进度所属配方（换配方时进度清零，防蹭进度）。 */
    record TickResult(int progress, @Nullable String recipeId) {}

    /**
     * 纯生产逻辑（可单元测试）：推进一次。
     * 无配方/缺料 → 进度 0；配方切换 → 进度 0；输出满 → 保持进度停机；完成 → 原子合成后归 0。
     */
    static TickResult tickProduction(ItemStackHandler items, int progress, @Nullable String activeRecipeId) {
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        FactoryRecipe recipe = FactoryRecipe.find(input);
        if (recipe == null || !recipe.matches(input)) return new TickResult(0, null);
        if (!recipe.id().equals(activeRecipeId)) {
            // 配方切换：进度清零，从头开始（防"小麦蹭进度→换铁矿"的投机）。
            return new TickResult(0, recipe.id());
        }
        if (!canAcceptOutput(items, recipe)) return new TickResult(progress, recipe.id());
        int next = progress + 1;
        if (next >= recipe.cookTime()) {
            return doCraft(items, recipe)
                    ? new TickResult(0, recipe.id())
                    : new TickResult(progress, recipe.id());
        }
        return new TickResult(next, recipe.id());
    }

    /** 当前输入对应的耗时，无配方返回 -1。 */
    static int currentCookTime(ItemStackHandler items) {
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        FactoryRecipe recipe = FactoryRecipe.find(input);
        if (recipe == null || !recipe.matches(input)) return -1;
        return recipe.cookTime();
    }

    /** 输出槽是否能完全容纳配方结果。 */
    static boolean canAcceptOutput(ItemStackHandler items, FactoryRecipe recipe) {
        ItemStack result = recipe.resultStack();
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) return true;
        return output.is(result.getItem())
                && output.getCount() + result.getCount()
                    <= Math.min(output.getMaxStackSize(), items.getSlotLimit(SLOT_OUTPUT));
    }

    /**
     * 原子合成：输出槽必须仍能完全容纳（防并发），且输入数量足够；
     * 成功时扣减输入、放入输出，返回 true。
     *
     * <p>输出槽写入<b>不</b>走带 isItemValid 校验的 {@code insertItem}（P1-3：
     * 输出槽 isItemValid 恒 false，玩家/料斗不可放入，生产逻辑内部直接合并写入），
     * 而是用 {@code getStackInSlot + setStackInSlot} 直接合并。</p>
     */
    static boolean doCraft(ItemStackHandler items, FactoryRecipe recipe) {
        if (!canAcceptOutput(items, recipe)) return false;
        if (items.getStackInSlot(SLOT_INPUT).getCount() < recipe.inputCount()) return false;
        // 实际扣减的堆叠（含 NBT）——回滚时原样塞回，绝不重建无 NBT 堆叠（P1-4）。
        ItemStack consumed = items.extractItem(SLOT_INPUT, recipe.inputCount(), false);
        if (consumed.getCount() < recipe.inputCount()) {
            // 理论上不可达（已预检数量）：把已扣的部分塞回。
            ItemStack leftover = items.insertItem(SLOT_INPUT, consumed, false);
            if (!leftover.isEmpty()) {
                LOGGER.error("[factory] doCraft 扣减不足回滚失败，丢失 {} x{}",
                        leftover.getItem(), leftover.getCount());
            }
            return false;
        }
        // 输出槽内部直接写入（旁路 isItemValid）。
        ItemStack result = recipe.resultStack();
        ItemStack output = items.getStackInSlot(SLOT_OUTPUT);
        boolean placed;
        if (output.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, result);
            placed = true;
        } else if (canAcceptOutput(items, recipe)) {
            output.grow(result.getCount());
            items.setStackInSlot(SLOT_OUTPUT, output);
            placed = true;
        } else {
            placed = false;
        }
        if (!placed) {
            // 理论上不可达（入口已预检）：把扣掉的输入原样塞回（含 NBT）。
            ItemStack leftover = items.insertItem(SLOT_INPUT, consumed, false);
            if (!leftover.isEmpty()) {
                // 绝不静默丢弃：记 error，便于服主排查。
                LOGGER.error("[factory] doCraft 输出写入失败且回滚塞回失败，丢失 {} x{}（含 NBT）",
                        leftover.getItem(), leftover.getCount());
            }
            return false;
        }
        return true;
    }

    /** 当前状态（供 GUI/指示灯）：0=工作中，1=缺料/无配方，2=输出已满。 */
    public int status() {
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        FactoryRecipe recipe = FactoryRecipe.find(input);
        if (recipe == null || !recipe.matches(input)) return 1;
        return canAcceptOutput(items, recipe) ? 0 : 2;
    }

    /** 破坏掉落用：机内物品快照。 */
    public Container inventorySnapshot() {
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            container.setItem(i, items.getStackInSlot(i).copy());
        }
        return container;
    }

    /**
     * 清空机内物品（{@code FactoryBlock.onRemove} 掉落后调用）：
     * 堵住"破坏时开着 GUI 的玩家在 stillValid 失效前点走槽内真物品"的窄窗口 dup。
     */
    public void clearInventory() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public ItemStackHandler itemHandler() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public int progress() { return progress; }
    public int maxProgress() { return maxProgress; }

    // ================================================================
    // MenuProvider
    // ================================================================

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.finance.factory");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory,
                                            @NotNull Player player) {
        return new FactoryMenu(containerId, playerInventory, this);
    }

    // ================================================================
    // NBT
    // ================================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", items.serializeNBT());
        tag.putInt("Progress", Math.max(0, progress));
        tag.putInt("MaxProgress", Math.max(1, maxProgress));
        if (activeRecipeId != null) tag.putString("ActiveRecipe", activeRecipeId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) items.deserializeNBT(tag.getCompound("Inventory"));
        // 进度钳制：防止非法存档导致 progress > maxProgress；配方 id 非法则丢弃。
        maxProgress = Math.max(1, tag.getInt("MaxProgress"));
        progress = Math.max(0, Math.min(tag.getInt("Progress"), maxProgress));
        String savedRecipe = tag.contains("ActiveRecipe") ? tag.getString("ActiveRecipe") : null;
        activeRecipeId = (savedRecipe != null && !savedRecipe.isBlank()) ? savedRecipe : null;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }
}
