package finance.block.entity;

import finance.company.Company;
import finance.factory.FactoryRecipe;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.factory.MachineCore;
import finance.gui.FactoryMenu;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 加工机：生产逻辑在 {@link MachineCore}，这里管界面、存档和公司仓库存取。
 * 连着公司仓库时，每秒把成品存进公司库存，并按选定的配方从库存补原料和煤。
 * 漏斗能往原料槽和燃料槽送料，只能从成品槽取货。
 */
public final class FactoryBlockEntity extends CompanyMachineBlockEntity {

    /** 给漏斗和管道用：只能往原料、燃料槽放，只能从成品槽取。 */
    private record AutomationHandler(MachineCore.Items items) implements IItemHandler {
        public int getSlots() {
            return MachineCore.SLOT_COUNT;
        }

        public @NotNull ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return items.insertItem(slot, stack, simulate);
        }

        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == MachineCore.SLOT_OUTPUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    }

    private final MachineCore core = new MachineCore(this::setChanged, MachineCore.FURNACE_FUEL);
    private final LazyOptional<IItemHandler> automation = LazyOptional.of(() -> new AutomationHandler(core.items()));

    private @Nullable String selectedRecipe;
    private long produced;
    /** 客户端界面显示的档次（服务端从机器记录读）。 */
    private int clientTier = 1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> core.progress();
                case 1 -> core.maxProgress();
                case 2 -> core.burnTime();
                case 3 -> core.burnDuration();
                case 4 -> core.status();
                case 5 -> link;
                case 6 -> selectedIndex();
                case 7 -> tier();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0, 1, 2, 3, 4 -> core.setSynced(index, value);
                case 5 -> link = value;
                case 6 -> selectedRecipe = value >= 0 && value < FactoryRecipe.ALL.size()
                        ? FactoryRecipe.ALL.get(value).id()
                        : null;
                case 7 -> clientTier = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity factory) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20 == 0) factory.logistics(server);
        for (int i = factory.steps(); i > 0; i--) if (factory.core.tick()) factory.setChanged();
    }

    @Override
    protected CompanyFacilityType facilityType() {
        return CompanyFacilityType.PROCESSOR;
    }

    /** 每秒一次：报状态；连着公司仓库时存成品、补原料和燃料。 */
    private void logistics(ServerLevel level) {
        Company company = refresh(level);
        report(
                level,
                switch (core.status()) {
                    case MachineCore.WORKING -> CompanyFacilityStatus.ACTIVE;
                    case MachineCore.OUTPUT_FULL -> CompanyFacilityStatus.OUTPUT_FULL;
                    default -> CompanyFacilityStatus.MISSING_INPUT;
                });
        if (company == null) return;
        var items = core.items();
        produced += store(company, items, MachineCore.SLOT_OUTPUT);
        FactoryRecipe recipe = selectedRecipe != null ? FactoryRecipe.byId(selectedRecipe) : null;
        if (recipe == null && core.activeRecipe() != null) recipe = FactoryRecipe.byId(core.activeRecipe());
        if (recipe == null) return;
        refill(company, items, MachineCore.SLOT_INPUT, recipe.input());
        if (recipe.second() != null) refill(company, items, MachineCore.SLOT_SECOND, recipe.second());
        if (recipe.needsFuel()) refill(company, items, MachineCore.SLOT_FUEL, Items.COAL);
    }

    /** 选“要做什么”：决定从公司仓库取哪种原料。index 越界表示不指定（按机器里现有原料做）。 */
    public void selectRecipe(int index) {
        selectedRecipe = index >= 0 && index < FactoryRecipe.ALL.size()
                ? FactoryRecipe.ALL.get(index).id()
                : null;
        setChanged();
    }

    private int selectedIndex() {
        for (int i = 0; i < FactoryRecipe.ALL.size(); i++)
            if (FactoryRecipe.ALL.get(i).id().equals(selectedRecipe)) return i;
        return -1;
    }

    public int status() {
        return core.status();
    }

    @Override
    public int tier() {
        return level != null && level.isClientSide ? clientTier : super.tier();
    }

    public long produced() {
        return produced;
    }

    public Container inventorySnapshot() {
        SimpleContainer container = new SimpleContainer(MachineCore.SLOT_COUNT);
        for (int i = 0; i < MachineCore.SLOT_COUNT; i++)
            container.setItem(i, core.items().getStackInSlot(i).copy());
        return container;
    }

    /** 掉落后清空，防止开着界面的玩家在界面失效前再拿走一次。 */
    public void clearInventory() {
        for (int i = 0; i < MachineCore.SLOT_COUNT; i++) core.items().setStackInSlot(i, ItemStack.EMPTY);
    }

    public MachineCore.Items itemHandler() {
        return core.items();
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.finance.factory");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new FactoryMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        core.save(tag);
        if (selectedRecipe != null) tag.putString("Selected", selectedRecipe);
        tag.putLong("Produced", produced);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        core.load(tag);
        selectedRecipe = tag.contains("Selected") ? tag.getString("Selected") : null;
        produced = Math.max(0, tag.getLong("Produced"));
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
