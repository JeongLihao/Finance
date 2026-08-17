package finance.data.serializer;

import finance.account.AccountManager;
import finance.bondmarket.*;
import finance.debt.BondStatus;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Bounded persistence and invariant recovery for the corporate-bond secondary market. */
public final class BondMarketDataSerializer {
    private BondMarketDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag market = new CompoundTag();
        market.putLong("NextSequence", BondMarketManager.nextSequence());
        ListTag positions = new ListTag();
        for (BondPosition position : BondPortfolioManager.positions().values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Bond", position.bondId()); tag.putUUID("Player", position.playerId());
            tag.putLong("Frozen", position.frozenQuantity()); tag.putLong("TotalCost", position.totalCost());
            tag.putLong("Realized", position.realizedProfit()); tag.putLong("Coupons", position.receivedCoupon());
            positions.add(tag);
        }
        market.put("Positions", positions);
        ListTag orders = new ListTag();
        for (BondOrder order : BondMarketManager.orders()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", order.orderId()); tag.putUUID("Player", order.playerId());
            tag.putUUID("Bond", order.bondId()); tag.putString("Side", order.side().name());
            tag.putLong("Price", order.limitPricePerUnit()); tag.putLong("Quantity", order.remainingQuantity());
            tag.putLong("Sequence", order.createdSequence()); orders.add(tag);
        }
        market.put("Orders", orders);
        ListTag trades = new ListTag();
        for (BondTrade trade : BondMarketManager.trades()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Buyer", trade.buyerId()); tag.putUUID("Seller", trade.sellerId());
            tag.putUUID("Bond", trade.bondId()); tag.putLong("Price", trade.pricePerUnit()); tag.putLong("Quantity", trade.quantity());
            tag.putLong("Day", trade.mcDay()); tag.putLong("Timestamp", trade.timestamp().toEpochSecond(ZoneOffset.UTC)); trades.add(tag);
        }
        market.put("Trades", trades);
        root.put("BondMarket", market);
    }

    public static void load(CompoundTag root) {
        BondPortfolioManager.clearDirect(); BondMarketManager.clearDirect();
        if (!root.contains("BondMarket", Tag.TAG_COMPOUND)) return;
        CompoundTag market = root.getCompound("BondMarket");
        ListTag positionTags = market.getList("Positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(BondMarketManager.MAX_ORDERS, positionTags.size()); i++) {
            CompoundTag tag = positionTags.getCompound(i);
            UUID bondId = NbtDataSupport.readUuidOrNull(tag, "Bond"), playerId = NbtDataSupport.readUuidOrNull(tag, "Player");
            CorporateBond bond = bondId == null ? null : CorporateBondManager.bonds().get(bondId);
            long frozen = tag.getLong("Frozen"), cost = tag.getLong("TotalCost"), coupons = tag.getLong("Coupons");
            if (bond == null || playerId == null || frozen < 0 || cost < 0 || coupons < 0
                    || frozen > bond.holdings().getOrDefault(playerId, 0L)) continue;
            BondPortfolioManager.putDirect(new BondPosition(bondId, playerId, frozen, cost, tag.getLong("Realized"), coupons));
        }

        List<BondOrder> candidates = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        ListTag orderTags = market.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(BondMarketManager.MAX_ORDERS, orderTags.size()); i++) {
            CompoundTag tag = orderTags.getCompound(i);
            UUID id = NbtDataSupport.readUuidOrNull(tag, "Id"), player = NbtDataSupport.readUuidOrNull(tag, "Player");
            UUID bondId = NbtDataSupport.readUuidOrNull(tag, "Bond");
            BondOrderSide side = NbtDataSupport.safeEnum(BondOrderSide.class, tag.getString("Side"), null);
            CorporateBond bond = bondId == null ? null : CorporateBondManager.bonds().get(bondId);
            long price = tag.getLong("Price"), quantity = tag.getLong("Quantity"), sequence = tag.getLong("Sequence");
            if (id == null || !ids.add(id) || player == null || bond == null || bond.status() != BondStatus.ACTIVE
                    || side == null || price <= 0 || quantity <= 0 || sequence <= 0 || exactProduct(price, quantity) < 0) continue;
            candidates.add(new BondOrder(id, player, bondId, side, price, quantity, sequence));
        }
        if (locksMatch(candidates)) candidates.forEach(BondMarketManager::addOrderDirect);
        else clearRestoredFrozenPositions();
        long maxSequence = candidates.stream().mapToLong(BondOrder::createdSequence).max().orElse(0);
        long sequenceAfterOrders = maxSequence == Long.MAX_VALUE ? Long.MAX_VALUE : maxSequence + 1;
        BondMarketManager.restoreSequence(Math.max(market.getLong("NextSequence"), sequenceAfterOrders));

        ListTag tradeTags = market.getList("Trades", Tag.TAG_COMPOUND);
        int start = Math.max(0, tradeTags.size() - BondMarketManager.MAX_TRADES);
        for (int i = start; i < tradeTags.size(); i++) {
            CompoundTag tag = tradeTags.getCompound(i);
            UUID buyer = NbtDataSupport.readUuidOrNull(tag, "Buyer"), seller = NbtDataSupport.readUuidOrNull(tag, "Seller");
            UUID bondId = NbtDataSupport.readUuidOrNull(tag, "Bond");
            long price = tag.getLong("Price"), quantity = tag.getLong("Quantity"), day = tag.getLong("Day"), epoch = tag.getLong("Timestamp");
            if (buyer == null || seller == null || bondId == null || price <= 0 || quantity <= 0 || day < 0) continue;
            try { BondMarketManager.addTradeDirect(new BondTrade(buyer, seller, bondId, price, quantity, day,
                    LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC))); } catch (RuntimeException ignored) { }
        }
    }

    private static boolean locksMatch(List<BondOrder> orders) {
        Map<BondPortfolioManager.Key, BigInteger> sellLocks = new HashMap<>();
        Map<UUID, BigInteger> buyLocks = new HashMap<>();
        for (BondOrder order : orders) {
            if (order.side() == BondOrderSide.SELL) sellLocks.merge(new BondPortfolioManager.Key(order.bondId(), order.playerId()),
                    BigInteger.valueOf(order.remainingQuantity()), BigInteger::add);
            else buyLocks.merge(order.playerId(), BigInteger.valueOf(order.limitPricePerUnit())
                    .multiply(BigInteger.valueOf(order.remainingQuantity())), BigInteger::add);
        }
        for (BondPosition position : BondPortfolioManager.positions().values()) {
            long expected = sellLocks.getOrDefault(new BondPortfolioManager.Key(position.bondId(), position.playerId()), BigInteger.ZERO)
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            if (position.frozenQuantity() != expected) return false;
        }
        for (Map.Entry<BondPortfolioManager.Key, BigInteger> entry : sellLocks.entrySet()) {
            BondPosition position = BondPortfolioManager.positions().get(entry.getKey());
            if (position == null || entry.getValue().compareTo(BigInteger.valueOf(position.frozenQuantity())) != 0) return false;
        }
        for (Map.Entry<UUID, BigInteger> entry : buyLocks.entrySet()) {
            if (entry.getValue().compareTo(BigInteger.valueOf(AccountManager.getAccount(entry.getKey()).getFrozenBalance())) > 0) return false;
        }
        return true;
    }
    private static void clearRestoredFrozenPositions() {
        for (BondPosition old : List.copyOf(BondPortfolioManager.positions().values())) {
            BondPortfolioManager.putDirect(new BondPosition(old.bondId(), old.playerId(), 0,
                    old.totalCost(), old.realizedProfit(), old.receivedCoupon()));
        }
    }
    private static long exactProduct(long a, long b) { try { return Math.multiplyExact(a, b); } catch (ArithmeticException ignored) { return -1; } }
}
