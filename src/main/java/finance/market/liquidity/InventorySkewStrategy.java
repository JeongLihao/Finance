package finance.market.liquidity;

import finance.config.FinanceConfig;
import java.util.Optional;

/**
 * 库存偏斜策略 —— 按设计 §2.2 公式调整：
 * <pre>
 *   skew = skew系数 × (库存水位 - 1)
 *   bid = mid × (1 - baseSpread - skew - volAdj)
 *   ask = mid × (1 + baseSpread - skew + volAdj)
 * </pre>
 * <p>本策略负责 skew 一项（volAdj 由 {@link VolatilitySpreadStrategy} 负责），
 * 在链中实现为对当前报价整体平移 {@code mid × skew}：
 * 库存高于目标 → 报价下移（鼓励玩家买走）；低于目标 → 上移（高价吸库存）。</p>
 */
public final class InventorySkewStrategy implements LiquidityStrategy {

    @Override
    public String name() {
        return "inventory-skew";
    }

    @Override
    public boolean enabled() {
        return FinanceConfig.marketMakerInventorySkewEnabled();
    }

    @Override
    public Optional<Quote> adjust(Quote base, MarketState state) {
        if (base == null) {
            return Optional.empty();
        }
        double skewFactor = FinanceConfig.marketMakerSkewFactor();
        double maxSkew = FinanceConfig.marketMakerMaxSkew();
        double skew = clamp(skewFactor * (state.inventoryLevel() - 1.0), -maxSkew, maxSkew);
        if (skew == 0.0) {
            return Optional.of(base);
        }
        double shift = base.mid() * skew;
        return Optional.of(new Quote(base.mid(), base.bid() - shift, base.ask() - shift));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
