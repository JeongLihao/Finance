package finance.market.liquidity;

import java.util.Optional;

/**
 * 做市商流动性策略插件（见 npc-market-making-design.md §2.3）。
 *
 * <p>引擎按链调用：{@link #adjust(Quote, MarketState)} 返回调整后的报价；
 * 返回 {@link Optional#empty()} 表示本策略弃权（沿用上一步结果），
 * 返回原样表示本策略不调整。</p>
 */
public interface LiquidityStrategy {

    /** 策略名（日志/管理用）。 */
    String name();

    /** 策略开关（走 FinanceConfig，默认全开）。 */
    default boolean enabled() {
        return true;
    }

    /**
     * 调整报价。
     *
     * @param base  上一步的报价（首步为 mid ± baseSpread）
     * @param state 市场状态
     * @return 调整后的报价；empty 表示弃权
     */
    Optional<Quote> adjust(Quote base, MarketState state);
}
