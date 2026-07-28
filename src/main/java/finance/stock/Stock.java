package finance.stock;

import java.util.UUID;

/**
 * 股票标的 —— 包装 StockPriceEngine，负责价格和数据管理。
 * 第一版由系统公司自动生成；后续支持玩家 IPO。
 */
public class Stock {

    private final String symbol;
    private final String name;
    private final UUID companyId;
    private final long totalShares;

    /** 流通股（IPO 时决定，默认等于 totalShares） */
    private long floatShares;

    /** 公司/系统持有的未流通股 */
    private long ownerShares;

    /** 新定价引擎 */
    private final StockPriceEngine priceEngine;

    // ---- 兼容旧版本字段（读取时用） ----
    @Deprecated
    private long availableShares;

    /**
     * 兼容旧存档的构造（用于从 NBT 加载旧数据）。
     * 参数：symbol, name, companyId, totalShares, availableShares, lastPrice, previousClose, dayVolume
     * 新建议：改用完整构造。
     */
    @Deprecated
    public static Stock fromLegacyData(String symbol, String name, UUID companyId, long totalShares,
                                       long availableShares, long lastPrice, long previousClose, long dayVolume) {
        return new Stock(symbol, name, companyId, totalShares,
                availableShares, // floatShares = availableShares
                0,              // ownerShares = 0
                lastPrice,      // currentPrice
                lastPrice       // fairValue = lastPrice（旧逻辑）
        );
    }

    /** 完整构造（支持新字段） */
    public Stock(String symbol, String name, UUID companyId, long totalShares,
                 long floatShares, long ownerShares, long currentPrice, long fairValue) {
        this.symbol = symbol;
        this.name = name;
        this.companyId = companyId;
        this.totalShares = totalShares;
        this.floatShares = floatShares > 0 ? floatShares : totalShares;
        this.ownerShares = ownerShares;
        this.availableShares = this.floatShares; // 兼容

        this.priceEngine = new StockPriceEngine(symbol, currentPrice, fairValue);
    }

    // ---- Getter ----

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public UUID getCompanyId() { return companyId; }
    public long getTotalShares() { return totalShares; }
    public long getFloatShares() { return floatShares; }
    public long getOwnerShares() { return ownerShares; }

    /** 兼容旧 API：availableShares = floatShares */
    public long getAvailableShares() { return floatShares; }

    public long getLastPrice() { return priceEngine.getCurrentPrice(); }
    public long getPreviousClose() { return priceEngine.getDayOpen(); }
    public long getDayVolume() { return priceEngine.getDayVolume(); }

    public long getFairValue() { return priceEngine.getFairValue(); }
    public double getTradeMomentum() { return priceEngine.getTradeMomentum(); }

    public double getDayChange() {
        return priceEngine.getDayChange();
    }

    public long getDayHigh() { return priceEngine.getDayHigh(); }
    public long getDayLow() { return priceEngine.getDayLow(); }

    // ---- 交易相关（旧 API 兼容） ----

    public boolean removeAvailableShares(long quantity) {
        if (quantity <= 0 || floatShares < quantity) return false;
        floatShares -= quantity;
        availableShares = floatShares; // 同步兼容字段
        return true;
    }

    public void addAvailableShares(long quantity) {
        if (quantity > 0) {
            floatShares += quantity;
            if (floatShares > totalShares) {
                floatShares = totalShares;
            }
            availableShares = floatShares;
        }
    }

    /**
     * 记录一笔成交 —— 通知定价引擎，由引擎驱动价格变化。
     *
     * @param tradePrice   成交单价
     * @param tradeVolume  成交数量
     * @param isBuy        true = 买入（推高价格），false = 卖出（压低价格）
     */
    public void recordTrade(long tradePrice, long tradeVolume, boolean isBuy) {
        priceEngine.recordTrade(tradePrice, tradeVolume, floatShares, isBuy);
    }

    /**
     * 旧 API 兼容版 —— 单纯记录成交，不推动价格（向后兼容）。
     */
    @Deprecated
    public void recordTrade(long price, long quantity) {
        // 旧代码调用这个时，默认是「买入」
        recordTrade(price, quantity, true);
    }

    /**
     * 设置股价（用于 stub / 初始化）。
     */
    public boolean setLastPrice(long price) {
        if (price <= 0 || price == getLastPrice()) {
            return false;
        }
        priceEngine.setCurrentPrice(price);
        return true;
    }

    // ---- Tick 调用 ----

    public void tickMomentum() {
        priceEngine.tickMomentum();
    }

    public void tickNoise() {
        priceEngine.tickNoise();
    }

    public void recalculateFromCurrent() {
        priceEngine.recalculateFromCurrent();
    }

    /**
     * 每 MC 天调用 —— 基本面更新 + 日统计重置。
     */
    public void updateFairValueAndResetDay(long companyTotalValue, long totalShares, long dailyProfit) {
        priceEngine.updateFairValue(companyTotalValue, totalShares, dailyProfit);
        priceEngine.newDayReset();
    }

    public void newDayReset() {
        priceEngine.newDayReset();
    }

    // ---- 持久化支持 ----

    public void setFairValue(long fairValue) {
        priceEngine.setFairValue(fairValue);
    }

    public void setTradeMomentum(double momentum) {
        priceEngine.setTradeMomentum(momentum);
    }

    public void setDayOpen(long dayOpen) {
        priceEngine.setDayOpen(dayOpen);
    }
}
