package finance.fixedincome;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.policy.MonetaryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TreasuryBondManagerTest {
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000004401");
    private final UUID player2 = UUID.fromString("00000000-0000-0000-0000-000000004402");
    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); AccountManager.deposit(player, 5_000_000); }

    @Test void termsWhitelistedAndYieldCurveOrdered() {
        assertTrue(YieldCurveService.treasuryYieldBasisPoints(30) > 0);
        assertTrue(YieldCurveService.treasuryYieldBasisPoints(30) < YieldCurveService.treasuryYieldBasisPoints(90));
        assertTrue(YieldCurveService.treasuryYieldBasisPoints(90) < YieldCurveService.treasuryYieldBasisPoints(180));
        assertEquals(-1, YieldCurveService.treasuryYieldBasisPoints(7));
        assertFalse(TreasuryBondManager.subscribe(player, 7, 10_000, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 365, 10_000, 0).success());
    }

    @Test void subscribeValidation() {
        assertFalse(TreasuryBondManager.subscribe(null, 30, 10_000, 0).success());
        assertFalse(TreasuryBondManager.subscribe(CentralBank.UUID, 30, 10_000, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 30, 0, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 30, 999, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 30, 10_000, -1).success());
        assertTrue(TreasuryBondManager.subscribe(player, 30, 1_000, 0).success());
    }

    @Test void rateLocksAtSubscription() {
        var result = TreasuryBondManager.subscribe(player, 90, 10_000, 2);
        TreasuryBond bond = TreasuryBondManager.bonds().get(result.bondId());
        int locked = bond.annualRateBasisPoints();
        MonetaryPolicyService.restore(2_000, java.util.List.of());
        assertEquals(locked, bond.annualRateBasisPoints());
        assertTrue(YieldCurveService.treasuryYieldBasisPoints(90) > locked);
    }

    @Test void maturityPaysPrincipalPlusInterestOnce() {
        var result = TreasuryBondManager.subscribe(player, 30, 100_000, 0);
        TreasuryBond bond = TreasuryBondManager.bonds().get(result.bondId());
        long due = TreasuryBondManager.expectedMaturityValue(bond, player);
        // 100000 * 650bps * 30 / (365*10000) = 534 (integer division)
        assertEquals(100_534, due);
        long before = AccountManager.getBalance(player);
        TreasuryBondManager.processDay(29);
        assertEquals(TreasuryBondStatus.ACTIVE, bond.status());
        TreasuryBondManager.processDay(30);
        assertEquals(TreasuryBondStatus.MATURED, bond.status());
        assertEquals(before + due, AccountManager.getBalance(player));
        TreasuryBondManager.processDay(31);
        assertEquals(before + due, AccountManager.getBalance(player));
        assertEquals(1, AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.TREASURY_BOND_MATURITY).count());
    }

    @Test void earlyRedeemReturnsPrincipalOnly() {
        var result = TreasuryBondManager.subscribe(player, 180, 50_000, 0);
        long before = AccountManager.getBalance(player);
        var redeem = TreasuryBondManager.earlyRedeem(player, result.bondId(), 10);
        assertTrue(redeem.success());
        assertEquals(before + 50_000, AccountManager.getBalance(player));
        // 债券无持仓后被移除，二次赎回失败
        assertFalse(TreasuryBondManager.earlyRedeem(player, result.bondId(), 11).success());
        assertEquals(1, AccountManager.getTransactions().stream()
                .filter(t -> t.getType() == TransactionType.TREASURY_BOND_EARLY_REDEEM).count());
    }

    @Test void perPlayerCapEnforced() {
        // 默认单人上限 2,000,000
        assertTrue(TreasuryBondManager.subscribe(player, 30, 1_500_000, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 90, 600_000, 0).success());
        assertTrue(TreasuryBondManager.subscribe(player, 90, 500_000, 0).success());
    }

    @Test void perTermIssuanceCapEnforced() {
        // 默认每期限档上限 10,000,000；用 5 个不同玩家绕开单人上限
        for (int i = 0; i < 5; i++) {
            UUID p = UUID.fromString("00000000-0000-0000-0000-0000000045" + String.format("%02d", 10 + i));
            AccountManager.deposit(p, 3_000_000);
            assertTrue(TreasuryBondManager.subscribe(p, 30, 2_000_000, i).success());
        }
        assertEquals(10_000_000, TreasuryBondManager.outstandingByTerm(30));
        assertFalse(TreasuryBondManager.subscribe(player2, 30, 1_000, 5).success());
        // 其他期限档不受影响
        AccountManager.deposit(player2, 3_000_000);
        assertTrue(TreasuryBondManager.subscribe(player2, 90, 1_000, 5).success());
    }

    // ---- 攻击视角 ----

    @Test void attack_hikeDoesNotRetroactivelyEnrich() {
        var result = TreasuryBondManager.subscribe(player, 180, 100_000, 0);
        TreasuryBond bond = TreasuryBondManager.bonds().get(result.bondId());
        long dueBefore = TreasuryBondManager.expectedMaturityValue(bond, player);
        MonetaryPolicyService.restore(2_000, java.util.List.of()); // 加息到 20%
        assertEquals(dueBefore, TreasuryBondManager.expectedMaturityValue(bond, player));
        long before = AccountManager.getBalance(player);
        TreasuryBondManager.processDay(180);
        assertEquals(before + dueBefore, AccountManager.getBalance(player));
    }

    @Test void attack_earlyRedeemCycleYieldsNothing() {
        long before = AccountManager.getBalance(player);
        for (int i = 0; i < 3; i++) {
            var r = TreasuryBondManager.subscribe(player, 30, 100_000, i);
            assertTrue(r.success());
            assertTrue(TreasuryBondManager.earlyRedeem(player, r.bondId(), i).success());
        }
        assertEquals(before, AccountManager.getBalance(player));
    }

    @Test void attack_subscribeRedeemSubscribeNoDoubleCount() {
        var r1 = TreasuryBondManager.subscribe(player, 30, 1_000_000, 0);
        assertTrue(TreasuryBondManager.earlyRedeem(player, r1.bondId(), 1).success());
        // 赎回后额度释放，可重新认购
        assertTrue(TreasuryBondManager.subscribe(player, 30, 1_000_000, 2).success());
        assertEquals(1_000_000, TreasuryBondManager.activePrincipalByPlayer(player));
    }

    // ---- bug-sweep 回归 ----

    @Test void fix_p1_8_maturedBondRemovedFromRegistry() {
        var result = TreasuryBondManager.subscribe(player, 30, 100_000, 0);
        UUID bondId = result.bondId();
        assertTrue(TreasuryBondManager.bonds().containsKey(bondId));
        TreasuryBondManager.processDay(30);
        // 兑付完成且持仓清空后从注册表移除，MATURED 不再永久驻留
        assertFalse(TreasuryBondManager.bonds().containsKey(bondId));
        // 次日不再重复兑付
        long balance = AccountManager.getBalance(player);
        TreasuryBondManager.processDay(31);
        assertEquals(balance, AccountManager.getBalance(player));
    }

    @Test void fix_settleSkipsUnpayableHolder() {
        // 同日同期限认购并入同一只债券
        var r1 = TreasuryBondManager.subscribe(player, 30, 100_000, 0);
        UUID bondId = r1.bondId();
        // player2 账户塞满至 balance+应付额 溢出，使其到期兑付 canDeposit 失败
        // （认购扣 10_000 后余额 = Long.MAX_VALUE - 10_052，应付 10_053 恰好溢出）
        AccountManager.deposit(player2, Long.MAX_VALUE - 1_052);
        assertTrue(TreasuryBondManager.subscribe(player2, 30, 10_000, 0).success());
        assertEquals(bondId, TreasuryBondManager.bonds().keySet().stream()
                .filter(id -> TreasuryBondManager.bonds().get(id).principalByPlayer().containsKey(player2))
                .findFirst().orElseThrow());

        TreasuryBond bond = TreasuryBondManager.bonds().get(bondId);
        long before1 = AccountManager.getBalance(player);
        long due1 = TreasuryBondManager.expectedMaturityValue(bond, player);
        TreasuryBondManager.processDay(30);
        // player 正常兑付
        assertEquals(before1 + due1, AccountManager.getBalance(player));
        // player2 被跳过：债券保持 ACTIVE 且其持仓保留，不拖死整只债券
        assertTrue(TreasuryBondManager.bonds().containsKey(bondId));
        assertEquals(TreasuryBondStatus.ACTIVE, bond.status());
        assertEquals(10_000, bond.principalByPlayer().get(player2));

        // 次日 player2 腾出空间后可继续兑付，持仓清空后债券移除
        AccountManager.withdraw(player2, 1_000_000);
        long before2 = AccountManager.getBalance(player2);
        long due2 = TreasuryBondManager.expectedMaturityValue(bond, player2);
        TreasuryBondManager.processDay(31);
        assertEquals(before2 + due2, AccountManager.getBalance(player2));
        assertFalse(TreasuryBondManager.bonds().containsKey(bondId));
    }

    @Test void attack_overflowInputsRejected() {
        assertFalse(TreasuryBondManager.subscribe(player, 30, Long.MAX_VALUE, 0).success());
        assertFalse(TreasuryBondManager.subscribe(player, 30, 10_000, Long.MAX_VALUE - 1).success());
        assertFalse(TreasuryBondManager.earlyRedeem(player, null, 0).success());
        assertFalse(TreasuryBondManager.earlyRedeem(null, UUID.randomUUID(), 0).success());
    }
}
