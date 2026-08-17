package finance.bondmarket;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.data.EconomySavedData;
import finance.debt.BondStatus;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.marketdata.RecentTradeService;
import finance.marketdata.TradeDirection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Price/time-priority secondary market for active corporate bonds. */
public final class BondMarketManager {
    public static final int MAX_ORDERS = 4_096;
    public static final int MAX_TRADES = 500;
    private static final List<BondOrder> ORDERS = new ArrayList<>();
    private static final List<BondTrade> TRADES = new ArrayList<>();
    private static final Map<UUID, Long> LAST_PRICES = new LinkedHashMap<>();
    private static long nextSequence = 1;
    private BondMarketManager() { }

    public static synchronized Result placeBuy(UUID playerId, UUID bondId, long price, long quantity) {
        CorporateBond bond = tradableBond(bondId);
        long reserve = exactProduct(price, quantity);
        if (playerId == null || bond == null || reserve <= 0 || ORDERS.size() >= MAX_ORDERS || !sequenceAvailable()) return Result.fail("债券买单参数无效");
        if (!AccountManager.freezeFunds(playerId, reserve)) return Result.fail("可用资金不足");
        BondOrder order = new BondOrder(UUID.randomUUID(), playerId, bondId, BondOrderSide.BUY, price, quantity, takeSequence());
        return matchOrBook(order);
    }

    public static synchronized Result placeSell(UUID playerId, UUID bondId, long price, long quantity) {
        CorporateBond bond = tradableBond(bondId);
        if (playerId == null || bond == null || price <= 0 || quantity <= 0 || ORDERS.size() >= MAX_ORDERS || !sequenceAvailable()) return Result.fail("债券卖单参数无效");
        if (!BondPortfolioManager.freeze(bondId, playerId, quantity)) return Result.fail("可用债券不足");
        BondOrder order = new BondOrder(UUID.randomUUID(), playerId, bondId, BondOrderSide.SELL, price, quantity, takeSequence());
        return matchOrBook(order);
    }

    private static Result matchOrBook(BondOrder incoming) {
        List<BondOrder> candidates = ORDERS.stream()
                .filter(o -> o.bondId().equals(incoming.bondId()) && o.side() != incoming.side())
                .sorted(oppositeComparator(incoming.side())).toList();
        for (BondOrder resting : candidates) {
            if (incoming.remainingQuantity() <= 0) break;
            if (resting.playerId().equals(incoming.playerId()) || !crosses(incoming, resting)) continue;
            long quantity = Math.min(incoming.remainingQuantity(), resting.remainingQuantity());
            long price = resting.limitPricePerUnit();
            BondOrder buy = incoming.side() == BondOrderSide.BUY ? incoming : resting;
            BondOrder sell = incoming.side() == BondOrderSide.SELL ? incoming : resting;
            if (!execute(buy, sell, price, quantity, incoming.side() == BondOrderSide.BUY)) break;
            incoming.reduce(quantity); resting.reduce(quantity);
            if (resting.remainingQuantity() == 0) ORDERS.remove(resting);
        }
        if (incoming.remainingQuantity() > 0) ORDERS.add(incoming);
        EconomySavedData.markDirty();
        return Result.ok(incoming.orderId(), incoming.remainingQuantity() == 0 ? "债券订单已成交" : "债券订单已挂入订单簿");
    }

    private static boolean execute(BondOrder buy, BondOrder sell, long price, long quantity, boolean buyInitiated) {
        long payment = exactProduct(price, quantity);
        long reserved = exactProduct(buy.limitPricePerUnit(), quantity);
        if (payment <= 0 || reserved < payment
                || !AccountManager.canSettleFrozenTransfer(buy.playerId(), sell.playerId(), reserved, payment)
                || !BondPortfolioManager.canTransferFrozen(buy.bondId(), sell.playerId(), buy.playerId(), quantity, price)) return false;
        if (!AccountManager.settleFrozenTransfer(buy.playerId(), sell.playerId(), reserved, payment)) return false;
        if (!BondPortfolioManager.transferFrozen(buy.bondId(), sell.playerId(), buy.playerId(), quantity, price)) {
            AccountManager.rollbackSettledFrozenTransfer(buy.playerId(), sell.playerId(), reserved, payment);
            return false;
        }
        long day = CandlestickService.currentMcDay();
        BondTrade trade = new BondTrade(buy.playerId(), sell.playerId(), buy.bondId(), price, quantity, day, LocalDateTime.now());
        TRADES.add(trade); if (TRADES.size() > MAX_TRADES) TRADES.subList(0, TRADES.size() - MAX_TRADES).clear();
        LAST_PRICES.put(buy.bondId(), price);
        String id = buy.bondId().toString();
        CandlestickService.recordTrade(MarketInstrumentType.BOND, id, day, price, quantity);
        RecentTradeService.record(MarketInstrumentType.BOND, id, day, price, quantity, trade.timestamp(),
                buyInitiated ? TradeDirection.BUY : TradeDirection.SELL);
        AccountManager.addTransactionRecord(new TransactionRecord(buy.playerId(), sell.playerId(), payment,
                TransactionType.BOND_BUY, buy.playerId(), id, quantity));
        AccountManager.addTransactionRecord(new TransactionRecord(buy.playerId(), sell.playerId(), payment,
                TransactionType.BOND_SELL, sell.playerId(), id, quantity));
        return true;
    }

    public static synchronized boolean cancel(UUID playerId, UUID orderId) {
        Iterator<BondOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            BondOrder order = iterator.next();
            if (!order.orderId().equals(orderId) || !order.playerId().equals(playerId)) continue;
            if (!release(order)) return false;
            iterator.remove();
            AccountManager.addTransactionRecord(new TransactionRecord(playerId, playerId,
                    order.side() == BondOrderSide.BUY ? exactProduct(order.limitPricePerUnit(), order.remainingQuantity()) : 0,
                    TransactionType.BOND_ORDER_CANCEL, playerId, order.bondId().toString(), order.remainingQuantity()));
            EconomySavedData.markDirty(); return true;
        }
        return false;
    }

    public static synchronized boolean cancelOrdersForBond(UUID bondId) {
        List<BondOrder> matching = ORDERS.stream().filter(o -> o.bondId().equals(bondId)).toList();
        for (BondOrder order : matching) if (!canRelease(order)) return false;
        for (BondOrder order : matching) {
            if (!release(order)) return false;
            ORDERS.remove(order);
        }
        if (!matching.isEmpty()) EconomySavedData.markDirty();
        return true;
    }

    public static synchronized boolean cancelOrdersForCompany(UUID companyId) {
        List<UUID> bondIds = CorporateBondManager.bonds().values().stream()
                .filter(b -> b.companyId().equals(companyId)).map(CorporateBond::id).toList();
        for (UUID bondId : bondIds) {
            List<BondOrder> matching = ORDERS.stream().filter(o -> o.bondId().equals(bondId)).toList();
            for (BondOrder order : matching) if (!canRelease(order)) return false;
        }
        for (UUID bondId : bondIds) if (!cancelOrdersForBond(bondId)) return false;
        return true;
    }

    private static boolean canRelease(BondOrder order) {
        if (order.side() == BondOrderSide.SELL) return BondPortfolioManager.position(order.bondId(), order.playerId()) != null
                && BondPortfolioManager.position(order.bondId(), order.playerId()).frozenQuantity() >= order.remainingQuantity();
        long amount = exactProduct(order.limitPricePerUnit(), order.remainingQuantity());
        return amount > 0 && AccountManager.getAccount(order.playerId()).getFrozenBalance() >= amount
                && AccountManager.getAccount(order.playerId()).canDeposit(amount);
    }
    private static boolean release(BondOrder order) {
        if (order.side() == BondOrderSide.BUY) return AccountManager.unfreezeFunds(order.playerId(), exactProduct(order.limitPricePerUnit(), order.remainingQuantity()));
        return BondPortfolioManager.unfreeze(order.bondId(), order.playerId(), order.remainingQuantity());
    }

    private static CorporateBond tradableBond(UUID id) {
        CorporateBond bond = id == null ? null : CorporateBondManager.bonds().get(id);
        return bond != null && bond.status() == BondStatus.ACTIVE ? bond : null;
    }
    private static boolean crosses(BondOrder incoming, BondOrder resting) {
        return incoming.side() == BondOrderSide.BUY
                ? incoming.limitPricePerUnit() >= resting.limitPricePerUnit()
                : incoming.limitPricePerUnit() <= resting.limitPricePerUnit();
    }
    private static Comparator<BondOrder> oppositeComparator(BondOrderSide incomingSide) {
        Comparator<BondOrder> price = Comparator.comparingLong(BondOrder::limitPricePerUnit);
        if (incomingSide == BondOrderSide.SELL) price = price.reversed();
        return price.thenComparingLong(BondOrder::createdSequence);
    }
    private static long exactProduct(long a, long b) {
        if (a <= 0 || b <= 0) return -1;
        try { return Math.multiplyExact(a, b); } catch (ArithmeticException ignored) { return -1; }
    }

    private static boolean sequenceAvailable() { return nextSequence > 0 && nextSequence < Long.MAX_VALUE; }
    private static long takeSequence() {
        if (!sequenceAvailable()) throw new IllegalStateException("bond order sequence exhausted");
        return nextSequence++;
    }

    public static long lastPrice(UUID bondId, long fallback) { return LAST_PRICES.getOrDefault(bondId, Math.max(0, fallback)); }
    public static List<BondOrder> orders() { return List.copyOf(ORDERS); }
    public static List<BondTrade> trades() { return List.copyOf(TRADES); }
    public static long nextSequence() { return nextSequence; }
    public static void restoreSequence(long value) { nextSequence = value > 0 ? value : 1; }
    public static void addOrderDirect(BondOrder order) { if (order != null && ORDERS.size() < MAX_ORDERS) ORDERS.add(order); }
    public static void addTradeDirect(BondTrade trade) { if (trade != null && TRADES.size() < MAX_TRADES) { TRADES.add(trade); LAST_PRICES.put(trade.bondId(), trade.pricePerUnit()); } }
    public static void putLastPriceDirect(UUID bondId, long price) { if (bondId != null && price > 0) LAST_PRICES.put(bondId, price); }
    public static void clearDirect() { ORDERS.clear(); TRADES.clear(); LAST_PRICES.clear(); nextSequence = 1; }
    public record Result(boolean success, UUID orderId, String message) {
        static Result ok(UUID id, String message) { return new Result(true, id, message); }
        static Result fail(String message) { return new Result(false, null, message); }
    }
}
