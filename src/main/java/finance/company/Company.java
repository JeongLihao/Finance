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

/**
 * 公司实体 —— 拥有现金、库存和实时市场估值。
 * 每个 MC 天自动生产商品，并将可出售产出卖给国际市场实现盈利。
 */
public class Company {

    private final UUID companyId;
    private final String name;
    private final CompanyType type;
    private final UUID ownerId;
    private long cash;
    private final Map<String, Integer> inventory = new HashMap<>();

    /** 库存保留比例 —— 每天卖出 50%，保留 50% */
    private static final double SELL_RATIO = 0.5;

    /** 原料安全库存天数，避免公司把生产原料卖掉后又立刻买回 */
    private static final int RAW_MATERIAL_RESERVE_DAYS = 3;

    // ---- P3：盈利与分红 ----
    /** 当日营收（产出销售收入） */
    private long dailyRevenue;

    /** 当日成本（原料购买支出） */
    private long dailyCost;

    /** 留存收益（未分配利润） */
    private long retainedEarnings;

    /** 上次分红的 MC 天数（用于判断是否需要分红） */
    private long lastDividendDay;

    /** 分红周期（每 N 个 MC 天分红一次） */
    private static final int DIVIDEND_CYCLE_DAYS = 7;

    // ---- P4：IPO ----
    /** 是否已上市 */
    private boolean isPublic;

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
            addInventory(entry.getKey(), entry.getValue());
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

            // 卖出库存的 SELL_RATIO，至少卖 1 个
            int sellQty = Math.max(1, (int)(amount * SELL_RATIO));
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

    /** 公司估值 = 现金 + 库存市值 */
    public long getEstimatedValue() {
        return cash + inventoryValue();
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
        long dailyProfit = dailyRevenue - dailyCost;
        retainedEarnings += dailyProfit;
        dailyRevenue = 0;
        dailyCost = 0;
    }

    /**
     * 尝试分红（CompanyManager 应每 DIVIDEND_CYCLE_DAYS 调用一次）。
     * 返回本次分红的总金额。
     */
    public long tryDividend(long currentMcDay) {
        if (lastDividendDay == 0) {
            lastDividendDay = currentMcDay;
            return 0; // 首次不分红
        }

        if (currentMcDay - lastDividendDay < DIVIDEND_CYCLE_DAYS) {
            return 0; // 未到分红日期
        }

        if (retainedEarnings <= 0) {
            lastDividendDay = currentMcDay;
            return 0; // 无利润可分
        }

        // 分红比例：40% 分给股东，60% 保留再投资
        long dividendAmount = Math.round(retainedEarnings * 0.4);
        retainedEarnings -= dividendAmount;
        lastDividendDay = currentMcDay;

        return dividendAmount;
    }

    public long getDailyRevenue() { return dailyRevenue; }
    public long getDailyCost() { return dailyCost; }
    public long getRetainedEarnings() { return retainedEarnings; }
    public long getLastDividendDay() { return lastDividendDay; }

    /**
     * 获取股息率（每股分红 / 股价）—— 用于 GUI 显示。
     * 假设总股本 10000，则股息 = dailyProfit / 10000 / 股价 * 365天。
     * 简化：按最近日利润年化。
     */
    public double getDividendYieldPercent() {
        if (cash + inventoryValue() <= 0) return 0;
        long recentDailyProfit = dailyRevenue - dailyCost;
        long annualProfit = recentDailyProfit * 365; // 粗估
        long totalValue = getEstimatedValue();
        return totalValue > 0 ? (double) annualProfit / totalValue * 100 : 0;
    }

    // ---- P4：IPO ----

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean pub) { this.isPublic = pub; }
}
