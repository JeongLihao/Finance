package finance.bank;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.policy.MonetaryPolicyService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 定期存款固定期限档 / 提前支取 / 上下限 / 反刷钱。纯内存逻辑测试。 */
class TimeDepositFeatureTest {
    UUID player = UUID.fromString("00000000-0000-0000-0000-000000007001"),
         other = UUID.fromString("00000000-0000-0000-0000-000000007002");
    CommercialBank a;

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        MonetaryPolicyService.clearDirect();
        BankingManager.clearDirect();
        BankingManager.ensureDefaultBanks();
        a = BankingManager.banks().values().stream().toList().get(0);
    }
    @AfterEach void end() {
        MonetaryPolicyService.clearDirect();
        EconomySavedData.resetRuntimeState();
    }

    @Test void simpleInterestExactValue() {
        // 基准500bp - 利差75bp + 期限奖励min(500,90*3)=270 -> 锁定利率695bp
        // 每日利息 = floor(10000*695*1/(365*10000)) = 1，90天共90（逐日向下取整是既有口径）
        AccountManager.deposit(player, 50_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 20_000, 0);
        var r = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 0);
        assertTrue(r.success());
        TimeDeposit td = BankingManager.timeDeposits().get(r.id());
        assertEquals(695, td.lockedRateBps());
        BankingManager.processDay(90);
        assertEquals(TimeDepositStatus.MATURED, td.status());
        assertEquals(90, td.accruedInterest());
        assertEquals(10_090, BankingManager.account(td.accountId()).balance());
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void earlyRedeemReturnsPrincipalAndForfeitsAllInterest() {
        AccountManager.deposit(player, 50_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 30_000, 0);
        var r = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 0);
        assertTrue(r.success());
        TimeDeposit td = BankingManager.timeDeposits().get(r.id());
        BankingManager.processDay(30);
        assertEquals(30, td.accruedInterest()); // 已计利息30，提前支取时全部罚没
        long demandBefore = BankingManager.account(demand.id()).balance();
        long feeIncomeBefore = a.ledger().balance(BankLedgerAccount.INCOME_FEE);
        var er = BankingManager.earlyRedeemTimeDeposit(player, r.id(), 30);
        assertTrue(er.success());
        assertEquals(TimeDepositStatus.EARLY_REDEEMED, td.status());
        assertEquals(demandBefore + 10_000, BankingManager.account(demand.id()).balance()); // 仅返本金
        assertEquals(BankAccountStatus.CLOSED, BankingManager.account(td.accountId()).status());
        assertEquals(feeIncomeBefore, a.ledger().balance(BankLedgerAccount.INCOME_FEE)); // 默认手续费0
        assertFalse(BankingManager.earlyRedeemTimeDeposit(player, r.id(), 30).success()); // 不可重复支取
        assertFalse(BankingManager.redeemTimeDeposit(player, r.id(), 30).success()); // 不可走到期兑付
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(t -> t.getType() == TransactionType.TIME_DEPOSIT_EARLY_REDEEM));
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void earlyRedeemDeductsFeeFromPrincipal() {
        AccountManager.deposit(player, 50_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 30_000, 0);
        var r = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 0);
        assertTrue(r.success());
        BankingManager.processDay(30);
        long demandBefore = BankingManager.account(demand.id()).balance();
        long feeIncomeBefore = a.ledger().balance(BankLedgerAccount.INCOME_FEE);
        // 包内重载：显式手续费50，验证从本金扣除并记手续费收入
        assertTrue(BankingManager.earlyRedeemTimeDeposit(player, r.id(), 30, 50).success());
        assertEquals(demandBefore + 10_000 - 50, BankingManager.account(demand.id()).balance());
        assertEquals(feeIncomeBefore + 50, a.ledger().balance(BankLedgerAccount.INCOME_FEE));
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void rateLockedAcrossBenchmarkChange() {
        AccountManager.deposit(player, 100_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 80_000, 0);
        var old = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 0);
        assertTrue(old.success());
        assertEquals(695, BankingManager.timeDeposits().get(old.id()).lockedRateBps());
        assertTrue(MonetaryPolicyService.setBenchmarkRate(1, 2_000, "test"));
        var neo = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 1);
        assertTrue(neo.success());
        assertEquals(2_195, BankingManager.timeDeposits().get(neo.id()).lockedRateBps()); // 新存单用新利率
        assertEquals(695, BankingManager.timeDeposits().get(old.id()).lockedRateBps()); // 旧存单利率不变
    }

    @Test void rejectsBelowMinAbovePerPlayerCapAndOffWhitelistTerm() {
        AccountManager.deposit(player, 3_000_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 2_500_000, 0);
        assertTrue(demand.success());
        assertFalse(BankingManager.openTimeDeposit(player, demand.id(), 999, 7, 0).success()); // 未达起存1000
        assertFalse(BankingManager.openTimeDeposit(player, demand.id(), 1_000, 10, 0).success()); // 期限不在白名单
        assertFalse(BankingManager.openTimeDeposit(player, demand.id(), 1_000_001, 7, 0).success()); // 超个人上限
        var ok = BankingManager.openTimeDeposit(player, demand.id(), 600_000, 7, 0);
        assertTrue(ok.success());
        assertFalse(BankingManager.openTimeDeposit(player, demand.id(), 500_000, 30, 0).success()); // 60万+50万>100万
        assertTrue(BankingManager.earlyRedeemTimeDeposit(player, ok.id(), 1).success()); // 提前支取释放额度
        assertTrue(BankingManager.openTimeDeposit(player, demand.id(), 500_000, 30, 1).success());
    }

    @Test void depositRedeemCycleYieldsNothing() {
        // A 反复 存入->计息->提前支取；B 只是持有活期。A 的总资产不得超过 B（无免费午餐）
        AccountManager.deposit(player, 200_000);
        AccountManager.deposit(other, 200_000);
        var dA = BankingManager.depositPlayer(player, a.id(), 200_000, 0);
        var dB = BankingManager.depositPlayer(other, a.id(), 200_000, 0);
        for (int cycle = 0; cycle < 3; cycle++) {
            long day = cycle * 30L;
            var r = BankingManager.openTimeDeposit(player, dA.id(), 100_000, 90, day);
            assertTrue(r.success());
            BankingManager.processDay(day + 30);
            assertTrue(BankingManager.earlyRedeemTimeDeposit(player, r.id(), day + 30).success());
        }
        assertTrue(BankingManager.timeDeposits().values().stream()
                .filter(t -> t.ownerId().equals(player))
                .allMatch(t -> t.status() == TimeDepositStatus.EARLY_REDEEMED));
        long totalA = BankingManager.account(dA.id()).balance() + AccountManager.getBalance(player);
        long totalB = BankingManager.account(dB.id()).balance() + AccountManager.getBalance(other);
        assertTrue(totalA <= totalB, "存取循环产生了正收益: A=" + totalA + " B=" + totalB);
    }

    @Test void interestAccrualIsIdempotent() {
        AccountManager.deposit(player, 50_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 20_000, 0);
        var r = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 90, 0);
        assertTrue(r.success());
        TimeDeposit td = BankingManager.timeDeposits().get(r.id());
        BankingManager.processDay(5);
        long balance5 = BankingManager.account(td.accountId()).balance();
        assertEquals(5, td.accruedInterest());
        BankingManager.processDay(5); // 同一天重复跑批
        assertEquals(balance5, BankingManager.account(td.accountId()).balance());
        assertEquals(5, td.accruedInterest());
        BankingManager.processDay(10);
        long balance10 = BankingManager.account(td.accountId()).balance();
        assertEquals(10, td.accruedInterest());
        BankingManager.processDay(10);
        assertEquals(balance10, BankingManager.account(td.accountId()).balance());
        assertEquals(10, td.accruedInterest());
    }

    @Test void earlyRedeemOnlyForActiveAndOwner() {
        AccountManager.deposit(player, 50_000);
        AccountManager.deposit(other, 50_000);
        var demand = BankingManager.depositPlayer(player, a.id(), 30_000, 0);
        BankingManager.depositPlayer(other, a.id(), 30_000, 0);
        var r = BankingManager.openTimeDeposit(player, demand.id(), 10_000, 7, 0);
        assertTrue(r.success());
        assertFalse(BankingManager.earlyRedeemTimeDeposit(other, r.id(), 1).success()); // 非本人
        assertFalse(BankingManager.earlyRedeemTimeDeposit(player, UUID.randomUUID(), 1).success()); // 不存在
        BankingManager.processDay(7); // 到期
        assertEquals(TimeDepositStatus.MATURED, BankingManager.timeDeposits().get(r.id()).status());
        assertFalse(BankingManager.earlyRedeemTimeDeposit(player, r.id(), 7).success()); // 已到期只能走兑付
        assertTrue(BankingManager.redeemTimeDeposit(player, r.id(), 7).success());
    }
}
