package finance.npcinvestor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 单个 NPC 投资者原型的持久状态。持仓本身在 StockPortfolioManager，这里只存决策相关数据。 */
public final class NpcInvestorState {
    /** 每个原型最多跟踪的公司数，防止存档膨胀。 */
    public static final int MAX_TRACKED_COMPANIES = 256;

    private final NpcInvestorArchetype archetype;
    private boolean initialFunded;
    private long contributedCapital;
    private final Map<UUID, Integer> sentiment = new LinkedHashMap<>();
    private final Map<UUID, Integer> targetBps = new LinkedHashMap<>();

    public NpcInvestorState(NpcInvestorArchetype archetype) {
        this.archetype = archetype;
    }

    public NpcInvestorArchetype archetype() { return archetype; }
    public boolean initialFunded() { return initialFunded; }
    public long contributedCapital() { return contributedCapital; }
    public Map<UUID, Integer> sentiment() { return Collections.unmodifiableMap(sentiment); }
    public Map<UUID, Integer> targetBps() { return Collections.unmodifiableMap(targetBps); }

    public int sentimentFor(UUID companyId) { return sentiment.getOrDefault(companyId, 0); }
    public int targetBpsFor(UUID companyId) { return targetBps.getOrDefault(companyId, 0); }

    void markInitialFunded() { initialFunded = true; }

    void addContribution(long amount) {
        if (amount <= 0) return;
        contributedCapital = contributedCapital > Long.MAX_VALUE - amount ? Long.MAX_VALUE : contributedCapital + amount;
    }

    void record(UUID companyId, int sentimentValue, int target) {
        if (companyId == null) return;
        if (!sentiment.containsKey(companyId) && sentiment.size() >= MAX_TRACKED_COMPANIES) return;
        sentiment.put(companyId, Math.max(-100, Math.min(100, sentimentValue)));
        targetBps.put(companyId, Math.max(0, target));
    }

    /** 清理已退市公司的跟踪数据。 */
    void retainCompanies(java.util.Set<UUID> listed) {
        sentiment.keySet().retainAll(listed);
        targetBps.keySet().retainAll(listed);
    }

    /** 反序列化用：直接恢复字段。 */
    void restore(boolean funded, long contributed, Map<UUID, Integer> sentiments, Map<UUID, Integer> targets) {
        initialFunded = funded;
        contributedCapital = Math.max(0, contributed);
        sentiment.clear();
        targetBps.clear();
        if (sentiments != null) {
            sentiments.forEach((id, v) -> {
                if (id != null && v != null && sentiment.size() < MAX_TRACKED_COMPANIES) {
                    sentiment.put(id, Math.max(-100, Math.min(100, v)));
                }
            });
        }
        if (targets != null) {
            targets.forEach((id, v) -> {
                if (id != null && v != null && targetBps.size() < MAX_TRACKED_COMPANIES) {
                    targetBps.put(id, Math.max(0, v));
                }
            });
        }
    }
}
