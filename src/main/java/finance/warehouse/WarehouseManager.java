package finance.warehouse;

import finance.block.entity.WarehouseControllerBlockEntity;
import finance.commodity.CommodityInventory;
import finance.commodity.CommodityInventoryManager;
import finance.data.EconomySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;

public final class WarehouseManager {
    public static final int DEFAULT_CAPACITY = 1_024;
    public static final int MAX_RECORDS = 4_096;
    private static final Map<UUID, WarehouseRecord> WAREHOUSES = new LinkedHashMap<>();

    private WarehouseManager() {}

    public static synchronized WarehouseRecord registerOrRecover(ServerPlayer player,
                                                                  WarehouseControllerBlockEntity entity) {
        String dimension = player.serverLevel().dimension().location().toString();
        BlockPos pos = entity.getBlockPos();
        UUID id = entity.warehouseId();
        WarehouseRecord existing = WAREHOUSES.get(id);
        if (existing != null && (!existing.dimensionId().equals(dimension) || !existing.blockPos().equals(pos))) {
            if (!makeRoomForRecord()) return null;
            id = UUID.randomUUID();
            entity.assignIdentity(id, player.getUUID());
            existing = null;
        }
        if (existing == null) {
            WarehouseRecord atPosition = WAREHOUSES.values().stream().filter(record ->
                    record.dimensionId().equals(dimension) && record.blockPos().equals(pos)
                            && record.status() != WarehouseStatus.DISABLED
                            && record.status() != WarehouseStatus.ORPHANED).findFirst().orElse(null);
            if (atPosition != null) {
                entity.assignIdentity(atPosition.warehouseId(), atPosition.ownerId());
                existing = atPosition;
            }
        }
        if (existing == null) {
            if (!makeRoomForRecord()) return null;
            UUID owner = entity.ownerId() != null ? entity.ownerId() : player.getUUID();
            entity.assignIdentity(id, owner);
            long day = player.serverLevel().getGameTime() / 24_000L;
            existing = new WarehouseRecord(id, dimension, pos, owner, null, WarehouseTier.BASIC,
                    WarehouseTier.BASIC.capacity(),
                    WarehouseStatus.ACTIVE, day, day, WarehousePermissionMode.OWNER_ONLY);
            WAREHOUSES.put(id, existing);
            finance.advancement.FinanceAdvancementTriggers.trigger(player, "warehouse_built");
            EconomySavedData.markDirty();
        }
        refreshOwnerStatus(existing.ownerId());
        return existing;
    }

    public static WarehouseRecord get(UUID id) { return id == null ? null : WAREHOUSES.get(id); }
    public static Collection<WarehouseRecord> all() { return java.util.List.copyOf(WAREHOUSES.values()); }

    public static synchronized boolean restore(WarehouseRecord record) {
        if (record == null || WAREHOUSES.containsKey(record.warehouseId())) return false;
        boolean incomingActive = record.status() != WarehouseStatus.DISABLED
                && record.status() != WarehouseStatus.ORPHANED;
        WarehouseRecord positionConflict = WAREHOUSES.values().stream().filter(existing -> incomingActive
                && existing.status() != WarehouseStatus.DISABLED && existing.status() != WarehouseStatus.ORPHANED
                && existing.dimensionId().equals(record.dimensionId()) && existing.blockPos().equals(record.blockPos()))
                .findFirst().orElse(null);
        if (positionConflict != null) return false;
        WAREHOUSES.put(record.warehouseId(), record);
        return true;
    }

    public static synchronized void disable(UUID warehouseId) {
        WarehouseRecord record = WAREHOUSES.get(warehouseId);
        if (record == null) return;
        record.setStatus(WarehouseStatus.DISABLED);
        refreshOwnerStatus(record.ownerId());
        EconomySavedData.markDirty();
    }

    public static long usedCapacity(UUID ownerId) {
        CommodityInventory inventory = CommodityInventoryManager.getInventories().get(ownerId);
        if (inventory == null) return 0;
        long used = 0;
        for (Integer value : inventory.getAllCommodities().values()) {
            if (value != null && value > 0) used = Math.min(Long.MAX_VALUE, used + (long) value);
        }
        return used;
    }

    public static long totalCapacity(UUID ownerId) {
        long total = 0;
        for (WarehouseRecord record : WAREHOUSES.values()) {
            boolean subjectMatches = record.ownerId().equals(ownerId)
                    || record.companyId() != null && CompanyInventoryFacade.custodyId(record.companyId()).equals(ownerId);
            if (subjectMatches && record.status() != WarehouseStatus.DISABLED
                    && record.status() != WarehouseStatus.ORPHANED) {
                total = Math.min(Long.MAX_VALUE, total + (long) record.capacityUnits());
            }
        }
        return total;
    }

    public static boolean canDepositCapacity(UUID ownerId, int amount) {
        if (ownerId == null || amount <= 0) return false;
        long used = usedCapacity(ownerId);
        long capacity = totalCapacity(ownerId);
        return used <= capacity && amount <= capacity - used;
    }

    public static void refreshOwnerStatus(UUID ownerId) {
        long used = usedCapacity(ownerId);
        long capacity = totalCapacity(ownerId);
        for (WarehouseRecord record : WAREHOUSES.values()) {
            boolean subjectMatches = record.ownerId().equals(ownerId)
                    || record.companyId() != null && CompanyInventoryFacade.custodyId(record.companyId()).equals(ownerId);
            if (!subjectMatches || record.status() == WarehouseStatus.DISABLED
                    || record.status() == WarehouseStatus.ORPHANED) continue;
            record.setStatus(used > capacity ? WarehouseStatus.OVER_CAPACITY : WarehouseStatus.ACTIVE);
        }
    }

    public static boolean canView(ServerPlayer player, WarehouseRecord record) {
        return player != null && record != null && (record.ownerId().equals(player.getUUID()) || player.hasPermissions(2)
                || record.companyId() != null && CompanyMembershipService.hasPermission(record.companyId(),
                player.getUUID(), CompanyPermission.VIEW_COMPANY));
    }
    public static boolean canDeposit(ServerPlayer player, WarehouseRecord record) {
        return player != null && record != null && (record.ownerId().equals(player.getUUID()) || player.hasPermissions(2)
                || record.companyId() != null && CompanyMembershipService.hasPermission(record.companyId(),
                player.getUUID(), CompanyPermission.DEPOSIT_WAREHOUSE)
                || record.permissionMode() == WarehousePermissionMode.PUBLIC_DEPOSIT);
    }
    public static boolean canWithdraw(ServerPlayer player, WarehouseRecord record) {
        return player != null && record != null && (record.ownerId().equals(player.getUUID()) || player.hasPermissions(2)
                || record.companyId() != null && CompanyMembershipService.hasPermission(record.companyId(),
                player.getUUID(), CompanyPermission.WITHDRAW_WAREHOUSE));
    }
    public static boolean canConfigure(ServerPlayer player, WarehouseRecord record) { return canView(player, record); }

    private static boolean makeRoomForRecord() {
        if (WAREHOUSES.size() < MAX_RECORDS) return true;
        UUID removable = WAREHOUSES.values().stream()
                .filter(record -> record.status() == WarehouseStatus.DISABLED)
                .min(java.util.Comparator.comparingLong(WarehouseRecord::createdDay)
                        .thenComparing(WarehouseRecord::warehouseId))
                .map(WarehouseRecord::warehouseId).orElse(null);
        if (removable == null) return false;
        WAREHOUSES.remove(removable);
        return true;
    }

    public static void clearDirect() { WAREHOUSES.clear(); }
}
