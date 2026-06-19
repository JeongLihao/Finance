package finance.event;

import finance.util.FormatUtil;

/**
 * 市场事件实体 —— 代表一个正在生效或已过期的市场事件。
 */
public class MarketEvent {

    private final String name;
    private final String description;
    private final EventTier tier;
    /** 受影响的商品 ID，null 表示全部商品 */
    private final String commodityId;
    /** 价格乘数，1.20 表示 +20%，0.85 表示 -15% */
    private final double priceMultiplier;
    /** 总持续 ticks */
    private final int totalTicks;
    /** 剩余 ticks */
    private int remainingTicks;

    public MarketEvent(String name, String description, EventTier tier,
                       String commodityId, double priceMultiplier, int totalTicks) {
        this.name = name;
        this.description = description;
        this.tier = tier;
        this.commodityId = commodityId;
        this.priceMultiplier = priceMultiplier;
        this.totalTicks = totalTicks;
        this.remainingTicks = totalTicks;
    }

    /** 从持久化恢复 */
    public MarketEvent(String name, String description, EventTier tier,
                       String commodityId, double priceMultiplier, int totalTicks, int remainingTicks) {
        this.name = name;
        this.description = description;
        this.tier = tier;
        this.commodityId = commodityId;
        this.priceMultiplier = priceMultiplier;
        this.totalTicks = totalTicks;
        this.remainingTicks = remainingTicks;
    }

    // ---- getters ----

    public String getName() { return name; }
    public String getDescription() { return description; }
    public EventTier getTier() { return tier; }
    public String getCommodityId() { return commodityId; }
    public double getPriceMultiplier() { return priceMultiplier; }
    public int getTotalTicks() { return totalTicks; }
    public int getRemainingTicks() { return remainingTicks; }

    /** 是否影响所有商品 */
    public boolean affectsAll() { return commodityId == null; }

    /** 每日 tick 衰减 */
    public void tickDay() {
        if (remainingTicks > 0) {
            remainingTicks = Math.max(0, remainingTicks - 24000);
        }
    }

    public boolean isExpired() { return remainingTicks <= 0; }

    /** 价格变化百分比描述，如 "+20%" 或 "-15%" */
    public String getChangePct() {
        double pct = (priceMultiplier - 1.0) * 100;
        return FormatUtil.formatPercent(pct);
    }

    /** 持续时间描述，如 "3 MC天" 或 "15 分钟" */
    public String getDurationDesc() {
        return ticksToDesc(totalTicks);
    }

    /** 剩余时间描述 */
    public String getRemainingDesc() {
        return ticksToDesc(remainingTicks);
    }

    private static String ticksToDesc(int ticks) {
        int realMinutes = ticks / 1200;
        int hours = realMinutes / 60;
        int mins = realMinutes % 60;
        if (hours >= 1) {
            return hours + "小时" + (mins > 0 ? mins + "分" : "");
        }
        return realMinutes + " 分钟";
    }
}
