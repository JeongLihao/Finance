package finance.logistics;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.serializer.LogisticsDataSerializer;
import finance.warehouse.WarehouseManager;
import finance.warehouse.WarehousePermissionMode;
import finance.warehouse.WarehouseRecord;
import finance.warehouse.WarehouseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LogisticsLongRunSimulationTest {
    @AfterEach void cleanup() {
        ShipmentManager.clearDirect(); WarehouseManager.clearDirect(); CommodityRegistry.resetToDefaults();
    }

    @Test @Timeout(5)
    void oneYearOfDeliveryLossRecoveryAndRestartsRemainsBoundedAndConservative() {
        CommodityRegistry.register(new Commodity("iron", "minecraft:iron_ingot", "Iron",
                CommodityCategory.RAW_MATERIALS, 10));
        UUID player = UUID.randomUUID();
        WarehouseRecord source = warehouse(player, BlockPos.ZERO);
        WarehouseRecord destination = warehouse(player, new BlockPos(10, 64, 0));
        WarehouseManager.restore(source); WarehouseManager.restore(destination);
        long loadedUnits = 0, deliveredUnits = 0;
        for (int day = 0; day < 365; day++) {
            int quantity = day % 64 + 1;
            Shipment shipment = new Shipment(UUID.randomUUID(), source.warehouseId(), destination.warehouseId(), null,
                    "iron", quantity, player, player, null, ShipmentStatus.IN_TRANSIT,
                    day, day + 14, UUID.randomUUID(), "");
            assertTrue(ShipmentManager.addLoaded(shipment,
                    new TransportCargo(shipment.id(), "iron", quantity), player + ":day:" + day));
            loadedUnits += quantity;
            if (day % 17 == 0) {
                UUID old = shipment.tokenId();
                assertTrue(shipment.markLossPending("simulation"));
                assertTrue(shipment.recover(player, UUID.randomUUID()));
                assertNotEquals(old, shipment.tokenId());
            }
            assertTrue(shipment.markDelivered());
            TransportCargo released = TransportCustodyManager.release(shipment.id());
            assertNotNull(released);
            deliveredUnits += released.quantity();
            if (day % 30 == 29) {
                CompoundTag root = new CompoundTag(); LogisticsDataSerializer.save(root);
                ShipmentManager.clearDirect(); LogisticsDataSerializer.load(root);
            }
        }
        assertEquals(loadedUnits, deliveredUnits);
        assertTrue(TransportCustodyManager.all().isEmpty());
        assertTrue(ShipmentManager.all().size() <= ShipmentManager.MAX_TERMINAL_HISTORY + 1);
    }

    private static WarehouseRecord warehouse(UUID owner, BlockPos pos) {
        return new WarehouseRecord(UUID.randomUUID(), "minecraft:overworld", pos, owner, null,
                1_024, WarehouseStatus.ACTIVE, 0, 0, WarehousePermissionMode.OWNER_ONLY);
    }
}
