package finance.gui;

import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.company.CompanyOperatingMode;
import finance.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class CompanyGameplayMenu extends AbstractContainerMenu {
    public static final int MAX_MEMBERS = 64,
            MAX_WAREHOUSES = 8,
            MAX_FACILITIES = 8,
            MAX_CONTRACTS = 16,
            MAX_PROJECTS = 32;

    public record MemberRow(UUID playerId, String role) {}

    public record WarehouseRow(UUID id, long used, long capacity) {}

    public record FacilityRow(UUID id, int level, String status, long lastDay) {}

    public record ContractRow(
            UUID id,
            UUID issuerCompanyId,
            UUID acceptedCompanyId,
            String commodity,
            int quantity,
            int delivered,
            long reward,
            long deadlineDay,
            String status) {}

    public record ProjectRow(
            UUID id,
            String type,
            UUID targetId,
            int targetLevel,
            long budget,
            long funded,
            String fundingSource,
            String status,
            boolean governanceRequired,
            UUID proposalId,
            String failureKey) {}

    // ---- 厂房页（从工厂控制器打开时才有）----
    public static final int MAX_FACTORY_METHODS = 3, MAX_FACTORY_EQUIPMENT = 8, MAX_FACTORY_FLOWS = 6;
    /** 设备行：设备枚举名、已有、需要。 */
    public record EquipRow(String equipment, int have, int need) {}
    /** 投入/产出行：商品 ID、每天数量（等级 1、满员）。 */
    public record FlowRow(String commodity, int amount) {}

    public record MethodRow(
            String tier,
            String key,
            boolean levelOk,
            boolean available,
            int jobsPerDevice,
            List<EquipRow> equipment,
            List<FlowRow> inputs,
            List<FlowRow> outputs) {}
    /**
     * @param activeTier          当前生效档位，空串表示缺少设备或临时作坊
     * @param retoolDaysLeft      改造期剩余天数，0 表示不在改造期
     * @param provisionalDaysLeft 临时作坊剩余天数，-1 表示没有临时作坊
     */
    public record FactoryView(
            UUID facilityId,
            int level,
            String status,
            String activeTier,
            String targetTier,
            int retoolDaysLeft,
            int provisionalDaysLeft,
            long employed,
            long jobs,
            long jobsCap,
            int zoneRadius,
            int zoneBelow,
            int zoneAbove,
            List<MethodRow> methods) {}

    private final UUID companyId;
    private final String name, role, dimension, statusKey, operatingHealth;
    private final long cash, inventoryValue, warehouseUsed, warehouseCapacity, debtPrincipal, dueWithinSevenDays;
    private final double autoSellRatio;
    private final int activeShipments;
    private final boolean inventoryValuationDegraded, risk;
    private final CompanyOperatingMode mode;
    private final BlockPos pos;
    private final List<MemberRow> members;
    private final List<WarehouseRow> warehouses;
    private final List<FacilityRow> facilities;
    private final List<ContractRow> contracts;
    private final List<ProjectRow> projects;
    private final FactoryView factory;

    public CompanyGameplayMenu(int id, Inventory inventory, FriendlyByteBuf b) {
        this(
                id,
                b.readUUID(),
                b.readUtf(64),
                b.readLong(),
                b.readDouble(),
                b.readEnum(CompanyOperatingMode.class),
                b.readUtf(24),
                b.readBoolean(),
                b.readUtf(128),
                b.readBlockPos(),
                readMembers(b),
                readWarehouses(b),
                readFacilities(b),
                readContracts(b),
                readProjects(b),
                b.readUtf(96),
                b.readUtf(32),
                b.readLong(),
                b.readBoolean(),
                b.readLong(),
                b.readLong(),
                b.readVarInt(),
                b.readLong(),
                b.readLong(),
                readFactory(b));
    }

    public CompanyGameplayMenu(
            int id,
            UUID companyId,
            String name,
            long cash,
            double autoSellRatio,
            CompanyOperatingMode mode,
            String role,
            boolean risk,
            String dimension,
            BlockPos pos,
            List<MemberRow> members,
            List<WarehouseRow> warehouses,
            List<FacilityRow> facilities,
            List<ContractRow> contracts,
            List<ProjectRow> projects,
            String statusKey,
            String operatingHealth,
            long inventoryValue,
            boolean inventoryValuationDegraded,
            long warehouseUsed,
            long warehouseCapacity,
            int activeShipments,
            long debtPrincipal,
            long dueWithinSevenDays,
            FactoryView factory) {
        super(ModMenus.COMPANY_GAMEPLAY.get(), id);
        this.companyId = companyId;
        this.name = name;
        this.cash = cash;
        this.autoSellRatio = Double.isFinite(autoSellRatio) ? Math.max(0, Math.min(1, autoSellRatio)) : 0;
        this.mode = mode;
        this.role = role;
        this.risk = risk;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.members = List.copyOf(members);
        this.warehouses = List.copyOf(warehouses);
        this.facilities = List.copyOf(facilities);
        this.contracts = List.copyOf(contracts);
        this.projects = List.copyOf(projects.subList(0, Math.min(MAX_PROJECTS, projects.size())));
        this.statusKey = statusKey == null ? "" : statusKey;
        this.operatingHealth = limit(operatingHealth, 32);
        this.inventoryValue = Math.max(0, inventoryValue);
        this.inventoryValuationDegraded = inventoryValuationDegraded;
        this.warehouseUsed = Math.max(0, warehouseUsed);
        this.warehouseCapacity = Math.max(0, warehouseCapacity);
        this.activeShipments = Math.max(0, activeShipments);
        this.debtPrincipal = Math.max(0, debtPrincipal);
        this.dueWithinSevenDays = Math.max(0, dueWithinSevenDays);
        this.factory = factory;
    }

    public static void write(
            FriendlyByteBuf b,
            UUID companyId,
            String name,
            long cash,
            double autoSellRatio,
            CompanyOperatingMode mode,
            String role,
            boolean risk,
            String dimension,
            BlockPos pos,
            List<MemberRow> members,
            List<WarehouseRow> warehouses,
            List<FacilityRow> facilities,
            List<ContractRow> contracts,
            List<ProjectRow> projects,
            String statusKey,
            String operatingHealth,
            long inventoryValue,
            boolean inventoryValuationDegraded,
            long warehouseUsed,
            long warehouseCapacity,
            int activeShipments,
            long debtPrincipal,
            long dueWithinSevenDays,
            FactoryView factory) {
        b.writeUUID(companyId);
        b.writeUtf(limit(name, 64), 64);
        b.writeLong(Math.max(0, cash));
        b.writeDouble(Double.isFinite(autoSellRatio) ? Math.max(0, Math.min(1, autoSellRatio)) : 0);
        b.writeEnum(mode);
        b.writeUtf(limit(role, 24), 24);
        b.writeBoolean(risk);
        b.writeUtf(limit(dimension, 128), 128);
        b.writeBlockPos(pos);
        b.writeVarInt(Math.min(MAX_MEMBERS, members.size()));
        for (int i = 0; i < Math.min(MAX_MEMBERS, members.size()); i++) {
            MemberRow r = members.get(i);
            b.writeUUID(r.playerId());
            b.writeUtf(limit(r.role(), 24), 24);
        }
        b.writeVarInt(Math.min(MAX_WAREHOUSES, warehouses.size()));
        for (int i = 0; i < Math.min(MAX_WAREHOUSES, warehouses.size()); i++) {
            WarehouseRow r = warehouses.get(i);
            b.writeUUID(r.id());
            b.writeLong(r.used());
            b.writeLong(r.capacity());
        }
        b.writeVarInt(Math.min(MAX_FACILITIES, facilities.size()));
        for (int i = 0; i < Math.min(MAX_FACILITIES, facilities.size()); i++) {
            FacilityRow r = facilities.get(i);
            b.writeUUID(r.id());
            b.writeVarInt(r.level());
            b.writeUtf(limit(r.status(), 24), 24);
            b.writeLong(r.lastDay());
        }
        b.writeVarInt(Math.min(MAX_CONTRACTS, contracts.size()));
        for (int i = 0; i < Math.min(MAX_CONTRACTS, contracts.size()); i++) {
            ContractRow r = contracts.get(i);
            b.writeUUID(r.id());
            b.writeUUID(r.issuerCompanyId());
            b.writeBoolean(r.acceptedCompanyId() != null);
            if (r.acceptedCompanyId() != null) b.writeUUID(r.acceptedCompanyId());
            b.writeUtf(limit(r.commodity(), 64), 64);
            b.writeVarInt(r.quantity());
            b.writeVarInt(r.delivered());
            b.writeLong(r.reward());
            b.writeLong(r.deadlineDay());
            b.writeUtf(limit(r.status(), 24), 24);
        }
        b.writeVarInt(Math.min(MAX_PROJECTS, projects.size()));
        for (int i = 0; i < Math.min(MAX_PROJECTS, projects.size()); i++) {
            ProjectRow r = projects.get(i);
            b.writeUUID(r.id());
            b.writeUtf(limit(r.type(), 24), 24);
            b.writeUUID(r.targetId());
            b.writeVarInt(r.targetLevel());
            b.writeLong(r.budget());
            b.writeLong(r.funded());
            b.writeUtf(limit(r.fundingSource(), 24), 24);
            b.writeUtf(limit(r.status(), 32), 32);
            b.writeBoolean(r.governanceRequired());
            b.writeBoolean(r.proposalId() != null);
            if (r.proposalId() != null) b.writeUUID(r.proposalId());
            b.writeUtf(limit(r.failureKey(), 96), 96);
        }
        b.writeUtf(limit(statusKey, 96), 96);
        b.writeUtf(limit(operatingHealth, 32), 32);
        b.writeLong(Math.max(0, inventoryValue));
        b.writeBoolean(inventoryValuationDegraded);
        b.writeLong(Math.max(0, warehouseUsed));
        b.writeLong(Math.max(0, warehouseCapacity));
        b.writeVarInt(Math.max(0, activeShipments));
        b.writeLong(Math.max(0, debtPrincipal));
        b.writeLong(Math.max(0, dueWithinSevenDays));
        writeFactory(b, factory);
    }

    static void writeFactory(FriendlyByteBuf b, FactoryView f) {
        b.writeBoolean(f != null);
        if (f == null) return;
        b.writeUUID(f.facilityId());
        b.writeVarInt(f.level());
        b.writeUtf(limit(f.status(), 24), 24);
        b.writeUtf(limit(f.activeTier(), 16), 16);
        b.writeUtf(limit(f.targetTier(), 16), 16);
        b.writeVarInt(Math.max(0, f.retoolDaysLeft()));
        b.writeVarInt(Math.max(-1, f.provisionalDaysLeft()) + 1);
        b.writeVarLong(Math.max(0, f.employed()));
        b.writeVarLong(Math.max(0, f.jobs()));
        b.writeVarLong(Math.max(0, f.jobsCap()));
        b.writeVarInt(f.zoneRadius());
        b.writeVarInt(f.zoneBelow());
        b.writeVarInt(f.zoneAbove());
        int methods = Math.min(MAX_FACTORY_METHODS, f.methods().size());
        b.writeVarInt(methods);
        for (int i = 0; i < methods; i++) {
            MethodRow m = f.methods().get(i);
            b.writeUtf(limit(m.tier(), 16), 16);
            b.writeUtf(limit(m.key(), 48), 48);
            b.writeBoolean(m.levelOk());
            b.writeBoolean(m.available());
            b.writeVarInt(Math.max(0, m.jobsPerDevice()));
            int eq = Math.min(MAX_FACTORY_EQUIPMENT, m.equipment().size());
            b.writeVarInt(eq);
            for (int j = 0; j < eq; j++) {
                EquipRow e = m.equipment().get(j);
                b.writeUtf(limit(e.equipment(), 24), 24);
                b.writeVarInt(Math.max(0, e.have()));
                b.writeVarInt(Math.max(0, e.need()));
            }
            writeFlows(b, m.inputs());
            writeFlows(b, m.outputs());
        }
    }

    private static void writeFlows(FriendlyByteBuf b, List<FlowRow> flows) {
        int n = Math.min(MAX_FACTORY_FLOWS, flows.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            b.writeUtf(limit(flows.get(i).commodity(), 64), 64);
            b.writeVarInt(Math.max(0, flows.get(i).amount()));
        }
    }

    static FactoryView readFactory(FriendlyByteBuf b) {
        if (!b.readBoolean()) return null;
        UUID id = b.readUUID();
        int level = b.readVarInt();
        String status = b.readUtf(24);
        String active = b.readUtf(16), target = b.readUtf(16);
        int retool = b.readVarInt(), provisional = b.readVarInt() - 1;
        long employed = b.readVarLong(), jobs = b.readVarLong(), cap = b.readVarLong();
        int radius = b.readVarInt(), below = b.readVarInt(), above = b.readVarInt();
        int methods = count(b, MAX_FACTORY_METHODS);
        List<MethodRow> rows = new ArrayList<>(methods);
        for (int i = 0; i < methods; i++) {
            String tier = b.readUtf(16), key = b.readUtf(48);
            boolean levelOk = b.readBoolean(), available = b.readBoolean();
            int jobsPerDevice = b.readVarInt();
            int eq = count(b, MAX_FACTORY_EQUIPMENT);
            List<EquipRow> equipment = new ArrayList<>(eq);
            for (int j = 0; j < eq; j++) equipment.add(new EquipRow(b.readUtf(24), b.readVarInt(), b.readVarInt()));
            rows.add(new MethodRow(
                    tier, key, levelOk, available, jobsPerDevice, List.copyOf(equipment), readFlows(b), readFlows(b)));
        }
        if (retool > 365 || provisional > 365 || radius > 16 || below > 16 || above > 16)
            throw new IllegalArgumentException("invalid factory view");
        return new FactoryView(
                id,
                level,
                status,
                active,
                target,
                retool,
                provisional,
                employed,
                jobs,
                cap,
                radius,
                below,
                above,
                List.copyOf(rows));
    }

    private static List<FlowRow> readFlows(FriendlyByteBuf b) {
        int n = count(b, MAX_FACTORY_FLOWS);
        List<FlowRow> flows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) flows.add(new FlowRow(b.readUtf(64), b.readVarInt()));
        return List.copyOf(flows);
    }

    private static List<MemberRow> readMembers(FriendlyByteBuf b) {
        int n = count(b, MAX_MEMBERS);
        List<MemberRow> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(new MemberRow(b.readUUID(), b.readUtf(24)));
        return r;
    }

    private static List<WarehouseRow> readWarehouses(FriendlyByteBuf b) {
        int n = count(b, MAX_WAREHOUSES);
        List<WarehouseRow> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(new WarehouseRow(b.readUUID(), b.readLong(), b.readLong()));
        return r;
    }

    private static List<FacilityRow> readFacilities(FriendlyByteBuf b) {
        int n = count(b, MAX_FACILITIES);
        List<FacilityRow> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(new FacilityRow(b.readUUID(), b.readVarInt(), b.readUtf(24), b.readLong()));
        return r;
    }

    private static List<ContractRow> readContracts(FriendlyByteBuf b) {
        int n = count(b, MAX_CONTRACTS);
        List<ContractRow> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            r.add(new ContractRow(
                    b.readUUID(),
                    b.readUUID(),
                    b.readBoolean() ? b.readUUID() : null,
                    b.readUtf(64),
                    b.readVarInt(),
                    b.readVarInt(),
                    b.readLong(),
                    b.readLong(),
                    b.readUtf(24)));
        return r;
    }

    private static List<ProjectRow> readProjects(FriendlyByteBuf b) {
        int n = count(b, MAX_PROJECTS);
        List<ProjectRow> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            r.add(new ProjectRow(
                    b.readUUID(),
                    b.readUtf(24),
                    b.readUUID(),
                    b.readVarInt(),
                    b.readLong(),
                    b.readLong(),
                    b.readUtf(24),
                    b.readUtf(32),
                    b.readBoolean(),
                    b.readBoolean() ? b.readUUID() : null,
                    b.readUtf(96)));
        return r;
    }

    private static int count(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("invalid company gameplay rows");
        return n;
    }

    private static String limit(String value, int max) {
        String s = value == null ? "" : value;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public long cash() {
        return cash;
    }

    public double autoSellRatio() {
        return autoSellRatio;
    }

    public CompanyOperatingMode mode() {
        return mode;
    }

    public String role() {
        return role;
    }

    public boolean risk() {
        return risk;
    }

    public BlockPos pos() {
        return pos;
    }

    public List<MemberRow> members() {
        return members;
    }

    public List<WarehouseRow> warehouses() {
        return warehouses;
    }

    public List<FacilityRow> facilities() {
        return facilities;
    }

    public List<ContractRow> contracts() {
        return contracts;
    }

    public List<ProjectRow> projects() {
        return projects;
    }

    public String statusKey() {
        return statusKey;
    }

    public String operatingHealth() {
        return operatingHealth;
    }

    public long inventoryValue() {
        return inventoryValue;
    }

    public boolean inventoryValuationDegraded() {
        return inventoryValuationDegraded;
    }

    public long warehouseUsed() {
        return warehouseUsed;
    }

    public long warehouseCapacity() {
        return warehouseCapacity;
    }

    public int activeShipments() {
        return activeShipments;
    }

    public long debtPrincipal() {
        return debtPrincipal;
    }

    public long dueWithinSevenDays() {
        return dueWithinSevenDays;
    }
    /** 厂房页数据；不是从工厂控制器打开时为 null。 */
    public FactoryView factory() {
        return factory;
    }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer server)
                || FinanceGameplayOpener.isValidTerminalSession(
                        server, FinanceTerminalType.COMPANY_DESK, dimension, pos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
