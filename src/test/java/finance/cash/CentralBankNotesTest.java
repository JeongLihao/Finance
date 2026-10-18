package finance.cash;

import finance.account.AccountManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.market.NpcMarketMaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 央行记下发出去的钞票；绿宝石注册成商品后按市场价浮动，卖得越多越便宜。 */
class CentralBankNotesTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-00000000ca52");
    private CashServiceTest.MemoryPurse purse;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        CommodityInventoryManager.clearInventoriesDirect();
        CommodityRegistry.resetToDefaults();
        NpcMarketMaker.clearMarketPrices();
        purse = new CashServiceTest.MemoryPurse();
        AccountManager.getAccount(player);
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        CommodityRegistry.resetToDefaults();
    }

    @Test void notesOutstandingFollowWithdrawAndDeposit() {
        assertEquals(0, CashService.notesOutstanding());
        assertTrue(CashService.withdraw(player, purse, 300).success());
        assertEquals(300, CashService.notesOutstanding());
        assertTrue(CashService.deposit(player, purse, 100).success());
        assertEquals(200, CashService.notesOutstanding());
        var m = finance.centralbank.MoneySupplyService.summary();
        assertEquals(200, m.notes());
        assertEquals(m.accounts() + m.companies() + m.notes(), m.circulating());
    }

    @Test void emeraldPriceFloatsWithMarketStock() {
        CommodityRegistry.register(new Commodity(CashService.EMERALD, "minecraft:emerald", "绿宝石", CommodityCategory.MISCELLANEOUS, 50).reserveTier(0));
        AccountManager.getAccount(NpcMarketMaker.NPC_UUID).setBalance(10_000_000);
        CommodityInventoryManager.setCommodity(NpcMarketMaker.NPC_UUID, CashService.EMERALD, 512);
        var mp = NpcMarketMaker.getMarketPrice(CashService.EMERALD);
        for (int i = 0; i < 40; i++) mp.recomputePrice(512);
        long before = CashService.emeraldToCash();
        purse.giveEmeralds(64);
        assertTrue(CashService.sellEmeralds(player, purse, 16, 1).success());
        assertEquals(528, CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, CashService.EMERALD), "卖出的绿宝石进了市场库存");
        for (int i = 0; i < 40; i++) mp.recomputePrice(4_000);
        assertTrue(CashService.emeraldToCash() < before, "市场里绿宝石多了，收购价就跌：" + before + " → " + CashService.emeraldToCash());
        assertTrue(CashService.cashToEmerald() > CashService.emeraldToCash(), "买价总高于卖价，不能来回套利");
        assertTrue(CashService.notesOutstanding() > 0, "卖绿宝石拿到的是央行钞票");
    }

    @Test void cannotBuyEmeraldsTheMarketDoesNotHave() {
        CommodityRegistry.register(new Commodity(CashService.EMERALD, "minecraft:emerald", "绿宝石", CommodityCategory.MISCELLANEOUS, 50).reserveTier(0));
        purse.give(Denomination.POUND, 100);
        var r = CashService.buyEmeralds(player, purse, 1, 1);
        assertFalse(r.success());
        assertEquals(0, purse.emeralds());
    }
}
