package finance.stock;

import finance.account.AccountManager;
import finance.data.EconomySavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家股票持仓管理器。
 */
public class StockPortfolioManager {

    private static final Map<UUID, Map<String, StockHolding>> PORTFOLIOS = new HashMap<>();

    public static Map<String, StockHolding> getPortfolio(UUID playerId) {
        return PORTFOLIOS.computeIfAbsent(playerId, id -> new HashMap<>());
    }

    public static StockHolding getHolding(UUID playerId, String symbol) {
        return getPortfolio(playerId).getOrDefault(normalizeSymbol(symbol), new StockHolding(0, 0));
    }

    public static void addHolding(UUID playerId, String symbol, long quantity, long price) {
        Map<String, StockHolding> portfolio = getPortfolio(playerId);
        String normalizedSymbol = normalizeSymbol(symbol);
        StockHolding holding = portfolio.computeIfAbsent(normalizedSymbol, key -> new StockHolding(0, 0));
        holding.add(quantity, price);
        EconomySavedData.markDirty();
    }

    public static boolean removeHolding(UUID playerId, String symbol, long quantity) {
        Map<String, StockHolding> portfolio = getPortfolio(playerId);
        String normalizedSymbol = normalizeSymbol(symbol);
        StockHolding holding = portfolio.get(normalizedSymbol);
        if (holding == null || !holding.remove(quantity)) {
            return false;
        }
        if (holding.getQuantity() == 0) {
            portfolio.remove(normalizedSymbol);
        }
        EconomySavedData.markDirty();
        return true;
    }

    public static Map<UUID, Map<String, StockHolding>> getPortfolios() {
        return PORTFOLIOS;
    }

    public static void clearPortfolios() {
        PORTFOLIOS.clear();
    }

    /**
     * P5：获取某公司股票的所有持股者及持股数（用于分红分账）。
     * 返回 Map: playerId -> quantity
     */
    public static Map<UUID, Long> getHoldingsForCompany(String symbol) {
        Map<UUID, Long> result = new HashMap<>();
        String normalizedSymbol = normalizeSymbol(symbol);

        for (Map.Entry<UUID, Map<String, StockHolding>> portfolioEntry : PORTFOLIOS.entrySet()) {
            StockHolding holding = portfolioEntry.getValue().get(normalizedSymbol);
            if (holding != null && holding.getQuantity() > 0) {
                result.put(portfolioEntry.getKey(), holding.getQuantity());
            }
        }

        return result;
    }

    public static void putHoldingDirect(UUID playerId, String symbol, StockHolding holding) {
        getPortfolio(playerId).put(normalizeSymbol(symbol), holding);
    }

    public static int liquidateHolding(String symbol, long price) {
        String normalizedSymbol = normalizeSymbol(symbol);
        int liquidated = 0;

        for (Map.Entry<UUID, Map<String, StockHolding>> portfolioEntry : PORTFOLIOS.entrySet()) {
            Map<String, StockHolding> portfolio = portfolioEntry.getValue();
            StockHolding holding = portfolio.get(normalizedSymbol);
            if (holding == null) {
                continue;
            }

            long compensation = multiplyOrZero(price, holding.getQuantity());
            if (compensation > 0) {
                AccountManager.deposit(portfolioEntry.getKey(), compensation);
            }
            portfolio.remove(normalizedSymbol);
            liquidated++;
        }

        if (liquidated > 0) {
            PORTFOLIOS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            EconomySavedData.markDirty();
        }
        return liquidated;
    }

    private static long multiplyOrZero(long price, long quantity) {
        try {
            return Math.multiplyExact(price, quantity);
        } catch (ArithmeticException ex) {
            return 0;
        }
    }

    private static String normalizeSymbol(String symbol) {
        return StockMarketManager.normalizeSymbol(symbol);
    }
}
