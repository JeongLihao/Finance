package finance.company;

import finance.data.EconomySavedData;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司管理器 —— 所有公司的注册和查询入口。
 * <p>
 * 维护两级索引（ownerId → Company, name.toLowerCase() → Company），
 * 使 getCompanyByOwner 和 getCompanyByName 均为 O(1)。
 * </p>
 */
public class CompanyManager {

    private static final Map<UUID, Company> COMPANIES = new HashMap<>();
    private static final Map<UUID, Company> OWNER_INDEX = new HashMap<>();
    private static final Map<String, Company> NAME_INDEX = new HashMap<>();
    private static double dividendRatio = 0.40;
    private static int dividendCycleDays = 7;

    public static void register(Company company) {
        COMPANIES.put(company.getCompanyId(), company);
        if (company.getOwnerId() != null) {
            OWNER_INDEX.put(company.getOwnerId(), company);
        }
        NAME_INDEX.put(company.getName().toLowerCase(), company);
        EconomySavedData.markDirty();
    }

    public static void registerDirect(Company company) {
        COMPANIES.put(company.getCompanyId(), company);
        if (company.getOwnerId() != null) {
            OWNER_INDEX.put(company.getOwnerId(), company);
        }
        NAME_INDEX.put(company.getName().toLowerCase(), company);
    }

    public static Collection<Company> getCompanies() {
        return COMPANIES.values();
    }

    public static Company getCompany(UUID id) {
        return COMPANIES.get(id);
    }

    /** O(1) 按所有者 UUID 查找公司 */
    public static Company getCompanyByOwner(UUID ownerId) {
        return OWNER_INDEX.get(ownerId);
    }

    /** O(1) 按名称查找公司（忽略大小写） */
    public static Company getCompanyByName(String name) {
        return NAME_INDEX.get(name.toLowerCase());
    }

    public static boolean hasCompanyNamed(String name) {
        return NAME_INDEX.containsKey(name.toLowerCase());
    }

    public static boolean renameCompany(UUID companyId, String newName) {
        Company company = COMPANIES.get(companyId);
        if (company == null || newName == null || newName.isBlank()) {
            return false;
        }
        String cleaned = newName.trim();
        String key = cleaned.toLowerCase();
        Company existing = NAME_INDEX.get(key);
        if (existing != null && !existing.getCompanyId().equals(companyId)) {
            return false;
        }
        NAME_INDEX.remove(company.getName().toLowerCase());
        company.setName(cleaned);
        NAME_INDEX.put(key, company);
        EconomySavedData.markDirty();
        return true;
    }

    /** 每日经营 tick —— 所有公司生产 + 自动交易（由 FinanceMod 每天调用一次） */
    public static void tickAll() {
        for (Company c : COMPANIES.values()) {
            c.produce();
            c.autoTrade();
        }
        EconomySavedData.markDirty();
    }

    /** P3：每日分红结算 —— 计算日利润并累加到留存收益 */
    public static void settleDailyProfits() {
        settleDailyProfits(-1);
    }

    public static void settleDailyProfits(long mcDay) {
        for (Company c : COMPANIES.values()) {
            c.settleDailyProfits(mcDay);
        }
        EconomySavedData.markDirty();
    }

    /** P3：周期性分红 —— 向股东分配利润（MC天数从服务器 tick count 推算） */
    public static void tryDividends(long currentMcDay) {
        for (Company c : COMPANIES.values()) {
            Stock stock = StockMarketManager.getStockByCompanyId(c.getCompanyId());
            if (!c.isPublic() || stock == null || stock.getTotalShares() <= 0) {
                continue;
            }
            if (StockPortfolioManager.getHoldingsForCompany(stock.getSymbol()).isEmpty()) {
                continue;
            }

            long dividendAmount = c.prepareDividend(currentMcDay, dividendRatio, dividendCycleDays);
            if (dividendAmount > 0) {
                // P5：按持股比例分给所有股东
                distributeDividend(c, dividendAmount, currentMcDay);
            }
        }
        EconomySavedData.markDirty();
    }

    /**
     * P5：分红分账逻辑 —— 按持股比例向所有股东转账现金。
     */
    private static void distributeDividend(Company company, long totalDividend, long currentMcDay) {
        Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
        if (stock == null || stock.getTotalShares() <= 0) {
            return;
        }

        String symbol = stock.getSymbol();
        long totalShares = stock.getTotalShares();

        // 获取所有持有该公司股票的玩家
        java.util.Map<java.util.UUID, Long> holdings = StockPortfolioManager.getHoldingsForCompany(symbol);

        if (holdings.isEmpty()) {
            return; // 无股东，无处分红
        }

        // 按比例分配
        long actuallyPaid = 0;
        for (java.util.Map.Entry<java.util.UUID, Long> entry : holdings.entrySet()) {
            java.util.UUID playerId = entry.getKey();
            long shareholding = entry.getValue();

            long payout = Math.round((double) totalDividend * shareholding / totalShares);
            if (payout > 0 && company.withdraw(payout)) {
                AccountManager.deposit(playerId, payout);
                AccountManager.addTransactionRecord(
                        new TransactionRecord(
                                company.getCompanyId(),
                                playerId,
                                payout,
                                TransactionType.DIVIDEND,
                                playerId,
                                company.getName() + "/" + symbol,
                                shareholding
                        )
                );
                actuallyPaid += payout;
            }
        }
        if (actuallyPaid > 0) {
            long perShare = totalShares > 0 ? actuallyPaid / totalShares : 0;
            company.addDividendRecord(currentMcDay, actuallyPaid, perShare);
        }
    }

    public static double getDividendRatio() {
        return dividendRatio;
    }

    public static int getDividendCycleDays() {
        return dividendCycleDays;
    }

    public static void setDividendPolicy(double ratio, int cycleDays) {
        dividendRatio = Math.max(0.0, Math.min(1.0, ratio));
        dividendCycleDays = Math.max(1, Math.min(365, cycleDays));
        EconomySavedData.markDirty();
    }

    public static void resetDividendPolicyDirect() {
        dividendRatio = 0.40;
        dividendCycleDays = 7;
    }

    /** 移除单个公司（退市时调用） */
    public static void removeCompany(UUID companyId) {
        Company c = COMPANIES.remove(companyId);
        if (c != null) {
            if (c.getOwnerId() != null) {
                OWNER_INDEX.remove(c.getOwnerId());
            }
            NAME_INDEX.remove(c.getName().toLowerCase());
            EconomySavedData.markDirty();
        }
    }

    /** 清空所有公司（数据加载前调用） */
    public static void clearCompanies() {
        COMPANIES.clear();
        OWNER_INDEX.clear();
        NAME_INDEX.clear();
        EconomySavedData.markDirty();
    }

    public static void clearCompaniesDirect() {
        COMPANIES.clear();
        OWNER_INDEX.clear();
        NAME_INDEX.clear();
    }
}
