package finance.block.entity;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.factory.CompanyStock;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 归属公司的机器（加工机、采集机）共用的部分：归属哪家公司、离公司仓库够不够近，
 * 以及把公司名同步给客户端（界面上显示“属于哪家公司”）。
 */
public abstract class CompanyMachineBlockEntity extends BlockEntity implements MenuProvider {

    /** 和仓库的连接：个人机器、公司机器但附近没有公司仓库、连着公司仓库。 */
    public static final int PERSONAL = 0, COMPANY = 1, LINKED = 2;

    private @Nullable UUID companyId;
    private String companyName = "";
    protected int link = PERSONAL;

    protected CompanyMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 重新判断和公司仓库的连接；连着就返回公司，否则返回 null。每秒调一次。 */
    protected @Nullable Company linkedCompany(ServerLevel level) {
        Company company = companyId == null ? null : CompanyManager.getCompany(companyId);
        int next = company == null
                ? PERSONAL
                : CompanyStock.nearWarehouse(company, level, worldPosition) ? LINKED : COMPANY;
        if (next != link) {
            link = next;
            setChanged();
        }
        return link == LINKED ? company : null;
    }

    /** 把这一槽的东西存进公司库存（只收商品），返回存进去的数量。 */
    protected static int store(Company company, IItemHandlerModifiable items, int slot) {
        int stored = CompanyStock.deposit(company, items.getStackInSlot(slot));
        if (stored > 0) items.setStackInSlot(slot, ItemStack.EMPTY);
        return stored;
    }

    /** 把这一槽从公司库存补到一整组；槽里放着别的东西就不动。 */
    protected static void refill(Company company, IItemHandlerModifiable items, int slot, Item item) {
        ItemStack current = items.getStackInSlot(slot);
        if (!current.isEmpty() && !current.is(item)) return;
        int room = item.getMaxStackSize() - current.getCount();
        if (room <= 0) return;
        ItemStack taken = CompanyStock.withdraw(company, item, room);
        if (!taken.isEmpty()) items.setStackInSlot(slot, new ItemStack(item, current.getCount() + taken.getCount()));
    }

    /** 放下时绑到公司。 */
    public void bindCompany(@Nullable Company company) {
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeOwner(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readOwner(tag);
    }

    private void writeOwner(CompoundTag tag) {
        if (companyId != null) tag.putUUID("Company", companyId);
        tag.putString("CompanyName", companyName);
    }

    private void readOwner(CompoundTag tag) {
        companyId = tag.hasUUID("Company") ? tag.getUUID("Company") : null;
        companyName = tag.getString("CompanyName");
    }

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

    /** 客户端只更新归属，机器里的物品靠界面同步。 */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) readOwner(packet.getTag());
    }
}
