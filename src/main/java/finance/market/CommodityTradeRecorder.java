package finance.market;

import finance.chart.CandlestickService;
import finance.chart.MarketInstrumentType;
import finance.metrics.EconomyMetricsService;
import finance.marketdata.RecentTradeService;
import finance.marketdata.TradeDirection;

import java.util.UUID;

/** Publishes market data only after the caller has fully committed a commodity settlement. */
public final class CommodityTradeRecorder {

    private CommodityTradeRecorder() {
    }

    public static void recordCompletedTrade(UUID buyer, UUID seller, String commodityId,
                                            long price, int quantity, CommodityTradeSource source,
                                            Boolean npcWasBuyer) {
        TradeDirection direction = npcWasBuyer == null || !npcWasBuyer
                ? TradeDirection.BUY : TradeDirection.SELL;
        recordCompletedTrade(buyer, seller, commodityId, price, quantity, source, npcWasBuyer, direction);
    }

    public static void recordCompletedTrade(UUID buyer, UUID seller, String commodityId,
                                            long price, int quantity, CommodityTradeSource source,
                                            Boolean npcWasBuyer, TradeDirection direction) {
        if (buyer == null || seller == null || commodityId == null || commodityId.isBlank()
                || price <= 0 || quantity <= 0 || source == null) return;
        MarketManager.addTradeToHistory(new Trade(buyer, seller, commodityId, price, quantity));
        if (npcWasBuyer != null) {
            NpcMarketMaker.recordNpcTrade(commodityId, npcWasBuyer, quantity, price);
        } else {
            MarketPrice marketPrice = NpcMarketMaker.getMarketPrice(commodityId);
            if (marketPrice != null) marketPrice.recordTrade(price, quantity);
        }
        EconomyMetricsService.recordCommodityTrade(quantity);
        CandlestickService.recordTrade(MarketInstrumentType.COMMODITY, commodityId, price, quantity);
        RecentTradeService.record(MarketInstrumentType.COMMODITY, commodityId, price, quantity, direction);
    }
}
