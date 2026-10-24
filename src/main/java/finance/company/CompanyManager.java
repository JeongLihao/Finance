package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.util.ProportionalAllocator;
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
    private static double dividendRatio = FinanceConfig.defaultDividendRatio();
    private static int dividendCycleDays = FinanceConfig.defaultDividendCycleDays();

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
        tickAll(-1);
    }

    /**
     * 每日公司经营。玩家公司的产量来自机器和收货箱（实物），这里只把仓库里的货按比例卖掉；
     * NPC 公司和系统公司在世界里没有厂房，按行业每天抽象生产再自动交易。
     */
    public static void tickAll(long day) {
        for (Company c : COMPANIES.values()) {
            if (c.isPlayerOwned()) {
                finance.gameplay.company.CompanyGameplayMarketService.autoSell(c);
            } else {
                c.produce();
                c.autoTrade();
            }
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

            long dividendAmount = c.prepareDividend(
                    currentMcDay,
                    c.effectiveDividendRatio(dividendRatio),
                    c.effectiveDividendCycleDays(dividendCycleDays));
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
        long totalShares = stock.getVotingShares();

        // 获取所有持有该公司股票的玩家（自由持仓）
        java.util.Map<java.util.UUID, Long> holdings = StockPortfolioManager.getHoldingsForCompany(symbol);
        // 挂卖单的份额：下单时已移出持仓，但还没卖出，仍归股东本人（与股东名册口径一致）
        for (finance.stock.StockOrder order : finance.stock.StockOrderManager.getOrdersBySymbol(symbol)) {
            if (order.getType() == finance.stock.StockOrderType.SELL && order.getQuantity() > 0)
                holdings.merge(
                        order.getPlayerId(),
                        (long) order.getQuantity(),
                        (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
        }
        // 回购锁定的份额：沿用旧口径，分红归股东本人
        finance.governance.CorporateActionManager.buybackLockedShares(symbol)
                .forEach((holder, quantity) ->
                        holdings.merge(holder, quantity, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b));
        // 要约锁定的份额：经济权利已让渡，分红计入要约托管账户（escrow）——
        // 交割成功归收购方，失败/撤销则随份额一并返还股东。以 escrow 为伪持有人参与分配。
        java.util.Map<java.util.UUID, java.util.UUID> escrowToOffer = new java.util.HashMap<>();
        for (java.util.Map.Entry<java.util.UUID, java.util.Map<java.util.UUID, Long>> oe :
                finance.governance.CorporateActionManager.tenderLockedByOffer(symbol)
                        .entrySet()) {
            finance.governance.TenderOffer offer =
                    finance.governance.CorporateActionManager.tenders().get(oe.getKey());
            if (offer == null || offer.status() != finance.governance.CapitalActionStatus.OPEN) continue;
            long locked = 0L;
            for (long qq : oe.getValue().values()) locked = locked > Long.MAX_VALUE - qq ? Long.MAX_VALUE : locked + qq;
            if (locked <= 0) continue;
            holdings.put(offer.escrowId(), locked);
            escrowToOffer.put(offer.escrowId(), offer.id());
        }

        if (holdings.isEmpty()) {
            return; // 无股东，无处分红
        }

        // 按比例精确分配，使用 BigInteger 计算“整除 + 最大余数补发”，避免溢出和超发。
        long actuallyPaid = 0;
        for (ProportionalAllocator.Allocation share :
                ProportionalAllocator.allocate(totalDividend, holdings, totalShares)) {
            UUID playerId = share.id();
            long shareholding = share.weight();
            long payout = share.amount();
            if (payout <= 0) {
                continue;
            }
            java.util.UUID tenderId = escrowToOffer.get(playerId);
            if (tenderId != null) {
                // 要约锁定份额的分红 → escrow 托管，按接受比例记到各股东名下
                actuallyPaid += payTenderDividend(company, symbol, tenderId, payout, shareholding, currentMcDay);
                continue;
            }
            if (AccountManager.canDeposit(playerId, payout) && company.withdraw(payout)) {
                if (!AccountManager.deposit(playerId, payout)) {
                    company.deposit(payout);
                    continue;
                }
                AccountManager.addTransactionRecord(new TransactionRecord(
                        company.getCompanyId(),
                        playerId,
                        payout,
                        TransactionType.DIVIDEND,
                        playerId,
                        company.getName() + "/" + symbol,
                        shareholding));
                actuallyPaid += payout;
            }
        }
        if (actuallyPaid > 0) {
            long perShare = totalShares > 0 ? actuallyPaid / totalShares : 0;
            company.addDividendRecord(currentMcDay, actuallyPaid, perShare);
            // 财报季：分红除权 —— 按每股分红额下调股价（严谨定价，不 whitewash 分红收益）
            if (perShare > 0 && FinanceConfig.earningsExDividend()) {
                stock.applyExDividend(perShare);
            }
        }
        if (actuallyPaid < totalDividend) {
            company.addDistributableProfit(totalDividend - actuallyPaid);
        }
    }

    /**
     * 要约锁定份额的分红 → 计入要约 escrow，并按接受份额比例记到各股东名下。
     * 交割成功时归收购方，失败/撤销时随份额原路返还。返回实际支付金额。
     */
    private static long payTenderDividend(
            Company company,
            String symbol,
            java.util.UUID tenderId,
            long payout,
            long shareholding,
            long currentMcDay) {
        finance.governance.TenderOffer offer =
                finance.governance.CorporateActionManager.tenders().get(tenderId);
        if (offer == null || offer.status() != finance.governance.CapitalActionStatus.OPEN) {
            return 0L; // 要约已不在进行中，分红留在公司可分配利润
        }
        if (!AccountManager.canDeposit(offer.escrowId(), payout) || !company.withdraw(payout)) {
            return 0L;
        }
        if (!AccountManager.deposit(offer.escrowId(), payout)) {
            company.deposit(payout);
            return 0L;
        }
        // 按接受份额比例把分红记到各股东名下（余数尘埃留在 escrow，随结算退给收购方）
        long totalAccepted = 0L;
        for (long qq : offer.accepted().values()) {
            totalAccepted = totalAccepted > Long.MAX_VALUE - qq ? Long.MAX_VALUE : totalAccepted + qq;
        }
        if (totalAccepted > 0) {
            java.math.BigInteger total = java.math.BigInteger.valueOf(payout);
            java.math.BigInteger denom = java.math.BigInteger.valueOf(totalAccepted);
            for (java.util.Map.Entry<java.util.UUID, Long> e : offer.accepted().entrySet()) {
                long div = total.multiply(java.math.BigInteger.valueOf(e.getValue()))
                        .divide(denom)
                        .longValue();
                if (div > 0) {
                    offer.addDividend(e.getKey(), div);
                }
            }
        }
        AccountManager.addTransactionRecord(new TransactionRecord(
                company.getCompanyId(),
                offer.escrowId(),
                payout,
                TransactionType.DIVIDEND,
                offer.buyerId(),
                company.getName() + "/" + symbol + "[tender-escrow]",
                shareholding));
        return payout;
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
        dividendRatio = FinanceConfig.defaultDividendRatio();
        dividendCycleDays = FinanceConfig.defaultDividendCycleDays();
    }

    /** 移除单个公司（退市时调用） */
    public static void removeCompany(UUID companyId) {
        Company c = COMPANIES.remove(companyId);
        if (c != null) {
            if (c.getOwnerId() != null) {
                OWNER_INDEX.remove(c.getOwnerId());
            }
            NAME_INDEX.remove(c.getName().toLowerCase());
            finance.gameplay.company.CompanyGameplayManager.removeCompany(companyId);
            EconomySavedData.markDirty();
        }
    }

    public static void clearCompaniesDirect() {
        COMPANIES.clear();
        OWNER_INDEX.clear();
        NAME_INDEX.clear();
        finance.gameplay.company.CompanyGameplayManager.clearDirect();
    }
}
