package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.commodity.CommodityInventoryManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司实体 —— 拥有现金、库存和实时市场估值。
 * 每个 MC 天自动生产商品，并将部分库存卖给 NPC 实现盈利。
 */
public class Company {

    private final UUID companyId;
    private final String name;
    private final CompanyType type;
    private long cash;
    private final Map<String, Integer> inventory = new HashMap<>();

    /** 库存保留比例 —— 每天卖出 50%，保留 50% */
    private static final double SELL_RATIO = 0.5;

    public Company(UUID companyId, String name, CompanyType type, long cash) {
        this.companyId = companyId;
        this.name = name;
        this.type = type;
        this.cash = cash;
    }

    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public CompanyType getType() { return type; }
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
        consumeRawMaterials();
        for (Map.Entry<String, Integer> entry : type.getDailyProduction().entrySet()) {
            addInventory(entry.getKey(), entry.getValue());
        }
    }

    /** 确保原料充足：不足时从 NPC 市场购买 */
    private void consumeRawMaterials() {
        Map<String, Integer> consumption = type.getDailyConsumption();
        if (consumption.isEmpty()) return;

        for (Map.Entry<String, Integer> entry : consumption.entrySet()) {
            String commodityId = entry.getKey();
            int needed = entry.getValue();
            int current = getInventoryAmount(commodityId);
            int shortfall = needed - current;

            if (shortfall > 0) {
                buyFromNpc(commodityId, shortfall);
                current = getInventoryAmount(commodityId);
            }

            int toConsume = Math.min(current, needed);
            if (toConsume > 0) {
                removeInventory(commodityId, toConsume);
            }
        }
    }

    /** 从 NPC 市场购买原料 */
    private void buyFromNpc(String commodityId, int quantity) {
        MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
        if (mp == null) return;
        long askPrice = mp.getAskPrice();
        long totalCost = multiplyPriceQuantity(askPrice, quantity);
        if (totalCost <= 0) return;

        int npcStock = CommodityInventoryManager.getCommodityAmount(
                NpcMarketMaker.NPC_UUID, commodityId);
        if (npcStock < quantity) return;
        if (cash < totalCost) return;

        CommodityInventoryManager.removeCommodity(NpcMarketMaker.NPC_UUID, commodityId, quantity);
        addInventory(commodityId, quantity);
        withdraw(totalCost);
        AccountManager.deposit(NpcMarketMaker.NPC_UUID, totalCost);

        AccountManager.addTransactionRecord(
                new TransactionRecord(companyId, NpcMarketMaker.NPC_UUID,
                        totalCost, TransactionType.NPC_SELL));

        long newNpcStock = CommodityInventoryManager.getCommodityAmount(
                NpcMarketMaker.NPC_UUID, commodityId);
        mp.onNpcTrade(newNpcStock, false, quantity);
        mp.recordTrade(askPrice, quantity);
    }

    /** 每日自动交易 —— 将库存的一部分卖给 NPC 变现 */
    public void autoTrade() {
        for (Map.Entry<String, Integer> entry : new HashMap<>(inventory).entrySet()) {
            String commodityId = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;

            MarketPrice mp = NpcMarketMaker.getMarketPrice(commodityId);
            if (mp == null) continue;

            long bidPrice = mp.getBidPrice();

            // 卖出库存的 SELL_RATIO，至少卖 1 个
            int sellQty = Math.max(1, (int)(amount * SELL_RATIO));
            sellQty = Math.min(sellQty, amount);

            // NPC 库存是否充足
            int npcStock = CommodityInventoryManager.getCommodityAmount(
                    NpcMarketMaker.NPC_UUID, commodityId);

            // NPC 余额是否充足
            long totalCost = multiplyPriceQuantity(bidPrice, sellQty);
            if (totalCost <= 0) continue;
            long npcBalance = AccountManager.getBalance(NpcMarketMaker.NPC_UUID);

            // 自适应调量：NPC 库存或余额不足时缩量
            while (sellQty > 1 && (npcStock < sellQty || npcBalance < totalCost)) {
                sellQty /= 2;
                totalCost = multiplyPriceQuantity(bidPrice, sellQty);
                if (totalCost <= 0) break;
            }

            if (sellQty <= 0 || totalCost <= 0) continue;

            if (removeInventory(commodityId, sellQty)) {
                // 商品：公司 → NPC
                CommodityInventoryManager.addCommodity(NpcMarketMaker.NPC_UUID, commodityId, sellQty);
                // 资金：NPC → 公司
                AccountManager.withdraw(NpcMarketMaker.NPC_UUID, totalCost);
                deposit(totalCost);

                AccountManager.addTransactionRecord(
                        new TransactionRecord(NpcMarketMaker.NPC_UUID, companyId,
                                totalCost, TransactionType.NPC_BUY));

                // 更新行情
                long newNpcStock = CommodityInventoryManager.getCommodityAmount(
                        NpcMarketMaker.NPC_UUID, commodityId);
                mp.onNpcTrade(newNpcStock, true, sellQty);
                mp.recordTrade(bidPrice, sellQty);
            }
        }
    }

    // ---- 估值 ----

    /** 库存市值 —— 按 NPC 做市商当前 midPrice 实时计价 */
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

    private static long multiplyPriceQuantity(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException ex) {
            return -1;
        }
    }
}
