package finance.logistics;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.gameplay.company.CompanyMembershipService;
import finance.gameplay.company.CompanyPermission;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseService;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;
import finance.contract.FinanceContract;

/** Server-authoritative, event-driven shipment state transitions. */
public final class ShipmentService {
    public static final int MAX_CARGO_UNITS = 1_024;
    public static final int DEFAULT_DEADLINE_DAYS = 14;
    private ShipmentService() {}

    public static synchronized ShipmentActionResult load(ServerPlayer player, UUID sourceId, UUID destinationId,
                                                          String commodityId, int quantity, UUID contractId,
                                                          String operationKey) {
        if (!validKey(operationKey) || destinationId == null || commodityId == null || commodityId.isBlank()
                || quantity <= 0 || quantity > finance.config.FinanceConfig.logisticsMaxCargoUnits()) {
            return ShipmentActionResult.failure("finance.logistics.invalid_request");
        }
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.LOGISTICS))
            return ShipmentActionResult.failure("finance.logistics.module_paused");
        WarehouseRecord source = WarehouseService.validRecord(player, sourceId, true);
        WarehouseRecord destination = WarehouseManager.get(destinationId);
        if (source == null || destination == null || sourceId.equals(destinationId)
                || destination.status() == finance.warehouse.WarehouseStatus.DISABLED
                || destination.status() == finance.warehouse.WarehouseStatus.ORPHANED) {
            return ShipmentActionResult.failure("finance.logistics.invalid_route");
        }
        String scopedKey = player.getUUID() + ":" + operationKey;
        if (ShipmentManager.byLoadKey(scopedKey) != null)
            return ShipmentActionResult.failure("finance.logistics.duplicate_operation");
        if (quantity > source.transferLimit())
            return ShipmentActionResult.failure("finance.warehouse.transfer_limit");
        if (!canCreateAt(player, source) || !WarehouseManager.canWithdraw(player, source))
            return ShipmentActionResult.failure("finance.logistics.no_permission");
        if (!ShipmentManager.canCreate(player.getUUID(), source.companyId()))
            return ShipmentActionResult.failure("finance.logistics.active_limit");
        UUID sourceCustody = WarehouseService.custodyOwner(source);
        UUID destinationCustody = WarehouseService.custodyOwner(destination);
        boolean sharedCustody = sourceCustody.equals(destinationCustody);
        if (sharedCustody ? WarehouseManager.usedCapacity(destinationCustody)
                > WarehouseManager.totalCapacity(destinationCustody)
                : !WarehouseManager.canDepositCapacity(destinationCustody, quantity))
            return ShipmentActionResult.failure("finance.logistics.destination_full");
        if (CommodityInventoryManager.getCommodityAmount(sourceCustody, commodityId) < quantity)
            return ShipmentActionResult.failure("finance.warehouse.insufficient_custody");
        if (!CommodityInventoryManager.removeCommodity(sourceCustody, commodityId, quantity))
            return ShipmentActionResult.failure("finance.warehouse.custody_changed");

        long day = player.serverLevel().getGameTime() / 24_000L;
        UUID linkedContract = contractId != null ? contractId
                : matchingContract(player.getUUID(), destinationId, commodityId, quantity, day);
        if (contractId != null && !validContract(contractId, player.getUUID(), destinationId,
                commodityId, quantity, day)) {
            if (!CommodityInventoryManager.addCommodity(sourceCustody, commodityId, quantity))
                throw new IllegalStateException("Invalid-contract rollback could not restore source custody");
            return ShipmentActionResult.failure("finance.contract.invalid_session");
        }
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment;
        try {
            shipment = new Shipment(shipmentId, sourceId, destinationId, linkedContract, commodityId, quantity,
                    player.getUUID(), player.getUUID(), source.companyId(), ShipmentStatus.IN_TRANSIT,
                    day, day + finance.config.FinanceConfig.logisticsDefaultDeadlineDays(), UUID.randomUUID(), "");
        } catch (RuntimeException exception) {
            CommodityInventoryManager.addCommodity(sourceCustody, commodityId, quantity);
            return ShipmentActionResult.failure("finance.logistics.invalid_request");
        }
        if (!ShipmentManager.addLoaded(shipment, new TransportCargo(shipmentId, commodityId, quantity), scopedKey)) {
            if (!CommodityInventoryManager.addCommodity(sourceCustody, commodityId, quantity))
                throw new IllegalStateException("Shipment creation rollback could not restore source custody");
            return ShipmentActionResult.failure("finance.logistics.create_failed");
        }
        source.recordOperation(scopedKey);
        WarehouseManager.refreshOwnerStatus(sourceCustody);
        AccountManager.addTransactionRecord(new TransactionRecord(sourceCustody, shipmentId, 0,
                TransactionType.SHIPMENT_LOAD, player.getUUID(), commodityId, quantity));
        EconomySavedData.markDirty();
        return ShipmentActionResult.success("finance.logistics.load_success", shipment);
    }

    public static synchronized ShipmentActionResult unload(ServerPlayer player, UUID shipmentId, UUID tokenId,
                                                            UUID destinationId, String operationKey) {
        if (!validKey(operationKey) || shipmentId == null || tokenId == null || destinationId == null)
            return ShipmentActionResult.failure("finance.logistics.invalid_request");
        Shipment shipment = ShipmentManager.get(shipmentId);
        WarehouseRecord destination = WarehouseService.validRecord(player, destinationId, true);
        if (shipment == null || destination == null || shipment.status() != ShipmentStatus.IN_TRANSIT
                || !destinationId.equals(shipment.destinationWarehouseId())
                || !tokenId.equals(shipment.tokenId()))
            return ShipmentActionResult.failure("finance.logistics.invalid_cargo");
        String scopedKey = player.getUUID() + ":" + operationKey;
        if (shipment.hasOperation(scopedKey))
            return ShipmentActionResult.failure("finance.logistics.duplicate_operation");
        boolean safeCompanyReturn = shipment.companyId() != null
                && shipment.companyId().equals(destination.companyId())
                && player.getUUID().equals(shipment.carrierId());
        if (!canCarry(player, shipment) || !(WarehouseManager.canDeposit(player, destination) || safeCompanyReturn))
            return ShipmentActionResult.failure("finance.logistics.no_permission");
        TransportCargo cargo = TransportCustodyManager.get(shipmentId);
        if (cargo == null || !cargo.commodityId().equals(shipment.commodityId())
                || cargo.quantity() != shipment.quantity()) {
            shipment.quarantine("transport custody mismatch");
            ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.LOGISTICS,
                    finance.diagnostic.ModuleRunState.PAUSED, "transport custody mismatch", currentDay(player));
            EconomySavedData.markDirty();
            return ShipmentActionResult.failure("finance.logistics.custody_mismatch");
        }
        UUID destinationCustody = WarehouseService.custodyOwner(destination);
        if (!WarehouseManager.canDepositCapacity(destinationCustody, cargo.quantity())
                || !CommodityInventoryManager.canAddCommodity(destinationCustody,
                cargo.commodityId(), cargo.quantity()))
            return ShipmentActionResult.failure("finance.logistics.destination_full");
        if (!CommodityInventoryManager.addCommodity(destinationCustody, cargo.commodityId(), cargo.quantity()))
            return ShipmentActionResult.failure("finance.logistics.destination_changed");
        if (shipment.contractId() != null) {
            FinanceContract linked = ContractManager.get(shipment.contractId());
            if (linked != null && linked.status() == ContractStatus.ACCEPTED
                    && currentDay(player) <= linked.deadlineDay()) {
                finance.contract.ContractSettlementResult settlement = finance.contract.ContractService.completeFromTransport(
                        player, shipment.contractId(), destination, cargo.commodityId(), cargo.quantity(),
                        "shipment:" + shipment.id());
                if (!settlement.success()) {
                    if (!CommodityInventoryManager.removeCommodity(destinationCustody,
                            cargo.commodityId(), cargo.quantity()))
                        throw new IllegalStateException("Transport contract rollback could not remove staged destination cargo");
                    return ShipmentActionResult.failure(settlement.messageKey());
                }
            } else if (linked != null && linked.status() != ContractStatus.ACCEPTED
                    && !linked.status().terminal()) {
                CommodityInventoryManager.removeCommodity(destinationCustody, cargo.commodityId(), cargo.quantity());
                return ShipmentActionResult.failure("finance.contract.invalid_session");
            }
        }
        if (!shipment.markDelivered()) {
            CommodityInventoryManager.removeCommodity(destinationCustody, cargo.commodityId(), cargo.quantity());
            return ShipmentActionResult.failure("finance.logistics.state_changed");
        }
        TransportCargo released = TransportCustodyManager.release(shipmentId);
        if (released == null) {
            CommodityInventoryManager.removeCommodity(destinationCustody, cargo.commodityId(), cargo.quantity());
            shipment.quarantine("custody release failed after delivery");
            throw new IllegalStateException("Shipment custody release failed after preflight");
        }
        shipment.recordOperation(scopedKey);
        WarehouseManager.refreshOwnerStatus(destinationCustody);
        AccountManager.addTransactionRecord(new TransactionRecord(shipmentId, destinationCustody, 0,
                TransactionType.SHIPMENT_DELIVER, player.getUUID(), cargo.commodityId(), cargo.quantity()));
        finance.advancement.FinanceAdvancementTriggers.trigger(player, "first_shipment");
        EconomySavedData.markDirty();
        return ShipmentActionResult.success("finance.logistics.unload_success", shipment);
    }

    public static synchronized boolean markLost(UUID shipmentId, UUID tokenId, String reason) {
        Shipment shipment = ShipmentManager.get(shipmentId);
        if (shipment == null || tokenId == null || !tokenId.equals(shipment.tokenId())
                || TransportCustodyManager.get(shipmentId) == null || !shipment.markLossPending(reason)) return false;
        AccountManager.addTransactionRecord(new TransactionRecord(shipmentId, shipmentId, 0,
                TransactionType.SHIPMENT_LOST, shipment.carrierId(), shipment.commodityId(), shipment.quantity()));
        EconomySavedData.markDirty();
        return true;
    }

    public static synchronized ShipmentActionResult recover(ServerPlayer player, UUID sourceWarehouseId,
                                                             String operationKey) {
        if (!validKey(operationKey)) return ShipmentActionResult.failure("finance.logistics.invalid_request");
        WarehouseRecord source = WarehouseService.validRecord(player, sourceWarehouseId, true);
        if (source == null || !canCreateAt(player, source))
            return ShipmentActionResult.failure("finance.logistics.no_permission");
        for (Shipment shipment : ShipmentManager.lossPendingAt(sourceWarehouseId, player.getUUID())) {
            String scopedKey = player.getUUID() + ":" + operationKey;
            if (shipment.hasOperation(scopedKey))
                return ShipmentActionResult.failure("finance.logistics.duplicate_operation");
            if (!shipment.recover(player.getUUID(), UUID.randomUUID())) continue;
            shipment.recordOperation(scopedKey);
            AccountManager.addTransactionRecord(new TransactionRecord(shipment.id(), shipment.id(), 0,
                    TransactionType.SHIPMENT_RECOVER, player.getUUID(), shipment.commodityId(), shipment.quantity()));
            EconomySavedData.markDirty();
            return ShipmentActionResult.success("finance.logistics.recover_success", shipment);
        }
        return ShipmentActionResult.failure("finance.logistics.no_recovery");
    }

    private static boolean canCreateAt(ServerPlayer player, WarehouseRecord warehouse) {
        if (warehouse.companyId() == null) return warehouse.ownerId().equals(player.getUUID()) || player.hasPermissions(2);
        finance.company.Company company = finance.company.CompanyManager.getCompany(warehouse.companyId());
        if (company == null || company.isBankruptcyRisk()) return false;
        return player.hasPermissions(2) || CompanyMembershipService.hasPermission(warehouse.companyId(),
                player.getUUID(), CompanyPermission.MANAGE_LOGISTICS);
    }
    private static boolean canCarry(ServerPlayer player, Shipment shipment) {
        if (player.hasPermissions(2) || player.getUUID().equals(shipment.carrierId())) return true;
        return shipment.companyId() != null && CompanyMembershipService.hasPermission(shipment.companyId(),
                player.getUUID(), CompanyPermission.TRANSPORT_CARGO);
    }
    private static boolean validKey(String key) { return key != null && !key.isBlank() && key.length() <= 64; }
    private static long currentDay(ServerPlayer player) { return player.serverLevel().getGameTime() / 24_000L; }

    private static UUID matchingContract(UUID playerId, UUID destinationId, String commodityId, int quantity,
                                         long currentDay) {
        return ContractManager.contracts().values().stream()
                .filter(contract -> contract.status() == ContractStatus.ACCEPTED
                        && playerId.equals(contract.acceptedPlayerId())
                        && destinationId.equals(contract.destinationWarehouseId())
                        && commodityId.equals(contract.commodityId())
                        && quantity == contract.requiredQuantity()
                        && currentDay <= contract.deadlineDay())
                .sorted(java.util.Comparator.comparingLong(FinanceContract::createdDay)
                        .thenComparing(FinanceContract::id))
                .map(FinanceContract::id).findFirst().orElse(null);
    }

    private static boolean validContract(UUID contractId, UUID playerId, UUID destinationId,
                                         String commodityId, int quantity, long currentDay) {
        FinanceContract contract = ContractManager.get(contractId);
        return contract != null && contract.status() == ContractStatus.ACCEPTED
                && playerId.equals(contract.acceptedPlayerId())
                && destinationId.equals(contract.destinationWarehouseId())
                && commodityId.equals(contract.commodityId())
                && quantity == contract.requiredQuantity()
                && currentDay <= contract.deadlineDay();
    }
}
