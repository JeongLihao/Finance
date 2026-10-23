package finance.block.entity;

import finance.block.HarvesterBlock;
import finance.company.Company;
import finance.config.FinanceConfig;
import finance.factory.HarvestMode;
import finance.factory.Harvesting;
import finance.factory.MachineCore;
import finance.gui.HarvesterMenu;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 采集机（农场、伐木、采矿、剪毛，见 {@link HarvestMode}）：按节奏在周围干一件活，收获进 9 格仓。
 * 连着公司仓库时每秒把能卖的收获存进公司库存，采矿机还会从库存补煤。漏斗能从仓里取货、往燃料槽送煤。
 */
public final class HarvesterBlockEntity extends CompanyMachineBlockEntity {

    public static final int BIN_SIZE = 9, SLOT_FUEL = BIN_SIZE, SLOT_COUNT = BIN_SIZE + 1;
    /** 状态：在干活、周围没活干、仓满了、缺燃料、矿挖到底了。 */
    public static final int WORKING = 0, IDLE = 1, FULL = 2, NO_FUEL = 3, DONE = 4;
    /** 一个燃料能挖多少块：燃烧时间 ÷ 这个数（一块煤 1600 → 16 块）。 */
    static final int FUEL_PER_BLOCK = 100;

    private final ItemStackHandler slots = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == SLOT_FUEL ? MachineCore.FURNACE_FUEL.applyAsInt(stack) > 0 : true;
        }
    };

    /** 只看收获仓的 9 格（干活时往里放东西用）。 */
    private final IItemHandler bin = new net.minecraftforge.items.wrapper.RangedWrapper(slots, 0, BIN_SIZE);

    /** 给漏斗用：从收获仓取，往燃料槽放；燃料槽里烧剩的空桶也能取走。 */
    private final LazyOptional<IItemHandler> automation = LazyOptional.of(() -> new IItemHandler() {
        public int getSlots() {
            return SLOT_COUNT;
        }

        public @NotNull ItemStack getStackInSlot(int slot) {
            return slots.getStackInSlot(slot);
        }

        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == SLOT_FUEL ? slots.insertItem(slot, stack, simulate) : stack;
        }

        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 燃料槽只能取走烧剩的空桶这类不能烧的东西
            boolean fuel = slot == SLOT_FUEL && MachineCore.FURNACE_FUEL.applyAsInt(slots.getStackInSlot(slot)) > 0;
            return fuel ? ItemStack.EMPTY : slots.extractItem(slot, amount, simulate);
        }

        public int getSlotLimit(int slot) {
            return slots.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == SLOT_FUEL && slots.isItemValid(slot, stack);
        }
    });

    private final int[] shaft = {Integer.MIN_VALUE, 0};
    private int cooldown, fuelBlocks, status = IDLE;
    private long harvested;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> status;
                case 1 -> link;
                case 2 -> fuelBlocks;
                case 3 -> shaft[0] == Integer.MIN_VALUE ? worldPosition.getY() - 1 : shaft[0];
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> status = value;
                case 1 -> link = value;
                case 2 -> fuelBlocks = value;
                case 3 -> shaft[0] = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HARVESTER.get(), pos, state);
    }

    public HarvestMode mode() {
        return getBlockState().getBlock() instanceof HarvesterBlock block ? block.mode() : HarvestMode.FARM;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HarvesterBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20 == 0) machine.logistics(server);
        if (--machine.cooldown > 0) return;
        HarvestMode mode = machine.mode();
        machine.cooldown = Math.max(1, mode.interval() * 100 / Math.max(1, FinanceConfig.factorySpeedPercent()));
        machine.setStatus(machine.work(server, mode));
    }

    private int work(ServerLevel level, HarvestMode mode) {
        if (mode.needsFuel() && fuelBlocks <= 0 && !burnFuel()) return NO_FUEL;
        Harvesting.Outcome outcome =
                switch (mode) {
                    case FARM -> Harvesting.farm(level, worldPosition, bin, (p, s) -> mayBreak(level, p, s));
                    case LUMBER -> Harvesting.lumber(level, worldPosition, bin, (p, s) -> mayBreak(level, p, s));
                    case SHEAR -> Harvesting.shear(level, worldPosition, bin);
                    case MINE -> {
                        if (shaft[0] == Integer.MIN_VALUE) shaft[0] = worldPosition.getY() - 1;
                        yield Harvesting.mine(level, worldPosition, bin, shaft, (p, s) -> mayBreak(level, p, s));
                    }
                };
        if (mode != HarvestMode.MINE
                && Harvesting.collect(level, worldPosition, bin)
                && outcome == Harvesting.Outcome.NOTHING) outcome = Harvesting.Outcome.WORKED;
        return switch (outcome) {
            case WORKED -> {
                harvested++;
                if (mode.needsFuel()) fuelBlocks--;
                yield WORKING;
            }
            case FULL -> FULL;
            case DONE -> DONE;
            case NOTHING -> IDLE;
        };
    }

    /** 烧掉燃料槽里一个燃料，换成能挖的方块数。 */
    private boolean burnFuel() {
        ItemStack fuel = slots.getStackInSlot(SLOT_FUEL);
        int burn = MachineCore.FURNACE_FUEL.applyAsInt(fuel);
        if (burn <= 0) return false;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        ItemStack left = fuel.copy();
        left.shrink(1);
        slots.setStackInSlot(SLOT_FUEL, left.isEmpty() ? remainder : left);
        fuelBlocks = Math.max(1, burn / FUEL_PER_BLOCK);
        return true;
    }

    private void setStatus(int next) {
        if (next != status) {
            status = next;
            setChanged();
        }
    }

    /** 每秒一次：连着公司仓库时把能卖的收获存进公司库存，采矿机补煤。 */
    private void logistics(ServerLevel level) {
        Company company = linkedCompany(level);
        if (company == null) return;
        for (int slot = 0; slot < BIN_SIZE; slot++) store(company, slots, slot);
        if (mode().needsFuel()) refill(company, slots, SLOT_FUEL, Items.COAL);
    }

    public long harvested() {
        return harvested;
    }

    public ItemStackHandler slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    public Container inventorySnapshot() {
        SimpleContainer container = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++)
            container.setItem(i, slots.getStackInSlot(i).copy());
        return container;
    }

    /** 掉落后清空，防止开着界面的玩家在界面失效前再拿走一次。 */
    public void clearInventory() {
        for (int i = 0; i < SLOT_COUNT; i++) slots.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.finance." + mode().blockId());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new HarvesterMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", slots.serializeNBT());
        if (shaft[0] != Integer.MIN_VALUE) tag.putIntArray("Shaft", shaft);
        tag.putInt("FuelBlocks", fuelBlocks);
        tag.putLong("Harvested", harvested);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        slots.deserializeNBT(tag.getCompound("Inventory"));
        int[] saved = tag.getIntArray("Shaft");
        if (saved.length == 2) {
            shaft[0] = saved[0];
            shaft[1] = saved[1];
        }
        fuelBlocks = Math.max(0, tag.getInt("FuelBlocks"));
        harvested = Math.max(0, tag.getLong("Harvested"));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automation.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? automation.cast() : super.getCapability(cap, side);
    }
}
