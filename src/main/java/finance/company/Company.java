package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.util.MathUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

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

    /** 每日基础运营成本占理论产值的比例 */
    private static final double OPERATING_COST_RATIO = 0.12;

    /** 库存持有成本占库存市值的比例，价格下行时会惩罚积压库存 */
    private static final double INVENTORY_CARRY_COST_RATIO = 0.01;

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

    /** 最近分红历史 */
    private final List<DividendRecord> dividendHistory = new ArrayList<>();

    /** 公司级分红比例；为负数时使用管理员全局默认策略。 */
    private double dividendRatio = -1.0;

    /** 公司级分红周期；为 0 或负数时使用管理员全局默认策略。 */
    private int dividendCycleDays = -1;

    /** 最近若干天净利润 */
    private final List<Long> recentProfits = new ArrayList<>();

    /** 最近周期财报 */
    private final List<CompanyFinancialReport> financialReports = new ArrayList<>();

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

    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public CompanyType getType() { return type; }
    public UUID getOwnerId() { return ownerId; }
    public boolean isPlayerOwned() { return ownerId != null; }
    public long getCash() { return cash; }
    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }
    public boolean isBankruptcyRisk() { return bankruptcyRisk; }
    public long getBankruptcyRiskStartDay() { return bankruptcyRiskStartDay; }
    public void setBankruptcyRisk(boolean risk, long startDay) {
        this.bankruptcyRisk = risk;
        this.bankruptcyRiskStartDay = risk ? startDay : -1;
    }

    // ---- 库存 ----

    public Map<String, Integer> getInventory() { return inventory; }

    public int getInventoryAmount(String commodityId) {
        return inventory.getOrDefault(commodityId, 0);
    }

    public void addInventory(String commodityId, int amount) {
        inventory.put(commodityId, getInventoryAmount(commodityId) + amount);
    }

    public boolean removeInventory(String commodityId, int amount) {
        int current = getInventoryAmount(commodityId);
        if (current < amount) return false;
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
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (mp == null) return;
        long askPrice = mp.getAskPrice();
        long totalCost = MathUtil.multiplyExactOrNegative1(askPrice, quantity);
        if (totalCost <= 0) return;

        int marketStock = CommodityInventoryManager.getCommodityAmount(
                NpcMarketMaker.NPC_UUID, commodityId);
        if (marketStock < quantity) return;
        if (cash < totalCost) return;

        CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity);
        addInventory(commodityId, quantity);
        withdraw(totalCost);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, totalCost);

        // P3：记录成本
        dailyCost += totalCost;

        AccountManager.addTransactionRecord(
                new TransactionRecord(companyId, NpcMarketMaker.NPC_UUID,
                        totalCost, TransactionType.NPC_SELL));

        NpcMarketMaker.recordNpcTrade(commodityId, false, quantity, askPrice);
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
            int sellQty = Math.max(1, (int)(amount * effectiveSellRatio));
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

            if (removeInventory(commodityId, sellQty)) {
                // 商品：公司 → 国际市场
                CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, sellQty);
                // 资金：国际市场 → 公司
                AccountManager.withdraw(NpcMarketMaker.NPC_UUID, totalCost);
                deposit(totalCost);

                // P3：记录收益
                dailyRevenue += totalCost;

                AccountManager.addTransactionRecord(
                        new TransactionRecord(NpcMarketMaker.NPC_UUID, companyId,
                                totalCost, TransactionType.NPC_BUY));

                // 更新行情
                NpcMarketMaker.recordNpcTrade(commodityId, true, sellQty, bidPrice);
            }
        }
    }

    // ---- 估值 ----

    /** 库存市值 —— 按国际市场当前 midPrice 实时计价 */
    public long inventoryValue() {
        long total = 0;
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            MarketPrice mp = NpcMarketMaker.getMarketPrice(entry.getKey());
            if (mp != null) {
                total += (long) mp.getMidPrice() * entry.getValue();
            }
        }
        return total;
    }

    /** 公司估值 = 现金 + 库存市值（展示用原始资产值） */
    public long getEstimatedValue() {
        return cash + inventoryValue();
    }

    /** 股票基本面资产值：库存按行业景气折价，避免商品跌价时股票仍按满额资产估值。 */
    public long getFundamentalAssetValue() {
        double sentiment = getIndustrySentiment();
        double inventoryDiscount = clamp(0.45 + sentiment * 0.45, 0.35, 1.05);
        return Math.max(0, cash + Math.round(inventoryValue() * inventoryDiscount));
    }

    // ---- 资金 ----

    public void deposit(long amount) { cash += amount; }

    public boolean withdraw(long amount) {
        if (cash < amount) return false;
        cash -= amount;
        return true;
    }

    private int getReserveAmount(String commodityId) {
        return type.getDailyConsumption()
                .getOrDefault(commodityId, 0) * RAW_MATERIAL_RESERVE_DAYS;
    }

    // ---- P3：盈利与分红 ----

    /**
     * 每 MC 天调用（在 produce 和 autoTrade 后）—— 计算日利润，累加到留存收益。
     */
    public void settleDailyProfits() {
        settleDailyProfits(-1);
    }

    public void settleDailyProfits(long mcDay) {
        dailyCost += calculateOperatingCost();
        long dailyProfit = dailyRevenue - dailyCost;
        long revenue = dailyRevenue;
        long expenses = dailyCost;
        retainedEarnings += dailyProfit;
        if (dailyProfit > 0) {
            distributableProfit += dailyProfit;
        }
        addRecentProfit(dailyProfit);
        generateFinancialReport(mcDay, revenue, expenses, dailyProfit);
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

    public long getDailyRevenue() { return dailyRevenue; }
    public long getDailyCost() { return dailyCost; }
    public long getRetainedEarnings() { return retainedEarnings; }
    public long getDistributableProfit() { return distributableProfit; }
    public long getLastDividendDay() { return lastDividendDay; }
    public double getDividendRatio() { return dividendRatio; }
    public int getDividendCycleDays() { return dividendCycleDays; }
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
    public List<Long> getRecentProfits() { return new ArrayList<>(recentProfits); }
    public List<DividendRecord> getDividendHistory() { return new ArrayList<>(dividendHistory); }
    public List<CompanyFinancialReport> getFinancialReports() { return new ArrayList<>(financialReports); }
    public CompanyFinancialReport getLatestFinancialReport() {
        return financialReports.isEmpty() ? null : financialReports.get(financialReports.size() - 1);
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

    public void restoreFinancials(long dailyRevenue, long dailyCost, long retainedEarnings,
                                  long lastDividendDay, List<Long> recentProfits) {
        restoreFinancials(dailyRevenue, dailyCost, retainedEarnings, retainedEarnings,
                lastDividendDay, recentProfits, List.of());
    }

    public void restoreFinancials(long dailyRevenue, long dailyCost, long retainedEarnings,
                                  long distributableProfit, long lastDividendDay,
                                  List<Long> recentProfits, List<DividendRecord> dividendHistory) {
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

    /**
     * 获取股息率（每股分红 / 股价）—— 用于 GUI 显示。
     * 假设总股本 10000，则股息 = dailyProfit / 10000 / 股价 * 365天。
     * 简化：按最近日利润年化。
     */
    public double getDividendYieldPercent() {
        if (cash + inventoryValue() <= 0) return 0;
        long recentDailyProfit = getSmoothedDailyProfit();
        long annualProfit = recentDailyProfit * 365; // 粗估
        long totalValue = getEstimatedValue();
        return totalValue > 0 ? (double) annualProfit / totalValue * 100 : 0;
    }

    // ---- P4：IPO ----

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean pub) { this.isPublic = pub; }

    public CompanyStrategy getStrategy() { return strategy; }
    public int getProductionLevel() { return productionLevel; }
    public int getStorageLevel() { return storageLevel; }
    public int getManagementLevel() { return managementLevel; }
    public double getAutoSellRatio() { return autoSellRatio; }

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
        int nextLevel = switch (upgradeType) {
            case "PRODUCTION" -> productionLevel + 1;
            case "STORAGE" -> storageLevel + 1;
            case "MANAGEMENT" -> managementLevel + 1;
            default -> 1;
        };
        return 2_500L * nextLevel * nextLevel;
    }

    public void restoreManagement(CompanyStrategy strategy, int productionLevel, int storageLevel,
                                  int managementLevel, double autoSellRatio) {
        setStrategy(strategy);
        this.productionLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, productionLevel));
        this.storageLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, storageLevel));
        this.managementLevel = Math.max(0, Math.min(MAX_UPGRADE_LEVEL, managementLevel));
        setAutoSellRatio(autoSellRatio);
    }

    private long calculateOperatingCost() {
        long theoreticalProductionValue = 0;
        for (Map.Entry<String, Integer> entry : type.getDailyProduction().entrySet()) {
            MarketPrice mp = NpcMarketMaker.getMarketPrice(entry.getKey());
            long price = mp != null ? mp.getMidPrice() : 1;
            theoreticalProductionValue += Math.max(0, price * (long) entry.getValue());
        }
        long productionCost = Math.round(theoreticalProductionValue * OPERATING_COST_RATIO
                * strategy.getOperatingCostMultiplier() * (1.0 - managementLevel * 0.06));
        long inventoryCarryCost = Math.round(inventoryValue() * INVENTORY_CARRY_COST_RATIO
                * (1.0 - storageLevel * 0.08));
        return Math.max(1, productionCost + inventoryCarryCost);
    }

    public long estimateDailyOperatingCost() {
        return calculateOperatingCost();
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
        long assets = Math.max(0, cash + inventoryValue());
        long liabilities = 0;
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

    public record DividendRecord(long mcDay, long totalAmount, long perShare) {
    }
}
