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

    /**
     * Allocates against the exact sum of valid weights. This overload is for
     * liquidation paths where a long sum of holdings can itself overflow.
     */
    public static List<Allocation> allocate(long totalAmount, Map<UUID, Long> weights) {
        return allocate(totalAmount, weights, null);
    }

    public static List<Allocation> allocate(long totalAmount, Map<UUID, Long> weights, long totalWeight) {
        if (totalWeight <= 0) {
            return List.of();
        }
        return allocate(totalAmount, weights, BigInteger.valueOf(totalWeight));
    }

    private static List<Allocation> allocate(long totalAmount, Map<UUID, Long> weights,
                                             BigInteger requestedTotalWeight) {
        List<Share> shares = new ArrayList<>();
        if (totalAmount <= 0 || weights == null || weights.isEmpty()) {
            return List.of();
        }

        BigInteger total = BigInteger.valueOf(totalAmount);
        BigInteger actualTotalWeight = BigInteger.ZERO;

        for (Map.Entry<UUID, Long> entry : weights.entrySet()) {
            UUID id = entry.getKey();
            long weight = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (id == null || weight <= 0) {
                continue;
            }

            actualTotalWeight = actualTotalWeight.add(BigInteger.valueOf(weight));
        }
        if (actualTotalWeight.signum() <= 0) {
            return List.of();
        }

        // Exact mode distributes the full amount among valid weights. Declared
        // mode distributes only the portion represented by actual ownership.
        // If corrupted data declares less than actual ownership, clamp the
        // denominator upward so the result can never exceed totalAmount.
        BigInteger denominator = requestedTotalWeight == null
                ? actualTotalWeight
                : requestedTotalWeight.max(actualTotalWeight);
        BigInteger allocationTarget = requestedTotalWeight == null
                ? total
                : total.multiply(actualTotalWeight).divide(denominator);
        BigInteger allocated = BigInteger.ZERO;

        for (Map.Entry<UUID, Long> entry : weights.entrySet()) {
            UUID id = entry.getKey();
            long weight = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (id == null || weight <= 0) {
                continue;
            }

            BigInteger[] divRem = allocationTarget.multiply(BigInteger.valueOf(weight))
                    .divideAndRemainder(actualTotalWeight);
            long base = divRem[0].longValueExact();
            shares.add(new Share(id, weight, base, divRem[1]));
            allocated = allocated.add(divRem[0]);
        }

        BigInteger remaining = allocationTarget.subtract(allocated).max(BigInteger.ZERO);
        shares.sort(Comparator
                .comparing(Share::remainder).reversed()
                .thenComparing(share -> share.id().toString()));

        List<Allocation> result = new ArrayList<>(shares.size());
        for (int i = 0; i < shares.size(); i++) {
            Share share = shares.get(i);
            long extra = remaining.compareTo(BigInteger.valueOf(i)) > 0 ? 1 : 0;
            result.add(new Allocation(share.id(), share.weight(), Math.addExact(share.amount(), extra)));
        }
        return result;
    }

    private record Share(UUID id, long weight, long amount, BigInteger remainder) {
    }

    public record Allocation(UUID id, long weight, long amount) {
    }
}
