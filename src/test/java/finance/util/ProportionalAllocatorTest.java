package finance.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProportionalAllocatorTest {

    @Test
    void allocatesExactlyWhenMultiplicationWouldOverflowLong() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009002");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        long weight = Long.MAX_VALUE / 2;
        weights.put(a, weight);
        weights.put(b, weight);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(Long.MAX_VALUE - 10, weights, weight + weight);

        long paid = 0;
        for (ProportionalAllocator.Allocation allocation : allocations) {
            paid = Math.addExact(paid, allocation.amount());
        }

        assertEquals(Long.MAX_VALUE - 10, paid);
    }

    @Test
    void remainderIsDistributedDeterministicallyWithoutExceedingTotal() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009003");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009004");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000009005");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, 1L);
        weights.put(b, 1L);
        weights.put(c, 1L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(2, weights, 3);

        long paid = allocations.stream().mapToLong(ProportionalAllocator.Allocation::amount).sum();
        long receivers = allocations.stream().filter(allocation -> allocation.amount() == 1).count();

        assertEquals(2, paid);
        assertEquals(2, receivers);
    }

    @Test
    void exactWeightSumSupportsHoldingsThatOverflowLongWhenAdded() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009006");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009007");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, Long.MAX_VALUE);
        weights.put(b, Long.MAX_VALUE);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(Long.MAX_VALUE, weights);

        long paid = allocations.stream()
                .map(ProportionalAllocator.Allocation::amount)
                .reduce(0L, Math::addExact);
        assertEquals(Long.MAX_VALUE, paid);
        assertEquals(2, allocations.size());
    }

    @Test
    void corruptedDeclaredWeightCannotAllocateMoreThanTotal() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009008");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009009");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, 10L);
        weights.put(b, 10L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(100, weights, 1);

        assertEquals(100, allocations.stream().mapToLong(ProportionalAllocator.Allocation::amount).sum());
    }

    @Test
    void declaredTotalWeightDoesNotOverpayPartialOwnership() {
        UUID holder = UUID.fromString("00000000-0000-0000-0000-000000009010");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(holder, 50L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(100, weights, 100);

        assertEquals(1, allocations.size());
        assertEquals(50, allocations.get(0).amount());
    }

    @Test
    void exactWeightModeDistributesWholeAmount() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009011");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009012");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, 1L);
        weights.put(b, 2L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(100, weights);

        assertEquals(100, allocations.stream().mapToLong(ProportionalAllocator.Allocation::amount).sum());
    }

    @Test
    void declaredWeightBelowActualWeightNeverOverAllocates() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009013");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009014");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, 80L);
        weights.put(b, 70L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(100, weights, 100);

        assertEquals(100, allocations.stream().mapToLong(ProportionalAllocator.Allocation::amount).sum());
    }

    @Test
    void largeWeightsDoNotOverflow() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000009015");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000009016");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(a, Long.MAX_VALUE);
        weights.put(b, Long.MAX_VALUE);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(Long.MAX_VALUE, weights);

        assertEquals(Long.MAX_VALUE, allocations.stream()
                .map(ProportionalAllocator.Allocation::amount)
                .reduce(0L, Math::addExact));
    }

    @Test
    void nullAndNegativeWeightsAreIgnored() {
        UUID valid = UUID.fromString("00000000-0000-0000-0000-000000009017");
        UUID negative = UUID.fromString("00000000-0000-0000-0000-000000009018");
        UUID nullValue = UUID.fromString("00000000-0000-0000-0000-000000009019");
        Map<UUID, Long> weights = new LinkedHashMap<>();
        weights.put(valid, 5L);
        weights.put(negative, -2L);
        weights.put(nullValue, null);
        weights.put(null, 3L);

        List<ProportionalAllocator.Allocation> allocations =
                ProportionalAllocator.allocate(20, weights);

        assertEquals(1, allocations.size());
        assertEquals(valid, allocations.get(0).id());
        assertEquals(20, allocations.get(0).amount());
        assertTrue(ProportionalAllocator.allocate(20, weights, 0).isEmpty());
    }
}
