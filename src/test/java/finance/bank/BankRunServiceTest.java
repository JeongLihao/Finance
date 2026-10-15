package finance.bank;

import finance.account.AccountManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.policy.MonetaryPolicyService;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankRunServiceTest {
    UUID player = UUID.fromString("00000000-0000-0000-0000-000000007001");
    CommercialBank a;

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        BankingManager.ensureDefaultBanks();
        a = BankingManager.banks().values().stream().toList().get(0);
        AccountManager.deposit(player, 10_000_000);
    }
    @AfterEach void end() { EconomySavedData.resetRuntimeState(); }

    private BankCustomerAccount npcDemand(CommercialBank b) {
        return BankRunService.npcAccount(b.id(), BankRunService.npcOwnerId(b), BankAccountType.DEMAND_DEPOSIT);
    }

    // ---------- 播种 ----------

    @Test void npcPoolSeededAsLoansCreateDeposits() {
        BankingManager.processDay(0);
        var demand = npcDemand(a);
        var time = BankRunService.npcAccount(a.id(), BankRunService.npcOwnerId(a), BankAccountType.TIME_DEPOSIT);
        assertNotNull(demand); assertNotNull(time);
        assertEquals(CustomerType.NPC, demand.ownerType());
        long seed = 5_000_000L * 4_000 / 10_000; // bankInitialCapital * npcSeedBps
        long demandPart = seed - seed * 3_000 / 10_000, timePart = seed * 3_000 / 10_000;
        assertEquals(timePart, time.balance());
        assertTrue(demand.balance() >= demandPart && demand.balance() <= demandPart + 100_000,
                "活期=播种+少量利息，实际 " + demand.balance());
        assertEquals(seed, BankRunService.stateOf(a.id()).npcLoanBook());
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    // ---------- 恐慌度 ----------

    @Test void stressOnlyReflectsRealRisk() {
        var bad = new BankPanicService.PanicInputs(0.5, FinanceConfig.bankMinimumCapitalBps(), 0, 1_000_000, false);
        var good = new BankPanicService.PanicInputs(1.2, FinanceConfig.bankMinimumCapitalBps(), 0, 1_000_000, false);
        assertTrue(BankPanicService.stressOf(bad) > 0);
        assertEquals(0, BankPanicService.stressOf(good));
        var bigOutflow = new BankPanicService.PanicInputs(1.2, FinanceConfig.bankMinimumCapitalBps(), 100_000, 1_000_000, false);
        assertTrue(BankPanicService.stressOf(bigOutflow) > 0); // 单日提现超 3%
    }

    @Test void panicDecaysWhenHealthy() {
        BankRunService.stateOf(a.id()).setPanic(20, -1);
        BankingManager.processDay(1); // 处理 d=0,1，两次日终各 -6，无挤兑流出
        assertEquals(8, BankRunService.panicOf(a.id()));
    }

    @Test void rumorOnlyFiresOnRealRiskAndCitesRealNumbers() {
        BankingManager.ensureDefaultBanks();
        var healthyState = new BankRunState(a.id());
        var healthy = new BankPanicService.PanicInputs(1.0, FinanceConfig.bankMinimumCapitalBps(), 0, 1_000_000, false);
        for (long d = 0; d < 200; d++)
            assertNull(BankPanicService.maybeRumor(a, healthy, healthyState, d), "健康银行绝不能有谣言");
        var riskyState = new BankRunState(a.id());
        var risky = new BankPanicService.PanicInputs(0.5, FinanceConfig.bankMinimumCapitalBps(), 0, 1_000_000, false);
        int fired = 0;
        for (long d = 0; d < 200; d++) {
            var r = BankPanicService.maybeRumor(a, risky, riskyState, d);
            if (r != null) {
                fired++;
                assertTrue(r.text().contains("50"), "谣言必须引用真实准备金率 50%，实际：" + r.text());
                assertTrue(r.text().contains(a.name()));
                riskyState.setRumorCooldownDay(r.cooldownUntilDay());
            }
        }
        assertTrue(fired > 0, "真实风险下谣言应当触发");
    }

    // ---------- 挤兑流出 ----------

    @Test void npcRunDrainsReservesThroughLedger() {
        BankingManager.processDay(0);
        long reservesBefore = a.ledger().balanceSheet().reserves();
        long budget = BankRunService.dailyBudget(a);
        BankRunService.stateOf(a.id()).setPanic(100, 0);
        BankingManager.processDay(1);
        BankRunState s = BankRunService.stateOf(a.id());
        assertTrue(Math.abs(s.cumulativeOutflow() - budget) < 1_000,
                "流出≈日预算，实际 " + s.cumulativeOutflow() + " 预算 " + budget);
        long reservesAfter = a.ledger().balanceSheet().reserves();
        assertTrue(Math.abs(reservesBefore - s.cumulativeOutflow() - reservesAfter) < 5_000,
                "准备金≈期初-流出±日终小额收支，实际 " + reservesAfter);
        assertTrue(a.ledger().balanceSheet().balanced());
        assertTrue(a.ledger().entries().stream().anyMatch(e -> e.reason() == BankLedgerReason.BANK_RUN_WITHDRAWAL));
    }

    @Test void dailyCapQueuesWithdrawals() {
        var dep = BankingManager.depositPlayer(player, a.id(), 300_000, 0);
        assertTrue(dep.success());
        BankingManager.processDay(0);
        BankRunService.stateOf(a.id()).setPanic(70, 0); // 达到限提档位
        // 预算 = (300k + 2M) × 10% = 230k
        assertTrue(BankingManager.withdrawPlayer(player, dep.id(), 200_000, 0).success());
        var denied = BankingManager.withdrawPlayer(player, dep.id(), 40_000, 0);
        assertFalse(denied.success());
        assertTrue(denied.message().contains("限提"));
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void interbankFreezesAtHighPanic() {
        BankingManager.processDay(0);
        var banks = BankingManager.banks().values().stream().toList();
        CommercialBank b = banks.get(1);
        long need = b.ledger().balanceSheet().reserves() + 100_000;
        BankRunService.stateOf(b.id()).setPanic(80, 0);
        assertEquals(0, InterbankMarketService.borrowFor(b.id(), need, 1), "恐慌 80 应被同业冻结");
        BankRunService.stateOf(b.id()).setPanic(0, 1);
        assertEquals(100_000, InterbankMarketService.borrowFor(b.id(), need, 1), "恐慌消除后同业恢复");
    }

    @Test void centralBankDependenceKeepsPanicHigh() {
        CommercialBank small = smallBank("TST2", 1_000_000);
        BankingManager.processDay(0);
        for (long d = 1; d <= 8; d++) {
            BankRunService.stateOf(small.id()).setPanic(100, d - 1); // 维持恐慌情绪
            BankingManager.processDay(d);
        }
        // 准备金被挤兑抽干 → 同业冻结 → 被迫借央行 → 有效准备金率为负 → 恐慌钉在高位
        assertTrue(small.ledger().balance(BankLedgerAccount.LIABILITY_CENTRAL_BANK) > 0);
        assertTrue(BankRunService.panicOf(small.id()) >= 85);
        assertTrue(small.ledger().balanceSheet().balanced());
    }

    @Test void panicDefaultsErodeEquity() {
        BankingManager.processDay(0);
        long equityBefore = a.ledger().balanceSheet().equity();
        BankRunService.stateOf(a.id()).setPanic(100, 0);
        BankingManager.processDay(1);
        long loss = a.ledger().balance(BankLedgerAccount.EXPENSE_CREDIT_LOSS);
        assertTrue(loss > 0, "恐慌违约应产生信用损失");
        assertTrue(a.ledger().balanceSheet().equity() < equityBefore);
        assertTrue(BankRunService.stateOf(a.id()).npcLoanBook() < 2_000_000);
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    // ---------- 玩家玩法 ----------

    @Test void earlyRedeemForfeitsInterest() {
        MonetaryPolicyService.setBenchmarkRate(1, 2_000, "test");
        var dep = BankingManager.depositPlayer(player, a.id(), 20_000, 0);
        var open = BankingManager.openTimeDeposit(player, dep.id(), 10_000, 30, 0);
        assertTrue(open.success());
        BankingManager.processDay(5); // 计 5 天利息
        TimeDeposit td = BankingManager.timeDeposits().get(open.id());
        long balance = BankingManager.account(td.accountId()).balance();
        assertTrue(balance > 10_000, "应已计利息");
        long feeBefore = a.ledger().balance(BankLedgerAccount.INCOME_FEE);
        long demandBefore = BankingManager.account(dep.id()).balance();
        var r = BankingManager.earlyRedeemTimeDeposit(player, open.id(), 5);
        assertTrue(r.success(), r.message());
        assertEquals(demandBefore + 10_000, BankingManager.account(dep.id()).balance(), "本金全额回活期");
        assertEquals(balance - 10_000, a.ledger().balance(BankLedgerAccount.INCOME_FEE) - feeBefore, "利息全部罚没记手续费收入");
        assertFalse(BankingManager.earlyRedeemTimeDeposit(player, open.id(), 5).success(), "不可重复提前支取");
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void attackOverWithdrawAndNpcTheftFail() {
        var dep = BankingManager.depositPlayer(player, a.id(), 5_000, 0);
        assertFalse(BankingManager.withdrawPlayer(player, dep.id(), 5_001, 0).success(), "超额提现必须拒绝");
        BankingManager.processDay(0);
        var npc = npcDemand(a);
        assertNotNull(npc);
        assertFalse(BankingManager.withdrawPlayer(player, npc.id(), 100, 0).success(), "玩家不能动 NPC 池");
        assertTrue(a.ledger().balanceSheet().balanced());
    }

    @Test void centralBankLoanCarriesPenaltyRate() {
        int expected = Math.min(20_000,
                MonetaryPolicyService.benchmarkRateBasisPoints() + FinanceConfig.bankCentralPenaltyBps());
        assertEquals(50_000, CentralBankLiquidityService.borrow(a.id(), 50_000, 0));
        var loan = CentralBankLiquidityService.loans().values().stream()
                .filter(l -> l.bankId().equals(a.id())).findFirst().orElseThrow();
        assertEquals(expected, loan.rateBasisPoints());
    }

    // ---------- 破产处置 ----------

    @Test void illiquidityLeadsToResolutionAndBridgePayout() {
        CommercialBank small = smallBank("TST", 1_000_000);
        // 堵住最后贷款人： facility 额度打满
        CentralBankLiquidityService.restore(CentralBankLiquidityService.FACILITY_LIMIT, 0, 0, 0);
        var dep = BankingManager.depositPlayer(player, small.id(), 50_000, 0);
        assertTrue(dep.success());
        BankingManager.processDay(0);
        long expectedInsured = BankingManager.account(dep.id()).balance(); // 含日终活期利息
        // 模拟昨日挤兑已抽干准备金（NPC 账户＋账本同步，保持对账一致）
        var npc = npcDemand(small);
        long drain = 900_000;
        assertTrue(npc.available() >= drain);
        assertTrue(npc.debit(drain));
        assertTrue(small.ledger().post(0, UUID.randomUUID(), BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                BankLedgerAccount.ASSET_RESERVE, drain, BankLedgerReason.BANK_RUN_WITHDRAWAL));
        BankRunService.stateOf(small.id()).setPanic(100, 0);
        for (long d = 1; d <= 10; d++) BankingManager.processDay(d);
        assertEquals(BankStatus.FAILED, small.status(), "非流动性死亡应走处置");
        assertTrue(DepositInsuranceService.resolvedBanks().contains(small.id()));
        CommercialBank bridge = BankingManager.banks().values().stream()
                .filter(b -> "BRIDGE".equals(b.code())).findFirst().orElseThrow();
        long bridgeBalance = BankingManager.accounts().values().stream()
                .filter(x -> x.bankId().equals(bridge.id()) && x.ownerId().equals(player))
                .mapToLong(BankCustomerAccount::balance).sum();
        assertTrue(bridgeBalance >= expectedInsured && bridgeBalance <= expectedInsured + 500,
                "保险上限内全额赔付到桥接银行（含处置前活期利息），实际 " + bridgeBalance);
        assertTrue(BankingManager.accounts().values().stream()
                .filter(x -> x.bankId().equals(small.id()) && x.ownerType() == CustomerType.NPC)
                .allMatch(x -> x.status() == BankAccountStatus.CLOSED), "NPC 池核销关户");
        assertTrue(small.ledger().balanceSheet().balanced());
        assertTrue(bridge.ledger().balanceSheet().balanced());
    }

    @Test void publicPanicViewExposesTier() {
        BankingManager.processDay(0);
        BankRunService.stateOf(a.id()).setPanic(75, 0);
        var v = PublicBankViewService.panicView(a);
        assertEquals(BankPanicTier.RUN, v.tier());
        assertEquals(75, v.panic());
        assertTrue(v.withdrawalCapped());
    }

    @Test void runStateSurvivesSaveLoad() {
        BankingManager.processDay(0);
        BankRunService.stateOf(a.id()).setPanic(42, 0);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);
        assertEquals(42, BankRunService.panicOf(a.id()));
        assertEquals(2_000_000, BankRunService.stateOf(a.id()).npcLoanBook());
        CommercialBank loaded = BankingManager.bank(a.id());
        assertTrue(loaded.ledger().balanceSheet().balanced());
    }

    @Test void lastProcessedDaySurvivesSaveLoad() {
        BankingManager.processDay(3);
        assertEquals(3, BankRunService.lastProcessedDay());
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);
        assertEquals(3, BankRunService.lastProcessedDay(), "重启后不应从第 0 天重放日终");
    }

    @Test void failedBanksSeededAsKnownOnLoad() {
        BankingManager.processDay(0);
        a.setStatus(BankStatus.FAILED);
        BankRunService.restoreFailureDay(10);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.resetRuntimeState();
        EconomySavedData.load(saved);
        assertEquals(10, BankRunService.lastFailureDay());
        BankingManager.processDay(20); // 重启后跳天重跑
        assertEquals(10, BankRunService.lastFailureDay(), "已倒闭银行不应被误判为新倒闭而延长传染窗口");
    }

    @Test void resolvePureNpcBankTriviallySucceeds() {
        BankingManager.processDay(0);
        CommercialBank b = smallBank("NPCB", 5_000_000);
        b.setStatus(BankStatus.RESOLUTION); // 无玩家存款：纯 NPC 银行
        assertTrue(DepositInsuranceService.resolve(b.id(), 1), "纯 NPC 银行应 trivial 成功，不僵尸化");
        assertEquals(BankStatus.FAILED, b.status());
        assertTrue(DepositInsuranceService.resolvedBanks().contains(b.id()));
    }

    // ---------- helpers ----------

    private CommercialBank smallBank(String code, long capital) {
        UUID id = UUID.nameUUIDFromBytes(("finance-test-bank-" + code).getBytes(StandardCharsets.UTF_8));
        CommercialBank b = new CommercialBank(id, code, code + " Test Bank", BankStatus.ACTIVE, capital, BankPolicy.standard());
        assertTrue(b.ledger().post(0, UUID.nameUUIDFromBytes(("finance-test-capital-" + code).getBytes(StandardCharsets.UTF_8)),
                BankLedgerAccount.ASSET_RESERVE, BankLedgerAccount.EQUITY_PAID_IN, capital, BankLedgerReason.INITIAL_CAPITAL));
        BankingManager.putBankDirect(b);
        return b;
    }
}
