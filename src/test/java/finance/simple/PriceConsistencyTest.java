package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 界面上显示的买卖价，必须就是下一笔实际成交的价。 */
class PriceConsistencyTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000a002");

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        CommodityRegistry.register(
                new Commodity("iron", "minecraft:iron_ingot", "Iron", CommodityCategory.RAW_MATERIALS, 10));
        NpcMarketMaker.putMarketPrice("iron", new MarketPrice("iron", 1_000, 0.05));
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(10_000_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "iron", 1_000);
    }

    @Test
    void shownBidIsWhatThePlayerGets() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("iron");
        long shown = mp.getBidPrice();
        CommodityInventoryManager.setCommodity(PLAYER, "iron", 1);
        long before = AccountManager.getBalance(PLAYER);
        assertEquals(1, NpcMarketMaker.npcBuyFilled(PLAYER, "iron", 1));
        assertEquals(shown, AccountManager.getBalance(PLAYER) - before);
    }

    @Test
    void shownAskIsWhatThePlayerPays() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("iron");
        long shown = mp.getAskPrice();
        AccountManager.getAccount(PLAYER).setBalance(1_000_000);
        assertEquals(1, NpcMarketMaker.npcSellFilled(PLAYER, "iron", 1));
        assertEquals(shown, 1_000_000 - AccountManager.getBalance(PLAYER));
    }

    @Test
    void periodicSnapshotsOnlyRecordChanges() {
        MarketPrice mp = new MarketPrice("x", 100, 0.05);
        mp.recordPeriodicSnapshot();
        mp.recordPeriodicSnapshot();
        mp.recordPeriodicSnapshot();
        assertEquals(1, mp.getSnapshots().size(), "价格没变就不加点");
        mp.setMidPrice(120);
        mp.recordPeriodicSnapshot();
        assertEquals(2, mp.getSnapshots().size());
    }
}
