package finance.earnings;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.Company;
import finance.company.CompanyType;
import finance.stock.StockPriceEngine;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 财报季测试：预期模型、预期差、股价联动、季度累加、反做假账。
 */
class EarningsSeasonTest {

    private static QuarterlyEarningsReport report(long netProfit) {
        return new QuarterlyEarningsReport(
                UUID.randomUUID(),
                "TST",
                "测试公司",
                1,
                30,
                30,
                1000,
                1000 - netProfit,
                netProfit,
                netProfit / 100,
                0,
                0,
                0,
                true,
                0,
                0,
                LocalDateTime.now());
    }

    // ---- 预期模型 ----

    @Test
    void expectedProfit_linearExtrapolation() {
        List<QuarterlyEarningsReport> h = List.of(report(100), report(200), report(300));
        // 300 + (300-100)/2 = 400
        assertEquals(400, EarningsExpectation.expectedProfit(h));
    }

    @Test
    void expectedProfit_twoPeriods() {
        List<QuarterlyEarningsReport> h = List.of(report(100), report(200));
        assertEquals(300, EarningsExpectation.expectedProfit(h));
    }

    @Test
    void expectedProfit_singlePeriod() {
        assertEquals(100, EarningsExpectation.expectedProfit(List.of(report(100))));
    }

    @Test
    void expectedProfit_emptyMeansNoExpectation() {
        assertEquals(Long.MIN_VALUE, EarningsExpectation.expectedProfit(List.of()));
    }

    @Test
    void expectedProfit_decliningTrend() {
        List<QuarterlyEarningsReport> h = List.of(report(300), report(200), report(100));
        assertEquals(0, EarningsExpectation.expectedProfit(h));
    }

    // ---- 预期差 ----

    @Test
    void surpriseBps_basic() {
        assertEquals(1000, EarningsExpectation.surpriseBps(110, 100));
        assertEquals(-1000, EarningsExpectation.surpriseBps(90, 100));
        assertEquals(0, EarningsExpectation.surpriseBps(100, 100));
    }

    @Test
    void surpriseBps_capped() {
        // 实际 1000、预期 100 → +90000bps，被钳制到 ±5000
        assertEquals(5000, EarningsExpectation.surpriseBps(1000, 100));
        assertEquals(-5000, EarningsExpectation.surpriseBps(-800, 100));
    }

    @Test
    void surpriseBps_noExpectationIsZero() {
        assertEquals(0, EarningsExpectation.surpriseBps(9999, Long.MIN_VALUE));
    }

    @Test
    void surpriseBps_zeroExpectedUsesOneDenominator() {
        // 预期 0、实际 100 → 分母按 1 算后钳制，不抛异常
        assertEquals(5000, EarningsExpectation.surpriseBps(100, 0));
    }

    @Test
    void qoqBps_zeroPreviousIsZeroNotInfinite() {
        assertEquals(0, EarningsExpectation.qoqBps(1000, 0));
        assertEquals(10000, EarningsExpectation.qoqBps(200, 100));
    }

    // ---- 财报日历 ----

    @Test
    void earningsDay_deterministicAndInRange() {
        UUID id = UUID.randomUUID();
        int o1 = EarningsExpectation.earningsDayOffset(id, 30);
        int o2 = EarningsExpectation.earningsDayOffset(id, 30);
        assertEquals(o1, o2);
        assertTrue(o1 >= 0 && o1 < 30);
        // 偏移那天就是财报日
        assertTrue(EarningsExpectation.isEarningsDay(id, o1, 30));
        assertTrue(EarningsExpectation.isEarningsDay(id, o1 + 30, 30));
        assertFalse(EarningsExpectation.isEarningsDay(id, (o1 + 1) % 30, 30));
    }

    // ---- 股价联动 ----

    @Test
    void shock_positiveRaisesPrice() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        engine.applyEarningsShock(1000, 0.5); // +10%
        assertTrue(engine.getCurrentPrice() > 1000);
        assertTrue(engine.getFairValue() > 1000);
        // fairValue 透传 50%：1000 * 1.05 = 1050
        assertEquals(1050, engine.getFairValue());
    }

    @Test
    void shock_negativeLowersPrice() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        engine.applyEarningsShock(-1000, 0.5);
        assertTrue(engine.getCurrentPrice() < 1000);
        assertTrue(engine.getFairValue() < 1000);
    }

    @Test
    void shock_zeroDoesNothing() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        engine.applyEarningsShock(0, 0.5);
        assertEquals(1000, engine.getCurrentPrice());
        assertEquals(1000, engine.getFairValue());
    }

    @Test
    void shock_cannotFlashCrash() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        engine.applyEarningsShock(-100000, 1.0); // -1000%，引擎硬钳制 ±50%
        // 夹逼在 fairValue × [0.45, 1.85]
        assertTrue(engine.getCurrentPrice() >= Math.round(engine.getFairValue() * 0.45));
        assertTrue(engine.getCurrentPrice() >= 1);
    }

    @Test
    void exDividend_lowersPriceByPerShare() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        engine.applyExDividend(100);
        // 除权同步下调 fairValue：(fv - price) 不变，均值回归不吞除权，恰为 900
        assertEquals(900, engine.getCurrentPrice());
        assertEquals(900, engine.getFairValue());
        assertTrue(engine.getCurrentPrice() >= 1);
    }

    @Test
    void exDividend_atFloor_notClampedBack() {
        // P1-19 回归：股价钉在 0.45×fairValue 下限时，除权下调不能被夹逼完整推回
        StockPriceEngine engine = new StockPriceEngine("TST", 450, 1000);
        engine.applyExDividend(100);
        assertEquals(900, engine.getFairValue()); // fairValue 同步下调
        assertTrue(engine.getCurrentPrice() < 450, "除权后价格应低于除权前 450，实际 " + engine.getCurrentPrice());
    }

    @Test
    void exDividend_recordsSnapshot() {
        StockPriceEngine engine = new StockPriceEngine("TST", 1000, 1000);
        int before = engine.getSnapshots().size();
        engine.applyExDividend(100);
        assertEquals(before + 1, engine.getSnapshots().size()); // 与 applyEarningsShock 一致，K 线留点
    }

    @Test
    void exDividend_neverBelowOne() {
        StockPriceEngine engine = new StockPriceEngine("TST", 50, 1000);
        engine.applyExDividend(10000);
        assertTrue(engine.getCurrentPrice() >= 1);
    }

    // ---- 季度累加 ----

    @Test
    void settleDaily_accumulatesQuarter() {
        Company company = new Company(UUID.randomUUID(), "测试公司", CompanyType.FOOD, 10_000);
        company.recordGameplayRevenue(5000);
        company.recordGameplayCost(2000);
        company.settleDailyProfits(1);
        Company.QuarterAccumulator acc = company.drainQuarter();
        assertEquals(5000, acc.revenue());
        assertTrue(acc.expenses() >= 2000); // 含运营成本
        assertEquals(1, acc.days());
        // drain 后清零
        Company.QuarterAccumulator acc2 = company.drainQuarter();
        assertEquals(0, acc2.revenue());
        assertEquals(0, acc2.days());
    }

    @Test
    void earningsHistory_appendOnlyAndTrimmed() {
        Company company = new Company(UUID.randomUUID(), "测试公司", CompanyType.FOOD, 10_000);
        for (int i = 1; i <= 10; i++) {
            QuarterlyEarningsReport r = new QuarterlyEarningsReport(
                    UUID.randomUUID(),
                    "TST",
                    "测试公司",
                    i,
                    i * 30L,
                    30,
                    100,
                    50,
                    50,
                    1,
                    0,
                    0,
                    0,
                    true,
                    0,
                    0,
                    LocalDateTime.now());
            company.addEarningsReportDirect(r, 8);
        }
        List<QuarterlyEarningsReport> history = company.getEarningsHistory();
        assertEquals(8, history.size());
        assertEquals(3, history.get(0).quarterIndex()); // 最早两期被裁掉
        assertEquals(10, history.get(7).quarterIndex());
    }

    // ---- 反做假账 ----

    @Test
    void restoreQuarterly_clampsNegatives() {
        Company company = new Company(UUID.randomUUID(), "测试公司", CompanyType.FOOD, 10_000);
        company.restoreQuarterly(-100, -200, -5, new ArrayList<>());
        Company.QuarterAccumulator acc = company.drainQuarter();
        assertEquals(0, acc.revenue());
        assertEquals(0, acc.expenses());
        assertEquals(0, acc.days());
    }

    @Test
    void historyIsACopy_ownerCannotMutate() {
        Company company = new Company(UUID.randomUUID(), "测试公司", CompanyType.FOOD, 10_000);
        company.addEarningsReportDirect(report(100), 8);
        List<QuarterlyEarningsReport> copy = company.getEarningsHistory();
        copy.clear();
        assertEquals(1, company.getEarningsHistory().size());
    }
}
