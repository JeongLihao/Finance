package finance.margin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家保证金账户（融资融券信用账户）。
 *
 * <p>与玩家自由资金账户（AccountManager）隔离：
 * <ul>
 *   <li>融资借入的资金不进入自由账户，直接由券商结算给卖方；</li>
 *   <li>融券卖出的资金冻结在信用账户，只能用于买回还券；</li>
 *   <li>现金保证金由玩家自由存入/提取（提取受维持保证金率约束）。</li>
 * </ul>
 */
public class MarginAccount {

    private final UUID playerId;

    /** 现金保证金（玩家存入，可追加；提取受保证金率约束） */
    private long cashCollateral;

    /** 融资负债本金 */
    private long financedDebt;

    /** 融资应计利息（未还） */
    private long financedInterestOwed;

    /** 融资买入的持仓：symbol -> 股数（不计入并购举牌门槛） */
    private final Map<String, Long> financedShares = new HashMap<>();

    /** 融券在途：symbol -> 借股数量 */
    private final Map<String, Long> shortPositions = new HashMap<>();

    /** 融券卖出冻结资金：symbol -> 金额（只能用于买回还券） */
    private final Map<String, Long> shortProceeds = new HashMap<>();

    /** 融券应计利息：symbol -> 金额 */
    private final Map<String, Long> shortInterestOwed = new HashMap<>();

    /** 融券卖出均价：symbol -> 价格（用于融券利息计提基数） */
    private final Map<String, Long> shortSellPrices = new HashMap<>();

    /** 穿仓不良债务（强平后缺口）。不清零、不计息，还清前冻结新开仓。 */
    private long badDebt;

    /** 上次计息的 MC 天（防重复计息） */
    private long lastInterestDay = -1;

    public MarginAccount(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }
    public long getCashCollateral() { return cashCollateral; }
    public long getFinancedDebt() { return financedDebt; }
    public long getFinancedInterestOwed() { return financedInterestOwed; }
    public long getBadDebt() { return badDebt; }
    public long getLastInterestDay() { return lastInterestDay; }

    public void setLastInterestDay(long day) { this.lastInterestDay = day; }

    public void addCashCollateral(long amount) { cashCollateral += amount; }

    /** @return 是否成功扣减（余额不足返回 false） */
    public boolean reduceCashCollateral(long amount) {
        if (amount <= 0 || cashCollateral < amount) return false;
        cashCollateral -= amount;
        return true;
    }

    public void addFinancedDebt(long amount) { financedDebt += amount; }

    public void reduceFinancedDebt(long amount) {
        financedDebt = Math.max(0, financedDebt - amount);
    }

    public void addFinancedInterest(long amount) { financedInterestOwed += amount; }

    public void reduceFinancedInterest(long amount) {
        financedInterestOwed = Math.max(0, financedInterestOwed - amount);
    }

    public Map<String, Long> getFinancedShares() { return financedShares; }

    public long financedSharesOf(String symbol) {
        return financedShares.getOrDefault(symbol, 0L);
    }

    public void addFinancedShares(String symbol, long qty) {
        financedShares.merge(symbol, qty, Long::sum);
    }

    public void removeFinancedShares(String symbol, long qty) {
        financedShares.computeIfPresent(symbol, (k, v) -> v - qty <= 0 ? null : v - qty);
    }

    public Map<String, Long> getShortPositions() { return shortPositions; }

    public long shortPositionOf(String symbol) {
        return shortPositions.getOrDefault(symbol, 0L);
    }

    public void addShortPosition(String symbol, long qty) {
        shortPositions.merge(symbol, qty, Long::sum);
    }

    public void reduceShortPosition(String symbol, long qty) {
        shortPositions.computeIfPresent(symbol, (k, v) -> v - qty <= 0 ? null : v - qty);
    }

    public Map<String, Long> getShortProceeds() { return shortProceeds; }

    public long shortProceedsOf(String symbol) {
        return shortProceeds.getOrDefault(symbol, 0L);
    }

    public void addShortProceeds(String symbol, long amount) {
        shortProceeds.merge(symbol, amount, Long::sum);
    }

    public void reduceShortProceeds(String symbol, long amount) {
        shortProceeds.computeIfPresent(symbol, (k, v) -> v - amount <= 0 ? null : v - amount);
    }

    public Map<String, Long> getShortSellPrices() { return shortSellPrices; }

    public long shortSellPriceOf(String symbol) {
        return shortSellPrices.getOrDefault(symbol, 0L);
    }

    public void setShortSellPrice(String symbol, long price) {
        if (price > 0) shortSellPrices.put(symbol, price);
    }

    public void clearShortSellPrice(String symbol) {
        shortSellPrices.remove(symbol);
    }

    public Map<String, Long> getShortInterestOwed() { return shortInterestOwed; }

    public void addShortInterest(String symbol, long amount) {
        shortInterestOwed.merge(symbol, amount, Long::sum);
    }

    public void reduceShortInterest(String symbol, long amount) {
        shortInterestOwed.computeIfPresent(symbol, (k, v) -> v - amount <= 0 ? null : v - amount);
    }

    public void addBadDebt(long amount) {
        if (amount > 0) badDebt += amount;
    }

    public void reduceBadDebt(long amount) {
        badDebt = Math.max(0, badDebt - amount);
    }

    /** 是否有未结清的两融业务 */
    public boolean hasOpenPositions() {
        return financedDebt > 0 || financedInterestOwed > 0
                || !shortPositions.isEmpty() || badDebt > 0;
    }
}
