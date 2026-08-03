package finance.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
