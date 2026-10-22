package finance.market.liquidity;

/**
 * 做市商双边报价 —— 策略链在 double 精度下逐级调整，最终发布时转 long。
 *
 * <p>Phase 2 做市商报价（见 npc-market-making-design.md §2.2/§2.3）：
 * mid 为中间价锚（商品 = MarketPrice 中间价，股票 = fairValue），
 * bid/ask 从"中间价 ± 基础价差"起步，经策略链调整后输出。</p>
 */
public record Quote(double mid, double bid, double ask) {

    /**
     * 从中间价和基础价差构建初始报价：
     * bid = mid × (1 - spread)，ask = mid × (1 + spread)。
     */
    public static Quote fromMidSpread(double mid, double baseSpread) {
        double safeSpread = Math.max(0.0, Math.min(0.5, baseSpread));
        return new Quote(mid, mid * (1.0 - safeSpread), mid * (1.0 + safeSpread));
    }



    /**
     * 策略链之后强制"往返必亏"：ask - bid &gt; 0 恒成立。
     * 兜底最小价差 = max(1, mid × minSpreadRatio)，买入立刻卖回必亏，无刷钱循环。
     */
    public Quote enforceMinSpread(double minSpreadRatio) {
        double minGap = Math.max(1.0, mid * Math.max(0.0, minSpreadRatio));
        double safeBid = Math.max(1.0, bid);
        double safeAsk = Math.max(safeBid + minGap, ask);
        return new Quote(mid, safeBid, safeAsk);
    }

    /** 最终发布买入价（long 金额，沿用仓库惯例），最低为 1。 */
    public long bidAsLong() {
        return Math.max(1L, Math.round(bid));
    }

    /**
     * 最终发布卖出价（long 金额），恒大于 {@link #bidAsLong()}。
     * 发布层再做一次 long 钳制，与 {@link #enforceMinSpread(double)} 组成双保险。
     */
    public long askAsLong() {
        return Math.max(bidAsLong() + 1L, Math.round(ask));
    }
}
