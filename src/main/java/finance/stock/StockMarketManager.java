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
            // 用完整构造：symbol, name, companyId, totalShares, floatShares, ownerShares, currentPrice, fairValue
            STOCKS.put(symbol, new Stock(
                    symbol,
                    company.getName(),
                    company.getCompanyId(),
                    DEFAULT_TOTAL_SHARES,
                    DEFAULT_TOTAL_SHARES,    // floatShares = totalShares（初始全流通）
                    0,                        // ownerShares = 0（系统不持有）
                    price,                    // currentPrice
                    price                     // fairValue（初始等于 currentPrice）
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
        STOCKS.put(normalizeSymbol(stock.getSymbol()), stock);
    }

    public static Stock getStockByCompanyId(UUID companyId) {
        for (Stock stock : STOCKS.values()) {
            if (stock.getCompanyId().equals(companyId)) {
                return stock;
            }
        }
        return null;
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

    /**
     * 每 MC 天调用 —— 基本面更新（新引擎）。
     * 根据公司最新估值，重新计算每只股票的 fairValue，驱动价格均值回归。
     * P3：传递日利润到 PE 系数计算。
     */
    public static void updateFairValuesAndResetDay() {
        boolean changed = false;
        for (Stock stock : STOCKS.values()) {
            Company company = CompanyManager.getCompany(stock.getCompanyId());
            if (company == null || stock.getTotalShares() <= 0) {
                continue;
            }

            long companyValue = company.getEstimatedValue();
            long dailyProfit = Math.max(0, company.getDailyRevenue() - company.getDailyCost()); // P3：传日利润

            stock.updateFairValueAndResetDay(companyValue, stock.getTotalShares(), dailyProfit);
            changed = true;
        }
        if (changed) {
            EconomySavedData.markDirty();
        }
    }

    /**
     * 每分钟调用（FinanceMod.onServerTick 中）—— 动量衰减。
     */
    public static void tickMomentum() {
        for (Stock stock : STOCKS.values()) {
            stock.tickMomentum();
        }
        if (!STOCKS.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    /**
     * 每 3 分钟调用 —— 噪音游走。
     */
    public static void tickNoise() {
        for (Stock stock : STOCKS.values()) {
            stock.tickNoise();
        }
        if (!STOCKS.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    /**
     * 动量或噪音变化后立即重算价格。
     */
    public static void recalculateAllPrices() {
        for (Stock stock : STOCKS.values()) {
            stock.recalculateFromCurrent();
        }
        if (!STOCKS.isEmpty()) {
            EconomySavedData.markDirty();
        }
    }

    public static TradeResult buy(UUID playerId, String symbol, long quantity) {
        Stock stock = getStock(symbol);
        if (stock == null) return TradeResult.fail("未知股票: " + symbol);
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("数量过大。");
        }

        // 按当前价一步到位成交（做市商 fallback）
        long price = stock.getLastPrice();
        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(playerId, symbol, price, (int) quantity);
        return new TradeResult(result.success(), result.message());
    }

    public static TradeResult placeLimitBuy(UUID playerId, String symbol, long price, long quantity) {
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (price <= 0) return TradeResult.fail("价格必须大于 0。");
        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("数量过大。");
        }
        StockOrderManager.OrderResult result = StockOrderManager.placeBuyOrder(playerId, symbol, price, (int) quantity);
        return new TradeResult(result.success(), result.message());
    }

    public static TradeResult sell(UUID playerId, String symbol, long quantity) {
        Stock stock = getStock(symbol);
        if (stock == null) return TradeResult.fail("未知股票: " + symbol);
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("数量过大。");
        }

        // 按当前价一步到位成交（做市商 fallback）
        long price = stock.getLastPrice();
        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(playerId, symbol, price, (int) quantity);
        return new TradeResult(result.success(), result.message());
    }

    public static TradeResult placeLimitSell(UUID playerId, String symbol, long price, long quantity) {
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (price <= 0) return TradeResult.fail("价格必须大于 0。");
        if (quantity > Integer.MAX_VALUE) {
            return TradeResult.fail("数量过大。");
        }
        StockOrderManager.OrderResult result = StockOrderManager.placeSellOrder(playerId, symbol, price, (int) quantity);
        return new TradeResult(result.success(), result.message());
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


    // ================================================================
    // 股票订单簿（P2）
    // ================================================================

    public static Collection<StockOrder> getOrders() {
        return StockOrderManager.getOrders();
    }

    public static Collection<StockOrder> getOrdersBySymbol(String symbol) {
        return StockOrderManager.getOrdersBySymbol(symbol);
    }

    public static Collection<StockTrade> getStockTradeHistory() {
        return StockOrderManager.getTradeHistory();
    }

    public static boolean cancelStockOrder(UUID orderId, UUID playerId) {
        return StockOrderManager.cancelOrder(orderId, playerId);
    }

    public static void clearStockOrders() {
        StockOrderManager.clearOrders();
    }

    public static void clearStockTradeHistory() {
        StockOrderManager.clearTradeHistory();
    }

    public static void addStockOrderDirect(StockOrder order) {
        StockOrderManager.addOrderDirect(order);
    }

    public static void addStockTradeDirect(StockTrade trade) {
        // 用于持久化恢复，直接添加到历史记录
        StockOrderManager.addTradeDirectly(trade);
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
