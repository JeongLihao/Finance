package finance.npcinvestor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC 治理记录：每个原型对每个提案的投票与理由、对每个要约的响应。全部有界，
 * 可按 proposalId 查询，供 GUI 展示"每类 NPC 怎么投、为什么"。
 */
public final class NpcGovernanceManager {
    public static final int MAX_VOTE_RECORDS = 512;
    public static final int MAX_TENDER_RECORDS = 256;

    /** choice 为 SUPPORT / OPPOSE / ABSTAIN；power 为快照投票权。 */
    public record VoteRecord(
            UUID proposalId,
            NpcInvestorArchetype archetype,
            NpcVotingRules.Choice choice,
            NpcVotingRules.VoteReason reason,
            long power,
            long day) {}

    /** accepted 为 true 时 shares 是申报的股数；为 false 表示溢价不足而拒绝。 */
    public record TenderRecord(
            UUID offerId,
            NpcInvestorArchetype archetype,
            boolean accepted,
            long shares,
            long offerPrice,
            long thresholdPrice,
            long day) {}

    private static final Map<String, VoteRecord> VOTES = new LinkedHashMap<>();
    private static final Map<String, TenderRecord> TENDERS = new LinkedHashMap<>();
    private static long lastProcessedDay = Long.MIN_VALUE;

    private NpcGovernanceManager() {}

    static String key(UUID id, NpcInvestorArchetype archetype) {
        return id + "/" + archetype.name();
    }

    public static synchronized long lastProcessedDay() {
        return lastProcessedDay;
    }

    static synchronized void setLastProcessedDay(long day) {
        lastProcessedDay = day;
    }

    static synchronized boolean hasVote(UUID proposalId, NpcInvestorArchetype archetype) {
        return VOTES.containsKey(key(proposalId, archetype));
    }

    static synchronized boolean hasTenderRecord(UUID offerId, NpcInvestorArchetype archetype) {
        TenderRecord r = TENDERS.get(key(offerId, archetype));
        return r != null && r.accepted();
    }

    static synchronized void putVote(VoteRecord r) {
        if (r == null) return;
        VOTES.remove(key(r.proposalId(), r.archetype()));
        while (VOTES.size() >= MAX_VOTE_RECORDS)
            VOTES.remove(VOTES.keySet().iterator().next());
        VOTES.put(key(r.proposalId(), r.archetype()), r);
    }

    /** 同一要约的拒绝记录会被后续的接受或新的拒绝覆盖（要约价不变但公允价可能变化）。 */
    static synchronized void putTender(TenderRecord r) {
        if (r == null) return;
        TENDERS.remove(key(r.offerId(), r.archetype()));
        while (TENDERS.size() >= MAX_TENDER_RECORDS)
            TENDERS.remove(TENDERS.keySet().iterator().next());
        TENDERS.put(key(r.offerId(), r.archetype()), r);
    }

    public static synchronized List<VoteRecord> votesFor(UUID proposalId) {
        List<VoteRecord> out = new ArrayList<>();
        for (VoteRecord r : VOTES.values()) if (r.proposalId().equals(proposalId)) out.add(r);
        return out;
    }

    public static synchronized List<TenderRecord> tenderResponsesFor(UUID offerId) {
        List<TenderRecord> out = new ArrayList<>();
        for (TenderRecord r : TENDERS.values()) if (r.offerId().equals(offerId)) out.add(r);
        return out;
    }

    public static synchronized List<VoteRecord> votesDirect() {
        return List.copyOf(VOTES.values());
    }

    public static synchronized List<TenderRecord> tendersDirect() {
        return List.copyOf(TENDERS.values());
    }

    public static synchronized void restoreDirect(long day, List<VoteRecord> votes, List<TenderRecord> tenders) {
        clearDirect();
        lastProcessedDay = day;
        if (votes != null) votes.forEach(NpcGovernanceManager::putVote);
        if (tenders != null) tenders.forEach(NpcGovernanceManager::putTender);
    }

    public static synchronized void clearDirect() {
        VOTES.clear();
        TENDERS.clear();
        lastProcessedDay = Long.MIN_VALUE;
    }
}
