package finance.block.entity;

import com.mojang.authlib.GameProfile;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.cycle.EconomyCycleService;
import finance.factory.CompanyStock;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.macro.MacroLaborService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 归属公司的机器（加工机、采集机）共用的部分：
 * 放下时在公司名下登记一条机器记录（{@link CompanyFacilityRecord}），拆掉时注销；
 * 判断离公司仓库够不够近；按档次和工人在岗比例算速度；把状态报给机器记录；
 * 把公司名同步给客户端（界面上显示“属于哪家公司”）；机器物品的快照、清空和给漏斗的视图。
 */
public abstract class CompanyMachineBlockEntity extends BlockEntity implements MenuProvider {

    /** 和仓库的连接：个人机器、公司机器但附近没有公司仓库、连着公司仓库。 */
    public static final int PERSONAL = 0, COMPANY = 1, LINKED = 2;

    private @Nullable UUID companyId, facilityId;
    /** 放下机器的玩家：机器替他改动世界，领地模组按他的身份判断。 */
    private @Nullable UUID placerId;

    private String companyName = "";
    protected int link = PERSONAL;
    /** 速度：10000 = 1 档满员。每秒按档次和在岗比例重算。 */
    private int speedBps = 10_000, credits;
    /** 客户端界面显示的档次（服务端从机器记录读，靠界面数据槽同步过来）。 */
    protected int clientTier = 1;

    private final LazyOptional<IItemHandler> automation = LazyOptional.of(this::automationView);

    protected CompanyMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 这台机器在机器记录里算哪一种。 */
    protected abstract CompanyFacilityType facilityType();

    /** 机器里的全部物品槽。 */
    protected abstract IItemHandlerModifiable inventory();

    /** 给漏斗和管道看的受限视图（见 {@link finance.factory.AutomationView}）。 */
    protected abstract IItemHandler automationView();

    /** 打开机器界面：把方块坐标写进网络包，客户端据此找到方块实体。 */
    public void openMenu(ServerPlayer player) {
        NetworkHooks.openScreen(player, this, buffer -> buffer.writeBlockPos(worldPosition));
    }

    /** 拆掉时掉落用的物品副本。 */
    public Container inventorySnapshot() {
        IItemHandlerModifiable items = inventory();
        SimpleContainer container = new SimpleContainer(items.getSlots());
        for (int i = 0; i < items.getSlots(); i++)
            container.setItem(i, items.getStackInSlot(i).copy());
        return container;
    }

    /** 掉落后清空，防止开着界面的玩家在界面失效前再拿走一次（原版熔炉同款）。 */
    public void clearInventory() {
        IItemHandlerModifiable items = inventory();
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, ItemStack.EMPTY);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /** 放下时绑到公司并登记；公司机器数到上限时登记不上，机器就算个人的。返回最终归属的公司。 */
    public @Nullable Company bindCompany(@Nullable Company company) {
        if (company != null && level != null) {
            CompanyFacilityRecord record = new CompanyFacilityRecord(
                    UUID.randomUUID(),
                    company.getCompanyId(),
                    level.dimension().location().toString(),
                    worldPosition,
                    facilityType(),
                    1,
                    CompanyFacilityStatus.ACTIVE,
                    -1);
            if (CompanyFacilityManager.register(record)) facilityId = record.facilityId();
            else company = null;
        }
        companyId = company == null ? null : company.getCompanyId();
        companyName = company == null ? "" : company.getName();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return company;
    }

    /** 方块被拆掉时注销机器记录。 */
    public void unregister() {
        CompanyFacilityManager.remove(facilityId);
        facilityId = null;
    }

    public @Nullable CompanyFacilityRecord facility() {
        return facilityId == null ? null : CompanyFacilityManager.get(facilityId);
    }

    /** 档次 1～3；个人机器是 1 档。客户端读界面同步过来的值。 */
    public int tier() {
        if (level != null && level.isClientSide) return clientTier;
        CompanyFacilityRecord record = facility();
        return record == null ? 1 : record.productionLevel();
    }

    /**
     * 每秒一次：重算速度，判断和公司仓库的连接。连着就返回公司，否则返回 null。
     * 速度 = 档次倍率 ×（50% + 50% × 在岗比例）；附近没有村庄算满员，雇不到人就是半速。
     */
    protected @Nullable Company refresh(ServerLevel level) {
        CompanyFacilityRecord record = facility();
        int staffing = record == null ? 10_000 : MacroLaborService.staffingBps(record);
        speedBps = speedBps(FinanceConfig.factoryThroughput(tier()), staffing);
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

    /** 速度（10000 = 1 档满员）= 档次倍率 ×（50% + 50% × 在岗比例）。 */
    public static int speedBps(int throughput, int staffingBps) {
        int staffing = Math.max(0, Math.min(10_000, staffingBps));
        return Math.max(1, throughput) * (5_000 + staffing / 2);
    }

    /** 这一 tick 机器该推进几步（按速度累计，1 档满员每 tick 一步）。 */
    protected int steps() {
        credits += speedBps;
        int steps = credits / 10_000;
        credits %= 10_000;
        return steps;
    }

    /** 把机器状态报给机器记录（雇工、停业险、管理台读这里）。 */
    protected void report(ServerLevel level, CompanyFacilityStatus status) {
        CompanyFacilityRecord record = facility();
        if (record == null) return;
        long day = EconomyCycleService.currentMcDay(level.getServer());
        record.setStatus(status, day);
        record.setLastProcessedDay(day);
    }

    /** 这台机器有没有报过“生产启动”进度（不存档，重进世界后再报一次也无妨）。 */
    private boolean productionAnnounced;

    /**
     * 把这一槽存进公司库存，返回存进去的数量。第一次存进去时给放置者和公司老板（在线的话）
     * 触发“生产启动”进度。
     */
    protected int deposit(ServerLevel level, Company company, IItemHandlerModifiable items, int slot) {
        int stored = store(company, items, slot);
        if (stored > 0 && !productionAnnounced) {
            productionAnnounced = true;
            var players = level.getServer().getPlayerList();
            for (UUID id : new java.util.LinkedHashSet<>(java.util.Arrays.asList(placerId, company.getOwnerId()))) {
                var player = id == null ? null : players.getPlayer(id);
                if (player != null)
                    finance.advancement.FinanceAdvancementTriggers.trigger(player, "company_production");
            }
        }
        return stored;
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

    /** 放下时记下是谁放的。 */
    public void setPlacer(@Nullable LivingEntity placer) {
        placerId = placer instanceof Player player ? player.getUUID() : null;
        setChanged();
    }

    /**
     * 能不能替放置者破坏这个方块：发一次 BlockEvent.BreakEvent，领地模组取消了就不能。
     * 用带放置者 UUID 的假玩家，放置者在自己的领地里照常干活；不知道放置者（旧机器）时用 Forge 默认假玩家。
     */
    protected boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state) {
        Player actor = placerId == null
                ? FakePlayerFactory.getMinecraft(level)
                : FakePlayerFactory.get(level, new GameProfile(placerId, "[Finance]"));
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, actor));
    }

    public @Nullable UUID companyId() {
        return companyId;
    }

    public @Nullable UUID facilityId() {
        return facilityId;
    }

    public String companyName() {
        return companyName;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeOwner(tag);
        if (facilityId != null) tag.putUUID("Facility", facilityId);
        if (placerId != null) tag.putUUID("Placer", placerId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readOwner(tag);
        facilityId = tag.hasUUID("Facility") ? tag.getUUID("Facility") : null;
        placerId = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
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

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automation.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? automation.cast() : super.getCapability(cap, side);
    }

    /** 客户端只更新归属，机器里的物品靠界面同步。 */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) readOwner(packet.getTag());
    }
}
