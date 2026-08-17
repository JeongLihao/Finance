package finance.stock;

import java.math.BigInteger;

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

    public boolean add(long addQuantity, long price) {
        if (!canAdd(addQuantity)) return false;
        if (price <= 0) {
            quantity += addQuantity;
            return true;
        }
        long newQuantity = quantity + addQuantity;
        BigInteger totalCost = BigInteger.valueOf(Math.max(0, averageCost))
                .multiply(BigInteger.valueOf(quantity))
                .add(BigInteger.valueOf(price).multiply(BigInteger.valueOf(addQuantity)));
        BigInteger newAverage = totalCost.divide(BigInteger.valueOf(newQuantity));
        if (newAverage.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
        quantity = newQuantity;
        averageCost = newAverage.longValue();
        return true;
    }

    public boolean canAdd(long addQuantity) {
        return addQuantity > 0 && quantity >= 0 && quantity <= Long.MAX_VALUE - addQuantity;
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
