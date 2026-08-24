package finance.logistics;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.serializer.LogisticsDataSerializer;
import finance.diagnostic.ModuleHealthRegistry;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LogisticsPersistenceTest {
    @BeforeEach void setup() {
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        ModuleHealthRegistry.clear();
    }
    @AfterEach void cleanup() {
        ShipmentManager.clearDirect();
        WarehouseManager.clearDirect();
        CommodityRegistry.resetToDefaults();
        ModuleHealthRegistry.clear();
    }

    @Test void roundTripPreservesShipmentCargoTokenAndIdempotencyKey() {
        UUID owner = UUID.randomUUID();
        WarehouseRecord source = warehouse(owner, new BlockPos(1, 64, 1));
        WarehouseRecord destination = warehouse(owner, new BlockPos(9, 64, 1));
        WarehouseManager.restore(source); WarehouseManager.restore(destination);
        Shipment shipment = new Shipment(UUID.randomUUID(), source.warehouseId(), destination.warehouseId(), null,
                "iron", 24, owner, owner, null, ShipmentStatus.IN_TRANSIT, 2, 16,
                UUID.randomUUID(), "");
        assertTrue(ShipmentManager.addLoaded(shipment,
                new TransportCargo(shipment.id(), "iron", 24), owner + ":load"));
        CompoundTag root = new CompoundTag();
        LogisticsDataSerializer.save(root);
        ShipmentManager.clearDirect();
        LogisticsDataSerializer.load(root);
        Shipment loaded = ShipmentManager.get(shipment.id());
        assertNotNull(loaded);
        assertEquals(shipment.tokenId(), loaded.tokenId());
        assertEquals(24, TransportCustodyManager.get(shipment.id()).quantity());
        assertSame(loaded, ShipmentManager.byLoadKey(owner + ":load"));
        assertTrue(ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.LOGISTICS));
    }

    @Test void mismatchedSavedCargoIsPreservedButQuarantinesAndPausesLogistics() {
        UUID owner = UUID.randomUUID();
        WarehouseRecord source = warehouse(owner, BlockPos.ZERO);
        WarehouseRecord destination = warehouse(owner, new BlockPos(5, 64, 0));
        WarehouseManager.restore(source); WarehouseManager.restore(destination);
        Shipment shipment = new Shipment(UUID.randomUUID(), source.warehouseId(), destination.warehouseId(), null,
                "iron", 5, owner, owner, null, ShipmentStatus.IN_TRANSIT, 0, 10,
                UUID.randomUUID(), "");
        ShipmentManager.restore(shipment);
        TransportCustodyManager.restore(new TransportCargo(shipment.id(), "iron", 6));
        CompoundTag root = new CompoundTag(); LogisticsDataSerializer.save(root);
        ShipmentManager.clearDirect(); LogisticsDataSerializer.load(root);
        assertEquals(ShipmentStatus.QUARANTINED, ShipmentManager.get(shipment.id()).status());
        assertEquals(6, TransportCustodyManager.get(shipment.id()).quantity());
        assertFalse(ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.LOGISTICS));
    }

    private static WarehouseRecord warehouse(UUID owner, BlockPos pos) {
        return new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", pos, owner, null,
                1_024, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
    }
}
