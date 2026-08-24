package finance.logistics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentManagerTest {
    @AfterEach void cleanup() { ShipmentManager.clearDirect(); }

    @Test void shipmentMetadataAndTransportCustodyHaveOneAuthoritativeAssociation() {
        UUID player = UUID.randomUUID();
        Shipment shipment = shipment(player, 12);
        TransportCargo cargo = new TransportCargo(shipment.id(), "iron", 12);
        assertTrue(ShipmentManager.addLoaded(shipment, cargo, player + ":load"));
        assertSame(shipment, ShipmentManager.get(shipment.id()));
        assertEquals(cargo, TransportCustodyManager.get(shipment.id()));
        assertFalse(ShipmentManager.addLoaded(shipment, cargo, player + ":load"));
    }

    @Test void lossRecoveryRotatesTokenWithoutMovingOrDuplicatingCargo() {
        UUID player = UUID.randomUUID();
        Shipment shipment = shipment(player, 7);
        assertTrue(ShipmentManager.addLoaded(shipment,
                new TransportCargo(shipment.id(), "iron", 7), player + ":load"));
        UUID oldToken = shipment.tokenId();
        assertTrue(shipment.markLossPending("destroyed"));
        assertFalse(shipment.markLossPending("duplicate"));
        assertEquals(7, TransportCustodyManager.get(shipment.id()).quantity());
        assertTrue(shipment.recover(player, UUID.randomUUID()));
        assertNotEquals(oldToken, shipment.tokenId());
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.status());
        assertEquals(1, TransportCustodyManager.all().size());
    }

    @Test void activeLimitsAreBoundedAndTerminalHistoryDoesNotOccupyPlayerLimit() {
        UUID player = UUID.randomUUID();
        for (int i = 0; i < ShipmentManager.MAX_ACTIVE_PER_PLAYER; i++) {
            Shipment shipment = shipment(player, 1);
            assertTrue(ShipmentManager.addLoaded(shipment,
                    new TransportCargo(shipment.id(), "iron", 1), player + ":" + i));
        }
        assertFalse(ShipmentManager.canCreate(player, null));
        Shipment first = ShipmentManager.relatedTo(player, false).get(0);
        assertTrue(first.markDelivered());
        assertNotNull(TransportCustodyManager.release(first.id()));
        assertTrue(ShipmentManager.canCreate(player, null));
    }

    private static Shipment shipment(UUID player, int quantity) {
        return new Shipment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "iron", quantity, player, player, null, ShipmentStatus.IN_TRANSIT,
                1, 15, UUID.randomUUID(), "");
    }
}
