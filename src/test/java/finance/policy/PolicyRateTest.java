package finance.policy;

import static org.junit.jupiter.api.Assertions.*;

import finance.data.EconomySavedData;
import finance.metrics.EconomyMetricsService;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 央行基准利率政策工具：规则、议息、传导、锁定。 */
class PolicyRateTest {

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        PolicyMeetingService.clearDirect();
        finance.bank.BankingManager.ensureDefaultBanks();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        PolicyMeetingService.clearDirect();
    }

    private static EconomyMetricsService.DailySnapshot snap(long day, double priceIndex) {
        return new EconomyMetricsService.DailySnapshot(day, 0, 0, 0, 0, 0, 0, 0, 0, priceIndex, 0);
    }

    // ---------------- 规则 ----------------

    @Test
    void highInflationRecommendsHikeCappedByMaxStep() {
        // 30 天通胀 30% → 年化约 365%，目标远超上限
        PolicyRuleService.Evaluation e = PolicyRuleService.evaluate(500, 130.0, 100.0, 0.0);
        assertEquals(PolicyRuleService.Direction.HIKE, e.direction());
        assertEquals(600, e.recommendedBps()); // 单次步长钳制 ±100
        assertTrue(e.annualizedInflationPct() > 300);
    }

    @Test
    void deflationAndSlackRecommendsCut() {
        PolicyRuleService.Evaluation e = PolicyRuleService.evaluate(500, 90.0, 100.0, 0.2);
        assertEquals(PolicyRuleService.Direction.CUT, e.direction());
        assertEquals(400, e.recommendedBps());
    }

    @Test
    void noiseBelowMinStepHolds() {
        // 通胀正好落在央行目标附近（再加 0.01% 的月度噪音）→ 缺口小于最小调整单位 → 维持
        double atTarget = 100.0 * (1 + finance.config.FinanceConfig.cbInflationTargetBps() / 10_000.0 * 30.0 / 365.0);
        int neutral = finance.config.FinanceConfig.policyNeutralRateBps();
        PolicyRuleService.Evaluation e = PolicyRuleService.evaluate(neutral, atTarget + 0.01, 100.0, 0.0);
        assertEquals(PolicyRuleService.Direction.HOLD, e.direction());
        assertEquals(neutral, e.recommendedBps());
    }

    @Test
    void recommendationRespectsMinMaxBounds() {
        PolicyRuleService.Evaluation low = PolicyRuleService.evaluate(100, 50.0, 100.0, 1.0);
        assertTrue(low.recommendedBps() >= 0);
        assertEquals(PolicyRuleService.Direction.CUT, low.direction());

        PolicyRuleService.Evaluation high = PolicyRuleService.evaluate(5000, 200.0, 100.0, 0.0);
        assertEquals(5000, high.recommendedBps()); // 已顶格，不再加
        assertEquals(PolicyRuleService.Direction.HOLD, high.direction());
    }

    @Test
    void slackRatioIsClamped() {
        PolicyRuleService.Evaluation e = PolicyRuleService.evaluate(500, 100.0, 100.0, 9.9);
        assertEquals(1.0, e.slackRatio(), 1e-9);
    }

    // ---------------- 议息 ----------------

    private void seedInflation() {
        EconomyMetricsService.restore(0, 0, List.of(snap(0, 100.0), snap(30, 130.0)));
    }

    @Test
    void autoMeetingAppliesHikeOnMeetingDay() {
        seedInflation();
        PolicyMeetingService.onDayTick(null, 0);
        assertEquals(30, PolicyMeetingService.nextMeetingDay());
        // 议息日之前不调息
        PolicyMeetingService.onDayTick(null, 29);
        assertEquals(500, MonetaryPolicyService.benchmarkRateBasisPoints());
        // 议息日执行
        PolicyMeetingService.onDayTick(null, 30);
        assertEquals(600, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1, MonetaryPolicyService.history().size());
        assertEquals(60, PolicyMeetingService.nextMeetingDay());
        // 同一天重复 tick 不重复执行
        PolicyMeetingService.onDayTick(null, 30);
        assertEquals(600, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1, MonetaryPolicyService.history().size());
    }

    @Test
    void guidanceIsSentOnceInLeadWindow() {
        seedInflation();
        PolicyMeetingService.onDayTick(null, 0); // 下次议息 day 30
        PolicyMeetingService.onDayTick(null, 26); // 窗口外
        assertEquals(-1, PolicyMeetingService.guidanceSentForMeeting());
        PolicyMeetingService.onDayTick(null, 27); // 进入前瞻窗口（提前 3 天）
        assertEquals(30, PolicyMeetingService.guidanceSentForMeeting());
        PolicyMeetingService.onDayTick(null, 28); // 不重复发
        assertEquals(30, PolicyMeetingService.guidanceSentForMeeting());
    }

    @Test
    void advisoryRespectsManualIntervention() {
        seedInflation();
        PolicyMeetingService.setModeOverrideForTest("ADVISORY");
        PolicyMeetingService.onDayTick(null, 0);
        PolicyMeetingService.onDayTick(null, 27); // 前瞻，基线 500
        assertTrue(MonetaryPolicyService.setBenchmarkRate(28, 550, "manual"));
        PolicyMeetingService.onDayTick(null, 30); // 检测到干预，不覆盖
        assertEquals(550, MonetaryPolicyService.benchmarkRateBasisPoints());
    }

    @Test
    void advisoryAppliesRecommendationWhenNoIntervention() {
        seedInflation();
        PolicyMeetingService.setModeOverrideForTest("ADVISORY");
        PolicyMeetingService.onDayTick(null, 0);
        PolicyMeetingService.onDayTick(null, 30);
        assertEquals(600, MonetaryPolicyService.benchmarkRateBasisPoints());
    }

    @Test
    void autoMeetingKeepsManualSameDayChange() {
        // P1-20：AUTO 模式议息日当天已有人工调息 → 尊重人工，不覆盖、不虚假公告
        seedInflation();
        PolicyMeetingService.onDayTick(null, 0); // 下次议息 day 30，周期基线 500
        PolicyMeetingService.onDayTick(null, 27); // 前瞻，基线 500
        assertTrue(MonetaryPolicyService.setBenchmarkRate(30, 550, "manual")); // 议息日当天人工调息
        PolicyMeetingService.onDayTick(null, 30); // 议息：检测到干预，走人工保留
        assertEquals(550, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1, MonetaryPolicyService.history().size()); // 无新增议息记录
        assertEquals(60, PolicyMeetingService.nextMeetingDay()); // 日历照常推进
    }

    @Test
    void manualChangeWithoutGuidanceIsRespected() {
        // P2：无前瞻快照（直接跳天，未触发前瞻）时，周期中人工调息不被建议静默覆盖
        seedInflation();
        PolicyMeetingService.setModeOverrideForTest("ADVISORY");
        PolicyMeetingService.onDayTick(null, 0); // 下次议息 day 30，周期基线 500；跳过 27-29，不发前瞻
        assertEquals(-1, PolicyMeetingService.guidanceSentForMeeting());
        assertTrue(MonetaryPolicyService.setBenchmarkRate(15, 700, "manual")); // 周期中人工调息
        PolicyMeetingService.onDayTick(null, 30); // 议息：退化为周期起点比对 → 干预 → 维持 700
        assertEquals(700, MonetaryPolicyService.benchmarkRateBasisPoints());
    }

    @Test
    void confirmAdvisoryAppliesAndMeetingKeepsIt() {
        // P2：ADVISORY 确认路径可用；确认后议息日不再覆盖确认值
        seedInflation();
        PolicyMeetingService.setModeOverrideForTest("ADVISORY");
        PolicyMeetingService.onDayTick(null, 0);
        assertTrue(PolicyMeetingService.confirmAdvisory(10));
        assertEquals(600, MonetaryPolicyService.benchmarkRateBasisPoints());
        PolicyMeetingService.onDayTick(null, 30);
        assertEquals(600, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertEquals(1, MonetaryPolicyService.history().size()); // 确认记录即议息记录，不重复
    }

    @Test
    void directionLabelToleratesNull() {
        // P2：防御性空检查，不 NPE
        assertEquals("—", PolicyRuleService.directionLabel(null));
    }

    @Test
    void manualModeNeverTouchesRate() {
        seedInflation();
        PolicyMeetingService.setModeOverrideForTest("MANUAL");
        PolicyMeetingService.onDayTick(null, 0);
        PolicyMeetingService.onDayTick(null, 30);
        assertEquals(500, MonetaryPolicyService.benchmarkRateBasisPoints());
        assertTrue(MonetaryPolicyService.history().isEmpty());
        assertEquals(60, PolicyMeetingService.nextMeetingDay()); // 日历照常推进
    }

    // ---------------- 传导 ----------------

    @Test
    void yieldCurveFollowsBenchmark() {
        assertEquals(600, finance.fixedincome.YieldCurveService.yieldBasisPoints(30));
        assertTrue(MonetaryPolicyService.setBenchmarkRate(7, 800, "hike"));
        assertEquals(900, finance.fixedincome.YieldCurveService.yieldBasisPoints(30));
    }

    @Test
    void issuedLoanKeepsLockedRateAfterHike() {
        // 攻击视角：议息日加息，已发放贷款不得追溯
        UUID owner = UUID.randomUUID(), companyId = UUID.randomUUID();
        finance.company.CompanyManager.registerDirect(new finance.company.Company(
                companyId, "PolicyLockCo", finance.company.CompanyType.RAW_MATERIALS, 1_000_000, owner));
        var bank = finance.bank.BankingManager.banks().values().iterator().next();
        var result = finance.debt.CompanyLoanManager.applyCommercial(owner, companyId, bank.id(), 100_000, 0, 10, 3);
        assertTrue(result.success(), result.message());
        int locked = finance.debt.CompanyLoanManager.loans().get(result.id()).annualRateBasisPoints();
        assertTrue(MonetaryPolicyService.setBenchmarkRate(1, 900, "hike"));
        assertEquals(
                locked, finance.debt.CompanyLoanManager.loans().get(result.id()).annualRateBasisPoints());
        // 但新贷款用新利率
        var result2 = finance.debt.CompanyLoanManager.applyCommercial(owner, companyId, bank.id(), 10_000, 1, 10, 3);
        assertTrue(result2.success(), result2.message());
        assertTrue(finance.debt.CompanyLoanManager.loans().get(result2.id()).annualRateBasisPoints() > locked);
    }

    // ---------------- 持久化 ----------------

    @Test
    void meetingStateSurvivesSaveLoad() {
        seedInflation();
        PolicyMeetingService.onDayTick(null, 0);
        PolicyMeetingService.onDayTick(null, 27);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        PolicyMeetingService.clearDirect();
        assertEquals(-1, PolicyMeetingService.nextMeetingDay());
        EconomySavedData.load(saved);
        assertEquals(30, PolicyMeetingService.nextMeetingDay());
        assertEquals(30, PolicyMeetingService.guidanceSentForMeeting());
        assertEquals(500, PolicyMeetingService.guidanceBaselineBps());
        assertEquals(500, PolicyMeetingService.cycleBaselineBps());
    }
}
