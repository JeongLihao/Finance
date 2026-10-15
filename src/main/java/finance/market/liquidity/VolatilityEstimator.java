package finance.market.liquidity;

import java.util.List;

/**
 * 轻量波动率估计器 —— 对最近 N 个价格算对数收益率的总体标准差。
 *
 * <p>MarketPrice / StockPriceEngine 只有价格快照、没有现成波动率，
 * 做市商报价在此基础上做无状态滚动估计（调用方传入快照价格序列）。</p>
 */
public final class VolatilityEstimator {

    /** 波动率上限钳制，防脏数据把价差撑爆。 */
    private static final double MAX_VOLATILITY = 1.0;

    private VolatilityEstimator() {
    }

    /**
     * @param prices 价格序列（任意顺序，按相邻两项算收益率）
     * @param window 最多取最近多少个价格
     * @return 对数收益率标准差；价格不足 2 个或全非法时返回 0
     */
    public static double estimate(List<Long> prices, int window) {
        if (prices == null || prices.isEmpty() || window < 2) {
            return 0.0;
        }
        int from = Math.max(0, prices.size() - window);
        double sum = 0.0;
        double sumSq = 0.0;
        int count = 0;
        long prev = 0;
        boolean hasPrev = false;
        for (int i = from; i < prices.size(); i++) {
            Long price = prices.get(i);
            if (price == null || price <= 0) {
                continue;
            }
            if (hasPrev && prev > 0) {
                double ret = Math.log((double) price / (double) prev);
                if (Double.isFinite(ret)) {
                    sum += ret;
                    sumSq += ret * ret;
                    count++;
                }
            }
            prev = price;
            hasPrev = true;
        }
        if (count < 1) {
            return 0.0;
        }
        double mean = sum / count;
        double variance = Math.max(0.0, sumSq / count - mean * mean);
        return Math.min(MAX_VOLATILITY, Math.sqrt(variance));
    }
}
