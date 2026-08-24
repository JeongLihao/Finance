package finance.warehouse;

import finance.config.FinanceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseTierTest {
    @Test
    void tiersIncreaseOnlyPhysicalCapacityThroughputAndFacilitySlots() {
        assertTrue(WarehouseTier.BASIC.capacity() < WarehouseTier.REINFORCED.capacity());
        assertTrue(WarehouseTier.REINFORCED.capacity() < WarehouseTier.INDUSTRIAL.capacity());
        assertTrue(WarehouseTier.BASIC.transferLimit() < WarehouseTier.REINFORCED.transferLimit());
        assertEquals(1, WarehouseTier.BASIC.facilitySlots());
        assertEquals(8, WarehouseTier.INDUSTRIAL.facilitySlots());
        assertEquals(4, FinanceConfig.factoryThroughput(3));
    }

    @Test
    void tierProgressionIsStrictAndStopsAtThree() {
        assertEquals(WarehouseTier.REINFORCED, WarehouseTier.BASIC.next());
        assertEquals(WarehouseTier.INDUSTRIAL, WarehouseTier.REINFORCED.next());
        assertNull(WarehouseTier.INDUSTRIAL.next());
    }
}
