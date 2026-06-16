package finance.company;

import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 公司实体 —— 拥有现金、库存和实时市场估值。
 * 股票/股东/IPO 等后续扩展。
 */
public class Company {

    private final UUID companyId;
    private final String name;
    private final CompanyType type;
    private long cash;
    private final Map<String, Integer> inventory = new HashMap<>();

    public Company(UUID companyId, String name, CompanyType type, long cash) {
        this.companyId = companyId;
        this.name = name;
        this.type = type;
        this.cash = cash;
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

    public long getCash() {
        return cash;
    }

    // ---- 库存 ----

    public Map<String, Integer> getInventory() {
        return inventory;
    }

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

    public void deposit(long amount) {
        cash += amount;
    }

    public boolean withdraw(long amount) {
        if (cash < amount) {
            return false;
        }
        cash -= amount;
        return true;
    }
}
