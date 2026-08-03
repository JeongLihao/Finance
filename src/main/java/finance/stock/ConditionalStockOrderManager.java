package finance.stock;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.util.MathUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class ConditionalStockOrderManager {

    private static final List<ConditionalStockOrder> ORDERS = new ArrayList<>();
    private static final int MAX_ORDERS_PER_PLAYER = 20;

    private ConditionalStockOrderManager() {
    }

    public static OrderResult addOrder(UUID playerId, String symbol, ConditionalStockOrderType type,
                                       long triggerPrice, long quantity) {
        if (playerId == null || symbol == null || symbol.isBlank() || type == null
                || triggerPrice <= 0 || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            return OrderResult.fail("条件委托参数无效。");
        }
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        if (StockMarketManager.getStock(normalized) == null) {
            return OrderResult.fail("股票不存在。");
        }
        if (StockPortfolioManager.getHolding(playerId, normalized).getQuantity() < quantity) {
            return OrderResult.fail("持仓不足，不能设置条件委托。");
        }
        long count = ORDERS.stream().filter(order -> order.getPlayerId().equals(playerId)).count();
        if (count >= MAX_ORDERS_PER_PLAYER) {
            return OrderResult.fail("条件委托数量已达上限 " + MAX_ORDERS_PER_PLAYER + "。");
        }

        ConditionalStockOrder order = new ConditionalStockOrder(playerId, normalized, type, triggerPrice, quantity);
        ORDERS.add(order);
        EconomySavedData.markDirty();
        return OrderResult.ok("条件委托已设置。");
    }

    public static boolean cancelOrder(UUID playerId, UUID orderId) {
        Iterator<ConditionalStockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            ConditionalStockOrder order = iterator.next();
            if (order.getOrderId().equals(orderId) && order.getPlayerId().equals(playerId)) {
                iterator.remove();
                addCancelRecord(order, "手动取消");
                EconomySavedData.markDirty();
                return true;
            }
        }
        return false;
    }

    public static void checkOrders(MinecraftServer server) {
        if (ORDERS.isEmpty()) {
            return;
        }
        Iterator<ConditionalStockOrder> iterator = ORDERS.iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            ConditionalStockOrder order = iterator.next();
            Stock stock = StockMarketManager.getStock(order.getSymbol());
            if (stock == null) {
                iterator.remove();
                addCancelRecord(order, "股票不存在");
                changed = true;
                continue;
            }
            long holding = StockPortfolioManager.getHolding(order.getPlayerId(), order.getSymbol()).getQuantity();
            if (holding < order.getQuantity()) {
                iterator.remove();
                addCancelRecord(order, "持仓不足");
                changed = true;
                continue;
            }
            long currentPrice = stock.getLastPrice();
            if (currentPrice <= 0 || !order.shouldTrigger(currentPrice)) {
                continue;
            }
            StockMarketManager.TradeResult result = StockMarketManager.placeLimitSell(
                    order.getPlayerId(),
                    order.getSymbol(),
                    currentPrice,
                    order.getQuantity());
            if (result.success()) {
                iterator.remove();
                addTriggerRecord(order, currentPrice);
                notifyPlayer(server, order, currentPrice, "已触发，自动提交卖单。");
                changed = true;
            } else {
                long afterCheckHolding = StockPortfolioManager.getHolding(order.getPlayerId(), order.getSymbol()).getQuantity();
                if (afterCheckHolding < order.getQuantity() || StockMarketManager.getStock(order.getSymbol()) == null) {
                    iterator.remove();
                    addCancelRecord(order, result.message());
                    changed = true;
                }
            }
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    public static int checkOrdersForTest() {
        int triggered = 0;
        boolean changed = false;
        Iterator<ConditionalStockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            ConditionalStockOrder order = iterator.next();
            Stock stock = StockMarketManager.getStock(order.getSymbol());
            if (stock == null || StockPortfolioManager.getHolding(order.getPlayerId(), order.getSymbol()).getQuantity() < order.getQuantity()) {
                iterator.remove();
                addCancelRecord(order, stock == null ? "股票不存在" : "持仓不足");
                changed = true;
                continue;
            }
            long currentPrice = stock.getLastPrice();
            if (currentPrice > 0 && order.shouldTrigger(currentPrice)) {
                StockMarketManager.TradeResult result = StockMarketManager.placeLimitSell(
                        order.getPlayerId(), order.getSymbol(), currentPrice, order.getQuantity());
                if (result.success()) {
                    iterator.remove();
                    addTriggerRecord(order, currentPrice);
                    triggered++;
                    changed = true;
                }
            }
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
        return triggered;
    }

    public static List<ConditionalStockOrder> getOrdersForPlayer(UUID playerId) {
        List<ConditionalStockOrder> result = new ArrayList<>();
        for (ConditionalStockOrder order : ORDERS) {
            if (order.getPlayerId().equals(playerId)) {
                result.add(order);
            }
        }
        return result;
    }

    public static List<ConditionalStockOrder> getOrders() {
        return ORDERS;
    }

    public static void addOrderDirect(ConditionalStockOrder order) {
        if (order != null) {
            ORDERS.add(order);
        }
    }

    public static void clearOrdersDirect() {
        ORDERS.clear();
    }

    public static int cancelOrdersForSymbol(String symbol, String reason) {
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        int cancelled = 0;
        Iterator<ConditionalStockOrder> iterator = ORDERS.iterator();
        while (iterator.hasNext()) {
            ConditionalStockOrder order = iterator.next();
            if (!normalized.equals(order.getSymbol())) {
                continue;
            }
            iterator.remove();
            addCancelRecord(order, reason == null ? "股票退市" : reason);
            cancelled++;
        }
        if (cancelled > 0) {
            EconomySavedData.markDirty();
        }
        return cancelled;
    }

    private static void addTriggerRecord(ConditionalStockOrder order, long currentPrice) {
        long amount = MathUtil.multiplyExactOrNegative1(currentPrice, (int) order.getQuantity());
        AccountManager.addTransactionRecord(new TransactionRecord(
                order.getPlayerId(),
                order.getPlayerId(),
                Math.max(0, amount),
                TransactionType.CONDITIONAL_STOCK_TRIGGER,
                order.getPlayerId(),
                order.getSymbol() + " " + displayType(order.getType()),
                order.getQuantity()));
    }

    private static void addCancelRecord(ConditionalStockOrder order, String reason) {
        AccountManager.addTransactionRecord(new TransactionRecord(
                order.getPlayerId(),
                order.getPlayerId(),
                0,
                TransactionType.CONDITIONAL_STOCK_CANCEL,
                order.getPlayerId(),
                order.getSymbol() + " " + displayType(order.getType()) + " " + reason,
                order.getQuantity()));
    }

    private static void notifyPlayer(MinecraftServer server, ConditionalStockOrder order, long currentPrice, String suffix) {
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(order.getPlayerId());
        if (player != null) {
            player.sendSystemMessage(Component.literal("§e[条件委托] " + order.getSymbol()
                    + " " + displayType(order.getType()) + "价 " + order.getTriggerPrice()
                    + "，当前价 " + currentPrice + "，" + suffix));
        }
    }

    private static String displayType(ConditionalStockOrderType type) {
        return type == ConditionalStockOrderType.TAKE_PROFIT ? "止盈" : "止损";
    }

    public record OrderResult(boolean success, String message) {
        public static OrderResult ok(String message) {
            return new OrderResult(true, message);
        }

        public static OrderResult fail(String message) {
            return new OrderResult(false, message);
        }
    }
}
