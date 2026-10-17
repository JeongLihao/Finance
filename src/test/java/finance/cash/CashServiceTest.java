package finance.cash;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 实体现金：账户 ⇄ 现金 1:1、找零、绿宝石兑换的点差与每日额度。 */
class CashServiceTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-00000000ca51");
    private MemoryPurse purse;

    /** 内存背包：每种面额一个计数。 */
    static final class MemoryPurse implements CashPurse {
        final Map<Denomination, Long> cash = new EnumMap<>(Denomination.class);
        long emeralds;
        final Map<String, long[]> quota = new HashMap<>();

        @Override public long count(Denomination d) { return cash.getOrDefault(d, 0L); }

        @Override public boolean remove(Denomination d, long n) {
            if (n < 0 || count(d) < n) return false;
            cash.put(d, count(d) - n);
            return true;
        }

        @Override public void give(Denomination d, long n) { cash.put(d, count(d) + n); }

        @Override public long emeralds() { return emeralds; }

        @Override public boolean removeEmeralds(long n) {
            if (emeralds < n) return false;
            emeralds -= n;
            return true;
        }

        @Override public void giveEmeralds(long n) { emeralds += n; }

        @Override public long quotaUsed(String key, long day) {
            long[] v = quota.get(key);
            return v != null && v[0] == day ? v[1] : 0;
        }

        @Override public void setQuotaUsed(String key, long day, long used) { quota.put(key, new long[]{day, used}); }
    }

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
        purse = new MemoryPurse();
        AccountManager.getAccount(player); // 新账户自带 1000
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        ModuleHealthRegistry.clear();
    }

    private long balance() { return AccountManager.getBalance(player); }

    @Test void withdrawPaysLargestNotesFirstAndDebitsAccount() {
        var r = CashService.withdraw(player, purse, 365);
        assertTrue(r.success(), r.message());
        assertEquals(1000 - 365, balance());
        assertEquals(3, purse.count(Denomination.POUND));
        assertEquals(6, purse.count(Denomination.TEN_PENCE));
        assertEquals(5, purse.count(Denomination.PENNY));
        assertEquals(365, purse.cashTotal());
    }

    @Test void withdrawRejectsOverdraftAndOversizeWithoutSideEffects() {
        assertFalse(CashService.withdraw(player, purse, 1001).success());
        assertFalse(CashService.withdraw(player, purse, 0).success());
        AccountManager.deposit(player, CashService.MAX_WITHDRAW * 2);
        assertFalse(CashService.withdraw(player, purse, CashService.MAX_WITHDRAW + 1).success());
        assertEquals(0, purse.cashTotal());
        assertEquals(1000 + CashService.MAX_WITHDRAW * 2, balance());
    }

    @Test void depositAllAndRoundTripConservesMoney() {
        assertTrue(CashService.withdraw(player, purse, 777).success());
        var r = CashService.deposit(player, purse, 0);
        assertTrue(r.success(), r.message());
        assertEquals(1000, balance());
        assertEquals(0, purse.cashTotal());
    }

    @Test void partialDepositBreaksANoteAndGivesChange() {
        purse.give(Denomination.POUND, 2);
        var r = CashService.deposit(player, purse, 35);
        assertTrue(r.success(), r.message());
        assertEquals(1035, balance());
        assertEquals(165, purse.cashTotal(), "200 现金存 35，剩 165");
        assertEquals(1, purse.count(Denomination.POUND));
        assertEquals(6, purse.count(Denomination.TEN_PENCE));
        assertEquals(5, purse.count(Denomination.PENNY));
    }

    @Test void depositMoreThanCarriedFails() {
        purse.give(Denomination.TEN_PENCE, 1);
        assertFalse(CashService.deposit(player, purse, 11).success());
        assertFalse(CashService.deposit(player, new MemoryPurse(), 0).success(), "身上没钱");
        assertEquals(10, purse.cashTotal());
        assertEquals(1000, balance());
    }

    @Test void sellingEmeraldsPaysCashNotAccount() {
        purse.emeralds = 10;
        var r = CashService.sellEmeralds(player, purse, 4, 1);
        assertTrue(r.success(), r.message());
        assertEquals(6, purse.emeralds);
        assertEquals(4 * CashService.emeraldToCash(), purse.cashTotal());
        assertEquals(1000, balance());
    }

    @Test void buyingEmeraldsCostsMoreThanSellingSoRoundTripLoses() {
        assertTrue(CashService.cashToEmerald() >= CashService.emeraldToCash());
        purse.emeralds = 8;
        assertTrue(CashService.sellEmeralds(player, purse, 8, 1).success());
        long cash = purse.cashTotal();
        var r = CashService.buyEmeralds(player, purse, 8, 1);
        if (CashService.cashToEmerald() > CashService.emeraldToCash()) {
            assertFalse(r.success(), "卖 8 颗的钱买不回 8 颗");
            assertEquals(cash, purse.cashTotal());
            assertEquals(0, purse.emeralds);
        }
        long canBuy = cash / CashService.cashToEmerald();
        assertTrue(CashService.buyEmeralds(player, purse, canBuy, 1).success());
        assertEquals(canBuy, purse.emeralds);
        assertEquals(cash - canBuy * CashService.cashToEmerald(), purse.cashTotal(), "找零正确");
    }

    @Test void dailyQuotaLimitsSellingAndResetsNextDay() {
        long limit = CashService.quote(purse, 1).sellLimit();
        purse.emeralds = limit * 2 + 5;
        long sold = 0;
        while (sold < limit) {
            long n = Math.min(CashService.MAX_EMERALDS_PER_ACTION, limit - sold);
            assertTrue(CashService.sellEmeralds(player, purse, n, 1).success());
            sold += n;
        }
        assertEquals(0, CashService.quote(purse, 1).sellLeft());
        assertFalse(CashService.sellEmeralds(player, purse, 1, 1).success(), "当天额度用完");
        assertEquals(limit, CashService.quote(purse, 2).sellLeft(), "第二天重置");
        assertTrue(CashService.sellEmeralds(player, purse, 1, 2).success());
    }

    @Test void buyNeedsCashOnHand() {
        var r = CashService.buyEmeralds(player, purse, 1, 1);
        assertFalse(r.success());
        assertTrue(r.message().contains("取现"), r.message());
        assertEquals(1000, balance(), "不会从账户直接扣");
    }

    @Test void pausedAccountModuleBlocksEverything() {
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.ACCOUNT, ModuleRunState.READ_ONLY, "test", 1);
        assertFalse(CashService.withdraw(player, purse, 10).success());
        assertEquals(1000, balance());
    }

    @Test void playerServicesSnapshotCarriesCashPageThroughTheWire() {
        purse.emeralds = 3;
        var r = PlayerServicesService.act(null, purse, player, PlayerServicesService.Action.CASH_WITHDRAW, "", 250, 0, 0, 0, 1);
        assertTrue(r.success(), r.message());
        assertTrue(PlayerServicesService.act(null, purse, player, PlayerServicesService.Action.EMERALD_SELL, "", 2, 0, 0, 0, 1).success());
        PlayerServicesResponsePacket p = PlayerServicesService.snapshot(7, player, purse, 1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerServicesResponsePacket.encode(p, buf);
        var c = PlayerServicesResponsePacket.decode(buf).cash();
        assertEquals(750, c.balance());
        assertEquals(250 + 2 * CashService.emeraldToCash(), c.cashOnHand());
        assertEquals(1, c.emeralds());
        assertEquals(c.sellLimit() - 2, c.sellLeft());
        assertTrue(c.enabled());
    }

    @Test void splitUsesFewestPieces() {
        assertEquals(Map.of(Denomination.POUND, 12L, Denomination.PENNY, 3L), CashService.split(1203));
        assertTrue(CashService.split(0).isEmpty());
    }
}
