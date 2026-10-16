package finance.macro;

import finance.village.VillageProfession;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MacroFormulasTest {

    @Test void priceFactorFollowsVictoriaFormula() {
        assertEquals(10_000, MacroFormulas.targetFactorBps(100, 100), "供需平衡");
        assertEquals(17_500, MacroFormulas.targetFactorBps(200, 100), "需求是供给两倍 → 上限");
        assertEquals(2_500, MacroFormulas.targetFactorBps(100, 200), "供给是需求两倍 → 下限");
        assertEquals(13_750, MacroFormulas.targetFactorBps(150, 100), "缺口 50% → +37.5%");
        assertEquals(17_500, MacroFormulas.targetFactorBps(10, 0), "有需求没供给");
        assertEquals(2_500, MacroFormulas.targetFactorBps(0, 10), "有供给没需求");
        assertEquals(10_000, MacroFormulas.targetFactorBps(0, 0));
    }

    @Test void priceFactorMovesAtMostOneStepPerDay() {
        assertEquals(11_000, MacroFormulas.stepFactor(10_000, 17_500, 1_000));
        assertEquals(9_000, MacroFormulas.stepFactor(10_000, 2_500, 1_000));
        assertEquals(10_400, MacroFormulas.stepFactor(10_000, 10_400, 1_000), "差距小于步长时直接到位");
        assertEquals(17_500, MacroFormulas.stepFactor(99_999, 99_999, 1_000), "越界输入被钳制");
    }

    @Test void standardOfLivingIsWeightedFulfilmentPlusIncome() {
        Map<NeedCategory, Double> all = new EnumMap<>(NeedCategory.class);
        for (NeedCategory c : NeedCategory.values()) all.put(c, 1.0);
        assertEquals(10_000, MacroFormulas.targetSol(all, 0, 0));
        Map<NeedCategory, Double> foodOnly = new EnumMap<>(NeedCategory.class);
        foodOnly.put(NeedCategory.FOOD, 1.0);
        assertEquals(3_500, MacroFormulas.targetSol(foodOnly, 0, 0), "食物权重 35");
        assertEquals(3_500 + 1_500, MacroFormulas.targetSol(foodOnly, 50, 0), "收入加成封顶 1500");
        assertEquals(3_500 + 300, MacroFormulas.targetSol(foodOnly, 2, 0), "人均 2 元收入 +300");
        assertEquals(4_000, MacroFormulas.targetSol(foodOnly, 0, 500), "法律修正");
        Map<NeedCategory, Double> noFood = new EnumMap<>(all);
        noFood.put(NeedCategory.FOOD, 0.0);
        assertEquals(3_250, MacroFormulas.targetSol(noFood, 0, 0), "饥荒：其余 65% 再打五折");
    }

    @Test void solSmoothsTowardTarget() {
        assertEquals(6_000, MacroFormulas.smoothSol(5_000, 10_000));
        assertEquals(4_999, MacroFormulas.smoothSol(5_000, 4_998), "小差距至少动 1 点");
        assertEquals(5_000, MacroFormulas.smoothSol(5_000, 5_000));
    }

    @Test void growthIsSignedBySolAndHalvedByUnrest() {
        assertEquals(500, MacroFormulas.growthMilli(100, 10_000, 50, false), "满分 +0.5%/天 = 0.5 人");
        assertEquals(-500, MacroFormulas.growthMilli(100, 0, 50, false));
        assertEquals(0, MacroFormulas.growthMilli(100, 5_000, 50, false));
        assertEquals(250, MacroFormulas.growthMilli(100, 10_000, 50, true), "骚乱只减半增长");
        assertEquals(-500, MacroFormulas.growthMilli(100, 0, 50, true), "骚乱不减缓衰退");
    }

    @Test void requiredUnitsMatchLegacyBasketForOldCategories() {
        Map<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, 10);
        mix.put(VillageProfession.ARTISAN, 10);
        Map<NeedCategory, Double> req = MacroNeeds.requiredUnits(mix, 0);
        // 与 VillageServiceTest.computeNeeds_matchesPopulationTimesBasket 一致：wheat 30、stone 12、iron 4
        assertEquals(30.0, req.get(NeedCategory.FOOD), 1e-9);
        assertEquals(12.0, req.get(NeedCategory.HOUSING), 1e-9);
        assertEquals(4.0, req.get(NeedCategory.TOOLS), 1e-9);
        assertEquals(7.0, req.get(NeedCategory.HEATING), 1e-9);
        assertEquals(3.0, req.get(NeedCategory.CLOTHING), 1e-9);
        Map<NeedCategory, Double> withWorkers = MacroNeeds.requiredUnits(mix, 10);
        assertEquals(35.0, withWorkers.get(NeedCategory.FOOD), 1e-9, "受雇工人多吃");
        assertEquals(6.0, withWorkers.get(NeedCategory.CLOTHING), 1e-9, "受雇工人多穿");
    }

    @Test void allocationPrefersCheaperUnitsAndSkipsUnavailableGoods() {
        // 小麦每份 8；面包 12/3 = 每份 4 → 权重 1/64 : 1/16，面包分到 80% 的份数
        Map<String, Long> split = MacroNeeds.allocate(NeedCategory.FOOD, 100, id -> true, id -> true,
                id -> id.equals("wheat") ? 8 : 12);
        assertEquals(20L, split.get("wheat"));
        assertEquals(26L, split.get("bread"), "80 份 / 3 = 26 个面包，余数 2 份给最便宜的面包不足一个");
        Map<String, Long> noBread = MacroNeeds.allocate(NeedCategory.FOOD, 100, id -> true, id -> id.equals("wheat"),
                id -> id.equals("wheat") ? 8 : 12);
        assertEquals(Map.of("wheat", 100L), noBread, "没有供给的替代品不参与");
        Map<String, Long> shortage = MacroNeeds.allocate(NeedCategory.CLOTHING, 5, id -> true, id -> false, id -> 9);
        assertEquals(Map.of("wool", 5L), shortage, "全部没有供给时仍按候选下需求（记为短缺）");
        assertTrue(MacroNeeds.allocate(NeedCategory.CLOTHING, 5, id -> false, id -> true, id -> 9).isEmpty(), "未注册的商品不下需求");
    }

    @Test void populationMixKeepsTotalsExact() {
        Map<VillageProfession, Integer> mix = new EnumMap<>(VillageProfession.class);
        mix.put(VillageProfession.FARMER, 12);
        mix.put(VillageProfession.ARTISAN, 9);
        mix.put(VillageProfession.GUARD, 4);
        mix.put(VillageProfession.OTHER, 5);
        var grown = PopulationMix.resized(mix, 40);
        assertEquals(40, grown.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(16, grown.get(VillageProfession.FARMER), "新增 10 人按默认占比 40% 给农民");
        var shrunk = PopulationMix.resized(mix, 3);
        assertEquals(3, shrunk.values().stream().mapToInt(Integer::intValue).sum());
        shrunk.values().forEach(v -> assertTrue(v >= 0));
        var one = new EnumMap<VillageProfession, Integer>(VillageProfession.class);
        one.put(VillageProfession.GUARD, 1);
        var down = PopulationMix.resized(one, 0);
        assertEquals(0, down.get(VillageProfession.GUARD));
    }
}
