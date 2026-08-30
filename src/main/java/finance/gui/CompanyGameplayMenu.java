package finance.gui;

import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceTerminalType;
import finance.gameplay.company.CompanyOperatingMode;
import finance.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompanyGameplayMenu extends AbstractContainerMenu {
    public static final int MAX_MEMBERS = 64, MAX_WAREHOUSES = 8, MAX_FACILITIES = 8,
            MAX_CONTRACTS = 16, MAX_PROJECTS = 32;
    public record MemberRow(UUID playerId, String role) {}
    public record WarehouseRow(UUID id, long used, long capacity) {}
    public record FacilityRow(UUID id, int level, String status, long lastDay) {}
    public record ContractRow(UUID id, String commodity, int quantity, long reward, String status) {}
    public record ProjectRow(UUID id, String type, UUID targetId, int targetLevel, long budget, long funded,
                             String fundingSource, String status, boolean governanceRequired, UUID proposalId,
                             String failureKey) {}

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

    public CompanyGameplayMenu(int id, Inventory inventory, FriendlyByteBuf b) {
        this(id, b.readUUID(), b.readUtf(64), b.readLong(), b.readDouble(), b.readEnum(CompanyOperatingMode.class),
                b.readUtf(24), b.readBoolean(), b.readUtf(128), b.readBlockPos(), readMembers(b), readWarehouses(b),
                readFacilities(b), readContracts(b), readProjects(b), b.readUtf(96), b.readUtf(32), b.readLong(),
                b.readBoolean(), b.readLong(), b.readLong(), b.readVarInt(), b.readLong(), b.readLong());
    }

    public CompanyGameplayMenu(int id, UUID companyId, String name, long cash, double autoSellRatio,
                               CompanyOperatingMode mode, String role, boolean risk, String dimension, BlockPos pos,
                               List<MemberRow> members, List<WarehouseRow> warehouses,
                               List<FacilityRow> facilities, List<ContractRow> contracts,
                               List<ProjectRow> projects, String statusKey, String operatingHealth,
                               long inventoryValue, boolean inventoryValuationDegraded, long warehouseUsed,
                               long warehouseCapacity, int activeShipments, long debtPrincipal,
                               long dueWithinSevenDays) {
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
    }

    public static void write(FriendlyByteBuf b, UUID companyId, String name, long cash, double autoSellRatio,
                             CompanyOperatingMode mode, String role, boolean risk, String dimension, BlockPos pos,
                             List<MemberRow> members, List<WarehouseRow> warehouses,
                             List<FacilityRow> facilities, List<ContractRow> contracts, List<ProjectRow> projects,
                             String statusKey, String operatingHealth, long inventoryValue,
                             boolean inventoryValuationDegraded, long warehouseUsed, long warehouseCapacity,
                             int activeShipments, long debtPrincipal, long dueWithinSevenDays) {
        b.writeUUID(companyId); b.writeUtf(limit(name, 64), 64); b.writeLong(Math.max(0, cash));
        b.writeDouble(Double.isFinite(autoSellRatio) ? Math.max(0, Math.min(1, autoSellRatio)) : 0);
        b.writeEnum(mode); b.writeUtf(limit(role, 24), 24); b.writeBoolean(risk);
        b.writeUtf(limit(dimension, 128), 128); b.writeBlockPos(pos);
        b.writeVarInt(Math.min(MAX_MEMBERS, members.size()));
        for (int i = 0; i < Math.min(MAX_MEMBERS, members.size()); i++) {
            MemberRow r = members.get(i); b.writeUUID(r.playerId()); b.writeUtf(limit(r.role(), 24), 24);
        }
        b.writeVarInt(Math.min(MAX_WAREHOUSES, warehouses.size()));
        for (int i = 0; i < Math.min(MAX_WAREHOUSES, warehouses.size()); i++) {
            WarehouseRow r = warehouses.get(i); b.writeUUID(r.id()); b.writeLong(r.used()); b.writeLong(r.capacity());
        }
        b.writeVarInt(Math.min(MAX_FACILITIES, facilities.size()));
        for (int i = 0; i < Math.min(MAX_FACILITIES, facilities.size()); i++) {
            FacilityRow r = facilities.get(i); b.writeUUID(r.id()); b.writeVarInt(r.level());
            b.writeUtf(limit(r.status(), 24), 24); b.writeLong(r.lastDay());
        }
        b.writeVarInt(Math.min(MAX_CONTRACTS, contracts.size()));
        for (int i = 0; i < Math.min(MAX_CONTRACTS, contracts.size()); i++) {
            ContractRow r = contracts.get(i); b.writeUUID(r.id()); b.writeUtf(limit(r.commodity(), 64), 64);
            b.writeVarInt(r.quantity()); b.writeLong(r.reward()); b.writeUtf(limit(r.status(), 24), 24);
        }
        b.writeVarInt(Math.min(MAX_PROJECTS, projects.size()));
        for (int i = 0; i < Math.min(MAX_PROJECTS, projects.size()); i++) {
            ProjectRow r = projects.get(i); b.writeUUID(r.id()); b.writeUtf(limit(r.type(), 24), 24);
            b.writeUUID(r.targetId()); b.writeVarInt(r.targetLevel()); b.writeLong(r.budget()); b.writeLong(r.funded());
            b.writeUtf(limit(r.fundingSource(), 24), 24); b.writeUtf(limit(r.status(), 32), 32);
            b.writeBoolean(r.governanceRequired()); b.writeBoolean(r.proposalId() != null);
            if (r.proposalId() != null) b.writeUUID(r.proposalId());
            b.writeUtf(limit(r.failureKey(), 96), 96);
        }
        b.writeUtf(limit(statusKey, 96), 96); b.writeUtf(limit(operatingHealth, 32), 32);
        b.writeLong(Math.max(0, inventoryValue)); b.writeBoolean(inventoryValuationDegraded);
        b.writeLong(Math.max(0, warehouseUsed)); b.writeLong(Math.max(0, warehouseCapacity));
        b.writeVarInt(Math.max(0, activeShipments)); b.writeLong(Math.max(0, debtPrincipal));
        b.writeLong(Math.max(0, dueWithinSevenDays));
    }

    private static List<MemberRow> readMembers(FriendlyByteBuf b) { int n=count(b,MAX_MEMBERS); List<MemberRow> r=new ArrayList<>(n); for(int i=0;i<n;i++)r.add(new MemberRow(b.readUUID(),b.readUtf(24))); return r; }
    private static List<WarehouseRow> readWarehouses(FriendlyByteBuf b) { int n=count(b,MAX_WAREHOUSES); List<WarehouseRow> r=new ArrayList<>(n); for(int i=0;i<n;i++)r.add(new WarehouseRow(b.readUUID(),b.readLong(),b.readLong())); return r; }
    private static List<FacilityRow> readFacilities(FriendlyByteBuf b) { int n=count(b,MAX_FACILITIES); List<FacilityRow> r=new ArrayList<>(n); for(int i=0;i<n;i++)r.add(new FacilityRow(b.readUUID(),b.readVarInt(),b.readUtf(24),b.readLong())); return r; }
    private static List<ContractRow> readContracts(FriendlyByteBuf b) { int n=count(b,MAX_CONTRACTS); List<ContractRow> r=new ArrayList<>(n); for(int i=0;i<n;i++)r.add(new ContractRow(b.readUUID(),b.readUtf(64),b.readVarInt(),b.readLong(),b.readUtf(24))); return r; }
    private static List<ProjectRow> readProjects(FriendlyByteBuf b) { int n=count(b,MAX_PROJECTS); List<ProjectRow> r=new ArrayList<>(n); for(int i=0;i<n;i++)r.add(new ProjectRow(b.readUUID(),b.readUtf(24),b.readUUID(),b.readVarInt(),b.readLong(),b.readLong(),b.readUtf(24),b.readUtf(32),b.readBoolean(),b.readBoolean()?b.readUUID():null,b.readUtf(96))); return r; }
    private static int count(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("invalid company gameplay rows");return n;}
    private static String limit(String value,int max){String s=value==null?"":value;return s.length()>max?s.substring(0,max):s;}

    public UUID companyId(){return companyId;} public String name(){return name;} public long cash(){return cash;}
    public double autoSellRatio(){return autoSellRatio;} public CompanyOperatingMode mode(){return mode;}
    public String role(){return role;} public boolean risk(){return risk;} public BlockPos pos(){return pos;}
    public List<MemberRow> members(){return members;} public List<WarehouseRow> warehouses(){return warehouses;}
    public List<FacilityRow> facilities(){return facilities;} public List<ContractRow> contracts(){return contracts;}
    public List<ProjectRow> projects(){return projects;} public String statusKey(){return statusKey;}
    public String operatingHealth(){return operatingHealth;} public long inventoryValue(){return inventoryValue;}
    public boolean inventoryValuationDegraded(){return inventoryValuationDegraded;}
    public long warehouseUsed(){return warehouseUsed;} public long warehouseCapacity(){return warehouseCapacity;}
    public int activeShipments(){return activeShipments;} public long debtPrincipal(){return debtPrincipal;}
    public long dueWithinSevenDays(){return dueWithinSevenDays;}

    @Override public boolean stillValid(Player player){return !(player instanceof ServerPlayer server)||FinanceGameplayOpener.isValidTerminalSession(server,FinanceTerminalType.COMPANY_DESK,dimension,pos);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
