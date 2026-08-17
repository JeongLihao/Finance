package finance.futures;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FuturesLifecycleTest{
    private final UUID a=UUID.fromString("00000000-0000-0000-0000-000000005501"),b=UUID.fromString("00000000-0000-0000-0000-000000005502");
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();CommodityRegistry.register(new Commodity("life_future","Life",CommodityCategory.RAW_MATERIALS,100));AccountManager.deposit(a,2000);AccountManager.deposit(b,2000);MarginManager.deposit(a,1000);MarginManager.deposit(b,1000);}
    @Test void lastTradingDayCancelsOrdersAndMaturitySettlesExactlyOnce(){var r=FuturesMarketManager.createStandard("life_future",0,2,3);FuturesContract c=FuturesMarketManager.contract(r.id());FuturesMarketManager.place(a,c.id(),FuturesOrderSide.BUY,100,1);FuturesMarketManager.place(b,c.id(),FuturesOrderSide.SELL,100,1);FuturesMarketManager.place(a,c.id(),FuturesOrderSide.BUY,90,1);FuturesMarketManager.processDay(2);assertEquals(FuturesContractStatus.LAST_TRADING_DAY,c.status());FuturesClearingService.closeDay(2);assertEquals(FuturesContractStatus.SETTLING,c.status());assertTrue(FuturesMarketManager.orders().isEmpty());assertEquals(0,MarginManager.account(a).frozenForOrders());FuturesMarketManager.processDay(3);assertEquals(FuturesContractStatus.SETTLED,c.status());assertNull(MarginManager.findPosition(a,c.id()));FuturesMarketManager.processDay(4);assertEquals(1,FuturesClearingService.history().stream().filter(FuturesSettlementRecord::finalSettlement).count());}
    @Test void finalSettlementPausesWhenCommodityHasNoReliablePrice(){FuturesContract c=new FuturesContract(UUID.randomUUID(),"MISSING2","missing_future",10,0,1,2,FuturesSettlementType.CASH,FuturesContractStatus.SETTLING);FuturesMarketManager.putContractDirect(c);assertFalse(FuturesClearingService.finalSettle(c,2));assertEquals(FuturesContractStatus.SETTLING,c.status());}
    @Test void lastTradingDayStillStopsTradingWhenNoSettlementPriceExists(){FuturesContract c=new FuturesContract(UUID.randomUUID(),"MISSING3","missing_future",10,0,1,2,FuturesSettlementType.CASH,FuturesContractStatus.LAST_TRADING_DAY);FuturesMarketManager.putContractDirect(c);FuturesClearingService.closeDay(1);assertEquals(FuturesContractStatus.SETTLING,c.status());}
    @Test void liveContractPreventsDeletingItsCommodity(){CommodityRegistry.register(new Commodity("protected_future","Protected",CommodityCategory.MISCELLANEOUS,100));assertTrue(FuturesMarketManager.createStandard("protected_future",0,1,2).success());assertFalse(CommodityRegistry.removeCommodity("protected_future"));}
    @Test void lastTradingDayAllowsClosingButRejectsNewRisk(){var r=FuturesMarketManager.createStandard("life_future",0,2,3);FuturesContract c=FuturesMarketManager.contract(r.id());FuturesMarketManager.place(a,c.id(),FuturesOrderSide.BUY,100,1);FuturesMarketManager.place(b,c.id(),FuturesOrderSide.SELL,100,1);FuturesMarketManager.processDay(2);assertFalse(FuturesMarketManager.place(a,c.id(),FuturesOrderSide.BUY,100,1).success());assertTrue(FuturesMarketManager.place(a,c.id(),FuturesOrderSide.SELL,110,1).success());}
}
