package finance.stock;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockOrderManagerTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID BUYER_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID BUYER_B = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID SELLER_A = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID SELLER_B = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final String SYMBOL = "BUG";

    @BeforeEach
    void resetState() {
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        CompanyManager.clearCompaniesDirect();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        StockPortfolioManager.clearPortfolios();

        Company company = new Company(COMPANY_ID, "Bug Regression Inc", CompanyType.RAW_MATERIALS, 10_000);
        company.setPublic(true);
        CompanyManager.registerDirect(company);

        Stock stock = new Stock(SYMBOL, "Bug Regression Inc", COMPANY_ID,
                10_000, 10_000, 0, 100, 100);
        StockMarketManager.putStockDirect(stock);
    }

    @Test
    void buyOrderBelowMarketMakerAskEntersBookAndKeepsFundsLocked() {
        long balanceBefore = AccountManager.getBalance(BUYER_A);

        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 90, 5);

        assertTrue(result.success());
        assertEquals(balanceBefore - 450, AccountManager.getBalance(BUYER_A));
        assertEquals(1, StockOrderManager.getOrders().size());
        StockOrder order = StockOrderManager.getOrders().get(0);
        assertEquals(BUYER_A, order.getPlayerId());
        assertEquals(StockOrderType.BUY, order.getType());
        assertEquals(5, order.getQuantity());
    }

    @Test
    void sellOrderAboveMarketMakerBidEntersBookAndKeepsSharesLocked() {
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 5, 100);

        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 110, 5);

        assertTrue(result.success());
        assertEquals(0, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        StockOrder order = StockOrderManager.getOrders().get(0);
        assertEquals(SELLER_A, order.getPlayerId());
        assertEquals(StockOrderType.SELL, order.getType());
        assertEquals(5, order.getQuantity());
    }

    @Test
    void cancelBuyOrderRefundsLockedFunds() {
        long balanceBefore = AccountManager.getBalance(BUYER_A);
        StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 90, 5);
        UUID orderId = StockOrderManager.getOrders().get(0).getOrderId();

        assertTrue(StockOrderManager.cancelOrder(orderId, BUYER_A));

        assertEquals(balanceBefore, AccountManager.getBalance(BUYER_A));
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    @Test
    void cancelSellOrderReturnsLockedShares() {
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 5, 100);
        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 110, 5);
        UUID orderId = StockOrderManager.getOrders().get(0).getOrderId();

        assertTrue(StockOrderManager.cancelOrder(orderId, SELLER_A));

        assertEquals(5, StockPortfolioManager.getHolding(SELLER_A, SYMBOL).getQuantity());
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    @Test
    void buyOrdersMatchHigherPriceFirst() {
        StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 90, 1);
        StockOrderManager.placeBuyOrder(BUYER_B, SYMBOL, 95, 1);
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 1, 100);

        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 94, 1);

        assertEquals(0, StockPortfolioManager.getHolding(BUYER_A, SYMBOL).getQuantity());
        assertEquals(1, StockPortfolioManager.getHolding(BUYER_B, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(BUYER_A, StockOrderManager.getOrders().get(0).getPlayerId());
    }

    @Test
    void sellOrdersMatchLowerPriceFirst() {
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 1, 100);
        StockPortfolioManager.addHolding(SELLER_B, SYMBOL, 1, 100);
        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 110, 1);
        StockOrderManager.placeSellOrder(SELLER_B, SYMBOL, 105, 1);

        StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 106, 1);

        assertEquals(0, StockPortfolioManager.getHolding(SELLER_B, SYMBOL).getQuantity());
        assertEquals(1105, AccountManager.getBalance(SELLER_B));
        assertEquals(1000, AccountManager.getBalance(SELLER_A));
        assertEquals(1, StockPortfolioManager.getHolding(BUYER_A, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(SELLER_A, StockOrderManager.getOrders().get(0).getPlayerId());
    }

    @Test
    void samePriceOrdersMatchEarlierOrderFirst() {
        StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 95, 1);
        StockOrderManager.placeBuyOrder(BUYER_B, SYMBOL, 95, 1);
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 1, 100);

        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 94, 1);

        assertEquals(1, StockPortfolioManager.getHolding(BUYER_A, SYMBOL).getQuantity());
        assertEquals(0, StockPortfolioManager.getHolding(BUYER_B, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        assertEquals(BUYER_B, StockOrderManager.getOrders().get(0).getPlayerId());
    }

    @Test
    void partialFillLeavesRemainingQuantityOnBook() {
        StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, 100, 2);
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 5, 100);

        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 99, 5);

        assertEquals(2, StockPortfolioManager.getHolding(BUYER_A, SYMBOL).getQuantity());
        assertEquals(1, StockOrderManager.getOrders().size());
        StockOrder remainingSell = findOrder(SELLER_A, StockOrderType.SELL);
        assertEquals(3, remainingSell.getQuantity());
        assertEquals(1, StockOrderManager.getTradeHistory().size());
        assertEquals(2, StockOrderManager.getTradeHistory().get(0).getQuantity());
    }

    @Test
    void playerCannotMatchOwnOppositeOrder() {
        StockOrderManager.placeBuyOrder(PLAYER_ID, SYMBOL, 100, 1);
        StockPortfolioManager.addHolding(PLAYER_ID, SYMBOL, 1, 100);

        StockOrderManager.placeSellOrder(PLAYER_ID, SYMBOL, 99, 1);

        assertEquals(2, StockOrderManager.getOrders().size());
        assertEquals(0, StockOrderManager.getTradeHistory().size());
        assertEquals(0, StockPortfolioManager.getHolding(PLAYER_ID, SYMBOL).getQuantity());
    }

    @Test
    void invalidFairValueRefundsBuyOrderAndDoesNotEnterOrderBook() {
        StockMarketManager.getStock(SYMBOL).setFairValue(0);
        long balanceBefore = AccountManager.getBalance(PLAYER_ID);

        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(PLAYER_ID, SYMBOL, 10, 5);

        assertFalse(result.success());
        assertEquals(balanceBefore, AccountManager.getBalance(PLAYER_ID));
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    @Test
    void invalidFairValueRefundsSellOrderAndDoesNotEnterOrderBook() {
        StockMarketManager.getStock(SYMBOL).setFairValue(0);
        StockPortfolioManager.addHolding(PLAYER_ID, SYMBOL, 5, 10);

        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(PLAYER_ID, SYMBOL, 10, 5);

        assertFalse(result.success());
        assertEquals(5, StockPortfolioManager.getHolding(PLAYER_ID, SYMBOL).getQuantity());
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    @Test
    void overflowDuringSettlementDoesNotAdvanceExistingOrderQuantity() {
        StockOrder existingBuy = new StockOrder(
                UUID.randomUUID(),
                BUYER_A,
                SYMBOL,
                StockOrderType.BUY,
                Long.MAX_VALUE,
                2,
                LocalDateTime.now().minusSeconds(1));
        StockOrderManager.addOrderDirect(existingBuy);
        StockPortfolioManager.addHolding(SELLER_A, SYMBOL, 2, 100);

        StockOrderManager.placeSellOrder(SELLER_A, SYMBOL, 1, 2);

        StockOrder buyOrder = findOrder(BUYER_A, StockOrderType.BUY);
        StockOrder sellOrder = findOrder(SELLER_A, StockOrderType.SELL);
        assertEquals(2, buyOrder.getQuantity());
        assertEquals(2, sellOrder.getQuantity());
        assertEquals(0, StockOrderManager.getTradeHistory().size());
    }

    @Test
    void overflowBeforeOrderCreationDoesNotCreateOrderOrMoveFunds() {
        long balanceBefore = AccountManager.getBalance(BUYER_A);

        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(BUYER_A, SYMBOL, Long.MAX_VALUE, 2);

        assertFalse(result.success());
        assertEquals(balanceBefore, AccountManager.getBalance(BUYER_A));
        assertEquals(0, StockOrderManager.getOrders().size());
    }

    private StockOrder findOrder(UUID playerId, StockOrderType type) {
        List<StockOrder> matches = StockOrderManager.getOrders().stream()
                .filter(order -> order.getPlayerId().equals(playerId))
                .filter(order -> order.getType() == type)
                .toList();
        assertEquals(1, matches.size());
        return matches.get(0);
    }
}
