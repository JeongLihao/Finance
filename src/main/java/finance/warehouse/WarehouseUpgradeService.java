package finance.warehouse;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class WarehouseUpgradeService {
    private WarehouseUpgradeService() {}

    public static synchronized WarehouseActionResult upgrade(ServerPlayer player, UUID warehouseId,
                                                              String operationKey) {
        WarehouseRecord record = WarehouseService.validRecord(player, warehouseId, true);
        if (record == null) return WarehouseActionResult.failure("finance.warehouse.invalid_session");
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 64)
            return WarehouseActionResult.failure("finance.warehouse.invalid_request");
        String key = player.getUUID() + ":upgrade:" + operationKey;
        if (record.hasOperation(key)) return WarehouseActionResult.failure("finance.warehouse.duplicate_operation");
        if (!record.ownerId().equals(player.getUUID()) && !player.hasPermissions(2))
            return WarehouseActionResult.failure("finance.warehouse.no_permission");
        if (record.status() == WarehouseStatus.DISABLED || record.status() == WarehouseStatus.ORPHANED)
            return WarehouseActionResult.failure("finance.warehouse.disabled");
        WarehouseUpgradeRequirementService.Requirement requirement =
                WarehouseUpgradeRequirementService.requirement(record.tier());
        if (requirement == null) return WarehouseActionResult.failure("finance.warehouse.max_tier");
        if (AccountManager.getBalance(player.getUUID()) < requirement.cash())
            return WarehouseActionResult.failure("finance.warehouse.upgrade_cash");
        var plans = PhysicalMaterialTransaction.plan(player, requirement.materials());
        if (plans == null) return WarehouseActionResult.failure("finance.warehouse.upgrade_materials");
        if (!PhysicalMaterialTransaction.commit(player, plans))
            return WarehouseActionResult.failure("finance.warehouse.inventory_changed");
        if (!AccountManager.withdraw(player.getUUID(), requirement.cash())) {
            PhysicalMaterialTransaction.rollback(player, plans);
            return WarehouseActionResult.failure("finance.warehouse.upgrade_cash");
        }
        if (!record.upgrade(requirement.targetTier(), requirement.targetTier().capacity())) {
            if (!AccountManager.deposit(player.getUUID(), requirement.cash())
                    || !PhysicalMaterialTransaction.rollback(player, plans))
                throw new IllegalStateException("warehouse upgrade compensation failed");
            return WarehouseActionResult.failure("finance.warehouse.max_tier");
        }
        record.recordOperation(key);
        WarehouseManager.refreshOwnerStatus(WarehouseService.custodyOwner(record));
        finance.block.WarehouseControllerBlock.updateIndicator(player.serverLevel(), record.blockPos(), record.warehouseId());
        AccountManager.addTransactionRecord(new TransactionRecord(player.getUUID(), record.warehouseId(),
                requirement.cash(), TransactionType.FACILITY_UPGRADE, player.getUUID(),
                "warehouse-tier-" + record.tier().level(), record.tier().level()));
        EconomySavedData.markDirty();
        return WarehouseActionResult.success("finance.warehouse.upgrade_success", record.tier().level());
    }
}
