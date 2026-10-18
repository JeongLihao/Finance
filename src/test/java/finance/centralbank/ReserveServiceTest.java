package finance.centralbank;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 央行储备：补货慢、价格涨得上去、玩家货不补、底价收购、旧存档搬迁。 */
class ReserveServiceTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000b001");

    @BeforeEach
    void reset() {
        EconomySavedData.resetRuntimeState();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        NpcMarketMaker.clearMarketPrices();
        CommodityRegistry.register(new Commodity("ore", "minecraft:iron_ore", "矿", CommodityCategory.RAW_MATERIALS, 10).reserveTier(1));
        CommodityRegistry.register(new Commodity("gem", "minecraft:emerald", "宝石", CommodityCategory.RAW_MATERIALS, 100).reserveTier(3));
        CommodityRegistry.register(new Commodity("pie", "minecraft:pumpkin_pie", "派", CommodityCategory.FOOD, 20).reserveTier(0));
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(10_000_000);
    }

    @Test void restockIsAFewPercentOfTargetPerDay() {
        assertEquals(1_000, ReserveService.target("ore"));
        assertEquals(20, ReserveService.restock("ore"), "每天最多补目标的 2%");
        assertEquals(20, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "ore"));
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "ore", 995);
        assertEquals(5, ReserveService.restock("ore"), "补到目标为止");
        assertEquals(0, ReserveService.restock("ore"));
    }

    @Test void restockUsesTheCentralBankReserveFirst() {
        CommodityInventoryManager.setCommodity(CentralBank.UUID, "ore", 7);
        ReserveService.restock("ore");
        assertEquals(0, CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, "ore"));
        assertEquals(20, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "ore"));
    }

    @Test void playerGoodsAreNeverRestocked() {
        assertEquals(0, ReserveService.target("pie"));
        assertEquals(0, ReserveService.restock("pie"));
        assertEquals(0, ReserveService.initialStock("pie"));
    }

    @Test void higherTiersUnlockWithServerTradeVolume() {
        assertEquals(1, ReserveService.unlockedTier());
        assertEquals(0, ReserveService.restock("gem"), "第 3 档还没解锁");
        ReserveService.recordTrade(1_000_000);
        assertEquals(3, ReserveService.unlockedTier());
        assertEquals(4, ReserveService.restock("gem"), "200 件的 2%");
        assertEquals(0, ReserveService.valueToNextTier());
    }

    @Test void buyingHalfTheStockRaisesThePriceALot() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("ore");
        assertEquals(1_000, mp.getReferenceStock());
        mp.setMidPrice(1_000);
        MarketPrice big = new MarketPrice("ore", 1_000, 0.05);
        ReserveService.configure(big);
        big.setMidPrice(1_000);
        for (int i = 0; i < 40; i++) big.recomputePrice(500);
        assertTrue(big.getMidPrice() >= 1_400, "库存少一半，价格应涨四成以上，实际 " + big.getMidPrice());
    }

    @Test void sellingCheapGoodsGetsTheFloorPriceWithinTheDailyCap() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("ore");
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "ore", 50_000);
        for (int i = 0; i < 40; i++) mp.recomputePrice(50_000);
        long floor = ReserveService.floorPrice("ore");
        assertEquals(5, floor);
        assertTrue(mp.getBidPrice() < floor, "市场收购价已经跌破底价：" + mp.getBidPrice());
        CommodityInventoryManager.setCommodity(PLAYER, "ore", 10);
        long before = AccountManager.getBalance(PLAYER);
        assertEquals(10, NpcMarketMaker.npcBuyFilled(PLAYER, "ore", 10));
        assertEquals(50, AccountManager.getBalance(PLAYER) - before, "按底价 5 便士收");
        assertEquals(246, ReserveService.floorQuotaLeft("ore", finance.chart.CandlestickService.currentMcDay()));
    }

    @Test void oldSavesMoveExcessStockIntoTheReserve() {
        NpcMarketMaker.getMarketPrice("ore");
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "ore", 100_000);
        ReserveService.migrateIfNeeded();
        assertEquals(2_000, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "ore"));
        assertEquals(98_000, CommodityInventoryManager.getCommodityAmount(CentralBank.UUID, "ore"), "多出来的搬进央行，不销毁");
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "ore", 100_000);
        ReserveService.migrateIfNeeded();
        assertEquals(100_000, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, "ore"), "只搬一次");
    }

    @Test void anEmptyMarketNeverPaysMoreThanTheBasePrice() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("pie");
        for (int i = 0; i < 40; i++) mp.recomputePrice(0);
        assertTrue(mp.getAskPrice() > 20, "缺货时卖得贵：" + mp.getAskPrice());
        assertEquals(20, mp.getBidPrice(), "但收购价最多给基准价");
        CommodityInventoryManager.setCommodity(PLAYER, "pie", 64);
        long before = AccountManager.getBalance(PLAYER);
        assertEquals(64, NpcMarketMaker.npcBuyFilled(PLAYER, "pie", 64));
        assertEquals(64 * 20, AccountManager.getBalance(PLAYER) - before);
    }

    @Test void buyingEightStacksInARowDoesNotPauseTheMarket() {
        MarketPrice mp = NpcMarketMaker.getMarketPrice("ore");
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, "ore", 1_000);
        for (int i = 0; i < 40; i++) mp.recomputePrice(1_000);
        AccountManager.getAccount(PLAYER).setBalance(10_000_000);
        for (int i = 0; i < 8; i++) {
            assertEquals(64, NpcMarketMaker.npcSellFilled(PLAYER, "ore", 64), "第 " + (i + 1) + " 组");
        }
    }

    @Test void emeraldsHaveNoFloorPrice() {
        CommodityRegistry.register(new Commodity(finance.cash.CashService.EMERALD, "minecraft:emerald", "绿宝石",
                CommodityCategory.MISCELLANEOUS, 50).reserveTier(0));
        assertEquals(0, ReserveService.floorPrice(finance.cash.CashService.EMERALD));
        assertEquals(10, ReserveService.floorPrice("pie"));
    }

    @Test void legacyCommoditiesKeepTheOldRules() {
        CommodityRegistry.register(new Commodity("old", "minecraft:stick", "旧", CommodityCategory.MISCELLANEOUS, 10));
        assertFalse(ReserveService.managed("old"));
        assertEquals(MarketPrice.REFERENCE_STOCK, ReserveService.target("old"));
        assertEquals(MarketPrice.REFERENCE_STOCK, NpcMarketMaker.getMarketPrice("old").getReferenceStock());
        assertEquals(0, ReserveService.floorPrice("old"));
    }
}
