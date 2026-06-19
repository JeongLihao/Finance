package finance.stock;

import java.util.UUID;

/**
 * 股票标的，第一版由系统公司自动生成。
 */
public class Stock {

    private final String symbol;
    private final String name;
    private final UUID companyId;
    private final long totalShares;
    private long availableShares;
    private long lastPrice;
    private long previousClose;
    private long dayVolume;

    public Stock(String symbol, String name, UUID companyId, long totalShares,
                 long availableShares, long lastPrice, long previousClose, long dayVolume) {
        this.symbol = symbol;
        this.name = name;
        this.companyId = companyId;
        this.totalShares = totalShares;
        this.availableShares = availableShares;
        this.lastPrice = lastPrice;
        this.previousClose = previousClose;
        this.dayVolume = dayVolume;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public UUID getCompanyId() { return companyId; }
    public long getTotalShares() { return totalShares; }
    public long getAvailableShares() { return availableShares; }
    public long getLastPrice() { return lastPrice; }
    public long getPreviousClose() { return previousClose; }
    public long getDayVolume() { return dayVolume; }

    public double getDayChange() {
        if (previousClose <= 0) return 0;
        return (double) (lastPrice - previousClose) / previousClose * 100;
    }

    public boolean removeAvailableShares(long quantity) {
        if (quantity <= 0 || availableShares < quantity) return false;
        availableShares -= quantity;
        return true;
    }

    public void addAvailableShares(long quantity) {
        if (quantity > 0) {
            availableShares += quantity;
            if (availableShares > totalShares) {
                availableShares = totalShares;
            }
        }
    }

    public void recordTrade(long price, long quantity) {
        if (price > 0) {
            lastPrice = price;
        }
        if (quantity > 0) {
            dayVolume += quantity;
        }
    }
}
