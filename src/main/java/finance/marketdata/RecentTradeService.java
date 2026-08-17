package finance.marketdata;

import finance.chart.MarketInstrumentKey;
import finance.chart.MarketInstrumentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import finance.market.MarketManager;
import finance.stock.StockOrderManager;

public final class RecentTradeService {
    public static final int MAX_TRADES = 20;
    private static final Map<MarketInstrumentKey, List<RecentTradeEntry>> TRADES = new HashMap<>();

    private RecentTradeService() {}

    public static boolean record(MarketInstrumentType type, String id, long price, long quantity,
                                 TradeDirection direction) {
        return record(type, id, finance.chart.CandlestickService.currentMcDay(),
                price, quantity, LocalDateTime.now(), direction);
    }

    public static boolean record(MarketInstrumentType type, String id, long price, long quantity,
                                 LocalDateTime timestamp, TradeDirection direction) {
        return record(type, id, finance.chart.CandlestickService.currentMcDay(),
                price, quantity, timestamp, direction);
    }

    public static boolean record(MarketInstrumentType type, String id, long mcDay,
                                 long price, long quantity, LocalDateTime timestamp, TradeDirection direction) {
        MarketInstrumentKey key = MarketInstrumentKey.tryCreate(type, id);
        if (key == null || mcDay < 0 || price <= 0 || quantity <= 0 || timestamp == null || direction == null) return false;
        List<RecentTradeEntry> entries = TRADES.computeIfAbsent(key, ignored -> new ArrayList<>());
        entries.add(new RecentTradeEntry(mcDay, price, quantity, timestamp, direction));
        if (entries.size() > MAX_TRADES) entries.subList(0, entries.size() - MAX_TRADES).clear();
        return true;
    }

    public static List<RecentTradeEntry> get(MarketInstrumentType type, String id) {
        MarketInstrumentKey key = MarketInstrumentKey.tryCreate(type, id);
        List<RecentTradeEntry> entries = key == null ? null : TRADES.get(key);
        if (entries == null) return List.of();
        List<RecentTradeEntry> newestFirst = new ArrayList<>(entries);
        java.util.Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    public static void clear() { TRADES.clear(); }

    /** Rebuilds the bounded tape from persisted canonical trade histories. */
    public static void rebuildFromHistories() {
        clear();
        for (finance.market.Trade trade : MarketManager.getTradeHistory()) {
            record(MarketInstrumentType.COMMODITY, trade.getCommodityId(), 0,
                    trade.getPrice(), trade.getQuantity(), trade.getTimestamp(), TradeDirection.BUY);
        }
        for (finance.stock.StockTrade trade : StockOrderManager.getTradeHistory()) {
            record(MarketInstrumentType.STOCK, trade.getSymbol(), 0,
                    trade.getPrice(), trade.getQuantity(), trade.getTimestamp(), TradeDirection.BUY);
        }
        for (finance.bondmarket.BondTrade trade : finance.bondmarket.BondMarketManager.trades()) {
            record(MarketInstrumentType.BOND, trade.bondId().toString(), trade.mcDay(),
                    trade.pricePerUnit(), trade.quantity(), trade.timestamp(), TradeDirection.BUY);
        }
        for (finance.futures.FuturesTrade trade : finance.futures.FuturesMarketManager.trades()) {
            record(MarketInstrumentType.FUTURES, trade.contractId().toString(), trade.mcDay(),
                    trade.price(), trade.quantity(), trade.timestamp(), TradeDirection.BUY);
        }
    }
}
