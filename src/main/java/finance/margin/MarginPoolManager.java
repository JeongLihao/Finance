package finance.margin;

import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 两融机械层：账户注册表 + 券池借还。
 *
 * <p>只做账、不做规则判断。规则（杠杆、保证金率、平仓线）见 {@link MarginService}。
 *
 * <p>券池设计（P0-2 教训：系统出借必须有硬上限）：
 * <ul>
 *   <li>每只股票券池总额 = 流通股 × 5%（可配置），借出的是"真实存在的额度"；</li>
 *   <li>单人借券上限 = 券池总额 × 20%（可配置）。</li>
 * </ul>
 */
public final class MarginPoolManager {

    private static final Map<UUID, MarginAccount> ACCOUNTS = new HashMap<>();

    /** symbol -> 已借出股数 */
    private static final Map<String, Long> POOL_BORROWED = new HashMap<>();

    private MarginPoolManager() {
    }

    // ================================================================
    // 账户
    // ================================================================

    public static MarginAccount getAccount(UUID playerId) {
        return ACCOUNTS.computeIfAbsent(playerId, MarginAccount::new);
    }


    public static Map<UUID, MarginAccount> accounts() {
        return ACCOUNTS;
    }

    public static void clearDirect() {
        ACCOUNTS.clear();
        POOL_BORROWED.clear();
    }

    // ================================================================
    // 券池
    // ================================================================

    private static String norm(String symbol) {
        return symbol == null ? "" : symbol.toUpperCase().trim();
    }

    /**
     * 某股票券池总额 = 流通股 × 配置比例。
     * 股票不存在或未上市返回 0。
     */
    public static long poolTotal(String symbol) {
        Stock stock = StockMarketManager.getStock(norm(symbol));
        if (stock == null) return 0;
        long floatShares = Math.max(0, stock.getFloatShares());
        double ratio = MarginService.poolRatio();
        long total = (long) Math.floor(floatShares * ratio);
        return Math.max(0, total);
    }

    /** 券池余量 */
    public static long poolAvailable(String symbol) {
        String key = norm(symbol);
        return Math.max(0, poolTotal(key) - POOL_BORROWED.getOrDefault(key, 0L));
    }

    /** 单人可借上限 */
    public static long perPlayerBorrowCap(String symbol) {
        double ratio = MarginService.perPlayerBorrowRatio();
        return (long) Math.floor(poolTotal(symbol) * ratio);
    }

    /**
     * 借券 —— 从券池划出额度。
     * @return 成功返回 true（额度不足/参数非法返回 false）
     */
    public static synchronized boolean borrow(String symbol, long qty) {
        String key = norm(symbol);
        if (qty <= 0 || poolAvailable(key) < qty) return false;
        POOL_BORROWED.merge(key, qty, Long::sum);
        EconomySavedData.markDirty();
        return true;
    }

    /** 还券 —— 额度归还券池 */
    public static synchronized void repay(String symbol, long qty) {
        String key = norm(symbol);
        if (qty <= 0) return;
        POOL_BORROWED.computeIfPresent(key, (k, v) -> Math.max(0, v - qty));
        EconomySavedData.markDirty();
    }

    /** 供序列化：已借出快照 */
    public static Map<String, Long> borrowedSnapshot() {
        return new HashMap<>(POOL_BORROWED);
    }

    /** 供反序列化恢复（向后兼容旧存档） */
    public static void restoreBorrowed(String symbol, long qty) {
        String key = norm(symbol);
        if (!key.isEmpty() && qty > 0) {
            POOL_BORROWED.put(key, qty);
        }
    }

    public static void putAccountDirect(MarginAccount account) {
        if (account != null) {
            ACCOUNTS.put(account.getPlayerId(), account);
        }
    }
}
