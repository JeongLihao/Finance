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
    public record CommodityRow(String id, String name, int custodyAmount, int inventoryAmount, boolean physical) {}
    public record ContractRow(UUID id, String commodityId, int quantity, long reward, long deadlineDay,
                              String status, boolean acceptedByPlayer) {}

    private final UUID warehouseId;
    private final String ownerName;
    private final String dimensionId;
    private final BlockPos blockPos;
    private final long used;
    private final long capacity;
    private final WarehouseStatus status;
    private final List<CommodityRow> rows;
    private final List<ContractRow> contracts;
    private final String statusKey;
    private final int statusAmount;

    public WarehouseMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(128), buffer.readBlockPos(),
                buffer.readLong(), buffer.readLong(), buffer.readEnum(WarehouseStatus.class), readRows(buffer),
                readContracts(buffer), buffer.readUtf(96), buffer.readVarInt());
    }

    public WarehouseMenu(int containerId, UUID warehouseId, String ownerName, String dimensionId,
                         BlockPos blockPos, long used, long capacity, WarehouseStatus status,
                         List<CommodityRow> rows, List<ContractRow> contracts,
                         String statusKey, int statusAmount) {
        super(ModMenus.WAREHOUSE.get(), containerId);
        this.warehouseId = warehouseId;
        this.ownerName = ownerName;
        this.dimensionId = dimensionId;
        this.blockPos = blockPos.immutable();
        this.used = Math.max(0, used);
        this.capacity = Math.max(0, capacity);
        this.status = status;
        this.rows = List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
        this.contracts = List.copyOf(contracts.subList(0, Math.min(MAX_CONTRACTS, contracts.size())));
        this.statusKey = statusKey == null ? "" : statusKey;
        this.statusAmount = Math.max(0, statusAmount);
    }

    public static void write(FriendlyByteBuf buffer, UUID id, String ownerName, String dimensionId,
                             BlockPos pos, long used, long capacity, WarehouseStatus status,
                             List<CommodityRow> rows, List<ContractRow> contracts,
                             String statusKey, int statusAmount) {
        buffer.writeUUID(id);
        buffer.writeUtf(limit(ownerName, 64), 64);
        buffer.writeUtf(limit(dimensionId, 128), 128);
        buffer.writeBlockPos(pos);
        buffer.writeLong(Math.max(0, used));
        buffer.writeLong(Math.max(0, capacity));
        buffer.writeEnum(status);
        int size = Math.min(MAX_ROWS, rows.size());
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            CommodityRow row = rows.get(i);
            buffer.writeUtf(limit(row.id(), 64), 64);
            buffer.writeUtf(limit(row.name(), 64), 64);
            buffer.writeVarInt(Math.max(0, row.custodyAmount()));
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

    private static List<CommodityRow> readRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ROWS) throw new IllegalArgumentException("Invalid warehouse row count: " + size);
        List<CommodityRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) rows.add(new CommodityRow(buffer.readUtf(64), buffer.readUtf(64),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
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
    public WarehouseStatus status() { return status; }
    public List<CommodityRow> rows() { return rows; }
    public List<ContractRow> contracts() { return contracts; }
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
