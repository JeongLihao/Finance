package finance.market.liquidity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Phase 2 做市商报价引擎的纯逻辑测试（攻击视角）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>库存过高 → 报价下移，库存过低 → 报价上移；</li>
 *   <li>波动放大 → 价差变宽；</li>
 *   <li>均值回归：现价偏离中间价超阈值时报价被往回拉，阈值内不调整；</li>
 *   <li>买入立刻卖回必亏：ask - bid &gt; 0 在各种极端状态下恒成立；</li>
 *   <li>策略可单独关闭（显式策略链）；</li>
 *   <li>熔断 / 中间价无效 → 无报价；</li>
 *   <li>轻量波动率估计器行为。</li>
 * </ul>
 *
 * <p>注意：策略开关与系数走 FinanceConfig；单元测试环境下 config 未加载，
 * getter 按既有模式回退到默认值（skew 系数 0.5、上限 0.10，vol 系数 0.5、
 * 上限 0.10，目标价偏离阈值 0.05），以下断言按默认值编写。</p>
 */
class QuoteEngineTest {

    private static MarketState state(
            long mid, long lastPrice, long inventory, long target, double volatility, double baseSpread) {
        return new MarketState(mid, lastPrice, mid, inventory, target, volatility, baseSpread, false);
    }

    // ---------- 基础价差 ----------

    @Test
    void neutralStateKeepsBaseSpread() {
        Quote quote = QuoteEngine.buildQuote(state(10_000, 10_000, 500, 500, 0.0, 0.02))
                .orElseThrow();
        assertEquals(9_800L, quote.bidAsLong());
        assertEquals(10_200L, quote.askAsLong());
    }

    // ---------- 库存偏斜 ----------

    @Test
    void highInventoryShiftsQuotesDown() {
        // 库存 1000/500：skew = 0.5×(2-1) = 0.5 → 钳制 0.10，整体下移 1000
        Quote quote = QuoteEngine.buildQuote(state(10_000, 10_000, 1_000, 500, 0.0, 0.02))
                .orElseThrow();
        assertEquals(8_800L, quote.bidAsLong());
        assertEquals(9_200L, quote.askAsLong());
    }

    @Test
    void lowInventoryShiftsQuotesUp() {
        // 库存 0/500：skew = 0.5×(0-1) = -0.5 → 钳制 -0.10，整体上移 1000
        Quote quote =
                QuoteEngine.buildQuote(state(10_000, 10_000, 0, 500, 0.0, 0.02)).orElseThrow();
        assertEquals(10_800L, quote.bidAsLong());
        assertEquals(11_200L, quote.askAsLong());
    }

    // ---------- 波动率价差 ----------

    @Test
    void volatilityWidensSpread() {
        // volAdj = min(0.20×0.5, 0.10) = 0.10，双边各加宽 1000
        Quote quote = QuoteEngine.buildQuote(state(10_000, 10_000, 500, 500, 0.20, 0.02))
                .orElseThrow();
        assertEquals(8_800L, quote.bidAsLong());
        assertEquals(11_200L, quote.askAsLong());
        assertTrue(quote.askAsLong() - quote.bidAsLong() > 400);
    }

    // ---------- 均值回归 ----------

    @Test
    void meanReversionPullsBackRunawayPrice() {
        // 现价 12000 偏离 mid 10000 达 20%（> 阈值 5%）：pull = 0.20×0.5 = 0.10，报价 ×0.9
        Quote quote = QuoteEngine.buildQuote(state(10_000, 12_000, 500, 500, 0.0, 0.02))
                .orElseThrow();
        assertEquals(8_820L, quote.bidAsLong());
        assertEquals(9_180L, quote.askAsLong());
    }

    @Test
    void meanReversionPullsUpDepressedPrice() {
        // 现价 8000（-20%）：pull = -0.10，报价 ×1.1
        Quote quote = QuoteEngine.buildQuote(state(10_000, 8_000, 500, 500, 0.0, 0.02))
                .orElseThrow();
        assertEquals(10_780L, quote.bidAsLong());
        assertEquals(11_220L, quote.askAsLong());
    }

    @Test
    void priceWithinThresholdIsUntouched() {
        // 偏离 3% ≤ 阈值 5%：均值回归不介入
        Quote quote = QuoteEngine.buildQuote(state(10_000, 10_300, 500, 500, 0.0, 0.02))
                .orElseThrow();
        assertEquals(9_800L, quote.bidAsLong());
        assertEquals(10_200L, quote.askAsLong());
    }

    // ---------- 往返必亏 ----------

    @Test
    void roundTripAlwaysLosesAcrossExtremeStates() {
        long[] mids = {1L, 10L, 100L, 10_000L, 1_000_000L};
        long[][] inventories = {{0L, 500L}, {250L, 500L}, {500L, 500L}, {1_000L, 500L}, {10_000L, 500L}, {500L, 0L}};
        double[] volatilities = {0.0, 0.01, 0.2, 1.0};
        long[] lastPrices = {8_000L, 10_000L, 12_000L};
        for (long mid : mids) {
            for (long[] inv : inventories) {
                for (double vol : volatilities) {
                    for (long last : lastPrices) {
                        MarketState s = new MarketState(mid, last, mid, inv[0], inv[1], vol, 0.02, false);
                        Quote quote = QuoteEngine.buildQuote(s).orElseThrow();
                        assertTrue(
                                quote.askAsLong() > quote.bidAsLong(),
                                "ask-bid 必须 > 0：mid=" + mid + " inv=" + inv[0] + "/" + inv[1] + " vol=" + vol + " last="
                                        + last);
                        assertTrue(quote.bidAsLong() >= 1);
                    }
                }
            }
        }
    }

    @Test
    void minSpreadEnforcedEvenIfStrategiesInvert() {
        Quote inverted = new Quote(100.0, 200.0, 150.0).enforceMinSpread(0.005);
        assertTrue(inverted.askAsLong() > inverted.bidAsLong());
    }

    // ---------- 策略可单独关闭 ----------

    @Test
    void strategiesCanBeDisabledIndividually() {
        MarketState s = state(10_000, 10_000, 1_000, 500, 0.20, 0.02);

        // 全关 → 只有基础价差
        Quote bare = QuoteEngine.buildQuote(s, List.of()).orElseThrow();
        assertEquals(9_800L, bare.bidAsLong());
        assertEquals(10_200L, bare.askAsLong());

        // 只开库存偏斜
        Quote skewOnly =
                QuoteEngine.buildQuote(s, List.of(new InventorySkewStrategy())).orElseThrow();
        assertEquals(8_800L, skewOnly.bidAsLong());
        assertEquals(9_200L, skewOnly.askAsLong());

        // 只开波动率价差
        Quote volOnly = QuoteEngine.buildQuote(s, List.of(new VolatilitySpreadStrategy()))
                .orElseThrow();
        assertEquals(8_800L, volOnly.bidAsLong());
        assertEquals(11_200L, volOnly.askAsLong());

        // 只开均值回归（现价=mid，无偏离 → 不调整）
        Quote targetOnly =
                QuoteEngine.buildQuote(s, List.of(new TargetPriceStrategy())).orElseThrow();
        assertEquals(9_800L, targetOnly.bidAsLong());
        assertEquals(10_200L, targetOnly.askAsLong());

        // 默认链全开：偏斜下移 1000 后波动率再加宽 1000
        Quote full = QuoteEngine.buildQuote(s).orElseThrow();
        assertEquals(7_800L, full.bidAsLong());
        assertEquals(10_200L, full.askAsLong());

        // 默认配置下三个策略都启用
        assertEquals(3, QuoteEngine.activeStrategies().size());
        assertEquals(
                List.of("target-price", "inventory-skew", "volatility-spread"),
                QuoteEngine.defaultChain().stream().map(LiquidityStrategy::name).toList());
    }

    @Test
    void abstainingStrategyKeepsPreviousQuote() {
        LiquidityStrategy abstain = new LiquidityStrategy() {
            @Override
            public String name() {
                return "abstain";
            }

            @Override
            public Optional<Quote> adjust(Quote base, MarketState state) {
                return Optional.empty();
            }
        };
        Quote quote = QuoteEngine.buildQuote(state(10_000, 10_000, 500, 500, 0.0, 0.02), List.of(abstain))
                .orElseThrow();
        assertEquals(9_800L, quote.bidAsLong());
        assertEquals(10_200L, quote.askAsLong());
    }

    // ---------- 熔断 / 无效状态 ----------

    @Test
    void haltedOrInvalidStateYieldsEmpty() {
        MarketState halted = new MarketState(10_000, 10_000, 10_000, 500, 500, 0.0, 0.02, true);
        assertTrue(QuoteEngine.buildQuote(halted).isEmpty());
        assertTrue(QuoteEngine.buildQuote(null).isEmpty());
        MarketState zeroMid = new MarketState(0, 0, 0, 500, 500, 0.0, 0.02, false);
        assertTrue(QuoteEngine.buildQuote(zeroMid).isEmpty());
    }

    // ---------- 波动率估计器 ----------

    @Test
    void volatilityEstimatorHandlesEdgeCases() {
        assertEquals(0.0, VolatilityEstimator.estimate(List.of(), 32));
        assertEquals(0.0, VolatilityEstimator.estimate(null, 32));
        assertEquals(0.0, VolatilityEstimator.estimate(List.of(100L), 32));
        assertEquals(0.0, VolatilityEstimator.estimate(List.of(100L, 100L, 100L, 100L), 32), 1e-12);
        assertEquals(0.0, VolatilityEstimator.estimate(List.of(0L, -5L), 32));

        // 100↔110 交替：收益率 ±ln(1.1)，标准差 ≈ 0.0953
        double alternating = VolatilityEstimator.estimate(List.of(100L, 110L, 100L, 110L, 100L), 32);
        assertTrue(alternating > 0.09 && alternating < 0.10, "vol=" + alternating);

        // 平稳序列波动更小
        double calm = VolatilityEstimator.estimate(List.of(100L, 101L, 102L, 103L), 32);
        assertTrue(calm < alternating);
        assertTrue(calm >= 0.0);
    }
}
