package finance.factory;

/**
 * 采集机的四种活。它们只收世界里真实存在的东西，不凭空产出。
 *
 * @param interval 多少 tick 干一次活（按 factory.speedPercent 缩放）
 * @param needsFuel 干活要不要烧燃料
 */
public enum HarvestMode {
    /** 收割周围 9×9 成熟的作物并补种；甘蔗只收上半截。 */
    FARM("farm_machine", 20, false),
    /** 砍周围的树，捡掉下的树苗和苹果，在树根补种树苗。 */
    LUMBER("lumber_machine", 20, false),
    /** 在机器正下方挖 5×5 竖井，一层层往下直到基岩。每块要烧燃料。 */
    MINE("mining_machine", 20, true),
    /** 给周围能剪毛的羊剪毛。 */
    SHEAR("shearing_machine", 100, false);

    private final String blockId;
    private final int interval;
    private final boolean needsFuel;

    HarvestMode(String blockId, int interval, boolean needsFuel) {
        this.blockId = blockId;
        this.interval = interval;
        this.needsFuel = needsFuel;
    }

    public String blockId() {
        return blockId;
    }

    public int interval() {
        return interval;
    }

    public boolean needsFuel() {
        return needsFuel;
    }
}
