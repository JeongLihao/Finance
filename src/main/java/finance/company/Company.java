package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.earnings.QuarterlyEarningsReport;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;
import finance.policy.MonetaryPolicyService;
import finance.util.MathUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公司实体 —— 拥有现金、库存和实时市场估值。
 * 每个 MC 天自动生产商品，并将可出售产出卖给国际市场实现盈利。
 */
public class Company {

    private final UUID companyId;
    private String name;
    private final CompanyType type;
    private final UUID ownerId;
    private long cash;
    private final Map<String, Integer> inventory = new HashMap<>();

    /** 库存保留比例 —— 每天卖出 50%，保留 50% */
    private static final double SELL_RATIO = 0.5;

    private static final int MAX_UPGRADE_LEVEL = 5;

    /** 原料安全库存天数，避免公司把生产原料卖掉后又立刻买回 */
    private static final int RAW_MATERIAL_RESERVE_DAYS = 3;

    /** 最近利润窗口，用于股票估值，避免只看单日利润造成暴涨暴跌 */
    private static final int PROFIT_HISTORY_DAYS = 7;

    /**
     * 没有厂房的 NPC 公司折算维护费用的机器台数：这么多台 1 档机器开满一天正好是行业标准日产量
     * （与长期模拟的设定一致），再乘公司的产量倍数。
     */
    static final int ABSTRACT_MACHINES_PER_STANDARD_OUTPUT = 3;

    // ---- P3：盈利与分红 ----
    /** 当日营收（产出销售收入） */
    private long dailyRevenue;

    /** 当日成本（原料购买支出） */
    private long dailyCost;

    /** 留存收益（未分配利润） */
    private long retainedEarnings;

    /** 可分配利润：真正允许进入分红池的利润。 */
    private long distributableProfit;

    /** 上次分红的 MC 天数（用于判断是否需要分红） */
    private long lastDividendDay;

    /** 所有者上次提现的真实时间戳（毫秒），用于提现冷却；0 = 从未提现 */
    private long lastOwnerWithdrawTime;

    /** 最近分红历史 */
    private final List<DividendRecord> dividendHistory = new ArrayList<>();

    /** 公司级分红比例；为负数时使用管理员全局默认策略。 */
    private double dividendRatio = -1.0;

    /** 公司级分红周期；为 0 或负数时使用管理员全局默认策略。 */
    private int dividendCycleDays = -1;

    /** 最近若干天净利润 */
    private final List<Long> recentProfits = new ArrayList<>();

    /** 当日已付的日常开销（工资 + 机器维护 + 库存持有），不含原料采购。 */
    private long dailyRunningCost;

    /** 最近若干天应付的日常开销（含没付上的缺口），破产安全线按它的日均算。 */
    private final List<Long> recentRunningCosts = new ArrayList<>();

    /** 最近一次日结时现金不够、没付上的日常开销；大于 0 时公司处于破产风险。 */
    private long lastCostShortfall;

    /** 最近周期财报 */
    private final List<CompanyFinancialReport> financialReports = new ArrayList<>();

    // ---- 财报季：季度累加器（只读聚合自密封的日度流水） ----
    /** 本季度累计营收 */
    private long quarterRevenue;
    /** 本季度累计成本 */
    private long quarterExpenses;
    /** 本季度覆盖的经营天数 */
    private int quarterDays;
    /** 已发布季报历史（只增不改） */
    private final List<QuarterlyEarningsReport> earningsHistory = new ArrayList<>();

    // ---- P4：IPO ----
    /** 是否已上市 */
    private boolean isPublic;

    private CompanyStrategy strategy = CompanyStrategy.STABLE;
    private int productionLevel = 0;
    private int storageLevel = 0;
    private int managementLevel = 0;
    private double autoSellRatio = SELL_RATIO;
    private boolean bankruptcyRisk;
    private long bankruptcyRiskStartDay = -1;

    public Company(UUID companyId, String name, CompanyType type, long cash) {
        this(companyId, name, type, cash, null);
    }

    public Company(UUID companyId, String name, CompanyType type, long cash, UUID ownerId) {
        this.companyId = companyId;
        this.name = name;
        this.type = type;
        this.cash = cash;
        this.ownerId = ownerId;
        this.isPublic = false; // 默认未上市
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public CompanyType getType() {
        return type;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isPlayerOwned() {
        return ownerId != null;
    }

    public long getCash() {
        return cash;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public boolean isBankruptcyRisk() {
        return bankruptcyRisk;
    }

    public long getBankruptcyRiskStartDay() {
        return bankruptcyRiskStartDay;
    }

    public void setBankruptcyRisk(boolean risk, long startDay) {
        this.bankruptcyRisk = risk;
        this.bankruptcyRiskStartDay = risk ? startDay : -1;
    }

    // ---- 库存 ----

    public Map<String, Integer> getInventory() {
        return inventory;
    }

    public int getInventoryAmount(String commodityId) {
        return inventory.getOrDefault(commodityId, 0);
    }

    public boolean addInventory(String commodityId, int amount) {
        if (!canAddInventory(commodityId, amount)) return false;
        inventory.put(commodityId, getInventoryAmount(commodityId) + amount);
        return true;
    }

    public boolean canAddInventory(String commodityId, int amount) {
        if (commodityId == null || commodityId.isBlank() || amount <= 0) return false;
        int current = getInventoryAmount(commodityId);
        return current >= 0 && current <= Integer.MAX_VALUE - amount;
    }

    public boolean removeInventory(String commodityId, int amount) {
        int current = getInventoryAmount(commodityId);
        if (commodityId == null || commodityId.isBlank() || amount <= 0 || current < amount) return false;
        inventory.put(commodityId, current - amount);
        return true;
    }

    // ---- 每日经营 ----

    /** 每日生产 —— 先消耗原料，再生产 */
    public void produce() {
        if (!consumeRawMaterials()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : type.getDailyProduction().entrySet()) {
            int produced = (int) Math.max(1, Math.round(entry.getValue() * getProductionMultiplier()));
            addInventory(entry.getKey(), produced);
        }
    }

    /** 确保原料充足：不足时从国际市场购买。购买失败则有多少消耗多少，不阻塞生产。 */
    private boolean consumeRawMaterials() {
        Map<String, Integer> consumption = type.getDailyConsumption();
        if (consumption.isEmpty()) return true;

        // 尝试补充不足的原料
        for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
            String commodityId = entry.getKey();
            int needed = entry.getValue();
            int current = getInventoryAmount(commodityId);

            if (current < needed) {
                buyFromInternationalMarket(commodityId, needed - current);
            }
        }

        // 有多少消耗多少，不因原料不足而完全阻塞生产
        for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
            int current = getInventoryAmount(entry.getKey());
            int toConsume = Math.min(current, entry.getValue());
            if (toConsume > 0) {
                removeInventory(entry.getKey(), toConsume);
            }
        }
        return true;
    }

    /** 从国际市场购买原料 */
    private void buyFromInternationalMarket(String commodityId, int quantity) {
        CompanyNpcTradeService.buyForCompany(this, commodityId, quantity);
    }

    /** 每日自动交易 —— 将可出售库存的一部分卖给国际市场变现 */
    public void autoTrade() {
        // 收集候选商品 ID，避免在遍历中修改 inventory
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            if (entry.getValue() > 0 && type.getDailyProduction().containsKey(entry.getKey())) {
                candidates.add(entry.getKey());
            }
        }

        for (String commodityId : candidates) {
            int amount = getInventoryAmount(commodityId);
            if (amount <= 0) continue;

            MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
            if (mp == null) continue;

            long bidPrice = mp.getBidPrice();

            // 卖出库存的自动比例，受经营策略调整。
            double effectiveSellRatio = clamp(autoSellRatio * strategy.getSellRatioMultiplier(), 0.05, 0.95);
            int sellQty = Math.max(1, (int) (amount * effectiveSellRatio));
            int reserve = getReserveAmount(commodityId);
            if (amount <= reserve) {
                continue;
            }
            sellQty = Math.min(sellQty, amount - reserve);
            sellQty = Math.min(sellQty, amount);

            // 国际市场余额是否充足
            long totalCost = MathUtil.multiplyExactOrNegative1(bidPrice, sellQty);
            if (totalCost <= 0) continue;
            long marketBalance = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);

            // 自适应调量：国际市场余额不足时缩量
            while (sellQty > 1 && marketBalance < totalCost) {
                sellQty /= 2;
                totalCost = MathUtil.multiplyExactOrNegative1(bidPrice, sellQty);
                if (totalCost <= 0) break;
            }

            if (sellQty <= 0 || totalCost <= 0) continue;

            CompanyNpcTradeService.sellForCompany(this, commodityId, sellQty, bidPrice);
        }
    }

    // ---- 估值 ----

    /** 库存市值 —— 按国际市场当前 midPrice 实时计价 */
    /** 库存市值：玩家公司算托管账户，NPC 公司算自带库存（见 {@link finance.gameplay.company.CompanyInventoryFacade}）。 */
    public long inventoryValue() {
        long total = 0;
        for (Map.Entry<String, Integer> entry : finance.gameplay.company.CompanyInventoryFacade.inventorySnapshot(this)
                .entrySet()) {
            MarketPrice mp = NpcMarketMaker.getMarketPrice(entry.getKey());
            if (mp != null) {
                long value = MathUtil.saturatedMultiplyNonNegative(mp.getMidPrice(), entry.getValue());
                total = MathUtil.saturatedAddNonNegative(total, value);
            }
        }
        return total;
    }

    /** 公司估值 = 现金 + 库存市值（展示用原始资产值） */
    public long getEstimatedValue() {
        return MathUtil.saturatedAddNonNegative(cash, inventoryValue());
    }

    /** 股票基本面资产值：库存按行业景气折价，避免商品跌价时股票仍按满额资产估值。 */
    public long getFundamentalAssetValue() {
        double sentiment = getIndustrySentiment();
        double inventoryDiscount = clamp(0.45 + sentiment * 0.45, 0.35, 1.05);
        long discountedInventory = Math.max(0, Math.round(inventoryValue() * inventoryDiscount));
        return MathUtil.saturatedAddNonNegative(cash, discountedInventory);
    }

    // ---- 资金 ----

    public boolean deposit(long amount) {
        if (amount <= 0 || cash < 0 || cash > Long.MAX_VALUE - amount) {
            return false;
        }
        cash += amount;
        return true;
    }

    public boolean canDeposit(long amount) {
        return amount > 0 && cash >= 0 && cash <= Long.MAX_VALUE - amount;
    }

    void recordPurchaseCost(long amount) {
        dailyCost = MathUtil.saturatedAddNonNegative(dailyCost, amount);
    }

    void recordSalesRevenue(long amount) {
        dailyRevenue = MathUtil.saturatedAddNonNegative(dailyRevenue, amount);
    }

    public void recordGameplayCost(long amount) {
        if (amount > 0) dailyCost = MathUtil.saturatedAddNonNegative(dailyCost, amount);
    }

    /** 记一笔已经从现金付出的日常开销（如工资）：同时算进当天成本和破产安全线。 */
    public void recordRunningCost(long amount) {
        if (amount <= 0) return;
        dailyCost = MathUtil.saturatedAddNonNegative(dailyCost, amount);
        dailyRunningCost = MathUtil.saturatedAddNonNegative(dailyRunningCost, amount);
    }

    public void recordGameplayRevenue(long amount) {
        if (amount > 0) dailyRevenue = MathUtil.saturatedAddNonNegative(dailyRevenue, amount);
    }

    public boolean withdraw(long amount) {
        if (amount <= 0 || cash < amount) return false;
        cash -= amount;
        return true;
    }

    private int getReserveAmount(String commodityId) {
        return type.getDailyConsumption().getOrDefault(commodityId, 0) * RAW_MATERIAL_RESERVE_DAYS;
    }

    // ---- P3：盈利与分红 ----

    /**
     * 每 MC 天调用（在 produce 和 autoTrade 后）—— 计算日利润，累加到留存收益。
     */
    public void settleDailyProfits() {
        settleDailyProfits(-1);
    }

    public void settleDailyProfits(long mcDay) {
        long owed = runningCostDue().total();
        long paid = payRunningCost(owed);
        lastCostShortfall = owed - paid;
        dailyCost = MathUtil.saturatedAddNonNegative(dailyCost, paid);
        dailyRunningCost = MathUtil.saturatedAddNonNegative(dailyRunningCost, paid);
        addRecentRunningCost(MathUtil.saturatedAddNonNegative(dailyRunningCost, lastCostShortfall));
        dailyRunningCost = 0;
        long dailyProfit = dailyRevenue - dailyCost;
        long revenue = dailyRevenue;
        long expenses = dailyCost;
        retainedEarnings += dailyProfit;
        if (dailyProfit > 0) {
            distributableProfit += dailyProfit;
        }
        addRecentProfit(dailyProfit);
        generateFinancialReport(mcDay, revenue, expenses, dailyProfit);
        // 财报季：同步累加季度流水（与现金在同一方法内更新，无时间差可钻）
        quarterRevenue = MathUtil.saturatedAddNonNegative(quarterRevenue, revenue);
        quarterExpenses = MathUtil.saturatedAddNonNegative(quarterExpenses, expenses);
        quarterDays++;
        dailyRevenue = 0;
        dailyCost = 0;
    }

    /**
     * 尝试分红（CompanyManager 应每 DIVIDEND_CYCLE_DAYS 调用一次）。
     * 返回本次分红的总金额。
     */
    public long prepareDividend(long currentMcDay, double dividendRatio, int dividendCycleDays) {
        if (lastDividendDay == 0) {
            lastDividendDay = currentMcDay;
            return 0; // 首次不分红
        }

        if (currentMcDay - lastDividendDay < dividendCycleDays) {
            return 0; // 未到分红日期
        }

        if (distributableProfit <= 0 || cash <= 0) {
            lastDividendDay = currentMcDay;
            return 0; // 无利润可分
        }

        long dividendAmount = Math.round(distributableProfit * clamp(dividendRatio, 0.0, 1.0));
        dividendAmount = Math.min(dividendAmount, cash);
        if (dividendAmount <= 0) {
            lastDividendDay = currentMcDay;
            return 0;
        }

        distributableProfit -= dividendAmount;
        retainedEarnings = Math.max(0, retainedEarnings - dividendAmount);
        lastDividendDay = currentMcDay;

        return dividendAmount;
    }

    public void addDistributableProfit(long amount) {
        if (amount > 0) {
            distributableProfit += amount;
            retainedEarnings += amount;
        }
    }

    public void addDividendRecord(long mcDay, long totalAmount, long perShare) {
        dividendHistory.add(new DividendRecord(mcDay, totalAmount, perShare));
        while (dividendHistory.size() > 20) {
            dividendHistory.remove(0);
        }
    }

    public long getDailyRevenue() {
        return dailyRevenue;
    }

    public long getDailyCost() {
        return dailyCost;
    }

    public long getRetainedEarnings() {
        return retainedEarnings;
    }

    public long getDistributableProfit() {
        return distributableProfit;
    }

    public long getLastDividendDay() {
        return lastDividendDay;
    }

    public long getLastOwnerWithdrawTime() {
        return lastOwnerWithdrawTime;
    }

    public void setLastOwnerWithdrawTime(long timeMillis) {
        this.lastOwnerWithdrawTime = Math.max(0, timeMillis);
    }

    public double getDividendRatio() {
        return dividendRatio;
    }

    public int getDividendCycleDays() {
        return dividendCycleDays;
    }

    public double effectiveDividendRatio(double globalDefault) {
        return dividendRatio >= 0 ? clamp(dividendRatio, 0.0, 1.0) : clamp(globalDefault, 0.0, 1.0);
    }

    public int effectiveDividendCycleDays(int globalDefault) {
        return dividendCycleDays > 0 ? Math.min(365, dividendCycleDays) : Math.max(1, globalDefault);
    }

    public void setDividendPolicy(double ratio, int cycleDays) {
        this.dividendRatio = clamp(ratio, 0.0, 1.0);
        this.dividendCycleDays = Math.max(1, Math.min(365, cycleDays));
    }

    public void restoreDividendPolicy(double ratio, int cycleDays) {
        this.dividendRatio = ratio >= 0 ? clamp(ratio, 0.0, 1.0) : -1.0;
        this.dividendCycleDays = cycleDays > 0 ? Math.min(365, cycleDays) : -1;
    }

    public List<Long> getRecentProfits() {
        return new ArrayList<>(recentProfits);
    }

    public List<DividendRecord> getDividendHistory() {
        return new ArrayList<>(dividendHistory);
    }

    public List<CompanyFinancialReport> getFinancialReports() {
        return new ArrayList<>(financialReports);
    }

    public CompanyFinancialReport getLatestFinancialReport() {
        return financialReports.isEmpty() ? null : financialReports.get(financialReports.size() - 1);
    }

    // ---- 财报季 ----
    /** 季度累加器快照（返回后清零，由 EarningsSeasonService 在财报日调用）。 */
    public record QuarterAccumulator(long revenue, long expenses, int days) {}

    public QuarterAccumulator drainQuarter() {
        QuarterAccumulator acc = new QuarterAccumulator(quarterRevenue, quarterExpenses, quarterDays);
        quarterRevenue = 0;
        quarterExpenses = 0;
        quarterDays = 0;
        return acc;
    }

    /** 季度累加器快照（不清空，供序列化用）。 */
    public QuarterAccumulator getQuarterAccumulator() {
        return new QuarterAccumulator(quarterRevenue, quarterExpenses, quarterDays);
    }

    public List<QuarterlyEarningsReport> getEarningsHistory() {
        return new ArrayList<>(earningsHistory);
    }

    public void addEarningsReportDirect(QuarterlyEarningsReport report, int keep) {
        if (report == null) {
            return;
        }
        earningsHistory.add(report);
        int maxKeep = Math.max(1, keep);
        while (earningsHistory.size() > maxKeep) {
            earningsHistory.remove(0);
        }
    }

    public void restoreQuarterly(
            long quarterRevenue, long quarterExpenses, int quarterDays, List<QuarterlyEarningsReport> history) {
        this.quarterRevenue = Math.max(0, quarterRevenue);
        this.quarterExpenses = Math.max(0, quarterExpenses);
        this.quarterDays = Math.max(0, quarterDays);
        this.earningsHistory.clear();
        if (history != null) {
            this.earningsHistory.addAll(history);
        }
    }

    public long getSmoothedDailyProfit() {
        if (!financialReports.isEmpty()) {
            int start = Math.max(0, financialReports.size() - PROFIT_HISTORY_DAYS);
            long total = 0;
            for (int i = start; i < financialReports.size(); i++) {
                total += financialReports.get(i).netProfit();
            }
            return total / (financialReports.size() - start);
        }
        if (recentProfits.isEmpty()) {
            return dailyRevenue - dailyCost;
        }
        long total = 0;
        for (long profit : recentProfits) {
            total += profit;
        }
        return total / recentProfits.size();
    }

    /** 行业景气：公司核心商品当前价格 / 基准价，低于 1 表示行业承压。 */
    public double getIndustrySentiment() {
        double total = 0;
        int count = 0;
        for (String commodityId : type.getCommodityIds()) {
            MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
            if (mp == null || mp.getBasePrice() <= 0) {
                continue;
            }
            total += (double) mp.getMidPrice() / mp.getBasePrice();
            count++;
        }
        return count == 0 ? 1.0 : clamp(total / count, 0.35, 2.0);
    }

    public void restoreFinancials(
            long dailyRevenue, long dailyCost, long retainedEarnings, long lastDividendDay, List<Long> recentProfits) {
        restoreFinancials(
                dailyRevenue, dailyCost, retainedEarnings, retainedEarnings, lastDividendDay, recentProfits, List.of());
    }

    public void restoreFinancials(
            long dailyRevenue,
            long dailyCost,
            long retainedEarnings,
            long distributableProfit,
            long lastDividendDay,
            List<Long> recentProfits,
            List<DividendRecord> dividendHistory) {
        this.dailyRevenue = dailyRevenue;
        this.dailyCost = dailyCost;
        this.retainedEarnings = retainedEarnings;
        this.distributableProfit = Math.max(0, distributableProfit);
        this.lastDividendDay = lastDividendDay;
        this.recentProfits.clear();
        if (recentProfits != null) {
            for (Long profit : recentProfits) {
                if (profit != null) {
                    addRecentProfit(profit);
                }
            }
        }
        this.dividendHistory.clear();
        if (dividendHistory != null) {
            for (DividendRecord record : dividendHistory) {
                if (record != null) {
                    addDividendRecord(record.mcDay(), record.totalAmount(), record.perShare());
                }
            }
        }
    }

    public void addFinancialReportDirect(CompanyFinancialReport report) {
        if (report != null) {
            financialReports.add(report);
            while (financialReports.size() > 20) {
                financialReports.remove(0);
            }
        }
    }

    public long getReportBasedAssetValue() {
        CompanyFinancialReport latest = getLatestFinancialReport();
        if (latest == null) {
            return getFundamentalAssetValue();
        }
        double sentiment = getIndustrySentiment();
        double inventoryDiscount = clamp(0.45 + sentiment * 0.45, 0.35, 1.05);
        long netAssets = Math.max(0, latest.assets() - latest.liabilities());
        return Math.max(0, Math.round(netAssets * inventoryDiscount));
    }

    // ---- P4：IPO ----

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean pub) {
        this.isPublic = pub;
    }

    public CompanyStrategy getStrategy() {
        return strategy;
    }

    public int getProductionLevel() {
        return productionLevel;
    }

    public int getStorageLevel() {
        return storageLevel;
    }

    public int getManagementLevel() {
        return managementLevel;
    }

    public double getAutoSellRatio() {
        return autoSellRatio;
    }

    public void setStrategy(CompanyStrategy strategy) {
        this.strategy = strategy != null ? strategy : CompanyStrategy.STABLE;
    }

    public void setAutoSellRatio(double ratio) {
        this.autoSellRatio = clamp(ratio, 0.05, 0.95);
    }

    public boolean upgradeProduction() {
        if (productionLevel >= MAX_UPGRADE_LEVEL) return false;
        productionLevel++;
        return true;
    }

    public boolean upgradeStorage() {
        if (storageLevel >= MAX_UPGRADE_LEVEL) return false;
        storageLevel++;
        return true;
    }

    public boolean upgradeManagement() {
        if (managementLevel >= MAX_UPGRADE_LEVEL) return false;
        managementLevel++;
        return true;
    }

    public long getUpgradeCost(String upgradeType) {
        int nextLevel =
                switch (upgradeType) {
                    case "PRODUCTION" -> productionLevel + 1;
                    case "STORAGE" -> storageLevel + 1;
                    case "MANAGEMENT" -> managementLevel + 1;
                    default -> 1;
                };
        return 2_500L * nextLevel * nextLevel;
    }

    public void restoreManagement(
            CompanyStrategy strategy,
            int productionLevel,
            int storageLevel,
            int managementLevel,
            double autoSellRatio) {
        setStrategy(strategy);
        this.productionLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, productionLevel));
        this.storageLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, storageLevel));
        this.managementLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, managementLevel));
        setAutoSellRatio(autoSellRatio);
    }

    // ---- 日常开销 ----

    /** 今天应付的日常开销（不含工资，工资由 {@code MacroLaborService} 招工时直接付）。 */
    public record RunningCost(long maintenance, long carryInterest, long storageFee) {
        public long total() {
            return MathUtil.saturatedAddNonNegative(
                    MathUtil.saturatedAddNonNegative(maintenance, carryInterest), storageFee);
        }
    }

    /**
     * 机器维护费 + 库存持有利息（库存市值 × 央行基准利率 ÷ 365）+ 仓储费。
     * 维护费受经营策略和管理等级影响，仓储费受仓储等级影响；利息是资金占用，升级打不了折。
     */
    public RunningCost runningCostDue() {
        long inventory = inventoryValue();
        long rateBps = Math.max(0, MonetaryPolicyService.benchmarkRateBasisPoints());
        long interest = Math.round(inventory * (rateBps / 10_000.0) / 365.0);
        double storageDiscount = levelDiscount(storageLevel, FinanceConfig.storageLevelDiscountBasisPoints());
        long storageFee =
                Math.round(inventory * (FinanceConfig.inventoryStorageFeeBasisPoints() / 10_000.0) * storageDiscount);
        return new RunningCost(machineMaintenanceDue(), Math.max(0, interest), Math.max(0, storageFee));
    }

    private long machineMaintenanceDue() {
        long base = 0;
        if (isPlayerOwned()) {
            for (CompanyFacilityRecord facility : CompanyFacilityManager.forCompany(companyId)) {
                if (chargesMaintenance(facility.status()))
                    base = MathUtil.saturatedAddNonNegative(
                            base, FinanceConfig.machineMaintenance(facility.productionLevel()));
            }
        } else {
            long machines = abstractMachineCount();
            base = MathUtil.saturatedMultiplyNonNegative(FinanceConfig.machineMaintenance(1), machines);
        }
        double discount = levelDiscount(managementLevel, FinanceConfig.managementLevelDiscountBasisPoints());
        return Math.max(0, Math.round(base * strategy.getOperatingCostMultiplier() * discount));
    }

    /** NPC 公司折算成的 1 档机器台数（至少 1 台）。 */
    public long abstractMachineCount() {
        return Math.max(1, Math.round(ABSTRACT_MACHINES_PER_STANDARD_OUTPUT * getProductionMultiplier()));
    }

    /** 装好的机器都要维护；停用、破产冻结和公司已不存在的不收。 */
    static boolean chargesMaintenance(CompanyFacilityStatus status) {
        return status == CompanyFacilityStatus.ACTIVE
                || status == CompanyFacilityStatus.MISSING_INPUT
                || status == CompanyFacilityStatus.OUTPUT_FULL;
    }

    private static double levelDiscount(int level, int bpsPerLevel) {
        return Math.max(0.0, 1.0 - level * (Math.max(0, bpsPerLevel) / 10_000.0));
    }

    /** 从公司现金付给 NPC 资金池；现金不够时有多少付多少。返回实际付出的金额。 */
    private long payRunningCost(long owed) {
        long paid = Math.min(owed, Math.max(0, cash));
        if (paid <= 0) return 0;
        if (!MoneyTransferService.transfer(
                        MoneyEndpoints.company(this), MoneyEndpoints.account(NpcMarketMaker.NPC_UUID), paid)
                .success()) return 0;
        AccountManager.addTransactionRecord(new TransactionRecord(
                companyId, NpcMarketMaker.NPC_UUID, paid, TransactionType.COMPANY_RUNNING_COST, ownerId, name, 1));
        return paid;
    }

    /**
     * 每天的日常开销：按最近几天真实应付的日均算（工资 + 维护 + 库存持有，含没付上的缺口）；
     * 还没有记录时（新公司、旧存档刚升级）用今天的应付额估。
     */
    public long estimateDailyOperatingCost() {
        if (recentRunningCosts.isEmpty()) return runningCostDue().total();
        long total = 0;
        for (long cost : recentRunningCosts) total = MathUtil.saturatedAddNonNegative(total, cost);
        return total / recentRunningCosts.size();
    }

    public long getLastCostShortfall() {
        return lastCostShortfall;
    }

    public List<Long> getRecentRunningCosts() {
        return new ArrayList<>(recentRunningCosts);
    }

    public void restoreRunningCosts(List<Long> recent, long shortfall) {
        recentRunningCosts.clear();
        if (recent != null) for (Long cost : recent) if (cost != null && cost >= 0) addRecentRunningCost(cost);
        lastCostShortfall = Math.max(0, shortfall);
    }

    private void addRecentRunningCost(long cost) {
        recentRunningCosts.add(cost);
        while (recentRunningCosts.size() > PROFIT_HISTORY_DAYS) {
            recentRunningCosts.remove(0);
        }
    }

    private double getProductionMultiplier() {
        return strategy.getProductionMultiplier() * (1.0 + productionLevel * 0.12);
    }

    private void addRecentProfit(long profit) {
        recentProfits.add(profit);
        while (recentProfits.size() > PROFIT_HISTORY_DAYS) {
            recentProfits.remove(0);
        }
    }

    private void generateFinancialReport(long mcDay, long revenue, long expenses, long netProfit) {
        long assets = finance.gameplay.company.CompanyFundamentalBridge.reportAssetValue(this, mcDay);
        long liabilities = finance.debt.CompanyCreditService.totalDebt(companyId);
        CompanyFinancialReport previous = getLatestFinancialReport();
        long assetChange = previous != null ? assets - previous.assets() : 0;
        long profitChange = previous != null ? netProfit - previous.netProfit() : 0;
        addFinancialReportDirect(new CompanyFinancialReport(
                mcDay,
                revenue,
                expenses,
                netProfit,
                assets,
                liabilities,
                cash,
                assetChange,
                profitChange,
                buildFinancialSummary(netProfit, assetChange),
                LocalDateTime.now()));
    }

    private String buildFinancialSummary(long netProfit, long assetChange) {
        String profitText = netProfit >= 0 ? "盈利" + netProfit : "亏损" + Math.abs(netProfit);
        String assetText = assetChange >= 0 ? "资产增加" + assetChange : "资产减少" + Math.abs(assetChange);
        return profitText + "，" + assetText + "，现金余额" + cash;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record DividendRecord(long mcDay, long totalAmount, long perShare) {}
}
