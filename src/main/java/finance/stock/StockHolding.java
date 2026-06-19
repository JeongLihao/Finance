package finance.stock;

/**
 * 玩家单只股票持仓。
 */
public class StockHolding {

    private long quantity;
    private long averageCost;

    public StockHolding(long quantity, long averageCost) {
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    public long getQuantity() { return quantity; }
    public long getAverageCost() { return averageCost; }

    public void add(long addQuantity, long price) {
        if (addQuantity <= 0 || price <= 0) return;
        long totalCost = averageCost * quantity + price * addQuantity;
        quantity += addQuantity;
        averageCost = quantity == 0 ? 0 : totalCost / quantity;
    }

    public boolean remove(long removeQuantity) {
        if (removeQuantity <= 0 || quantity < removeQuantity) return false;
        quantity -= removeQuantity;
        if (quantity == 0) {
            averageCost = 0;
        }
        return true;
    }
}
