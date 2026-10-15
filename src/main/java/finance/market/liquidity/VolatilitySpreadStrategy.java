package finance.market.liquidity;

import finance.config.FinanceConfig;

import java.util.Optional;

/**
 * 波动率价差策略 —— 近期波动放大时加宽双边价差自保。
 * <pre>
 *   volAdj = min(vol系数 × 波动率, 上限)
 *   bid -= mid × volAdj；ask += mid × volAdj
 * </pre>
 * <p>波动率来自 {@link VolatilityEstimator} 对价格快照的滚动估计；
 * 无波动（vol ≤ 0）时不调整。</p>
 */
public final class VolatilitySpreadStrategy implements LiquidityStrategy {

    @Override
    public String name() {
        return "volatility-spread";
    }

    @Override
    public boolean enabled() {
        return FinanceConfig.marketMakerVolatilitySpreadEnabled();
    }

    @Override
    public Optional<Quote> adjust(Quote base, MarketState state) {
        if (base == null) {
            return Optional.empty();
        }
        double volatility = state.getVolatility();
        if (!(volatility > 0.0)) {
            return Optional.of(base);
        }
        double volAdj = Math.min(volatility * FinanceConfig.marketMakerVolatilityFactor(),
                FinanceConfig.marketMakerMaxVolatilityAdjust());
        if (!(volAdj > 0.0)) {
            return Optional.of(base);
        }
        double widen = base.mid() * volAdj;
        return Optional.of(new Quote(base.mid(), base.bid() - widen, base.ask() + widen));
    }
}
