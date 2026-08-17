package finance.index;

import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Daily stock, commodity and sector index calculator. */
public final class MarketIndexService {
    public static final String STOCK_COMPOSITE = "stock:composite";
    public static final String COMMODITY_COMPOSITE = "commodity:composite";
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final Map<String, MarketIndexState> STATES = new LinkedHashMap<>();

    private MarketIndexService() {
    }

    public static void closeDay(long mcDay) {
        closeStockIndex(STOCK_COMPOSITE, StockMarketManager.getListedStocks(), mcDay);
        for (CompanyType type : CompanyType.values()) {
            List<Stock> stocks = new ArrayList<>();
            for (Stock stock : StockMarketManager.getListedStocks()) {
                Company company = CompanyManager.getCompany(stock.getCompanyId());
                if (company != null && company.getType() == type) stocks.add(stock);
            }
            closeStockIndex("sector:" + type.name(), stocks, mcDay);
        }
        closeCommodityIndex(mcDay);
        EconomySavedData.markDirty();
    }

    private static void closeStockIndex(String id, Collection<Stock> stocks, long day) {
        List<Stock> valid = stocks.stream()
                .filter(s -> s != null && s.getLastPrice() > 0 && s.getFloatShares() > 0)
                .sorted(Comparator.comparing(Stock::getSymbol)).toList();
        BigInteger raw = BigInteger.ZERO;
        StringBuilder fingerprint = new StringBuilder();
        for (Stock stock : valid) {
            raw = raw.add(BigInteger.valueOf(stock.getLastPrice()).multiply(BigInteger.valueOf(stock.getFloatShares())));
            fingerprint.append(stock.getSymbol()).append(':').append(stock.getFloatShares()).append(';');
        }
        if (raw.signum() > 0) state(id).close(day, new BigDecimal(raw), fingerprint.toString());
    }

    private static void closeCommodityIndex(long day) {
        List<Commodity> commodities = CommodityRegistry.getAllCommodities().stream()
                .filter(c -> c != null && c.getBasePrice() > 0)
                .sorted(Comparator.comparing(Commodity::getId)).toList();
        BigDecimal raw = BigDecimal.ZERO;
        StringBuilder fingerprint = new StringBuilder();
        for (Commodity commodity : commodities) {
            MarketPrice price = NpcMarketMaker.getMarketPrice(commodity.getId());
            if (price == null || price.getMidPrice() <= 0) continue;
            raw = raw.add(BigDecimal.valueOf(price.getMidPrice())
                    .divide(BigDecimal.valueOf(commodity.getBasePrice()), MC), MC);
            fingerprint.append(commodity.getId()).append(';');
        }
        if (raw.signum() > 0) state(COMMODITY_COMPOSITE).close(day, raw, fingerprint.toString());
    }

    public static MarketIndexState state(String id) {
        return STATES.computeIfAbsent(id, MarketIndexState::new);
    }

    public static Map<String, MarketIndexState> states() { return java.util.Collections.unmodifiableMap(STATES); }
    public static void putDirect(MarketIndexState state) { if (state != null) STATES.put(state.id(), state); }
    public static void clearDirect() { STATES.clear(); }

    public static double changePercent(String id) {
        List<MarketIndexPoint> points = state(id).history();
        if (points.size() < 2 || points.get(points.size() - 2).value() <= 0) return 0;
        double previous = points.get(points.size() - 2).value();
        return (points.get(points.size() - 1).value() - previous) / previous * 100;
    }
}
