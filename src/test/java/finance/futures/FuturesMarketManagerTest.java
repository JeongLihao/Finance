package finance.futures;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FuturesMarketManagerTest {
    private final UUID buyer=UUID.fromString("00000000-0000-0000-0000-000000005101"),seller=UUID.fromString("00000000-0000-0000-0000-000000005102"),seller2=UUID.fromString("00000000-0000-0000-0000-000000005103");
    private FuturesContract contract;
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();CommodityRegistry.register(new Commodity("fut_test","Test",CommodityCategory.RAW_MATERIALS,100));contract=create();AccountManager.deposit(buyer,2_000);AccountManager.deposit(seller,2_000);AccountManager.deposit(seller2,2_000);assertTrue(MarginManager.deposit(buyer,1_000));assertTrue(MarginManager.deposit(seller,1_000));assertTrue(MarginManager.deposit(seller2,1_000));}
    private FuturesContract create(){var r=FuturesMarketManager.createStandard("fut_test",0,10,11);assertTrue(r.success());return FuturesMarketManager.contract(r.id());}
    @Test void tradeCreatesOppositePositionsWithoutMovingNotionalPrincipal(){long b=MarginManager.account(buyer).cashBalance(),s=MarginManager.account(seller).cashBalance();FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,100,2);FuturesMarketManager.place(seller,contract.id(),FuturesOrderSide.SELL,100,2);assertEquals(2,MarginManager.findPosition(buyer,contract.id()).signedQuantity());assertEquals(-2,MarginManager.findPosition(seller,contract.id()).signedQuantity());assertEquals(b,MarginManager.account(buyer).cashBalance());assertEquals(s,MarginManager.account(seller).cashBalance());assertEquals(1,FuturesMarketManager.trades().size());}
    @Test void priceThenTimePriorityAndPartialFill(){FuturesMarketManager.place(seller,contract.id(),FuturesOrderSide.SELL,105,2);FuturesMarketManager.place(seller2,contract.id(),FuturesOrderSide.SELL,100,2);FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,110,3);assertEquals(seller2,FuturesMarketManager.trades().get(0).sellerId());assertEquals(1,FuturesMarketManager.orders().stream().filter(o->o.playerId().equals(seller)).findFirst().orElseThrow().remainingQuantity());}
    @Test void equalPriceUsesPersistentSequence(){FuturesMarketManager.place(seller,contract.id(),FuturesOrderSide.SELL,100,1);FuturesMarketManager.place(seller2,contract.id(),FuturesOrderSide.SELL,100,1);FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,100,1);assertEquals(seller,FuturesMarketManager.trades().get(0).sellerId());}
    @Test void cancellationReleasesReservationAndSelfTradeIsSkipped(){var buy=FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,90,2);assertTrue(MarginManager.account(buyer).frozenForOrders()>0);assertTrue(FuturesMarketManager.cancel(buyer,buy.id()));assertEquals(0,MarginManager.account(buyer).frozenForOrders());FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.SELL,100,1);FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,100,1);assertTrue(FuturesMarketManager.trades().isEmpty());}
    @Test void reverseTradeClosesBeforeOpeningOppositeSide(){FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,100,2);FuturesMarketManager.place(seller,contract.id(),FuturesOrderSide.SELL,100,2);FuturesMarketManager.place(seller2,contract.id(),FuturesOrderSide.BUY,110,3);FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.SELL,110,3);assertEquals(-1,MarginManager.findPosition(buyer,contract.id()).signedQuantity());assertEquals(200,MarginManager.findPosition(buyer,contract.id()).realizedPnl());}
    @Test void invalidAndOverflowOrdersDoNotFreezeMargin(){long before=MarginManager.account(buyer).cashBalance();assertFalse(FuturesMarketManager.place(buyer,contract.id(),FuturesOrderSide.BUY,Long.MAX_VALUE,2).success());assertEquals(before,MarginManager.account(buyer).cashBalance());assertEquals(0,MarginManager.account(buyer).frozenForOrders());}
}
