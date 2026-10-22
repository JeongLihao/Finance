package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.market.liquidity.MarketMakerQuotes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 单人游戏里挂单也要能成交：市场报价到了玩家定的价，就由市场吃下挂单。 */
class MarketOrderFillerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(
                new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 10));
        NpcMarketMaker.putMarketPrice("iron", new MarketPrice("iron", 10, 0.05));
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(100_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", 1_000);
    }

    private static long bid() {
        return MarketMakerQuotes.getQuote(MarketMakerQuotes.MARKET_COMMODITY, "iron")
                .bid();
    }

    private static long ask() {
        return MarketMakerQuotes.getQuote(MarketMakerQuotes.MARKET_COMMODITY, "iron")
                .ask();
    }

    @Test
    void sellOrderAtOrBelowTheMarketBidIsFilledAtTheBid() {
        CommodityInventoryManager.setCommodity(PLAYER, "iron", 10);
        long limit = bid();
        assertTrue(MarketManager.placeOrder(new Order(PLAYER, "iron", OrderType.SELL, limit, 10)));
        long before = AccountManager.getBalance(PLAYER);

        assertEquals(1, MarketOrderFiller.fillCrossedOrders(null));

        assertTrue(MarketManager.getOrders().isEmpty(), "成交后挂单消失");
        long earned = AccountManager.getBalance(PLAYER) - before;
        assertTrue(earned >= limit * 10L, "不低于挂单价成交，实际得到 " + earned + "，挂单价 " + limit);
    }

    @Test
    void sellOrderAboveTheMarketWaits() {
        CommodityInventoryManager.setCommodity(PLAYER, "iron", 10);
        assertTrue(MarketManager.placeOrder(new Order(PLAYER, "iron", OrderType.SELL, bid() + 50, 10)));

        assertEquals(0, MarketOrderFiller.fillCrossedOrders(null));

        assertEquals(1, MarketManager.getOrders().size());
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(PLAYER, "iron"), "货仍然冻结在挂单里");
    }

    @Test
    void buyOrderAtOrAboveTheMarketAskIsFilledAndUnusedMoneyUnfrozen() {
        AccountManager.getAccount(PLAYER).setBalance(10_000);
        long ask = ask(), limit = ask + 5;
        assertTrue(MarketManager.placeOrder(new Order(PLAYER, "iron", OrderType.BUY, limit, 4)));

        assertEquals(1, MarketOrderFiller.fillCrossedOrders(null));

        assertTrue(MarketManager.getOrders().isEmpty());
        assertEquals(4, CommodityInventoryManager.getCommodityAmount(PLAYER, "iron"), "离线时货暂存在市场账上");
        assertEquals(0, AccountManager.getAccount(PLAYER).getFrozenBalance(), "多冻结的钱要解冻");
        long spent = 10_000 - AccountManager.getBalance(PLAYER);
        assertEquals(ask * 4L, spent, "按出售价付钱，不按挂单价");
    }
}
