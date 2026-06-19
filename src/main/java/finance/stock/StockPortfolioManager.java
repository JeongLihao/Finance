package finance.stock;

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
        return getPortfolio(playerId).getOrDefault(symbol, new StockHolding(0, 0));
    }

    public static void addHolding(UUID playerId, String symbol, long quantity, long price) {
        Map<String, StockHolding> portfolio = getPortfolio(playerId);
        StockHolding holding = portfolio.computeIfAbsent(symbol, key -> new StockHolding(0, 0));
        holding.add(quantity, price);
        EconomySavedData.markDirty();
    }

    public static boolean removeHolding(UUID playerId, String symbol, long quantity) {
        Map<String, StockHolding> portfolio = getPortfolio(playerId);
        StockHolding holding = portfolio.get(symbol);
        if (holding == null || !holding.remove(quantity)) {
            return false;
        }
        if (holding.getQuantity() == 0) {
            portfolio.remove(symbol);
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

    public static void putHoldingDirect(UUID playerId, String symbol, StockHolding holding) {
        getPortfolio(playerId).put(symbol, holding);
    }
}
