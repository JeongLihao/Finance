package finance.gui;

import finance.block.entity.HarvesterBlockEntity;
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

/** 采集机界面：3×3 收获仓（只能取）+ 燃料槽 + 玩家背包。 */
public final class HarvesterMenu extends AbstractContainerMenu implements MachineMenu {
    public static final int INVENTORY_Y = 122, BIN_X = 62, BIN_Y = 38;

    private final HarvesterBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    public HarvesterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, resolveOrEmpty(playerInventory, buffer.readBlockPos()));
    }

    public HarvesterMenu(int containerId, Inventory playerInventory, HarvesterBlockEntity blockEntity) {
        super(ModMenus.HARVESTER.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.data = blockEntity.data();
        var slots = blockEntity.slots();
        for (int i = 0; i < HarvesterBlockEntity.BIN_SIZE; i++)
            addSlot(new SlotItemHandler(slots, i, BIN_X + (i % 3) * 18, BIN_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        addSlot(new SlotItemHandler(slots, HarvesterBlockEntity.SLOT_FUEL, 26, BIN_Y + 18));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58));
        addDataSlots(data);
    }

    /** 开包瞬间方块已被破坏时，用空的占位机器撑起界面，避免客户端崩溃。 */
    private static HarvesterBlockEntity resolveOrEmpty(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof HarvesterBlockEntity machine) return machine;
        return new HarvesterBlockEntity(pos, playerInventory.player.level().getBlockState(pos));
    }

    @Override
    public HarvesterBlockEntity blockEntity() {
        return blockEntity;
    }

    public int status() {
        return data.get(0);
    }

    @Override
    public int link() {
        return data.get(1);
    }

    public int fuelBlocks() {
        return data.get(2);
    }

    public int shaftY() {
        return data.get(3);
    }

    /** 档次 1～3。 */
    @Override
    public int tier() {
        return Math.max(1, data.get(4));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == MachineUpgrade.BUTTON_ID && MachineUpgrade.click(player, blockEntity);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, blockEntity.getBlockState().getBlock());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int machineSlots = HarvesterBlockEntity.SLOT_COUNT, end = machineSlots + 36;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, HarvesterBlockEntity.SLOT_FUEL, HarvesterBlockEntity.SLOT_FUEL + 1, false)) {
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
