package finance.event;

/**
 * 事件等级。
 */
public enum EventTier {

    /** 一级事件：常见，±5%~10%，持续 0.5~1 MC 天 */
    MINOR(5, 10, 12000, 24000),

    /** 二级事件：稀有，±15%~25%，持续 1~3 MC 天 */
    MAJOR(15, 25, 24000, 72000),

    /** 三级事件（黑天鹅）：极低概率，±40%~60%，持续 2~5 MC 天 */
    BLACK_SWAN(40, 60, 48000, 120000);

    public final int minPct;
    public final int maxPct;
    public final int minDuration;
    public final int maxDuration;

    EventTier(int minPct, int maxPct, int minDuration, int maxDuration) {
        this.minPct = minPct;
        this.maxPct = maxPct;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }
}
