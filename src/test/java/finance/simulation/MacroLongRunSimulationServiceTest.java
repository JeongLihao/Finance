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
}
