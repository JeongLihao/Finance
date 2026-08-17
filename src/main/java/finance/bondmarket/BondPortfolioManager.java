package finance.bondmarket;

import finance.data.EconomySavedData;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Single service boundary for bond availability, locks and cost accounting. */
public final class BondPortfolioManager {
    private static final Map<Key, BondPosition> POSITIONS = new LinkedHashMap<>();
    private BondPortfolioManager() { }

    public static synchronized BondPosition position(UUID bondId, UUID playerId) {
        CorporateBond bond = CorporateBondManager.bonds().get(bondId);
        if (bond == null || playerId == null) return null;
        Key key = new Key(bondId, playerId);
        return POSITIONS.computeIfAbsent(key, ignored -> new BondPosition(
                bondId, playerId, 0, exactOrMax(bond.faceValue(), bond.holdings().getOrDefault(playerId, 0L)), 0, 0));
    }

    public static synchronized long total(UUID bondId, UUID playerId) {
        CorporateBond bond = CorporateBondManager.bonds().get(bondId);
        return bond == null ? 0 : bond.holdings().getOrDefault(playerId, 0L);
    }

    public static synchronized long available(UUID bondId, UUID playerId) {
        BondPosition position = position(bondId, playerId);
        return position == null ? 0 : Math.max(0, total(bondId, playerId) - position.frozenQuantity());
    }

    public static synchronized long averageCost(UUID bondId, UUID playerId) {
        BondPosition position = position(bondId, playerId);
        long quantity = total(bondId, playerId);
        return position == null || quantity <= 0 ? 0 : position.totalCost() / quantity;
    }

    public static synchronized boolean canAcquire(UUID bondId, UUID playerId, long quantity, long price) {
        CorporateBond bond = CorporateBondManager.bonds().get(bondId);
        if (bond == null || playerId == null || quantity <= 0 || price < 0 || !bond.canAddHolding(playerId, quantity)) return false;
        BondPosition position = position(bondId, playerId);
        BigInteger added = BigInteger.valueOf(price).multiply(BigInteger.valueOf(quantity));
        return position != null && added.add(BigInteger.valueOf(position.totalCost()))
                .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }

    public static synchronized boolean acquire(UUID bondId, UUID playerId, long quantity, long price) {
        if (!canAcquire(bondId, playerId, quantity, price)) return false;
        CorporateBond bond = CorporateBondManager.bonds().get(bondId);
        BondPosition position = position(bondId, playerId);
        long added = Math.multiplyExact(price, quantity);
        if (!bond.addHolding(playerId, quantity)) return false;
        position.setTotalCost(position.totalCost() + added);
        EconomySavedData.markDirty();
        return true;
    }

    /** Initializes or adds cost after another authoritative path already added quantity. */
    public static synchronized boolean recordAcquisition(UUID bondId, UUID playerId, long quantity, long price) {
        if (quantity <= 0 || price < 0) return false;
        BondPosition position = position(bondId, playerId);
        if (position == null) return false;
        BigInteger added = BigInteger.valueOf(price).multiply(BigInteger.valueOf(quantity));
        BigInteger next = BigInteger.valueOf(position.totalCost()).add(added);
        if (next.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
        position.setTotalCost(next.longValue());
        EconomySavedData.markDirty();
        return true;
    }

    public static synchronized boolean freeze(UUID bondId, UUID playerId, long quantity) {
        BondPosition position = position(bondId, playerId);
        if (position == null || quantity <= 0 || available(bondId, playerId) < quantity
                || position.frozenQuantity() > Long.MAX_VALUE - quantity) return false;
        position.setFrozenQuantity(position.frozenQuantity() + quantity);
        EconomySavedData.markDirty();
        return true;
    }

    public static synchronized boolean unfreeze(UUID bondId, UUID playerId, long quantity) {
        BondPosition position = position(bondId, playerId);
        if (position == null || quantity <= 0 || position.frozenQuantity() < quantity) return false;
        position.setFrozenQuantity(position.frozenQuantity() - quantity);
        EconomySavedData.markDirty();
        return true;
    }

    public static synchronized boolean canTransferFrozen(UUID bondId, UUID sellerId, UUID buyerId,
                                                         long quantity, long price) {
        BondPosition seller = position(bondId, sellerId);
        return seller != null && seller.frozenQuantity() >= quantity
                && canAcquire(bondId, buyerId, quantity, price);
    }

    public static synchronized boolean transferFrozen(UUID bondId, UUID sellerId, UUID buyerId,
                                                       long quantity, long price) {
        if (!canTransferFrozen(bondId, sellerId, buyerId, quantity, price)) return false;
        CorporateBond bond = CorporateBondManager.bonds().get(bondId);
        BondPosition seller = position(bondId, sellerId);
        BondPosition buyer = position(bondId, buyerId);
        long sellerQuantity = total(bondId, sellerId);
        BigInteger allocated = BigInteger.valueOf(seller.totalCost()).multiply(BigInteger.valueOf(quantity))
                .divide(BigInteger.valueOf(sellerQuantity));
        long allocatedCost = allocated.longValue();
        long purchaseCost = Math.multiplyExact(price, quantity);
        BigInteger realized = BigInteger.valueOf(seller.realizedProfit())
                .add(BigInteger.valueOf(purchaseCost)).subtract(allocated);
        if (!bond.removeHolding(sellerId, quantity)) return false;
        if (!bond.addHolding(buyerId, quantity)) {
            bond.addHolding(sellerId, quantity);
            return false;
        }
        seller.setFrozenQuantity(seller.frozenQuantity() - quantity);
        seller.setTotalCost(seller.totalCost() - allocatedCost);
        seller.setRealizedProfit(clampSigned(realized));
        buyer.setTotalCost(buyer.totalCost() + purchaseCost);
        EconomySavedData.markDirty();
        return true;
    }

    public static synchronized void recordCoupon(UUID bondId, UUID playerId, long amount) {
        BondPosition position = position(bondId, playerId);
        if (position == null || amount <= 0) return;
        position.setReceivedCoupon(saturatedAdd(position.receivedCoupon(), amount));
        EconomySavedData.markDirty();
    }

    public static synchronized void closeBond(UUID bondId) {
        POSITIONS.entrySet().stream().filter(e -> e.getKey().bondId.equals(bondId)).forEach(e -> {
            e.getValue().setFrozenQuantity(0);
            e.getValue().setTotalCost(0);
        });
        EconomySavedData.markDirty();
    }

    public static Map<Key, BondPosition> positions() { return Collections.unmodifiableMap(POSITIONS); }
    public static void putDirect(BondPosition position) {
        if (position != null) POSITIONS.put(new Key(position.bondId(), position.playerId()), position);
    }
    public static void clearDirect() { POSITIONS.clear(); }

    private static long exactOrMax(long a, long b) {
        BigInteger value = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        return value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static long clampSigned(BigInteger value) {
        return value.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public record Key(UUID bondId, UUID playerId) { }
}
