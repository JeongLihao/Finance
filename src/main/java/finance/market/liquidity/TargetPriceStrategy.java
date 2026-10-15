package finance.market.liquidity;

import finance.config.FinanceConfig;

import java.util.Optional;

/**
 * 均值回归策略 —— 现价偏离中间价超阈值时，把双边报价往中间价方向牵引。
 *
 * <p>对应设计 §2.3 的 TargetPriceStrategy：现价高于 mid 时整体下移报价
 * （施加反向压力、把价格往回拉），低于 mid 时上移；偏离在阈值内时不调整。
 * 商品的现价即中间价（恒不触发），主要作用于股票（现价可偏离 fairValue）。</p>
 */
public final class TargetPriceStrategy implements LiquidityStrategy {

    /** 回归强度：每次把偏离的一半计入报价牵引。 */
    private static final double REVERSION_STRENGTH = 0.5;

    /** 单次牵引上限（相对 mid），防止报价被一次性拉太远。 */
    private static final double MAX_PULL = 0.10;

    @Override
    public String name() {
        return "target-price";
    }

    @Override
    public boolean enabled() {
        return FinanceConfig.marketMakerTargetPriceEnabled();
    }

    @Override
    public Optional<Quote> adjust(Quote base, MarketState state) {
        if (base == null) {
            return Optional.empty();
        }
        long mid = state.getMid();
        long lastPrice = state.getLastPrice();
        if (mid <= 0 || lastPrice <= 0) {
            return Optional.empty();
        }
        double threshold = FinanceConfig.marketMakerTargetPriceDeviationThreshold();
        double deviation = (double) (lastPrice - mid) / (double) mid;
        if (Math.abs(deviation) <= threshold) {
            return Optional.of(base);
        }
        double pull = clamp(deviation * REVERSION_STRENGTH, -MAX_PULL, MAX_PULL);
        double factor = 1.0 - pull;
        if (!(factor > 0.0)) {
            return Optional.empty();
        }
        return Optional.of(new Quote(base.mid(), base.bid() * factor, base.ask() * factor));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
