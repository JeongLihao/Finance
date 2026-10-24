package finance.block.entity;

import finance.company.Company;
import finance.factory.AutomationView;
import finance.factory.FactoryRecipe;
import finance.factory.MachineCore;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.gui.FactoryMenu;
import finance.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 加工机：生产逻辑在 {@link MachineCore}，这里管界面、存档和公司仓库存取。
 * 连着公司仓库时，每秒把成品存进公司库存，并按选定的配方从库存补原料和煤。
 * 漏斗能往原料槽和燃料槽送料，能从成品槽取货，也能取走燃料槽里烧剩的空桶。
 * 归属、登记、速度、掉落和漏斗接口在 {@link CompanyMachineBlockEntity}。
 */
public final class FactoryBlockEntity extends CompanyMachineBlockEntity {

    private final MachineCore core = new MachineCore(this::setChanged, MachineCore.FURNACE_FUEL);

    private @Nullable String selectedRecipe;
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
        if (everySecond(level, pos)) factory.logistics(server);
        for (int i = factory.steps(); i > 0; i--) if (factory.core.tick()) factory.setChanged();
    }

    @Override
    protected CompanyFacilityType facilityType() {
        return CompanyFacilityType.PROCESSOR;
    }

    @Override
    protected IItemHandlerModifiable inventory() {
        return core.items();
    }

    /** 漏斗只能往原料、燃料槽放（槽位规则见 MachineCore.Items）；能取成品，燃料槽只能取走烧剩的空桶。 */
    @Override
    protected IItemHandler automationView() {
        return new AutomationView(
                core.items(),
                slot -> true,
                (slot, stack) ->
                        slot == MachineCore.SLOT_OUTPUT || slot == MachineCore.SLOT_FUEL && !MachineCore.isFuel(stack));
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
        produced += deposit(level, company, items, MachineCore.SLOT_OUTPUT);
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

    public long produced() {
        return produced;
    }

    public MachineCore.Items itemHandler() {
        return core.items();
    }

    public ContainerData data() {
        return data;
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
}
