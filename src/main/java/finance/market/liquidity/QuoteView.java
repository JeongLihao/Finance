package finance.market.liquidity;

/**
 * 做市商报价视图 —— Phase 4 GUI 与交易路径共用的只读报价。
 *
 * <p>熔断时 bid/ask 为 0、halted=true 并携带原因；标的未知时 getQuote 返回 null。</p>
 */
public record QuoteView(
        String market, String symbol, long mid, long bid, long ask, boolean halted, String haltReason) {}
