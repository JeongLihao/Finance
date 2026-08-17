package finance.data;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.futures.*;
import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FuturesPersistenceTest {
    private final UUID longOwner=UUID.fromString("00000000-0000-0000-0000-000000005301"),shortOwner=UUID.fromString("00000000-0000-0000-0000-000000005302");
    @BeforeEach void setup(){EconomySavedData.resetRuntimeState();CommodityRegistry.register(new Commodity("persist_future","Persist",CommodityCategory.RAW_MATERIALS,100));}
    @AfterEach void clear(){EconomySavedData.resetRuntimeState();}
    @Test void contractsMarginPositionsOrdersAndClearingSurviveRestart(){AccountManager.deposit(longOwner,3_000);AccountManager.deposit(shortOwner,3_000);MarginManager.deposit(longOwner,2_000);MarginManager.deposit(shortOwner,2_000);var created=FuturesMarketManager.createStandard("persist_future",0,10,11);UUID contract=created.id();FuturesMarketManager.place(longOwner,contract,FuturesOrderSide.BUY,100,2);FuturesMarketManager.place(shortOwner,contract,FuturesOrderSide.SELL,100,2);CandlestickService.recordTrade(MarketInstrumentType.FUTURES,contract.toString(),1,110,10);FuturesClearingService.closeDay(1);FuturesMarketManager.place(longOwner,contract,FuturesOrderSide.BUY,90,1);long sequence=FuturesMarketManager.nextSequence(),frozen=MarginManager.account(longOwner).frozenForOrders();CompoundTag saved=new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();EconomySavedData.load(saved);
        assertNotNull(FuturesMarketManager.contract(contract));assertEquals(2,MarginManager.findPosition(longOwner,contract).signedQuantity());assertEquals(-2,MarginManager.findPosition(shortOwner,contract).signedQuantity());assertEquals(1,FuturesMarketManager.orders().size());assertEquals(frozen,MarginManager.account(longOwner).frozenForOrders());assertEquals(sequence,FuturesMarketManager.nextSequence());assertEquals(110,FuturesClearingService.lastSettlementPrice(contract));assertEquals(1,FuturesClearingService.lastSettlementDay(contract));}
    @Test void oldSaveLoadsEmptyAndCorruptRecordsAreIsolated(){assertDoesNotThrow(()->EconomySavedData.load(new CompoundTag()));assertTrue(FuturesMarketManager.contracts().isEmpty());CompoundTag root=new CompoundTag(),state=new CompoundTag();ListTag contracts=new ListTag();CompoundTag bad=new CompoundTag();bad.putUUID("Id",UUID.randomUUID());bad.putString("Code","BAD");bad.putString("Commodity","missing");bad.putLong("Size",-1);bad.putString("Status","BROKEN");bad.putString("SettlementType","CASH");contracts.add(bad);state.put("Contracts",contracts);ListTag accounts=new ListTag();CompoundTag badAccount=new CompoundTag();badAccount.putUUID("Owner",longOwner);badAccount.putLong("Cash",-1);badAccount.putString("Risk","NORMAL");accounts.add(badAccount);state.put("Accounts",accounts);root.put("Futures",state);assertDoesNotThrow(()->EconomySavedData.load(root));assertTrue(FuturesMarketManager.contracts().isEmpty());assertTrue(MarginManager.accounts().isEmpty());}
    @Test void inconsistentOrderLocksRejectOrdersAndReleasePersistedFreeze(){AccountManager.deposit(longOwner,2_000);MarginManager.deposit(longOwner,1_000);var created=FuturesMarketManager.createStandard("persist_future",0,10,11);FuturesMarketManager.place(longOwner,created.id(),FuturesOrderSide.BUY,90,1);CompoundTag saved=new EconomySavedData().save(new CompoundTag());saved.getCompound("Futures").getList("Orders",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("Reserved",1);
        EconomySavedData.resetRuntimeState();EconomySavedData.load(saved);
        assertTrue(FuturesMarketManager.orders().isEmpty());assertEquals(0,MarginManager.account(longOwner).frozenForOrders());}
}
