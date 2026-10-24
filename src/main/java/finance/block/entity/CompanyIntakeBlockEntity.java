package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.factory.CompanyStock;
import finance.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 公司收货箱：放进来（或用漏斗送进来）的商品物品每秒存进公司库存，算公司的产量，之后每天自动卖。
 * 农场、树场、矿道的产出靠它进公司。不是商品的东西留在箱子里；没有归属公司时就是个普通箱子。
 */
public final class CompanyIntakeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SIZE = 9;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            CompanyIntakeBlockEntity.this.setChanged();
        }
    };
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(items));

    private @Nullable UUID companyId;
    private long received;

    public CompanyIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPANY_INTAKE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CompanyIntakeBlockEntity box) {
        if (CompanyMachineBlockEntity.everySecond(level, pos)) box.collect();
    }

    /** 把箱子里的商品存进公司库存；存不下（仓库满了）的留在箱子里。 */
    void collect() {
        Company company = companyId == null ? null : CompanyManager.getCompany(companyId);
        if (company == null) return;
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = items.getItem(i);
            int stored = CompanyStock.deposit(company, stack);
            if (stored > 0) {
                items.setItem(i, ItemStack.EMPTY);
                received += stored;
            }
        }
    }

    public void bindCompany(Company company) {
        companyId = company == null ? null : company.getCompanyId();
        setChanged();
    }

    public @Nullable UUID companyId() {
        return companyId;
    }

    public long received() {
        return received;
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.finance.company_intake");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new DispenserMenu(id, inventory, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.createTag());
        if (companyId != null) tag.putUUID("Company", companyId);
        tag.putLong("Received", received);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clearContent();
        items.fromTag(tag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND));
        companyId = tag.hasUUID("Company") ? tag.getUUID("Company") : null;
        received = Math.max(0, tag.getLong("Received"));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : super.getCapability(cap, side);
    }
}
