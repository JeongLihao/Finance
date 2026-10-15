package finance.stock;

import java.util.List;
import java.util.UUID;

/**
 * 股票标的 —— 包装 StockPriceEngine，负责价格和数据管理。
 * 第一版由系统公司自动生成；后续支持玩家 IPO。
 */
public class Stock {

    private final String symbol;
    private final String name;
    private final UUID companyId;
    private long totalShares;

    /** 流通股（IPO 时决定，默认等于 totalShares） */
    private long floatShares;

    /** 公司/系统持有的未流通股 */
    private long ownerShares;
    private long treasuryShares;

    /**
     * 创始人股份锁定期（MC 天）—— IPO 后创始人 N 天内不可卖出。
     * 与模组内"周"概念一致（分红周期、7 天利润平滑窗口），给每日基本面
     * 重定价足够时间把操纵价格打回原形。
     */
    public static final int FOUNDER_SHARE_LOCKUP_DAYS = 7;

    /** IPO 创始人（玩家 IPO 时设置；系统股票为 null） */
    private UUID founderId;

    /** 创始人股份锁定期剩余天数（0 = 无锁定） */
    private int founderLockupDaysRemaining;

    /** 做市商累计接盘股数（终身），防止无限印钞 */
    private long marketMakerBoughtShares;

    /**
     * 停牌标志 —— 要约收购期间目标公司股票停牌。
     * 停牌期间不可挂新单（可撤单）、做市商暂停报价，但基本面定价照常。
     */
    private boolean tradingHalted;

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
    public long getTreasuryShares(){return treasuryShares;}
    public long getVotingShares(){return Math.max(0,totalShares-treasuryShares);}
    public boolean addTreasuryShares(long quantity){if(quantity<=0||treasuryShares>Long.MAX_VALUE-quantity||treasuryShares+quantity>totalShares)return false;treasuryShares+=quantity;return true;}
    public boolean retireTreasuryShares(long quantity){if(quantity<=0||quantity>treasuryShares||quantity>totalShares)return false;treasuryShares-=quantity;totalShares-=quantity;floatShares=Math.min(floatShares,totalShares);availableShares=floatShares;return true;}
    public void restoreTreasuryShares(long quantity){if(quantity>=0&&quantity<=totalShares)treasuryShares=quantity;}

    // ---- 创始人锁定与做市商深度 ----

    public UUID getFounderId() { return founderId; }

    public int getFounderLockupDaysRemaining() { return founderLockupDaysRemaining; }

    public long getMarketMakerBoughtShares() { return marketMakerBoughtShares; }

    /** IPO 时设置创始人锁定 */
    public void setFounderLockup(UUID founderId, int lockupDays) {
        this.founderId = founderId;
        this.founderLockupDaysRemaining = Math.max(0, lockupDays);
    }

    /** 加载存档时恢复创始人锁定状态（向后兼容旧存档） */
    public void restoreFounderLockup(UUID founderId, int lockupDaysRemaining) {
        this.founderId = founderId;
        this.founderLockupDaysRemaining = Math.max(0, lockupDaysRemaining);
    }

    /** 指定玩家是否处于创始人锁定期 */
    public boolean isFounderLocked(UUID playerId) {
        return founderId != null && founderId.equals(playerId) && founderLockupDaysRemaining > 0;
    }

    /** 做市商接盘计数（终身累计） */
    public void addMarketMakerBoughtShares(long quantity) {
        if (quantity > 0 && marketMakerBoughtShares <= Long.MAX_VALUE - quantity) {
            marketMakerBoughtShares += quantity;
        }
    }

    /** 加载存档时恢复做市商接盘计数 */
    public void restoreMarketMakerBoughtShares(long shares) {
        if (shares >= 0) {
            marketMakerBoughtShares = shares;
        }
    }

    /** 是否停牌中（要约收购期间） */
    public boolean isTradingHalted() { return tradingHalted; }

    /** 设置停牌状态 */
    public void setTradingHalted(boolean halted) { this.tradingHalted = halted; }

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
    public List<StockPriceEngine.PriceSnapshot> getSnapshots() { return priceEngine.getSnapshots(); }

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

    public boolean canIncreaseShares(long quantity) {
        return quantity > 0
                && totalShares <= Long.MAX_VALUE - quantity
                && floatShares <= Long.MAX_VALUE - quantity;
    }

    public boolean increaseShares(long quantity) {
        if (!canIncreaseShares(quantity)) {
            return false;
        }
        totalShares += quantity;
        floatShares += quantity;
        availableShares = floatShares;
        return true;
    }

    public boolean decreaseShares(long quantity) {
        if (quantity <= 0 || totalShares < quantity || floatShares < quantity) return false;
        totalShares -= quantity;
        floatShares -= quantity;
        availableShares = floatShares;
        return true;
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

    public void recordPriceSnapshot(long volume) {
        priceEngine.recordSnapshot(volume);
    }

    /**
     * 每 MC 天调用 —— 基本面更新 + 日统计重置。
     * 注意：创始人锁仓递减已拆出到 {@link #tickFounderLockupDay()}，仅由日结算显式驱动；
     * 开服初始化复用本方法更新公允价值时，不再消耗锁仓天数（P1-2：每次重启曾偷减 1 天）。
     */
    public void updateFairValueAndResetDay(long companyAssetValue, long totalShares,
                                           long smoothedDailyProfit, double industrySentiment) {
        priceEngine.updateFairValue(companyAssetValue, totalShares, smoothedDailyProfit, industrySentiment);
        priceEngine.newDayReset();
    }

    /**
     * 创始人锁定期按天递减 —— 仅由日结算（{@code StockMarketManager.tickNewDay}）调用，
     * 开服初始化路径（FinanceMod）不得调用。
     */
    public void tickFounderLockupDay() {
        if (founderLockupDaysRemaining > 0) {
            founderLockupDaysRemaining--;
        }
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

    public void setNoiseOffset(int offset) {
        priceEngine.setNoiseOffset(offset);
    }

    public int getNoiseOffset() {
        return priceEngine.getNoiseOffset();
    }

    public void addSnapshotDirect(StockPriceEngine.PriceSnapshot snapshot) {
        priceEngine.addSnapshotDirect(snapshot);
    }

    public void clearSnapshots() {
        priceEngine.clearSnapshots();
    }
}
