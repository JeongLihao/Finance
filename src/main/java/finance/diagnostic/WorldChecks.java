package finance.diagnostic;

import static finance.diagnostic.EconomyConsistencyService.add;

import finance.account.Account;
import finance.account.AccountManager;
import finance.bank.*;
import finance.debt.*;
import finance.futures.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 仓库、物流、聚落、探索和商队的一致性检查。只读，从 {@link EconomyConsistencyService} 拆出。 */
final class WorldChecks {

    private WorldChecks() {}

    static void checkWarehouses(List<DiagnosticIssue> out) {
        Set<String> positions = new HashSet<>();
        Set<UUID> owners = new HashSet<>();
        for (finance.warehouse.WarehouseRecord record : finance.warehouse.WarehouseManager.all()) {
            String position = record.dimensionId() + ":" + record.blockPos().asLong();
            boolean locationMustBeUnique = record.status() != finance.warehouse.WarehouseStatus.DISABLED
                    && record.status() != finance.warehouse.WarehouseStatus.ORPHANED;
            if (record.capacityUnits() <= 0 || (locationMustBeUnique && !positions.add(position))) {
                add(
                        out,
                        DiagnosticSeverity.ERROR,
                        "WAREHOUSE",
                        "INVALID_WAREHOUSE",
                        record.warehouseId().toString(),
                        "仓库容量无效，或有效位置重复");
            }
            owners.add(record.ownerId());
        }
        for (UUID owner : owners) {
            long used = finance.warehouse.WarehouseManager.usedCapacity(owner);
            long capacity = finance.warehouse.WarehouseManager.totalCapacity(owner);
            if (used > capacity)
                add(
                        out,
                        DiagnosticSeverity.WARN,
                        "WAREHOUSE",
                        "WAREHOUSE_OVER_CAPACITY",
                        owner.toString(),
                        "托管量超出仓库有效容量，已禁止新存入");
        }
    }

    static void checkLogistics(List<DiagnosticIssue> out) {
        for (finance.logistics.Shipment shipment :
                finance.logistics.ShipmentManager.all().values()) {
            finance.logistics.TransportCargo cargo = finance.logistics.TransportCustodyManager.get(shipment.id());
            boolean exact = cargo != null
                    && shipment.commodityId().equals(cargo.commodityId())
                    && shipment.quantity() == cargo.quantity();
            if (shipment.status().carriesCargo() != exact)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "SHIPMENT_CUSTODY_MISMATCH",
                        shipment.id().toString(),
                        "运单状态和运输托管不一致");
            if (finance.warehouse.WarehouseManager.get(shipment.sourceWarehouseId()) == null
                    || finance.warehouse.WarehouseManager.get(shipment.destinationWarehouseId()) == null)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "SHIPMENT_WAREHOUSE_MISSING",
                        shipment.id().toString(),
                        "运单指向不存在的仓库");
        }
        for (UUID shipmentId : finance.logistics.TransportCustodyManager.all().keySet())
            if (finance.logistics.ShipmentManager.get(shipmentId) == null)
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "LOGISTICS",
                        "ORPHAN_TRANSPORT_CUSTODY",
                        shipmentId.toString(),
                        "运输托管没有对应的运单记录");
    }

    static void checkSettlements(List<DiagnosticIssue> out) {
        for (finance.settlement.LocalDemand demand :
                finance.settlement.SettlementManager.demands().values()) {
            if (finance.settlement.SettlementManager.get(demand.settlementId()) == null) {
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "SETTLEMENT",
                        "MISSING_SETTLEMENT",
                        demand.id().toString(),
                        "本地需求指向不存在的聚落");
                continue;
            }
            Account escrow = AccountManager.getAccounts().get(demand.escrowAccountId());
            boolean live = demand.status() == finance.settlement.DemandStatus.OPEN
                    || demand.status() == finance.settlement.DemandStatus.ACCEPTED;
            long balance = escrow == null ? -1 : escrow.getBalance();
            if ((live && balance != demand.reward()) || (!live && balance != 0))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "SETTLEMENT",
                        "ESCROW_MISMATCH",
                        demand.id().toString(),
                        "本地需求状态和预存资金不一致");
        }
    }

    static void checkExploration(List<DiagnosticIssue> out) {
        for (finance.exploration.ExplorationAssignment assignment :
                finance.exploration.ExplorationManager.assignments().values()) {
            Account escrow = AccountManager.getAccounts().get(assignment.escrowId());
            long balance = escrow == null ? -1 : escrow.getBalance();
            boolean live = assignment.status() == finance.exploration.ExplorationStatus.ACTIVE;
            if ((live && balance != assignment.reward()) || (!live && balance != 0))
                add(
                        out,
                        DiagnosticSeverity.FATAL,
                        "EXPLORATION",
                        "EXPLORATION_ESCROW_MISMATCH",
                        assignment.id().toString(),
                        "调查任务状态和预存资金不一致");
        }
    }

    static void checkCaravan(List<DiagnosticIssue> out) {
        // P1-6：商队模块熔断后的恢复路径——校验 routes/shipments 基本完整性，
        // 仿其他模块（损坏条目在 load 期已跳过＋计数，这里只做一致性核查）。
        Set<UUID> routeIds = new HashSet<>();
        for (finance.caravan.CaravanRoute r : finance.caravan.CaravanManager.routes()) {
            String id = r.id() == null ? "null" : r.id().toString();
            if (r.id() == null
                    || !routeIds.add(r.id())
                    || r.ownerId() == null
                    || r.sourceSettlementId() == null
                    || r.destSettlementId() == null
                    || r.sourceSettlementId().equals(r.destSettlementId())
                    || r.commodityId() == null
                    || r.commodityId().isBlank()
                    || r.qtyPerTrip() <= 0
                    || r.guardLevel() < finance.caravan.CaravanRoute.MIN_GUARD_LEVEL
                    || r.guardLevel() > finance.caravan.CaravanRoute.MAX_GUARD_LEVEL
                    || r.status() == null
                    || r.saturationBps() < 0
                    || r.createdDay() < 0)
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "INVALID_ROUTE", id, "商队路线的身份、起止点、数量、护卫等级或状态无效");
        }
        Set<UUID> shipIds = new HashSet<>();
        for (finance.caravan.CaravanShipment s : finance.caravan.CaravanManager.allInTransit()) {
            String id = s.id() == null ? "null" : s.id().toString();
            if (s.id() == null
                    || !shipIds.add(s.id())
                    || s.routeId() == null
                    || s.ownerId() == null
                    || s.commodityId() == null
                    || s.commodityId().isBlank()
                    || s.qty() <= 0
                    || s.buyUnitPrice() <= 0
                    || s.arriveDay() <= s.departDay()
                    || s.departDay() < 0
                    || s.guardLevel() < finance.caravan.CaravanRoute.MIN_GUARD_LEVEL
                    || s.guardLevel() > finance.caravan.CaravanRoute.MAX_GUARD_LEVEL
                    || s.raidBpsSnapshot() < 0
                    || s.insuredValuePerUnit() < 0
                    || s.dispatchCost() < 0
                    || s.status() == null)
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "INVALID_SHIPMENT", id, "在途货物的身份、定价、劫掠快照、日期或状态无效");
            else if (!routeIds.contains(s.routeId()))
                add(out, DiagnosticSeverity.ERROR, "CARAVAN", "ORPHAN_SHIPMENT", id, "在途货物指向不存在的路线");
        }
    }
}
