package finance.simulation;

import static finance.simulation.MacroLongRunSimulationService.*;
import static org.junit.jupiter.api.Assertions.*;

import finance.commodity.CommodityRegistry;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.macro.MacroFormulas;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 宏观经济一年期平衡基线（数值见 docs/MACRO_ECONOMY.md “平衡基线”）。 */
class MacroLongRunSimulationServiceTest {
    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void macroEconomyStaysBalancedForAFullYear() {
        Result r = MacroLongRunSimulationService.run(STANDARD_DAYS);
        System.out.println(r.summary());
        assertEquals(STANDARD_DAYS, r.daily().size());
        assertTrue(r.deterministic(), "同样的场景跑两遍结果一致");

        // 货币守恒：账户 + 公司现金 + 已记录的销毁 = 初始值（每天都成立）
        assertTrue(r.moneyConserved(), "货币不守恒");
        assertEquals(0, r.last().burned(), "生产不销毁货币");
        // 日常开销真付了：5 家公司各 3 台 1 档机器，一年的维护费至少 5 × 3 × 1 档维护费 × 天数的一半（铁厂停工期间也照付）
        long minMaintenance = 5L * MACHINES * FinanceConfig.machineMaintenance(1) * STANDARD_DAYS / 2;
        assertTrue(
                r.last().runningCosts() >= minMaintenance,
                "日常开销没有真付：" + r.last().runningCosts());

        // 人口：不撞上下限，不单调涨到底
        int min = FinanceConfig.macroMinPopulation(), max = FinanceConfig.macroMaxPopulation();
        for (String name : List.of("alpha", "beta", "gamma")) {
            List<Integer> pop = r.populationOf(name);
            assertTrue(pop.stream().allMatch(p -> p > min && p < max), name + " 人口撞到上下限：" + pop);
            int peak = pop.stream().mapToInt(Integer::intValue).max().orElseThrow();
            assertTrue(peak < max / 2, name + " 一年内不应逼近人口上限：" + peak);
        }
        // 冲击：铁厂停工期间 gamma 失业、人口外流；复工后回升
        List<Integer> gamma = r.populationOf("gamma");
        int beforeShock = gamma.get(SHOCK_START - 1),
                atRecovery = gamma.get(SHOCK_END - 1),
                end = gamma.get(STANDARD_DAYS - 1);
        assertTrue(atRecovery < beforeShock, "停工期间人口下降：" + beforeShock + " → " + atRecovery);
        assertTrue(end > atRecovery, "复工后人口回升：" + atRecovery + " → " + end);
        assertTrue(
                r.populationOf("beta").get(SHOCK_END - 1)
                        > r.populationOf("beta").get(SHOCK_START - 1),
                "失业人口迁往邻近聚落");

        // 价格系数：没有任何商品在任何聚落连续 30 天以上卡在 2500 或 17500
        assertTrue(r.longestPinnedRun() <= 30, "价格系数长期卡在边界：" + r.factors());
        assertTrue(
                r.factors().stream()
                        .allMatch(f -> f.finalBps() > MacroFormulas.MIN_FACTOR_BPS
                                && f.finalBps() < MacroFormulas.MAX_FACTOR_BPS),
                "年末价格系数不在边界");

        // CPI：稳态年化通胀在 −3%～+5%；含开服磨合期也不超过 −10%
        double steady = r.annualizedCpiInflationPct(STEADY_STATE_DAY);
        assertTrue(steady > -3 && steady < 5, "稳态 CPI 年化通胀：" + steady);
        assertTrue(r.annualizedCpiInflationPct(30) > -10, "磨合期通缩过大：" + r.annualizedCpiInflationPct(30));

        // 工资和日常开销不掏空公司：每家公司全年最低现金不低于初始的一半，年末现金高于初始
        for (var e : r.companyStartCash().entrySet()) {
            assertTrue(r.minCash(e.getKey()) >= e.getValue() / 2, e.getKey() + " 现金被掏空：" + r.minCash(e.getKey()));
            assertTrue(r.last().companyCash().get(e.getKey()) > e.getValue(), e.getKey() + " 全年亏损");
        }
        assertTrue(r.last().settlements().stream().allMatch(s -> s.employed() > 0), "年末每个聚落都有人就业");
    }

    // ---------------------------------------------------------------- A2：利率传导

    private static double cpiOn(Result r, int day) {
        return r.daily().get(day - 1).cpi();
    }

    /** 利率场景共同的底线：货币守恒、确定性、人口和价格系数不撞边界、公司不被掏空。 */
    private static void assertHealthy(Result r) {
        assertTrue(r.deterministic(), "同样的场景跑两遍结果一致");
        assertTrue(r.moneyConserved(), "货币不守恒");
        int min = FinanceConfig.macroMinPopulation(), max = FinanceConfig.macroMaxPopulation();
        for (String name : List.of("alpha", "beta", "gamma"))
            assertTrue(r.populationOf(name).stream().allMatch(p -> p > min && p < max), name + " 人口撞到上下限");
        assertTrue(r.longestPinnedRun() <= 30, "价格系数长期卡在边界：" + r.factors());
        for (var e : r.companyStartCash().entrySet())
            assertTrue(r.minCash(e.getKey()) >= e.getValue() / 2, e.getKey() + " 现金被掏空：" + r.minCash(e.getKey()));
    }

    @Test
    void repeatedHikesPullCpiDown() {
        Result hikes = MacroLongRunSimulationService.run(STANDARD_DAYS, Scenario.HIKES);
        EconomySavedData.resetRuntimeState();
        Result base = MacroLongRunSimulationService.run(STANDARD_DAYS, Scenario.BASELINE);
        System.out.println(hikes.summary());
        assertHealthy(hikes);
        double before = cpiOn(hikes, RATE_SCENARIO_START - 1);
        // 连续加息两个议息周期（60 天）内，CPI 比加息前低 1% 以上，并一直低于不加息的基线
        int by = RATE_SCENARIO_START + 2 * RATE_STEP_DAYS;
        assertTrue(cpiOn(hikes, by) < before * 0.99, "加息后 CPI 没回落：" + before + " → " + cpiOn(hikes, by));
        assertTrue(cpiOn(hikes, STANDARD_DAYS) < cpiOn(base, STANDARD_DAYS), "年末 CPI 不低于基线");
    }

    @Test
    void repeatedCutsPushCpiUp() {
        Result cuts = MacroLongRunSimulationService.run(STANDARD_DAYS, Scenario.CUTS);
        EconomySavedData.resetRuntimeState();
        Result base = MacroLongRunSimulationService.run(STANDARD_DAYS, Scenario.BASELINE);
        System.out.println(cuts.summary());
        assertHealthy(cuts);
        double before = cpiOn(cuts, RATE_SCENARIO_START - 1);
        // 降到 0 之后（五个议息周期）CPI 比降息前高 1% 以上，并高于不降息的基线
        int by = RATE_SCENARIO_START + 6 * RATE_STEP_DAYS;
        assertTrue(cpiOn(cuts, by) > before * 1.01, "降息后 CPI 没回升：" + before + " → " + cpiOn(cuts, by));
        assertTrue(cpiOn(cuts, STANDARD_DAYS) > cpiOn(base, STANDARD_DAYS), "年末 CPI 不高于基线");
    }

    @Test
    void automaticPolicyKeepsCpiStableWithoutWhipsaw() {
        Result r = MacroLongRunSimulationService.run(STANDARD_DAYS, Scenario.POLICY);
        System.out.println(r.summary());
        assertHealthy(r);
        // 央行按规则自动议息：磨合期后 CPI 不偏离第 30 天水平 5% 以上；单次调息不超过规则上限
        double anchor = cpiOn(r, 30);
        for (int day = 30; day <= STANDARD_DAYS; day++)
            assertTrue(Math.abs(cpiOn(r, day) / anchor - 1) < 0.05, "第 " + day + " 天 CPI 偏离过大：" + cpiOn(r, day));
        for (int day = 2; day <= STANDARD_DAYS; day++)
            assertTrue(
                    Math.abs(r.daily().get(day - 1).rateBps()
                                    - r.daily().get(day - 2).rateBps())
                            <= FinanceConfig.policyMaxStepBps(),
                    "第 " + day + " 天调息幅度过大");
    }
}
