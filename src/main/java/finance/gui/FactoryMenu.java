package finance.gui;

import finance.block.entity.FactoryBlockEntity;
import finance.factory.FactoryRecipe;
import finance.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 工厂方块 Menu —— 仿熔炉：1 输入槽 ＋ 1 输出槽（只取不放）＋ 玩家背包。
 */
public final class FactoryMenu extends AbstractContainerMenu {
    private final FactoryBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    /** 客户端构造：从网络包读 BlockPos；BE 缺失时降级为空容器（不抛异常，避免客户端崩溃）。 */
    public FactoryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, resolveOrEmpty(playerInventory, buffer.readBlockPos()));
    }

    /** 服务端构造。 */
    public FactoryMenu(int containerId, Inventory playerInventory, FactoryBlockEntity blockEntity) {
        super(ModMenus.FACTORY.get(), containerId);
        if (blockEntity == null) throw new IllegalArgumentException("Factory block entity missing");
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(
                blockEntity.getLevel(), blockEntity.getBlockPos());

        // 0=输入槽：只接受有配方的物品；1=输出槽：只取不放。
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler(), FactoryBlockEntity.SLOT_INPUT, 56, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FactoryRecipe.find(stack) != null;
            }
        });
        this.addSlot(new SlotItemHandler(blockEntity.itemHandler(), FactoryBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // 玩家背包 27 格。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏 9 格。
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(blockEntity.data());
    }

    /**
     * 按 BlockPos 找 BE；找不到时（极端情况：开包瞬间方块已被破坏）用空 handler 的
     * 占位 BE 撑起空菜单，而不是抛 IllegalArgumentException 让客户端崩溃。
     */
    private static FactoryBlockEntity resolveOrEmpty(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof FactoryBlockEntity factory) return factory;
        return new FactoryBlockEntity(pos, playerInventory.player.level().getBlockState(pos));
    }

    public FactoryBlockEntity blockEntity() {
        return blockEntity;
    }

    /** 进度 0~1（GUI 箭头用）。 */
    public float progressFraction() {
        int max = blockEntity.maxProgress();
        if (max <= 0) return 0f;
        return Math.min(1f, blockEntity.progress() / (float) max);
    }

    /** 状态：0=工作中，1=缺料/无配方，2=输出已满。 */
    public int status() {
        return blockEntity.status();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, finance.registry.ModBlocks.FACTORY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return moved;
        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index == FactoryBlockEntity.SLOT_OUTPUT) {
            // 输出槽 → 玩家背包。
            if (!this.moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, moved);
        } else if (index == FactoryBlockEntity.SLOT_INPUT) {
            // 输入槽 → 玩家背包。
            if (!this.moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
        } else {
            // 玩家背包 → 有配方则进输入槽，否则在背包/快捷栏之间移动。
            if (FactoryRecipe.find(stack) != null) {
                if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else if (index < 29) {
                if (!this.moveItemStackTo(stack, 29, 38, false)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 2, 29, false)) return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == moved.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return moved;
    }
}
