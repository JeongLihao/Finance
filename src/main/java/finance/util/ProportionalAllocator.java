package finance.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProportionalAllocator {

    private ProportionalAllocator() {
    }

    public static List<Allocation> allocate(long totalAmount, Map<UUID, Long> weights, long totalWeight) {
        List<Share> shares = new ArrayList<>();
        if (totalAmount <= 0 || totalWeight <= 0 || weights == null || weights.isEmpty()) {
            return List.of();
        }

        BigInteger total = BigInteger.valueOf(totalAmount);
        BigInteger denominator = BigInteger.valueOf(totalWeight);
        long allocated = 0;

        for (Map.Entry<UUID, Long> entry : weights.entrySet()) {
            UUID id = entry.getKey();
            long weight = Math.max(0, entry.getValue());
            if (id == null || weight <= 0) {
                continue;
            }

            BigInteger[] divRem = total.multiply(BigInteger.valueOf(weight)).divideAndRemainder(denominator);
            long base = divRem[0].longValueExact();
            shares.add(new Share(id, weight, base, divRem[1]));
            allocated += base;
        }

        long remaining = Math.max(0, totalAmount - allocated);
        shares.sort(Comparator
                .comparing(Share::remainder).reversed()
                .thenComparing(share -> share.id().toString()));

        List<Allocation> result = new ArrayList<>(shares.size());
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            long extra = i < remaining ? 1 : 0;
            result.add(new Allocation(share.id(), share.weight(), share.amount() + extra));
        }
        return result;
    }

    private record Share(UUID id, long weight, long amount, BigInteger remainder) {
    }

    public record Allocation(UUID id, long weight, long amount) {
    }
}
