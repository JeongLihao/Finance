package finance.market.liquidity;

import finance.commodity.CommodityInventoryManager;
import finance.config.FinanceConfig;
import finance.market.MarketMakerLedger;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPriceEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 做市商统一报价入口 —— 商品 / 股票双边报价与熔断判断。
 *
 * <p>Phase 2（见 npc-market-making-design.md §3）：</p>
 * <ul>
 *   <li>商品：mid = MarketPrice 中间价，库存水位 = NPC 实际库存 / REFERENCE_STOCK，
 *       基础价差沿用 MarketPrice 自身 spread（默认 5%）；</li>
 *   <li>股票：mid = fairValue，现价 = Stock 当前价，库存 = 股票子账本库存，
 *       基础价差复用 {@link FinanceConfig#stockMarketMakerSpread()}（默认 2%）；</li>
 *   <li>熔断：|现价 - 参考价| / 参考价 &gt; 熔断阈值（默认 30%）时暂停报价，
 *       偏离回归后自动恢复；</li>
 *   <li>做市商报价绝不写入 {@code marketdata.OrderBookService}（它只聚合玩家订单）。</li>
 * </ul>
 *
 * <p>MarketPrice 的参数（REFERENCE_STOCK、价格钳制、动量衰减）与
 * {@code getBidPrice()/getAskPrice()} 的原 spread 行为保持不动；
 * 本类只新增"价差/报价"层的动态报价，供 NpcMarketMaker 交易路径与 GUI 使用。</p>
 */
public final class MarketMakerQuotes {

    /** 商品市场。 */
    public static final String MARKET_COMMODITY = "commodity";

    /** 股票市场。 */
    public static final String MARKET_STOCK = "stock";

    /** 波动率估计窗口（最近快照数）。 */
    private static final int VOLATILITY_WINDOW = 32;

    private MarketMakerQuotes() {
    }

    /**
     * 该标的做市商是否熔断（暂停报价）。
     *
     * @param market "commodity" / "stock"，其他值返回 false
     * @param symbol 商品 ID / 股票代码
     */
    public static boolean isHalted(String market, String symbol) {
        double threshold = FinanceConfig.marketMakerCircuitBreakerThreshold();
        if (!(threshold >= 0.0) || symbol == null || symbol.isEmpty()) {
            return false;
        }
        // 管理员手动暂停（Phase 4）：先于熔断判断
        if (MARKET_COMMODITY.equals(market)
                && finance.market.MarketMakerPauseState.isPaused(
                        finance.market.MarketMakerLedger.SubMarket.COMMODITY)) {
            return true;
        }
        if (MARKET_STOCK.equals(market)
                && finance.market.MarketMakerPauseState.isPaused(
                        finance.market.MarketMakerLedger.SubMarket.STOCK)) {
            return true;
        }
        if (MARKET_COMMODITY.equals(market)) {
            MarketPrice mp = NpcMarketMaker.getMarketPrice(symbol);
            if (mp == null) {
                return false;
            }
            // 商品熔断锚定成交动能（瞬态偏离），而非静态 basePrice：
            // mid 本来就会随供需曲线大幅偏离 basePrice（模型正常行为），
            // 真正的"被单边吃光"信号是动能项 |tradeMomentum| 超阈值。
            return Math.abs(mp.getTradeMomentum()) > threshold;
        }
        if (MARKET_STOCK.equals(market)) {
            Stock stock = StockMarketManager.getStock(StockMarketManager.normalizeSymbol(symbol));
            if (stock == null) {
                return false;
            }
            return deviates(stock.getLastPrice(), stock.getFairValue(), threshold);
        }
        return false;
    }

    /**
     * 获取做市商当前双边报价（Phase 4 GUI 与交易路径共用，方法名保持稳定）。
     *
     * @param market "commodity" / "stock"
     * @param symbol 商品 ID / 股票代码
     * @return 报价视图；熔断时 halted=true；标的未知时返回 null
     */
    public static QuoteView getQuote(String market, String symbol) {
        if (MARKET_COMMODITY.equals(market)) {
            return commodityQuote(symbol);
        }
        if (MARKET_STOCK.equals(market)) {
            return stockQuote(symbol);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // 商品
    // ---------------------------------------------------------------

    private static QuoteView commodityQuote(String commodityId) {
        if (commodityId == null || commodityId.isEmpty()) {
            return null;
        }
        return commodityQuote(NpcMarketMaker.getMarketPrice(commodityId));
    }

    /** 某个商品价格对象的做市报价：玩家实际成交用它，界面显示的买卖价也用它。 */
    public static QuoteView commodityQuote(MarketPrice mp) {
        if (mp == null) {
            return null;
        }
        String commodityId = mp.getCommodityId();
        long mid = mp.getMidPrice();
        long basePrice = mp.getBasePrice();
        long inventory = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, commodityId);
        List<Long> prices = new ArrayList<>(VOLATILITY_WINDOW);
        List<MarketPrice.PriceSnapshot> snapshots = mp.getSnapshots();
        for (int i = Math.max(0, snapshots.size() - VOLATILITY_WINDOW); i < snapshots.size(); i++) {
            prices.add(snapshots.get(i).getPrice());
        }
        double volatility = VolatilityEstimator.estimate(prices, VOLATILITY_WINDOW);
        double momentum = mp.getTradeMomentum();
        boolean halted = Math.abs(momentum) > FinanceConfig.marketMakerCircuitBreakerThreshold();
        MarketState state = new MarketState(mid, mid, basePrice, inventory,
                mp.getReferenceStock(), volatility, mp.getSpread(), halted);
        return toView(MARKET_COMMODITY, commodityId, state,
                haltReason(momentum));
    }

    // ---------------------------------------------------------------
    // 股票
    // ---------------------------------------------------------------

    private static QuoteView stockQuote(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }
        String normalized = StockMarketManager.normalizeSymbol(symbol);
        Stock stock = StockMarketManager.getStock(normalized);
        if (stock == null) {
            return null;
        }
        long fairValue = stock.getFairValue();
        long lastPrice = stock.getLastPrice();
        long inventory = MarketMakerLedger.getStockInventory(normalized);
        long target = MarketMakerLedger.targetInventory(stock.getFloatShares());
        List<Long> prices = new ArrayList<>(VOLATILITY_WINDOW);
        List<StockPriceEngine.PriceSnapshot> snapshots = stock.getSnapshots();
        for (int i = Math.max(0, snapshots.size() - VOLATILITY_WINDOW); i < snapshots.size(); i++) {
            prices.add(snapshots.get(i).getPrice());
        }
        double volatility = VolatilityEstimator.estimate(prices, VOLATILITY_WINDOW);
        boolean halted = deviates(lastPrice, fairValue, FinanceConfig.marketMakerCircuitBreakerThreshold());
        MarketState state = new MarketState(fairValue, lastPrice, fairValue, inventory,
                target, volatility, FinanceConfig.stockMarketMakerSpread(), halted);
        return toView(MARKET_STOCK, normalized, state,
                haltReason(lastPrice, fairValue, "股价"));
    }

    private static QuoteView toView(String market, String symbol, MarketState state, String haltReason) {
        if (state.isHalted()) {
            return new QuoteView(market, symbol, state.getMid(), 0L, 0L, true, haltReason);
        }
        Optional<Quote> quote = QuoteEngine.buildQuote(state);
        if (quote.isEmpty()) {
            return new QuoteView(market, symbol, state.getMid(), 0L, 0L, true, "报价引擎无有效报价。");
        }
        Quote q = quote.get();
        // 发布层 long 钳制：ask - bid > 0 恒成立（往返必亏）
        long bid = q.bidAsLong();
        long ask = Math.max(bid + 1L, q.askAsLong());
        return new QuoteView(market, symbol, state.getMid(), bid, ask, false, null);
    }

    // ---------------------------------------------------------------
    // 熔断
    // ---------------------------------------------------------------

    private static boolean deviates(long lastPrice, long referencePrice, double threshold) {
        if (referencePrice <= 0 || lastPrice <= 0) {
            return false;
        }
        return (double) Math.abs(lastPrice - referencePrice) / (double) referencePrice > threshold;
    }

    private static String haltReason(long lastPrice, long referencePrice, String name) {
        if (referencePrice <= 0) {
            return name + "参考价异常，做市商暂停报价。";
        }
        double pct = (double) (lastPrice - referencePrice) / (double) referencePrice * 100.0;
        return String.format("%s偏离参考价 %+.1f%%，超过熔断阈值，做市商暂停报价。", name, pct);
    }

    /** 商品熔断原因：成交动能（瞬态冲击）超过阈值。 */
    private static String haltReason(double tradeMomentum) {
        return String.format("成交动量 %+.1f%% 超过熔断阈值，做市商暂停报价（动能随时间衰减后自动恢复）。",
                tradeMomentum * 100.0);
    }
}
