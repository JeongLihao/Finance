package finance.gui;

import finance.registry.ModMenus;
import finance.warehouse.WarehouseService;
import finance.warehouse.WarehouseStatus;
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

public final class WarehouseMenu extends AbstractContainerMenu {
    public static final int MAX_ROWS = 128;
    public static final int MAX_CONTRACTS = 64;
    public static final int MAX_SHIPMENTS = 64;
    public record CommodityRow(String id, String name, int custodyAmount, int pledgedAmount,
                               int availableAmount, int inventoryAmount, boolean physical) {}
    public record ContractRow(UUID id, String commodityId, int quantity, long reward, long deadlineDay,
                              String status, boolean acceptedByPlayer) {}
    public record ShipmentRow(UUID id, String commodityId, int quantity, String source, String destination,
                              long deadlineDay, String status) {}

    private final UUID warehouseId;
    private final String ownerName;
    private final String dimensionId;
    private final BlockPos blockPos;
    private final long used;
    private final long capacity;
    private final int tier;
    private final int transferLimit;
    private final long upgradeCash;
    private final String upgradeMaterials;
    private final WarehouseStatus status;
    private final boolean companyBound;
    private final UUID capitalProjectId;
    private final String capitalStatus;
    private final long capitalBudget;
    private final long capitalFunded;
    private final List<CommodityRow> rows;
    private final List<ContractRow> contracts;
    private final List<ShipmentRow> shipments;
    private final String statusKey;
    private final int statusAmount;

    public WarehouseMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(128), buffer.readBlockPos(),
                buffer.readLong(), buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(),
                buffer.readUtf(160), buffer.readEnum(WarehouseStatus.class), buffer.readBoolean(), readOptionalUuid(buffer),
                buffer.readUtf(32), buffer.readLong(), buffer.readLong(), readRows(buffer),
                readContracts(buffer), readShipments(buffer), buffer.readUtf(96), buffer.readVarInt());
    }

    public WarehouseMenu(int containerId, UUID warehouseId, String ownerName, String dimensionId,
                         BlockPos blockPos, long used, long capacity, int tier, int transferLimit,
                         long upgradeCash, String upgradeMaterials, WarehouseStatus status,
                         boolean companyBound, UUID capitalProjectId, String capitalStatus,
                         long capitalBudget, long capitalFunded,
                         List<CommodityRow> rows, List<ContractRow> contracts,
                         List<ShipmentRow> shipments,
                         String statusKey, int statusAmount) {
        super(ModMenus.WAREHOUSE.get(), containerId);
        this.warehouseId = warehouseId;
        this.ownerName = ownerName;
        this.dimensionId = dimensionId;
        this.blockPos = blockPos.immutable();
        this.used = Math.max(0, used);
        this.capacity = Math.max(0, capacity);
        this.tier = Math.max(1, Math.min(3, tier));
        this.transferLimit = Math.max(1, transferLimit);
        this.upgradeCash = Math.max(0, upgradeCash);
        this.upgradeMaterials = limit(upgradeMaterials, 160);
        this.status = status;
        this.companyBound = companyBound;
        this.capitalProjectId = capitalProjectId;
        this.capitalStatus = limit(capitalStatus, 32);
        this.capitalBudget = Math.max(0, capitalBudget);
        this.capitalFunded = Math.max(0, Math.min(this.capitalBudget, capitalFunded));
        this.rows = List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
        this.contracts = List.copyOf(contracts.subList(0, Math.min(MAX_CONTRACTS, contracts.size())));
        this.shipments = List.copyOf(shipments.subList(0, Math.min(MAX_SHIPMENTS, shipments.size())));
        this.statusKey = statusKey == null ? "" : statusKey;
        this.statusAmount = Math.max(0, statusAmount);
    }

    public static void write(FriendlyByteBuf buffer, UUID id, String ownerName, String dimensionId,
                             BlockPos pos, long used, long capacity, int tier, int transferLimit,
                             long upgradeCash, String upgradeMaterials, WarehouseStatus status,
                             boolean companyBound, UUID capitalProjectId, String capitalStatus,
                             long capitalBudget, long capitalFunded,
                             List<CommodityRow> rows, List<ContractRow> contracts,
                             List<ShipmentRow> shipments,
                             String statusKey, int statusAmount) {
        buffer.writeUUID(id);
        buffer.writeUtf(limit(ownerName, 64), 64);
        buffer.writeUtf(limit(dimensionId, 128), 128);
        buffer.writeBlockPos(pos);
        buffer.writeLong(Math.max(0, used));
        buffer.writeLong(Math.max(0, capacity));
        buffer.writeVarInt(Math.max(1, Math.min(3, tier)));
        buffer.writeVarInt(Math.max(1, transferLimit));
        buffer.writeLong(Math.max(0, upgradeCash));
        buffer.writeUtf(limit(upgradeMaterials, 160), 160);
        buffer.writeEnum(status);
        buffer.writeBoolean(companyBound);
        buffer.writeBoolean(capitalProjectId != null);
        if (capitalProjectId != null) buffer.writeUUID(capitalProjectId);
        buffer.writeUtf(limit(capitalStatus, 32), 32);
        buffer.writeLong(Math.max(0, capitalBudget));
        buffer.writeLong(Math.max(0, Math.min(capitalBudget, capitalFunded)));
        int size = Math.min(MAX_ROWS, rows.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            CommodityRow row = rows.get(i);
            buffer.writeUtf(limit(row.id(), 64), 64);
            buffer.writeUtf(limit(row.name(), 64), 64);
            buffer.writeVarInt(Math.max(0, row.custodyAmount()));
            buffer.writeVarInt(Math.max(0,row.pledgedAmount()));
            buffer.writeVarInt(Math.max(0,row.availableAmount()));
            buffer.writeVarInt(Math.max(0, row.inventoryAmount()));
            buffer.writeBoolean(row.physical());
        }
        int contractSize = Math.min(MAX_CONTRACTS, contracts.size());
        buffer.writeVarInt(contractSize);
        for (int i = 0; i < contractSize; i++) {
            ContractRow row = contracts.get(i);
            buffer.writeUUID(row.id());
            buffer.writeUtf(limit(row.commodityId(), 64), 64);
            buffer.writeVarInt(Math.max(0, row.quantity()));
            buffer.writeLong(Math.max(0, row.reward()));
            buffer.writeLong(Math.max(0, row.deadlineDay()));
            buffer.writeUtf(limit(row.status(), 16), 16);
            buffer.writeBoolean(row.acceptedByPlayer());
        }
        int shipmentSize = Math.min(MAX_SHIPMENTS, shipments.size());
        buffer.writeVarInt(shipmentSize);
        for (int i = 0; i < shipmentSize; i++) {
            ShipmentRow row = shipments.get(i);
            buffer.writeUUID(row.id());
            buffer.writeUtf(limit(row.commodityId(), 64), 64);
            buffer.writeVarInt(Math.max(0, row.quantity()));
            buffer.writeUtf(limit(row.source(), 8), 8);
            buffer.writeUtf(limit(row.destination(), 8), 8);
            buffer.writeLong(Math.max(0, row.deadlineDay()));
            buffer.writeUtf(limit(row.status(), 20), 20);
        }
        buffer.writeUtf(limit(statusKey, 96), 96);
        buffer.writeVarInt(Math.max(0, statusAmount));
    }

    private static List<ContractRow> readContracts(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CONTRACTS) throw new IllegalArgumentException("Invalid contract row count: " + size);
        List<ContractRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) rows.add(new ContractRow(buffer.readUUID(), buffer.readUtf(64),
                buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readUtf(16), buffer.readBoolean()));
        return rows;
    }

    private static UUID readOptionalUuid(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    static List<ShipmentRow> readShipments(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SHIPMENTS) throw new IllegalArgumentException("Invalid shipment row count: " + size);
        List<ShipmentRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buffer.readUUID();
            String commodity = buffer.readUtf(64);
            int quantity = buffer.readVarInt();
            String source = buffer.readUtf(8), destination = buffer.readUtf(8);
            long deadline = buffer.readLong();
            String status = buffer.readUtf(20);
            if (commodity.isBlank() || source.isBlank() || destination.isBlank() || quantity <= 0
                    || deadline < 0 || status.isBlank()) throw new IllegalArgumentException("Invalid shipment row");
            rows.add(new ShipmentRow(id, commodity, quantity, source, destination, deadline, status));
        }
        return rows;
    }

    private static List<CommodityRow> readRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ROWS) throw new IllegalArgumentException("Invalid warehouse row count: " + size);
        List<CommodityRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) rows.add(new CommodityRow(buffer.readUtf(64), buffer.readUtf(64),
                buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        return rows;
    }

    private static String limit(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public UUID warehouseId() { return warehouseId; }
    public String ownerName() { return ownerName; }
    public long used() { return used; }
    public long capacity() { return capacity; }
    public int tier() { return tier; }
    public int transferLimit() { return transferLimit; }
    public long upgradeCash() { return upgradeCash; }
    public String upgradeMaterials() { return upgradeMaterials; }
    public WarehouseStatus status() { return status; }
    public boolean companyBound() { return companyBound; }
    public UUID capitalProjectId() { return capitalProjectId; }
    public String capitalStatus() { return capitalStatus; }
    public long capitalBudget() { return capitalBudget; }
    public long capitalFunded() { return capitalFunded; }
    public List<CommodityRow> rows() { return rows; }
    public List<ContractRow> contracts() { return contracts; }
    public List<ShipmentRow> shipments() { return shipments; }
    public String statusKey() { return statusKey; }
    public int statusAmount() { return statusAmount; }
    public BlockPos blockPos() { return blockPos; }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer serverPlayer)
                || WarehouseService.validRecord(serverPlayer, warehouseId, true) != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
