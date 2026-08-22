package finance.market;

import finance.account.AccountManager;
import finance.commodity.*;
import finance.contract.ContractManager;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketOpportunityServiceTest {
    @BeforeEach void setup(){ContractManager.clearDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();CommodityRegistry.register(new Commodity("opportunity_iron","minecraft:iron_ingot","Iron",CommodityCategory.RAW_MATERIALS,10));NpcMarketMaker.getMarketPrice("opportunity_iron");AccountManager.getOrCreateSystemAccount(NpcMarketMaker.NPC_UUID).setBalance(10_000);ContractManager.createNpcProcurement("opportunity_iron",5,500,1,4);MarketOpportunityService.invalidate();}
    @AfterEach void cleanup(){ContractManager.clearDirect();AccountManager.clearAccountsDirect();NpcMarketMaker.clearMarketPrices();CommodityRegistry.removeCommodity("opportunity_iron");MarketOpportunityService.invalidate();}
    @Test void summaryShowsOnlyPublicOpportunityAndPlayerDeliverability(){var none=MarketOpportunityService.summary(1,id->0);assertEquals("opportunity_iron",none.bestContractCommodity());assertTrue(none.deliverableCommodity().isBlank());var ready=MarketOpportunityService.summary(1,id->5);assertEquals("opportunity_iron",ready.deliverableCommodity());assertEquals(500,ready.deliverableReward());}
}
