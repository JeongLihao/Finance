package finance.warehouse;

import java.util.ArrayList;
import java.util.List;

/** Pure slot-allocation logic, intentionally independent from Minecraft registries. */
public final class InventorySlotPlanner {
    public record SlotState(int count, int maxCount, boolean sameItem, boolean empty, boolean eligible) {
        public SlotState {
            if (count < 0 || maxCount < 0 || count > maxCount) throw new IllegalArgumentException("invalid slot state");
        }
    }
    public record Allocation(int slot, int amount) {}

    private InventorySlotPlanner() {}

    public static List<Allocation> removal(List<SlotState> slots, int amount) {
        if (slots == null || amount <= 0) return null;
        int remaining = amount;
        List<Allocation> result = new ArrayList<>();
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            SlotState state = slots.get(slot);
            if (!state.eligible() || !state.sameItem() || state.empty()) continue;
            int take = Math.min(remaining, state.count());
            if (take > 0) result.add(new Allocation(slot, take));
            remaining -= take;
        }
        return remaining == 0 ? List.copyOf(result) : null;
    }

    public static List<Allocation> insertion(List<SlotState> slots, int amount, int newStackMax) {
        if (slots == null || amount <= 0 || newStackMax <= 0) return null;
        int remaining = amount;
        List<Allocation> result = new ArrayList<>();
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            SlotState state = slots.get(slot);
            if (state.empty() || !state.sameItem() || !state.eligible()) continue;
            int add = Math.min(remaining, state.maxCount() - state.count());
            if (add > 0) result.add(new Allocation(slot, add));
            remaining -= add;
        }
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            SlotState state = slots.get(slot);
            if (!state.empty()) continue;
            int add = Math.min(remaining, newStackMax);
            result.add(new Allocation(slot, add));
            remaining -= add;
        }
        return remaining == 0 ? List.copyOf(result) : null;
    }
}
