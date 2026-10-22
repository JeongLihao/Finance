package finance.gui;

import finance.block.entity.FactoryBlockEntity;
import finance.factory.FactoryRecipe;
import finance.factory.MachineCore;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import finance.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

/** 加工机界面：原料、第二原料、燃料、成品四个槽 + 玩家背包；按钮选“要做什么”。 */
public final class FactoryMenu extends AbstractContainerMenu {
    public static final int INVENTORY_Y = 98;
    /** 选配方按钮的 id：0..配方数-1 选对应配方，NO_RECIPE 取消选择。 */
    public static final int NO_RECIPE = 99;

    private final FactoryBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public FactoryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, resolveOrEmpty(playerInventory, buffer.readBlockPos()));
    }

    public FactoryMenu(int containerId, Inventory playerInventory, FactoryBlockEntity blockEntity) {
        super(ModMenus.FACTORY.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.data = blockEntity.data();
        var items = blockEntity.itemHandler();
        addSlot(new SlotItemHandler(items, MachineCore.SLOT_INPUT, 26, 32));
        addSlot(new SlotItemHandler(items, MachineCore.SLOT_SECOND, 44, 32));
        addSlot(new SlotItemHandler(items, MachineCore.SLOT_FUEL, 35, 56));
        addSlot(new SlotItemHandler(items, MachineCore.SLOT_OUTPUT, 104, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
        addDataSlots(data);
    }

    /** 开包瞬间方块已被破坏时，用空的占位机器撑起界面，避免客户端崩溃。 */
    private static FactoryBlockEntity resolveOrEmpty(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof FactoryBlockEntity factory) return factory;
        return new FactoryBlockEntity(pos, playerInventory.player.level().getBlockState(pos));
    }

    public FactoryBlockEntity blockEntity() {
        return blockEntity;
    }

    public float progressFraction() {
        return Math.min(1f, data.get(0) / (float) Math.max(1, data.get(1)));
    }

    public float fuelFraction() {
        return Math.min(1f, data.get(2) / (float) Math.max(1, data.get(3)));
    }

    public int status() {
        return data.get(4);
    }

    public int link() {
        return data.get(5);
    }

    /** 选定的配方序号，-1 = 没选（按机器里现有原料做）。 */
    public int selectedRecipe() {
        return data.get(6);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        var companyId = blockEntity.companyId();
        if (companyId != null
                && !CompanyMembershipService.hasPermission(
                        companyId, player.getUUID(), CompanyPermission.MANAGE_PRODUCTION)) return false;
        if (id != NO_RECIPE && (id < 0 || id >= FactoryRecipe.ALL.size())) return false;
        blockEntity.selectRecipe(id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, finance.registry.ModBlocks.FACTORY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int machineSlots = MachineCore.SLOT_COUNT, end = machineSlots + 36;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, moved);
        } else if (!moveItemStackTo(stack, 0, MachineCore.SLOT_OUTPUT, false)) {
            // 机器不收：在背包和快捷栏之间挪
            int hotbar = machineSlots + 27;
            if (index < hotbar
                    ? !moveItemStackTo(stack, hotbar, end, false)
                    : !moveItemStackTo(stack, machineSlots, hotbar, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == moved.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return moved;
    }
}
