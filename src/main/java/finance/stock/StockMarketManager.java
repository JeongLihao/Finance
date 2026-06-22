package finance.stock;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 股票市场第一版：系统公司股票 + 当前价直接买卖。
 */
public class StockMarketManager {

    private static final long DEFAULT_TOTAL_SHARES = 10_000L;
    private static final Map<String, Stock> STOCKS = new LinkedHashMap<>();

    public static void seedSystemStocksIfNeeded() {
        for (Company company : CompanyManager.getCompanies()) {
            if (company.isPlayerOwned()) {
                continue;
            }
            if (hasStockForCompany(company)) {
                continue;
            }
            String symbol = symbolForCompany(company);
            if (STOCKS.containsKey(symbol)) {
                continue;
            }
            long price = Math.max(1, company.getEstimatedValue() / DEFAULT_TOTAL_SHARES);
            STOCKS.put(symbol, new Stock(
                    symbol,
                    company.getName(),
                    company.getCompanyId(),
                    DEFAULT_TOTAL_SHARES,
                    DEFAULT_TOTAL_SHARES,
                    price,
                    price,
                    0
            ));
        }
        EconomySavedData.markDirty();
    }

    private static boolean hasStockForCompany(Company company) {
        for (Stock stock : STOCKS.values()) {
            if (stock.getCompanyId().equals(company.getCompanyId())) {
                return true;
            }
        }
        return false;
    }

    public static Collection<Stock> getStocks() {
        return STOCKS.values();
    }

    public static Stock getStock(String symbol) {
        return STOCKS.get(normalizeSymbol(symbol));
    }

    public static void putStockDirect(Stock stock) {
        STOCKS.put(stock.getSymbol(), stock);
    }

    /** 根据公司 ID 移除对应股票，返回被移除的股票（可能为 null） */
    public static Stock removeStockByCompanyId(UUID companyId) {
        Iterator<Map.Entry<String, Stock>> it = STOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Stock stock = it.next().getValue();
            if (stock.getCompanyId().equals(companyId)) {
                it.remove();
                EconomySavedData.markDirty();
                return stock;
            }
        }
        return null;
    }

    public static void clearStocks() {
        STOCKS.clear();
    }

    public static void resetDayStats() {
        for (Stock stock : STOCKS.values()) {
            stock.newDayReset();
        }
        EconomySavedData.markDirty();
    }

    public static void updatePricesFromCompaniesAndMarket() {
        boolean changed = false;
        for (Stock stock : STOCKS.values()) {
            Company company = CompanyManager.getCompany(stock.getCompanyId());
            if (company == null || stock.getTotalShares() <= 0) {
                continue;
            }

            long valuationPrice = Math.max(1, company.getEstimatedValue() / stock.getTotalShares());
            double commodityChange = averageCommodityChange(company.getType());
            double marketFactor = 1.0 + (commodityChange / 100.0) * 0.35;
            marketFactor = Math.max(0.60, Math.min(1.60, marketFactor));

            long targetPrice = Math.max(1, Math.round(valuationPrice * marketFactor));
            long smoothedPrice = Math.max(1, Math.round(stock.getLastPrice() * 0.75 + targetPrice * 0.25));
            changed |= stock.setLastPrice(smoothedPrice);
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    public static TradeResult buy(UUID playerId, String symbol, long quantity) {
        Stock stock = getStock(symbol);
        if (stock == null) return TradeResult.fail("未知股票: " + symbol);
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (stock.getAvailableShares() < quantity) {
            return TradeResult.fail("流通股不足，可买: " + stock.getAvailableShares());
        }

        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("交易金额过大。");
        }
        long totalCost = MathUtil.multiplyExactOrNegative1(stock.getLastPrice(), (int) quantity);
        if (totalCost <= 0) return TradeResult.fail("交易金额过大。");
        if (!AccountManager.withdraw(playerId, totalCost)) {
            return TradeResult.fail("余额不足，需要: " + totalCost);
        }
        if (!stock.removeAvailableShares(quantity)) {
            AccountManager.deposit(playerId, totalCost);
            return TradeResult.fail("流通股不足。");
        }
        StockPortfolioManager.addHolding(playerId, stock.getSymbol(), quantity, stock.getLastPrice());
        stock.recordTrade(stock.getLastPrice(), quantity);
        EconomySavedData.markDirty();
        return TradeResult.ok("已买入 " + quantity + " 股 " + stock.getSymbol() + "，成交价: " + stock.getLastPrice());
    }

    public static TradeResult sell(UUID playerId, String symbol, long quantity) {
        Stock stock = getStock(symbol);
        if (stock == null) return TradeResult.fail("未知股票: " + symbol);
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        StockHolding holding = StockPortfolioManager.getHolding(playerId, stock.getSymbol());
        if (holding.getQuantity() < quantity) {
            return TradeResult.fail("持仓不足，拥有: " + holding.getQuantity());
        }

        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("交易金额过大。");
        }
        long proceeds = MathUtil.multiplyExactOrNegative1(stock.getLastPrice(), (int) quantity);
        if (proceeds <= 0) return TradeResult.fail("交易金额过大。");
        if (!StockPortfolioManager.removeHolding(playerId, stock.getSymbol(), quantity)) {
            return TradeResult.fail("持仓不足。");
        }
        stock.addAvailableShares(quantity);
        stock.recordTrade(stock.getLastPrice(), quantity);
        AccountManager.deposit(playerId, proceeds);
        EconomySavedData.markDirty();
        return TradeResult.ok("已卖出 " + quantity + " 股 " + stock.getSymbol() + "，成交价: " + stock.getLastPrice());
    }

    public static String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static String symbolForCompany(Company company) {
        String typeSymbol = symbolForType(company.getType());
        if (!typeSymbol.isEmpty()) {
            return typeSymbol;
        }
        String cleaned = company.getName().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() >= 5) {
            return cleaned.substring(0, 5);
        }
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return company.getType().name().substring(0, Math.min(5, company.getType().name().length()));
    }

    private static String symbolForType(CompanyType type) {
        return switch (type) {
            case RAW_MATERIALS -> "铁锭";
            case BUILDING_BLOCKS -> "石头";
            case FOOD -> "小麦";
        };
    }

    private static double averageCommodityChange(CompanyType type) {
        double total = 0;
        int count = 0;
        for (String commodityId : type.getCommodityIds()) {
            MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(commodityId);
            if (mp == null) {
                continue;
            }
            total += mp.getDayChange();
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    public record TradeResult(boolean success, String message) {
        public static TradeResult ok(String message) {
            return new TradeResult(true, message);
        }

        public static TradeResult fail(String message) {
            return new TradeResult(false, message);
        }
    }
}
