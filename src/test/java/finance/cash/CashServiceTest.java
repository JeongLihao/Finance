package finance.cash;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.network.PlayerServicesResponsePacket;
import finance.playerservice.PlayerServicesService;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 实体现金：账户 ⇄ 现金 1:1、找零、绿宝石浮动汇率（越卖越便宜、越买越贵、每天回落）。 */
class CashServiceTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-00000000ca51");
    private MemoryPurse purse;

    /** 内存背包：每种面额一个计数。 */
    static final class MemoryPurse implements CashPurse {
        final Map<Denomination, Long> cash = new EnumMap<>(Denomination.class);
        long emeralds;

        @Override
        public long count(Denomination d) {
            return cash.getOrDefault(d, 0L);
        }

        @Override
        public boolean remove(Denomination d, long n) {
            if (n < 0 || count(d) < n) return false;
            cash.put(d, count(d) - n);
            return true;
        }

        @Override
        public void give(Denomination d, long n) {
            cash.put(d, count(d) + n);
        }

        @Override
        public long emeralds() {
            return emeralds;
        }

        @Override
        public boolean removeEmeralds(long n) {
            if (emeralds < n) return false;
            emeralds -= n;
            return true;
        }

        @Override
        public void giveEmeralds(long n) {
            emeralds += n;
        }
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

    private long balance() {
        return AccountManager.getBalance(player);
    }

    @Test
    void withdrawPaysLargestNotesFirstAndDebitsAccount() {
        var r = CashService.withdraw(player, purse, 365);
        assertTrue(r.success(), r.message());
        assertEquals(1000 - 365, balance());
        assertEquals(3, purse.count(Denomination.POUND));
        assertEquals(6, purse.count(Denomination.TEN_PENCE));
        assertEquals(5, purse.count(Denomination.PENNY));
        assertEquals(365, purse.cashTotal());
    }

    @Test
    void withdrawRejectsOverdraftAndOversizeWithoutSideEffects() {
        assertFalse(CashService.withdraw(player, purse, 1001).success());
        assertFalse(CashService.withdraw(player, purse, 0).success());
        AccountManager.deposit(player, CashService.MAX_WITHDRAW * 2);
        assertFalse(CashService.withdraw(player, purse, CashService.MAX_WITHDRAW + 1)
                .success());
        assertEquals(0, purse.cashTotal());
        assertEquals(1000 + CashService.MAX_WITHDRAW * 2, balance());
    }

    @Test
    void depositAllAndRoundTripConservesMoney() {
        assertTrue(CashService.withdraw(player, purse, 777).success());
        var r = CashService.deposit(player, purse, 0);
        assertTrue(r.success(), r.message());
        assertEquals(1000, balance());
        assertEquals(0, purse.cashTotal());
    }

    @Test
    void partialDepositBreaksANoteAndGivesChange() {
        purse.give(Denomination.POUND, 2);
        var r = CashService.deposit(player, purse, 35);
        assertTrue(r.success(), r.message());
        assertEquals(1035, balance());
        assertEquals(165, purse.cashTotal(), "200 现金存 35，剩 165");
        assertEquals(1, purse.count(Denomination.POUND));
        assertEquals(6, purse.count(Denomination.TEN_PENCE));
        assertEquals(5, purse.count(Denomination.PENNY));
    }

    @Test
    void depositMoreThanCarriedFails() {
        purse.give(Denomination.TEN_PENCE, 1);
        assertFalse(CashService.deposit(player, purse, 11).success());
        assertFalse(CashService.deposit(player, new MemoryPurse(), 0).success(), "身上没钱");
        assertEquals(10, purse.cashTotal());
        assertEquals(1000, balance());
    }

    @Test
    void sellingEmeraldsPaysCashNotAccountAndLowersThePrice() {
        purse.emeralds = 10;
        long before = CashService.emeraldToCash();
        var r = CashService.sellEmeralds(player, purse, 4, 1);
        assertTrue(r.success(), r.message());
        assertEquals(6, purse.emeralds);
        long after = CashService.emeraldToCash();
        assertTrue(after < before, "卖了绿宝石，收购价下跌：" + before + " → " + after);
        assertTrue(purse.cashTotal() <= 4 * before && purse.cashTotal() >= 4 * after, "逐颗按下跌后的价格结算");
        assertEquals(1000, balance(), "现金进背包，不进账户");
    }

    @Test
    void buyingRaisesThePrice() {
        purse.give(Denomination.POUND, 100);
        long before = CashService.cashToEmerald();
        assertTrue(CashService.buyEmeralds(player, purse, 10, 1).success());
        assertTrue(CashService.cashToEmerald() > before, "买走绿宝石，卖价上涨");
        assertEquals(10, purse.emeralds);
    }

    @Test
    void roundTripAlwaysLoses() {
        for (long n : new long[] {1, 8, 64}) {
            EconomySavedData.resetRuntimeState();
            MemoryPurse p = new MemoryPurse();
            p.emeralds = n;
            assertTrue(CashService.sellEmeralds(player, p, n, 1).success());
            long proceeds = p.cashTotal();
            p.give(Denomination.POUND, 1_000);
            long cash = p.cashTotal();
            assertTrue(CashService.buyEmeralds(player, p, n, 1).success());
            long cost = cash - p.cashTotal();
            assertTrue(cost > proceeds, n + " 颗：卖得 " + proceeds + "，买回要 " + cost);
        }
        assertTrue(CashService.cashToEmerald() > CashService.emeraldToCash());
    }

    @Test
    void deviationRecoversEachDay() {
        long base = CashService.quote(purse, 1).baseBid();
        purse.emeralds = 128;
        assertTrue(CashService.sellEmeralds(player, purse, 64, 1).success());
        assertTrue(CashService.sellEmeralds(player, purse, 64, 1).success(), "没有每日额度");
        long day1 = -CashService.quote(purse, 1).deviationPercent();
        long day2 = -CashService.quote(purse, 2).deviationPercent();
        assertTrue(day1 > 50, "卖了 128 颗，收购价大跌：-" + day1 + "%");
        assertTrue(day2 < day1 && day2 > day1 / 2, "过一天往回落一部分：-" + day1 + "% → -" + day2 + "%");
        assertEquals(base, CashService.quote(purse, 200).emeraldToCash(), "很久以后回到基准价");
    }

    @Test
    void villagerBreadArbitrageClosesItself() {
        // 农民 1 绿宝石卖 6 个面包，面包基准价 20：6 个值 120 便士。低价买绿宝石去换面包有利可图，但越买越贵。
        purse.give(Denomination.POUND, 500);
        assertTrue(CashService.cashToEmerald() < 120, "一开始有套利空间");
        long bought = 0;
        while (CashService.cashToEmerald() < 120 && bought < 1_000) {
            assertTrue(CashService.buyEmeralds(player, purse, 8, 1).success());
            bought += 8;
        }
        assertTrue(bought < 200, "买到 " + bought + " 颗时，绿宝石已经比 6 个面包还贵，套利自己关上");
    }

    @Test
    void pressureSurvivesSaveAndLoad() {
        purse.emeralds = 20;
        assertTrue(CashService.sellEmeralds(player, purse, 20, 5).success());
        double p = CashService.emeraldPressure();
        long price = CashService.emeraldToCash();
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        finance.data.serializer.CentralBankDataSerializer.save(root);
        EconomySavedData.resetRuntimeState();
        assertEquals(0, CashService.emeraldPressure());
        finance.data.serializer.CentralBankDataSerializer.load(root);
        assertEquals(p, CashService.emeraldPressure(), 1e-9);
        assertEquals(5, CashService.emeraldPressureDay());
        assertEquals(price, CashService.emeraldToCash());
    }

    @Test
    void buyNeedsCashOnHand() {
        var r = CashService.buyEmeralds(player, purse, 1, 1);
        assertFalse(r.success());
        assertTrue(r.message().contains("取现"), r.message());
        assertEquals(1000, balance(), "不会从账户直接扣");
    }

    @Test
    void pausedAccountModuleBlocksEverything() {
        ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.ACCOUNT, ModuleRunState.READ_ONLY, "test", 1);
        assertFalse(CashService.withdraw(player, purse, 10).success());
        assertEquals(1000, balance());
    }

    @Test
    void playerServicesSnapshotCarriesCashPageThroughTheWire() {
        purse.emeralds = 3;
        var r = PlayerServicesService.act(
                null, purse, player, PlayerServicesService.Action.CASH_WITHDRAW, "", 250, 0, 0, 0, 1);
        assertTrue(r.success(), r.message());
        assertTrue(PlayerServicesService.act(
                        null, purse, player, PlayerServicesService.Action.EMERALD_SELL, "", 2, 0, 0, 0, 1)
                .success());
        PlayerServicesResponsePacket p = PlayerServicesService.snapshot(7, player, purse, 1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerServicesResponsePacket.encode(p, buf);
        var c = PlayerServicesResponsePacket.decode(buf).cash();
        assertEquals(750, c.balance());
        assertTrue(c.cashOnHand() > 250, "卖绿宝石的钱进了背包");
        assertEquals(1, c.emeralds());
        assertTrue(c.deviationPercent() < 0, "卖过绿宝石，汇率低于基准");
        assertEquals(c.baseBid(), CashService.quote(purse, 1).baseBid());
        assertTrue(c.trendText().contains("回落"), c.trendText());
        assertTrue(c.enabled());
    }

    @Test
    void splitUsesFewestPieces() {
        assertEquals(Map.of(Denomination.POUND, 12L, Denomination.PENNY, 3L), CashService.split(1203));
        assertTrue(CashService.split(0).isEmpty());
    }
}
