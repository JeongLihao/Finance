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

    /**
     * 每 MC 天调用 —— 基本面更新（新引擎）。
     * 根据公司最新估值，重新计算每只股票的 fairValue，驱动价格均值回归。
     * 替代旧的 updatePricesFromCompaniesAndMarket 覆盖式逻辑。
     */
    public static void updateFairValuesAndResetDay() {
        boolean changed = false;
        for (Stock stock : STOCKS.values()) {
            Company company = CompanyManager.getCompany(stock.getCompanyId());
            if (company == null || stock.getTotalShares() <= 0) {
                continue;
            }

            long companyValue = company.getEstimatedValue();
            long dailyProfit = 0; // P3 时从 Company.getDailyProfit() 读取

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
    }

    /**
     * 每 3 分钟调用 —— 噪音游走。
     */
    public static void tickNoise() {
        for (Stock stock : STOCKS.values()) {
            stock.tickNoise();
        }
    }

    /**
     * 动量或噪音变化后立即重算价格。
     */
    public static void recalculateAllPrices() {
        for (Stock stock : STOCKS.values()) {
            stock.recalculateFromCurrent();
        }
    }

    public static TradeResult buy(UUID playerId, String symbol, long quantity) {
        Stock stock = getStock(symbol);
        if (stock == null) return TradeResult.fail("未知股票: " + symbol);
        if (quantity <= 0) return TradeResult.fail("数量必须大于 0。");
        if (stock.getFloatShares() < quantity) {
            return TradeResult.fail("流通股不足，可买: " + stock.getFloatShares());
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

        // 记录成交，通知定价引擎（推高价格）
        stock.recordTrade(stock.getLastPrice(), quantity, true);

        StockPortfolioManager.addHolding(playerId, stock.getSymbol(), quantity, stock.getLastPrice());
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
        // 记录成交，通知定价引擎（压低价格）
        stock.recordTrade(stock.getLastPrice(), quantity, false);

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


    public record TradeResult(boolean success, String message) {
        public static TradeResult ok(String message) {
            return new TradeResult(true, message);
        }

        public static TradeResult fail(String message) {
            return new TradeResult(false, message);
        }
    }
}
