package finance.futures;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FuturesClearingServiceTest {
    private final UUID longOwner=UUID.fromString("00000000-0000-0000-0000-000000005201"),shortOwner=UUID.fromString("00000000-0000-0000-0000-000000005202");private FuturesContract contract;
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();CommodityRegistry.register(new Commodity("clear_test","Clear",CommodityCategory.RAW_MATERIALS,100));var r=FuturesMarketManager.createStandard("clear_test",0,5,6);contract=FuturesMarketManager.contract(r.id());AccountManager.deposit(longOwner,2_000);AccountManager.deposit(shortOwner,2_000);MarginManager.deposit(longOwner,1_000);MarginManager.deposit(shortOwner,1_000);FuturesMarketManager.place(longOwner,contract.id(),FuturesOrderSide.BUY,100,1);FuturesMarketManager.place(shortOwner,contract.id(),FuturesOrderSide.SELL,100,1);}
    @Test void dailyVariationIsSymmetricAndIdempotent(){long fund=FuturesClearingService.guaranteeFund();assertTrue(FuturesClearingService.settle(contract,110,1,false));assertEquals(1_100,MarginManager.account(longOwner).cashBalance());assertEquals(900,MarginManager.account(shortOwner).cashBalance());assertEquals(fund,FuturesClearingService.guaranteeFund());assertFalse(FuturesClearingService.settle(contract,110,1,false));}
    @Test void lossCapacityThenFundPaysWinnerWithoutMinting(){MarginManager.account(shortOwner).forceDebit(800);FuturesClearingService.restoreFund(1_000,0,-1);assertTrue(FuturesClearingService.settle(contract,200,1,false));assertEquals(2_000,MarginManager.account(longOwner).cashBalance());assertEquals(0,MarginManager.account(shortOwner).cashBalance());assertEquals(200,FuturesClearingService.guaranteeFund());var record=FuturesClearingService.history().get(0);assertEquals(800,record.guaranteeFundUsed());assertEquals(0,record.profitHaircut());}
    @Test void fundShortageHaircutsWinnerInsteadOfMinting(){MarginManager.account(shortOwner).forceDebit(800);FuturesClearingService.restoreFund(100,0,-1);long totalBefore=MarginManager.account(longOwner).cashBalance()+MarginManager.account(shortOwner).cashBalance()+FuturesClearingService.guaranteeFund();assertTrue(FuturesClearingService.settle(contract,200,1,false));long totalAfter=MarginManager.account(longOwner).cashBalance()+MarginManager.account(shortOwner).cashBalance()+FuturesClearingService.guaranteeFund();assertEquals(totalBefore,totalAfter);assertEquals(700,FuturesClearingService.history().get(0).profitHaircut());}
    @Test void finalCashSettlementClosesPositionsAndCannotRepeat(){contract.setStatus(FuturesContractStatus.SETTLING);assertTrue(FuturesClearingService.finalSettle(contract,6));assertEquals(FuturesContractStatus.SETTLED,contract.status());assertNull(MarginManager.findPosition(longOwner,contract.id()));assertNull(MarginManager.findPosition(shortOwner,contract.id()));assertFalse(FuturesClearingService.finalSettle(contract,6));}
}
