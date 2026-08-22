package finance.warehouse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventorySlotPlannerTest {
    @Test void removalUsesOnlyEligibleMatchingSlotsAndRequiresFullAmount() {
        var slots = List.of(
                new InventorySlotPlanner.SlotState(4, 64, true, false, false),
                new InventorySlotPlanner.SlotState(6, 64, true, false, true),
                new InventorySlotPlanner.SlotState(8, 64, false, false, true));
        assertNull(InventorySlotPlanner.removal(slots, 7));
        assertEquals(List.of(new InventorySlotPlanner.Allocation(1, 6)),
                InventorySlotPlanner.removal(slots, 6));
    }

    @Test void insertionFillsMatchingStackThenEmptySlot() {
        var slots = List.of(
                new InventorySlotPlanner.SlotState(60, 64, true, false, true),
                new InventorySlotPlanner.SlotState(0, 64, false, true, true));
        assertEquals(List.of(new InventorySlotPlanner.Allocation(0, 4),
                        new InventorySlotPlanner.Allocation(1, 6)),
                InventorySlotPlanner.insertion(slots, 10, 64));
    }

    @Test void insertionIsAllOrNothingWhenCapacityIsInsufficient() {
        var slots = List.of(
                new InventorySlotPlanner.SlotState(64, 64, true, false, true),
                new InventorySlotPlanner.SlotState(63, 64, false, false, true));
        assertNull(InventorySlotPlanner.insertion(slots, 1, 64));
    }

    @Test void plannerRejectsInvalidAmounts() {
        assertNull(InventorySlotPlanner.removal(List.of(), 0));
        assertNull(InventorySlotPlanner.insertion(List.of(), -1, 64));
    }
}
