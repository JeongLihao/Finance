package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.factory.CompanyStock;
import finance.factory.FactoryRecipe;
import finance.factory.MachineCore;
import finance.gui.FactoryMenu;
import finance.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 加工机：生产逻辑在 {@link MachineCore}，这里管归属、存档、界面和公司仓库存取。
 * 归属公司、且 16 格内有公司仓库时，每秒把成品存进公司库存，并按选定的配方从库存补原料和煤。
 * 漏斗能往原料槽和燃料槽送料，只能从成品槽取货。
 */
public final class FactoryBlockEntity extends BlockEntity implements MenuProvider {

    /** 和仓库的连接：个人机器、公司机器但附近没有公司仓库、连着公司仓库。 */
    public static final int PERSONAL = 0, COMPANY = 1, LINKED = 2;

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

    private @Nullable UUID companyId;
    private String companyName = "";
    private @Nullable String selectedRecipe;
    private int link = PERSONAL;
    private long produced;

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
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 7;
        }
    };

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY.get(), pos, state);
    }

    // ---------------------------------------------------------------- 生产

    public static void serverTick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity factory) {
        if (level.getGameTime() % 20 == 0 && level instanceof ServerLevel server) factory.logistics(server);
        if (factory.core.tick()) factory.setChanged();
    }

    /** 每秒一次：连着公司仓库时存成品、补原料和燃料。 */
    void logistics(ServerLevel level) {
        Company company = companyId == null ? null : CompanyManager.getCompany(companyId);
        int next = company == null
                ? PERSONAL
                : CompanyStock.nearWarehouse(company, level, worldPosition) ? LINKED : COMPANY;
        if (next != link) {
            link = next;
            setChanged();
        }
        if (link != LINKED) return;
        var items = core.items();
        int stored = CompanyStock.deposit(company, items.getStackInSlot(MachineCore.SLOT_OUTPUT));
        if (stored > 0) {
            items.setStackInSlot(MachineCore.SLOT_OUTPUT, ItemStack.EMPTY);
            produced += stored;
        }
        FactoryRecipe recipe = selectedRecipe != null ? FactoryRecipe.byId(selectedRecipe) : null;
        if (recipe == null && core.activeRecipe() != null) recipe = FactoryRecipe.byId(core.activeRecipe());
        if (recipe == null) return;
        refill(company, MachineCore.SLOT_INPUT, recipe.input());
        if (recipe.second() != null) refill(company, MachineCore.SLOT_SECOND, recipe.second());
        if (recipe.needsFuel() && items.getStackInSlot(MachineCore.SLOT_FUEL).isEmpty())
            refill(company, MachineCore.SLOT_FUEL, net.minecraft.world.item.Items.COAL);
    }

    /** 把这一槽从公司库存补到一整组；槽里放着别的东西就不动。 */
    private void refill(Company company, int slot, net.minecraft.world.item.Item item) {
        var items = core.items();
        ItemStack current = items.getStackInSlot(slot);
        if (!current.isEmpty() && !current.is(item)) return;
        int room = item.getMaxStackSize() - current.getCount();
        if (room <= 0) return;
        ItemStack taken = CompanyStock.withdraw(company, item, room);
        if (taken.isEmpty()) return;
        items.setStackInSlot(slot, new ItemStack(item, current.getCount() + taken.getCount()));
    }

    // ---------------------------------------------------------------- 归属和选配方

    /** 放下时绑到公司。 */
    public void bindCompany(Company company) {
        companyId = company == null ? null : company.getCompanyId();
        companyName = company == null ? "" : company.getName();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public @Nullable UUID companyId() {
        return companyId;
    }

    public String companyName() {
        return companyName;
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

    public long produced() {
        return produced;
    }

    // ---------------------------------------------------------------- 掉落、界面、存档

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
        writeOwner(tag);
        if (selectedRecipe != null) tag.putString("Selected", selectedRecipe);
        tag.putLong("Produced", produced);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        core.load(tag);
        readOwner(tag);
        selectedRecipe = tag.contains("Selected") ? tag.getString("Selected") : null;
        produced = Math.max(0, tag.getLong("Produced"));
    }

    private void writeOwner(CompoundTag tag) {
        if (companyId != null) tag.putUUID("Company", companyId);
        tag.putString("CompanyName", companyName);
    }

    private void readOwner(CompoundTag tag) {
        companyId = tag.hasUUID("Company") ? tag.getUUID("Company") : null;
        companyName = tag.getString("CompanyName");
    }

    /** 归属同步给客户端，界面上显示“属于哪家公司”。 */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeOwner(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readOwner(tag);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) readOwner(packet.getTag());
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
