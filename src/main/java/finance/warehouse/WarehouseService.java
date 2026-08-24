package finance.warehouse;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.block.entity.WarehouseControllerBlockEntity;
import finance.commodity.CommodityInventoryManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;
import finance.gameplay.company.CompanyInventoryFacade;

public final class WarehouseService {
    private WarehouseService() {}

    public static synchronized WarehouseActionResult deposit(ServerPlayer player, UUID warehouseId,
                                                               String commodityId, int amount,
                                                               String operationKey) {
        WarehouseRecord record = validRecord(player, warehouseId, true);
        if (record == null) return WarehouseActionResult.failure("finance.warehouse.invalid_session");
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.WAREHOUSE))
            return WarehouseActionResult.failure("finance.warehouse.module_paused");
        if (!validOperation(operationKey) || amount <= 0 || commodityId == null || commodityId.isBlank())
            return WarehouseActionResult.failure("finance.warehouse.invalid_request");
        if (amount > record.transferLimit())
            return WarehouseActionResult.failure("finance.warehouse.transfer_limit");
        String scopedKey = player.getUUID() + ":" + operationKey;
        if (record.hasOperation(scopedKey)) return WarehouseActionResult.failure("finance.warehouse.duplicate_operation");
        if (!WarehouseManager.canDeposit(player, record)) return WarehouseActionResult.failure("finance.warehouse.no_permission");
        if (record.status() == WarehouseStatus.DISABLED || record.status() == WarehouseStatus.ORPHANED)
            return WarehouseActionResult.failure("finance.warehouse.disabled");
        UUID custodyOwner = custodyOwner(record);
        if (!WarehouseManager.canDepositCapacity(custodyOwner, amount))
            return WarehouseActionResult.failure("finance.warehouse.over_capacity");
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(commodityId);
        if (!resolution.valid()) return WarehouseActionResult.failure(resolution.messageKey());
        if (!CommodityInventoryManager.canAddCommodity(custodyOwner, commodityId, amount))
            return WarehouseActionResult.failure("finance.warehouse.custody_overflow");
        InventoryTransactionService.RemovalPlan plan = InventoryTransactionService.planRemoval(
                player.getInventory(), resolution.item(), amount);
        if (plan == null) return WarehouseActionResult.failure("finance.warehouse.insufficient_items");
        if (!InventoryTransactionService.commitRemoval(player.getInventory(), plan))
            return WarehouseActionResult.failure("finance.warehouse.inventory_changed");
        if (!CommodityInventoryManager.addCommodity(custodyOwner, commodityId, amount)) {
            InventoryTransactionService.rollbackRemoval(player, plan);
            return WarehouseActionResult.failure("finance.warehouse.custody_failed");
        }
        record.recordOperation(scopedKey);
        WarehouseManager.refreshOwnerStatus(custodyOwner);
        finance.block.WarehouseControllerBlock.updateIndicator(player.serverLevel(),record.blockPos(),record.warehouseId());
        recordWarehouseTransaction(player, record, commodityId, amount, TransactionType.WAREHOUSE_DEPOSIT);
        finance.advancement.FinanceAdvancementTriggers.trigger(player,"warehouse_deposit");
        EconomySavedData.markDirty();
        return WarehouseActionResult.success("finance.warehouse.deposit_success", amount);
    }

    public static synchronized WarehouseActionResult withdraw(ServerPlayer player, UUID warehouseId,
                                                                String commodityId, int amount,
                                                                String operationKey) {
        WarehouseRecord record = validRecord(player, warehouseId, true);
        if (record == null) return WarehouseActionResult.failure("finance.warehouse.invalid_session");
        if (!validOperation(operationKey) || amount <= 0 || commodityId == null || commodityId.isBlank())
            return WarehouseActionResult.failure("finance.warehouse.invalid_request");
        if (amount > record.transferLimit())
            return WarehouseActionResult.failure("finance.warehouse.transfer_limit");
        String scopedKey = player.getUUID() + ":" + operationKey;
        if (record.hasOperation(scopedKey)) return WarehouseActionResult.failure("finance.warehouse.duplicate_operation");
        if (!WarehouseManager.canWithdraw(player, record)) return WarehouseActionResult.failure("finance.warehouse.no_permission");
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(commodityId);
        if (!resolution.valid()) return WarehouseActionResult.failure(resolution.messageKey());
        UUID custodyOwner = custodyOwner(record);
        if (CommodityInventoryManager.getCommodityAmount(custodyOwner, commodityId) < amount)
            return WarehouseActionResult.failure("finance.warehouse.insufficient_custody");
        InventoryTransactionService.InsertionPlan plan = InventoryTransactionService.planInsertion(
                player.getInventory(), resolution.item(), amount);
        if (plan == null) return WarehouseActionResult.failure("finance.warehouse.inventory_full");
        if (!CommodityInventoryManager.removeCommodity(custodyOwner, commodityId, amount))
            return WarehouseActionResult.failure("finance.warehouse.custody_changed");
        if (!InventoryTransactionService.commitInsertion(player.getInventory(), plan)) {
            if (!CommodityInventoryManager.addCommodity(custodyOwner, commodityId, amount)) {
                throw new IllegalStateException("Warehouse withdrawal compensation could not restore custody");
            }
            return WarehouseActionResult.failure("finance.warehouse.inventory_changed");
        }
        record.recordOperation(scopedKey);
        WarehouseManager.refreshOwnerStatus(custodyOwner);
        finance.block.WarehouseControllerBlock.updateIndicator(player.serverLevel(),record.blockPos(),record.warehouseId());
        recordWarehouseTransaction(player, record, commodityId, amount, TransactionType.WAREHOUSE_WITHDRAW);
        EconomySavedData.markDirty();
        return WarehouseActionResult.success("finance.warehouse.withdraw_success", amount);
    }

    public static WarehouseRecord validRecord(ServerPlayer player, UUID warehouseId, boolean requireLoadedBlock) {
        if (player == null || !player.isAlive() || warehouseId == null) return null;
        WarehouseRecord record = WarehouseManager.get(warehouseId);
        if (record == null) return null;
        if (!player.serverLevel().dimension().location().toString().equals(record.dimensionId())) return null;
        double max = FinanceConfig.terminalInteractionDistance();
        BlockPos pos = record.blockPos();
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > max * max) return null;
        if (requireLoadedBlock) {
            if (!player.serverLevel().isLoaded(pos)) return null;
            BlockEntity entity = player.serverLevel().getBlockEntity(pos);
            if (!(entity instanceof WarehouseControllerBlockEntity warehouse)
                    || !warehouse.warehouseId().equals(warehouseId)) return null;
        }
        return record;
    }

    private static boolean validOperation(String key) {
        return key != null && !key.isBlank() && key.length() <= 64;
    }

    private static void recordWarehouseTransaction(ServerPlayer actor, WarehouseRecord record,
                                                   String commodityId, int amount, TransactionType type) {
        AccountManager.addTransactionRecord(new TransactionRecord(
                custodyOwner(record), custodyOwner(record), 0, type, actor.getUUID(),
                commodityId + "@" + record.warehouseId().toString().substring(0, 8), amount));
    }

    public static UUID custodyOwner(WarehouseRecord record) {
        return record.companyId() == null ? record.ownerId() : CompanyInventoryFacade.custodyId(record.companyId());
    }
}
