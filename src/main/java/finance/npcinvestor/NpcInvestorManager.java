package finance.npcinvestor;

import finance.account.AccountManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NPC 投资者的运行时存储：各原型状态、全局日结标记和有界的决策记录。 */
public final class NpcInvestorManager {
    /** 决策记录上限（全部原型合计）。 */
    public static final int MAX_DECISIONS = 256;

    /** 一条可展示的决策记录。action：BUY / SELL / HOLD / FUND。 */
    public record Decision(long day, NpcInvestorArchetype archetype, UUID companyId, String symbol,
                           String action, long quantity, long price, NpcReason reason) { }

    private static final Map<NpcInvestorArchetype, NpcInvestorState> STATES = new EnumMap<>(NpcInvestorArchetype.class);
    private static final ArrayDeque<Decision> DECISIONS = new ArrayDeque<>();
    private static long lastProcessedDay = Long.MIN_VALUE;

    static {
        resetStates();
    }

    private NpcInvestorManager() { }

    public static synchronized NpcInvestorState state(NpcInvestorArchetype archetype) {
        return STATES.get(archetype);
    }

    public static synchronized long lastProcessedDay() { return lastProcessedDay; }

    static synchronized void setLastProcessedDay(long day) { lastProcessedDay = day; }

    /**
     * 确保四个原型账户都以系统账户身份存在。
     * 必须在任何资金操作之前调用：{@link AccountManager#getAccount} 会给新 UUID 发 1000 新手资金。
     */
    public static void ensureAccounts() {
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            AccountManager.getOrCreateSystemAccount(a.accountId());
        }
    }

    static synchronized void addDecision(Decision decision) {
        if (decision == null) return;
        while (DECISIONS.size() >= MAX_DECISIONS) DECISIONS.removeFirst();
        DECISIONS.addLast(decision);
    }


    public static synchronized List<Decision> decisionsDirect() { return List.copyOf(DECISIONS); }

    public static synchronized void restoreState(NpcInvestorArchetype archetype, boolean funded, long contributed,
                                                 Map<UUID, Integer> sentiments, Map<UUID, Integer> targets) {
        NpcInvestorState state = STATES.get(archetype);
        if (state != null) state.restore(funded, contributed, sentiments, targets);
    }

    public static synchronized void restoreDirect(long day, List<Decision> decisions) {
        lastProcessedDay = day;
        DECISIONS.clear();
        if (decisions != null) {
            for (Decision d : decisions) addDecision(d);
        }
    }

    public static synchronized void clearDirect() {
        resetStates();
        DECISIONS.clear();
        lastProcessedDay = Long.MIN_VALUE;
    }

    private static void resetStates() {
        STATES.clear();
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) STATES.put(a, new NpcInvestorState(a));
    }
}
