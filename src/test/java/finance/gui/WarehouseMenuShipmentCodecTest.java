package finance.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseMenuShipmentCodecTest {
    @Test void boundedShipmentRowsDecodeWithoutCoordinatesOrPrivateRouteData() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1); buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf("iron", 64);
        buffer.writeVarInt(12); buffer.writeUtf("12345678", 8); buffer.writeUtf("87654321", 8);
        buffer.writeLong(30); buffer.writeUtf("IN_TRANSIT", 20);
        WarehouseMenu.ShipmentRow row = WarehouseMenu.readShipments(buffer).get(0);
        assertEquals("iron", row.commodityId());
        assertEquals(12, row.quantity());
        assertEquals("12345678", row.source());
    }

    @Test void oversizedAndInvalidShipmentRowsAreRejectedBeforeAllocationOrDisplay() {
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeVarInt(WarehouseMenu.MAX_SHIPMENTS + 1);
        assertThrows(IllegalArgumentException.class, () -> WarehouseMenu.readShipments(oversized));

        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        invalid.writeVarInt(1); invalid.writeUUID(UUID.randomUUID()); invalid.writeUtf("iron", 64);
        invalid.writeVarInt(0); invalid.writeUtf("source", 8); invalid.writeUtf("target", 8);
        invalid.writeLong(1); invalid.writeUtf("IN_TRANSIT", 20);
        assertThrows(IllegalArgumentException.class, () -> WarehouseMenu.readShipments(invalid));
    }
}
