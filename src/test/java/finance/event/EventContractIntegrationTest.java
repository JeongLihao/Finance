package finance.event;

import finance.account.AccountManager;import finance.commodity.*;import finance.contract.*;import finance.market.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class EventContractIntegrationTest {
 @BeforeEach void setup(){ContractManager.clearDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();CommodityRegistry.register(new Commodity("relief_iron","minecraft:iron_ingot","Iron",CommodityCategory.RAW_MATERIALS,10));NpcMarketMaker.getMarketPrice("relief_iron");AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID).setBalance(100_000);}
 @AfterEach void cleanup(){ContractManager.clearDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();CommodityRegistry.removeCommodity("relief_iron");}
 @Test void sameEventDayGeneratesOneEscrowBackedContract(){long before=AccountManager.getAccounts().values().stream().mapToLong(a->a.getBalance()).sum();FinanceContract first=ContractManager.generateEventProcurement("storm","relief_iron",2);assertNotNull(first);assertNull(ContractManager.generateEventProcurement("storm","relief_iron",2));long after=AccountManager.getAccounts().values().stream().mapToLong(a->a.getBalance()).sum();assertEquals(before,after);assertEquals(first.rewardAmount(),AccountManager.getBalance(first.escrowAccountId()));}
}
