package finance.data.serializer;

import finance.commodity.CommodityRegistry;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.logistics.Shipment;
import finance.logistics.ShipmentManager;
import finance.logistics.ShipmentStatus;
import finance.logistics.TransportCargo;
import finance.logistics.TransportCustodyManager;
import finance.warehouse.WarehouseManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

public final class LogisticsDataSerializer {
    public static final String ROOT = "Logistics";
    public static final int VERSION = 1;
    private LogisticsDataSerializer() {}

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        ListTag shipments = new ListTag();
        for (Shipment shipment : ShipmentManager.all().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", shipment.id());
            tag.putUUID("Source", shipment.sourceWarehouseId());
            tag.putUUID("Destination", shipment.destinationWarehouseId());
            if (shipment.contractId() != null) tag.putUUID("Contract", shipment.contractId());
            tag.putString("Commodity", shipment.commodityId());
            tag.putInt("Quantity", shipment.quantity());
            tag.putUUID("Creator", shipment.creatorId());
            tag.putUUID("Carrier", shipment.carrierId());
            if (shipment.companyId() != null) tag.putUUID("Company", shipment.companyId());
            tag.putString("Status", shipment.status().name());
            tag.putLong("CreatedDay", shipment.createdDay());
            tag.putLong("DeadlineDay", shipment.deadlineDay());
            tag.putUUID("Token", shipment.tokenId());
            tag.putString("Failure", shipment.failureReason());
            ListTag operations = new ListTag();
            for (String operation : shipment.operationKeys()) operations.add(StringTag.valueOf(operation));
            tag.put("Operations", operations);
            shipments.add(tag);
        }
        data.put("Shipments", shipments);
        ListTag cargo = new ListTag();
        for (TransportCargo entry : TransportCustodyManager.all().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Shipment", entry.shipmentId());
            tag.putString("Commodity", entry.commodityId());
            tag.putInt("Quantity", entry.quantity());
            cargo.add(tag);
        }
        data.put("Cargo", cargo);
        ListTag loadKeys = new ListTag();
        ShipmentManager.loadKeys().forEach((key, shipmentId) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Key", key);
            tag.putUUID("Shipment", shipmentId);
            loadKeys.add(tag);
        });
        data.put("LoadKeys", loadKeys);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        ShipmentManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        ListTag shipments = data.getList("Shipments", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(ShipmentManager.MAX_RECORDS, shipments.size()); i++) {
            CompoundTag tag = shipments.getCompound(i);
            try {
                UUID id = NbtDataSupport.readUuidOrNull(tag, "Id");
                UUID source = NbtDataSupport.readUuidOrNull(tag, "Source");
                UUID destination = NbtDataSupport.readUuidOrNull(tag, "Destination");
                UUID creator = NbtDataSupport.readUuidOrNull(tag, "Creator");
                UUID carrier = NbtDataSupport.readUuidOrNull(tag, "Carrier");
                UUID token = NbtDataSupport.readUuidOrNull(tag, "Token");
                String commodity = tag.getString("Commodity");
                ShipmentStatus status = NbtDataSupport.safeEnum(ShipmentStatus.class, tag.getString("Status"), null);
                if (id == null || source == null || destination == null || creator == null || carrier == null
                        || token == null || status == null || WarehouseManager.get(source) == null
                        || WarehouseManager.get(destination) == null || CommodityRegistry.getCommodity(commodity) == null)
                    { invalid = true; continue; }
                Shipment shipment = new Shipment(id, source, destination,
                        NbtDataSupport.readUuidOrNull(tag, "Contract"), commodity, tag.getInt("Quantity"),
                        creator, carrier, NbtDataSupport.readUuidOrNull(tag, "Company"), status,
                        tag.getLong("CreatedDay"), tag.getLong("DeadlineDay"), token, tag.getString("Failure"));
                ListTag operations = tag.getList("Operations", Tag.TAG_STRING);
                for (int op = Math.max(0, operations.size() - Shipment.MAX_OPERATION_KEYS); op < operations.size(); op++)
                    shipment.restoreOperation(operations.getString(op));
                if (!ShipmentManager.restore(shipment)) invalid = true;
            } catch (RuntimeException exception) { invalid = true; }
        }
        ListTag cargo = data.getList("Cargo", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(ShipmentManager.MAX_RECORDS, cargo.size()); i++) {
            CompoundTag tag = cargo.getCompound(i);
            try {
                TransportCargo entry = new TransportCargo(NbtDataSupport.readUuidOrNull(tag, "Shipment"),
                        tag.getString("Commodity"), tag.getInt("Quantity"));
                if (CommodityRegistry.getCommodity(entry.commodityId()) == null
                        || !TransportCustodyManager.restore(entry)) invalid = true;
            } catch (RuntimeException exception) { invalid = true; }
        }
        for (Shipment shipment : ShipmentManager.all().values()) {
            TransportCargo entry = TransportCustodyManager.get(shipment.id());
            boolean matches = entry != null && entry.commodityId().equals(shipment.commodityId())
                    && entry.quantity() == shipment.quantity();
            if (shipment.status().carriesCargo() != matches) {
                shipment.quarantine("saved transport custody mismatch");
                invalid = true;
            }
        }
        for (UUID shipmentId : TransportCustodyManager.all().keySet())
            if (ShipmentManager.get(shipmentId) == null) invalid = true;
        ListTag keys = data.getList("LoadKeys", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, keys.size() - ShipmentManager.MAX_LOAD_KEYS); i < keys.size(); i++) {
            CompoundTag tag = keys.getCompound(i);
            ShipmentManager.restoreLoadKey(tag.getString("Key"), NbtDataSupport.readUuidOrNull(tag, "Shipment"));
        }
        if (invalid) ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.LOGISTICS,
                ModuleRunState.PAUSED, "logistics save invariant failed", 0);
    }
}
