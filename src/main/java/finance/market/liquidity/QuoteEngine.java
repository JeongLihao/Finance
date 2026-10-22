package finance.market.liquidity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 做市商报价引擎 —— 按策略链顺序跑出最终双边报价。
 *
 * <p>默认链：均值回归 → 库存偏斜 → 波动率价差（默认全开，开关走 FinanceConfig）。
 * 策略链之后强制 {@code ask - bid > 0}（往返必亏）；熔断或中间价无效时返回 empty。</p>
 */
public final class QuoteEngine {

    /** 默认策略链（顺序固定）。 */
    private static final List<LiquidityStrategy> DEFAULT_CHAIN =
            List.of(new TargetPriceStrategy(), new InventorySkewStrategy(), new VolatilitySpreadStrategy());

    /** 策略链之后的兜底最小价差（相对 mid），保证往返必亏。 */
    private static final double MIN_SPREAD_RATIO = 0.005;

    private QuoteEngine() {}

    /**
     * 按当前启用的策略链构建报价。
     *
     * @return 最终报价；熔断 / 中间价无效时 empty
     */
    public static Optional<Quote> buildQuote(MarketState state) {
        return buildQuote(state, activeStrategies());
    }

    /**
     * 用显式策略链构建报价（测试与管理工具用，可单独开关策略）。
     */
    public static Optional<Quote> buildQuote(MarketState state, List<LiquidityStrategy> chain) {
        if (state == null || state.getMid() <= 0 || state.isHalted()) {
            return Optional.empty();
        }
        Quote current = Quote.fromMidSpread(state.getMid(), state.getBaseSpread());
        if (chain != null) {
            for (LiquidityStrategy strategy : chain) {
                if (strategy == null) {
                    continue;
                }
                Optional<Quote> adjusted = strategy.adjust(current, state);
                if (adjusted.isPresent()) {
                    current = adjusted.get();
                }
            }
        }
        // 反刷钱：往返必亏，ask - bid > 0 恒成立
        return Optional.of(current.enforceMinSpread(MIN_SPREAD_RATIO));
    }

    /**
     * 当前启用的策略（按默认顺序，过滤已关闭的）。
     */
    public static List<LiquidityStrategy> activeStrategies() {
        List<LiquidityStrategy> active = new ArrayList<>(DEFAULT_CHAIN.size());
        for (LiquidityStrategy strategy : DEFAULT_CHAIN) {
            if (strategy.enabled()) {
                active.add(strategy);
            }
        }
        return active;
    }

    /**
     * 默认策略链（不过滤开关，测试用）。 */
    static List<LiquidityStrategy> defaultChain() {
        return DEFAULT_CHAIN;
    }

    // ---- 集成入口（Phase 4 GUI/服务端取数用，委托给 MarketMakerQuotes） ----

    /**
     * 该标的做市商是否熔断/暂停报价（委托 {@link MarketMakerQuotes#isHalted}）。
     */
    public static boolean isHalted(String market, String symbol) {
        return MarketMakerQuotes.isHalted(market, symbol);
    }

    /**
     * 获取做市商当前双边报价（委托 {@link MarketMakerQuotes#getQuote}）。
     */
    public static QuoteView getQuote(String market, String symbol) {
        return MarketMakerQuotes.getQuote(market, symbol);
    }
}
