package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.futures.FuturesMarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 换存档后同一 MC 日也要能自动挂期货合约。 */
class SimpleCenterServiceResetTest {

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    private static void seedMarket() {
        CommodityRegistry.register(
                new Commodity("listing_test", "minecraft:wheat", "Listing Test", CommodityCategory.FOOD, 100));
        NpcMarketMaker.putMarketPrice("listing_test", new MarketPrice("listing_test", 100, 0.02));
    }

    @Test
    void futuresAreListedAgainOnSameDayAfterWorldReset() {
        EconomySavedData.resetRuntimeState();
        seedMarket();
        SimpleCenterService.ensureFuturesListed(5);
        assertTrue(FuturesMarketManager.hasLiveContractForCommodity("listing_test"));

        EconomySavedData.resetRuntimeState();
        seedMarket();
        assertFalse(FuturesMarketManager.hasLiveContractForCommodity("listing_test"), "新世界还没有合约");
        SimpleCenterService.ensureFuturesListed(5);
        assertTrue(FuturesMarketManager.hasLiveContractForCommodity("listing_test"), "上一个存档同一天挂过，不该挡住新存档");
    }
}
