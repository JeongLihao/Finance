package finance.gui;

import finance.block.entity.WarehouseControllerBlockEntity;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.warehouse.CommodityItemResolver;
import finance.warehouse.InventoryTransactionService;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehouseRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import finance.contract.ContractManager;
import finance.contract.ContractStatus;

public final class WarehouseGuiOpener {
    private WarehouseGuiOpener() {}

    public static boolean open(ServerPlayer player, BlockPos pos) {
        return open(player, pos, "", 0);
    }

    public static boolean open(ServerPlayer player, BlockPos pos, String statusKey, int statusAmount) {
        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof WarehouseControllerBlockEntity warehouseEntity)) return false;
        WarehouseRecord record = WarehouseManager.registerOrRecover(player, warehouseEntity);
        if (record == null || !WarehouseManager.canView(player, record)) return false;
        List<WarehouseMenu.CommodityRow> rows = rows(player, record);
        List<WarehouseMenu.ContractRow> contracts = contracts(player);
        List<WarehouseMenu.ShipmentRow> shipments = shipments(player, record);
        java.util.UUID custodyOwner = finance.warehouse.WarehouseService.custodyOwner(record);
        long used = WarehouseManager.usedCapacity(custodyOwner);
        long capacity = WarehouseManager.totalCapacity(custodyOwner);
        var upgrade = finance.warehouse.WarehouseUpgradeRequirementService.requirement(record.tier());
        long upgradeCash = upgrade == null ? 0 : upgrade.cash();
        String upgradeMaterials = finance.warehouse.WarehouseUpgradeRequirementService.summary(upgrade);
        String ownerName = record.ownerId().equals(player.getUUID())
                ? player.getGameProfile().getName() : record.ownerId().toString().substring(0, 8);
        MenuProvider provider = new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("screen.finance.warehouse"); }
            @Override public WarehouseMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                       net.minecraft.world.entity.player.Player menuPlayer) {
                return new WarehouseMenu(containerId, record.warehouseId(), ownerName, record.dimensionId(),
                        record.blockPos(), used, capacity, record.tier().level(), record.transferLimit(),
                        upgradeCash, upgradeMaterials, record.status(), rows, contracts, shipments,
                        statusKey, statusAmount);
            }
        };
        NetworkHooks.openScreen(player, provider, buffer -> WarehouseMenu.write(buffer, record.warehouseId(),
                ownerName, record.dimensionId(), record.blockPos(), used, capacity, record.tier().level(),
                record.transferLimit(), upgradeCash, upgradeMaterials, record.status(), rows,
                contracts, shipments, statusKey, statusAmount));
        return true;
    }

    private static List<WarehouseMenu.ShipmentRow> shipments(ServerPlayer player, WarehouseRecord warehouse) {
        return finance.logistics.ShipmentManager.relatedTo(player.getUUID(), player.hasPermissions(2)).stream()
                .filter(shipment -> shipment.sourceWarehouseId().equals(warehouse.warehouseId())
                        || shipment.destinationWarehouseId().equals(warehouse.warehouseId()))
                .limit(WarehouseMenu.MAX_SHIPMENTS)
                .map(shipment -> new WarehouseMenu.ShipmentRow(shipment.id(), shipment.commodityId(),
                        shipment.quantity(), shortId(shipment.sourceWarehouseId()),
                        shortId(shipment.destinationWarehouseId()), shipment.deadlineDay(), shipment.status().name()))
                .toList();
    }

    private static String shortId(java.util.UUID id) { return id.toString().substring(0, 8); }

    private static List<WarehouseMenu.ContractRow> contracts(ServerPlayer player) {
        return ContractManager.contracts().values().stream()
                .filter(contract -> contract.status() == ContractStatus.OPEN
                        || player.getUUID().equals(contract.acceptedPlayerId()))
                .sorted(Comparator.comparingLong(finance.contract.FinanceContract::deadlineDay))
                .limit(WarehouseMenu.MAX_CONTRACTS)
                .map(contract -> new WarehouseMenu.ContractRow(contract.id(), contract.commodityId(),
                        contract.requiredQuantity(), contract.rewardAmount(), contract.deadlineDay(),
                        contract.status().name(), player.getUUID().equals(contract.acceptedPlayerId())))
                .toList();
    }

    private static List<WarehouseMenu.CommodityRow> rows(ServerPlayer player, WarehouseRecord record) {
        List<WarehouseMenu.CommodityRow> rows = new ArrayList<>();
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            CommodityItemResolver.Resolution resolution = CommodityItemResolver.resolve(commodity.getId());
            int inventory = resolution.valid() ? InventoryTransactionService.countEligible(
                    player.getInventory(), resolution.item()) : 0;
            int custody = CommodityInventoryManager.getCommodityAmount(finance.warehouse.WarehouseService.custodyOwner(record), commodity.getId());
            rows.add(new WarehouseMenu.CommodityRow(commodity.getId(), commodity.getDisplayName(), custody,
                    inventory, resolution.valid()));
        }
        rows.sort(Comparator.comparing(WarehouseMenu.CommodityRow::name));
        return rows;
    }
}
