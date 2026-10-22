package finance.rival;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/**
 * 一家 NPC 竞争公司的档案。独立存储、按公司 ID 索引，不往 Company 加字段。
 */
public final class RivalProfile {
    public static final int MAX_HISTORY = 16;

    /** 一条可展示的决策记录。action：SPAWN / STRATEGY / UPGRADE / CONTRACT / IPO。 */
    public record Decision(long day, String action, String detail) {}

    private final UUID companyId;
    private final RivalPersonality personality;
    private final String mainCommodity;
    private final long createdDay;
    private long lastUpgradeDay = Long.MIN_VALUE;
    /** 敌对收购：当前目标公司（null 表示没有）、开始建仓日、上次发起要约或放弃的日子。 */
    private UUID hostileTarget;

    private long hostileStartDay = Long.MIN_VALUE;
    private long hostileCooldownDay = Long.MIN_VALUE;
    /** 股东激进主义：上次发起提案的日子。 */
    private long lastActivismDay = Long.MIN_VALUE;

    private final ArrayDeque<Decision> history = new ArrayDeque<>();

    public RivalProfile(UUID companyId, RivalPersonality personality, String mainCommodity, long createdDay) {
        if (companyId == null) throw new IllegalArgumentException("companyId");
        this.companyId = companyId;
        this.personality = personality == null ? RivalPersonality.forCompany(companyId) : personality;
        this.mainCommodity = mainCommodity == null ? "" : mainCommodity;
        this.createdDay = createdDay;
    }

    public UUID companyId() {
        return companyId;
    }

    public RivalPersonality personality() {
        return personality;
    }

    public String mainCommodity() {
        return mainCommodity;
    }

    public long createdDay() {
        return createdDay;
    }

    public long lastUpgradeDay() {
        return lastUpgradeDay;
    }

    public List<Decision> history() {
        return List.copyOf(history);
    }

    public UUID hostileTarget() {
        return hostileTarget;
    }

    public long hostileStartDay() {
        return hostileStartDay;
    }

    public long hostileCooldownDay() {
        return hostileCooldownDay;
    }

    public long lastActivismDay() {
        return lastActivismDay;
    }

    void startHostile(UUID target, long day) {
        hostileTarget = target;
        hostileStartDay = day;
    }

    void endHostile(long day) {
        hostileTarget = null;
        hostileStartDay = Long.MIN_VALUE;
        hostileCooldownDay = day;
    }

    void setLastActivismDay(long day) {
        lastActivismDay = day;
    }

    /** 反序列化用。 */
    public void restoreHostile(UUID target, long start, long cooldown, long activism) {
        hostileTarget = target;
        hostileStartDay = start;
        hostileCooldownDay = cooldown;
        lastActivismDay = activism;
    }

    /** 竞争公司持股用的确定性投资账户（上市时的创始股记在这里）。 */
    public static UUID investmentAccount(UUID companyId) {
        return UUID.nameUUIDFromBytes(("finance-rival-" + companyId).getBytes(StandardCharsets.UTF_8));
    }

    void setLastUpgradeDay(long day) {
        lastUpgradeDay = day;
    }

    void record(long day, String action, String detail) {
        while (history.size() >= MAX_HISTORY) history.removeFirst();
        history.addLast(new Decision(day, action, detail == null ? "" : detail));
    }

    /** 反序列化用。 */
    public void restoreDirect(long lastUpgrade, List<Decision> decisions) {
        lastUpgradeDay = lastUpgrade;
        history.clear();
        if (decisions != null) {
            for (Decision d : decisions.subList(Math.max(0, decisions.size() - MAX_HISTORY), decisions.size())) {
                if (d != null) history.addLast(d);
            }
        }
    }
}
