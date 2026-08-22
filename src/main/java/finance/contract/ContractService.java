package finance.contract;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.data.EconomySavedData;
import finance.market.NpcMarketMaker;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.InventoryTransactionService;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseService;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import finance.gameplay.company.CompanyInventoryFacade;
import finance.warehouse.WarehouseManager;

public final class ContractService {
    private ContractService() {}

    public static synchronized ContractSettlementResult accept(ServerPlayer player, UUID contractId,
                                                                UUID warehouseId, long day,
                                                                String operationKey) {
        if (!validKey(operationKey)) return ContractSettlementResult.failure("finance.contract.invalid_request");
        if(!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.CONTRACT))
            return ContractSettlementResult.failure("finance.contract.module_paused");
        FinanceContract contract = ContractManager.get(contractId);
        WarehouseRecord warehouse = WarehouseService.validRecord(player, warehouseId, true);
        if (contract == null || warehouse == null || !warehouse.ownerId().equals(player.getUUID()))
            return ContractSettlementResult.failure("finance.contract.invalid_session");
        String key = player.getUUID() + ":" + operationKey;
        if (contract.hasOperation(key)) return ContractSettlementResult.failure("finance.contract.duplicate_operation");
        if (contract.status() != ContractStatus.OPEN) return ContractSettlementResult.failure("finance.contract.not_open");
        if (day > contract.deadlineDay()) return ContractSettlementResult.failure("finance.contract.expired");
        if (ContractManager.activeFor(player.getUUID()) >= ContractManager.MAX_ACTIVE_PER_PLAYER)
            return ContractSettlementResult.failure("finance.contract.active_limit");
        if (!contract.accept(player.getUUID(), warehouseId)) return ContractSettlementResult.failure("finance.contract.accept_failed");
        contract.recordOperation(key);
        EconomySavedData.markDirty();
        return ContractSettlementResult.success("finance.contract.accept_success", 0);
    }

    public static synchronized ContractSettlementResult complete(ServerPlayer player, UUID contractId,
                                                                  UUID warehouseId, long day,
                                                                  String operationKey) {
        if (!validKey(operationKey)) return ContractSettlementResult.failure("finance.contract.invalid_request");
        FinanceContract contract = ContractManager.get(contractId);
        WarehouseRecord warehouse = WarehouseService.validRecord(player, warehouseId, true);
        if (contract == null || warehouse == null || contract.status() != ContractStatus.ACCEPTED
                || !player.getUUID().equals(contract.acceptedPlayerId())
                || !warehouseId.equals(contract.destinationWarehouseId()))
            return ContractSettlementResult.failure("finance.contract.invalid_session");
        String key = player.getUUID() + ":" + operationKey;
        if (contract.hasOperation(key)) return ContractSettlementResult.failure("finance.contract.duplicate_operation");
        if (day > contract.deadlineDay()) return ContractSettlementResult.failure("finance.contract.expired");
        CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(contract.commodityId());
        if (!resolution.valid()) return ContractSettlementResult.failure("finance.contract.invalid_commodity");
        Account escrow = AccountManager.getAccounts().get(contract.escrowAccountId());
        if (escrow == null || escrow.getBalance() != contract.rewardAmount())
            return ContractSettlementResult.failure("finance.contract.escrow_mismatch");
        if (!AccountManager.canDeposit(player.getUUID(), contract.rewardAmount()))
            return ContractSettlementResult.failure("finance.contract.balance_overflow");
        UUID destinationCustody = contract.issuerType() == ContractIssuerType.COMPANY
                ? CompanyInventoryFacade.custodyId(contract.issuerId()) : NpcMarketMaker.NPC_UUID;
        if (contract.issuerType() == ContractIssuerType.COMPANY
                && !WarehouseManager.canDepositCapacity(destinationCustody, contract.requiredQuantity()))
            return ContractSettlementResult.failure("finance.contract.destination_full");
        if (!CommodityInventoryManager.canAddCommodity(destinationCustody,
                contract.commodityId(), contract.requiredQuantity()))
            return ContractSettlementResult.failure("finance.contract.destination_full");
        InventoryTransactionService.RemovalPlan plan = InventoryTransactionService.planRemoval(
                player.getInventory(), resolution.item(), contract.requiredQuantity());
        if (plan == null) return ContractSettlementResult.failure("finance.contract.insufficient_items");
        if (!InventoryTransactionService.commitRemoval(player.getInventory(), plan))
            return ContractSettlementResult.failure("finance.contract.inventory_changed");
        if (!CommodityInventoryManager.addCommodity(destinationCustody,
                contract.commodityId(), contract.requiredQuantity())) {
            InventoryTransactionService.rollbackRemoval(player, plan);
            return ContractSettlementResult.failure("finance.contract.delivery_failed");
        }
        if (!AccountManager.moveFunds(contract.escrowAccountId(), player.getUUID(), contract.rewardAmount())) {
            if (!CommodityInventoryManager.removeCommodity(destinationCustody,
                    contract.commodityId(), contract.requiredQuantity())) {
                throw new IllegalStateException("Contract payment rollback could not remove delivered custody");
            }
            InventoryTransactionService.rollbackRemoval(player, plan);
            return ContractSettlementResult.failure("finance.contract.payment_failed");
        }
        contract.complete();
        finance.advancement.FinanceAdvancementTriggers.trigger(player,"first_contract");
        contract.recordOperation(key);
        AccountManager.addTransactionRecord(new TransactionRecord(contract.escrowAccountId(), player.getUUID(),
                contract.rewardAmount(), TransactionType.CONTRACT_COMPLETE, player.getUUID(),
                contract.commodityId(), contract.requiredQuantity()));
        WarehouseManager.refreshOwnerStatus(player.getUUID());
        EconomySavedData.markDirty();
        return ContractSettlementResult.success("finance.contract.complete_success", contract.rewardAmount());
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 64;
    }
}
