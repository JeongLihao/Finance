package finance.data.serializer;

import finance.diagnostic.ModuleHealthRegistry;
import finance.diagnostic.ModuleRunState;
import finance.npcinvestor.NpcGovernanceManager;
import finance.npcinvestor.NpcInvestorArchetype;
import finance.npcinvestor.NpcVotingRules;
import finance.npcinvestor.NpcInvestorManager;
import finance.npcinvestor.NpcInvestorState;
import finance.npcinvestor.NpcReason;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单人经济（NPC 投资者）持久化。DataVersion 38 新增；旧存档没有该标签时按"刚启用"初始化。
 *
 * <p>持仓本身存在股票持仓里，这里只存决策状态。单条坏记录跳过并计数，有跳过时把
 * NPC_ECONOMY 模块降为只读，不影响其他模块加载。</p>
 */
public final class NpcEconomyDataSerializer {
    public static final String ROOT = "NpcEconomy";
    public static final int VERSION = 1;

    private NpcEconomyDataSerializer() { }

    public static void save(CompoundTag root) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putLong("LastProcessedDay", NpcInvestorManager.lastProcessedDay());
        ListTag investors = new ListTag();
        for (NpcInvestorArchetype archetype : NpcInvestorArchetype.values()) {
            NpcInvestorState state = NpcInvestorManager.state(archetype);
            CompoundTag t = new CompoundTag();
            t.putString("Archetype", archetype.name());
            t.putBoolean("Funded", state.initialFunded());
            t.putLong("Contributed", state.contributedCapital());
            ListTag companies = new ListTag();
            for (Map.Entry<UUID, Integer> e : state.sentiment().entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Company", e.getKey());
                c.putInt("Sentiment", e.getValue());
                c.putInt("TargetBps", state.targetBpsFor(e.getKey()));
                companies.add(c);
            }
            t.put("Companies", companies);
            investors.add(t);
        }
        data.put("Investors", investors);
        ListTag decisions = new ListTag();
        for (NpcInvestorManager.Decision d : NpcInvestorManager.decisionsDirect()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Day", d.day());
            t.putString("Archetype", d.archetype().name());
            if (d.companyId() != null) t.putUUID("Company", d.companyId());
            t.putString("Symbol", d.symbol() == null ? "" : d.symbol());
            t.putString("Action", d.action());
            t.putLong("Qty", d.quantity());
            t.putLong("Price", d.price());
            t.putString("Reason", d.reason().name());
            decisions.add(t);
        }
        data.put("Decisions", decisions);
        data.putLong("GovernanceDay", NpcGovernanceManager.lastProcessedDay());
        ListTag votes = new ListTag();
        for (NpcGovernanceManager.VoteRecord v : NpcGovernanceManager.votesDirect()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Proposal", v.proposalId());
            t.putString("Archetype", v.archetype().name());
            t.putString("Choice", v.choice().name());
            t.putString("Reason", v.reason().name());
            t.putLong("Power", v.power());
            t.putLong("Day", v.day());
            votes.add(t);
        }
        data.put("Votes", votes);
        ListTag tenders = new ListTag();
        for (NpcGovernanceManager.TenderRecord r : NpcGovernanceManager.tendersDirect()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Offer", r.offerId());
            t.putString("Archetype", r.archetype().name());
            t.putBoolean("Accepted", r.accepted());
            t.putLong("Shares", r.shares());
            t.putLong("OfferPrice", r.offerPrice());
            t.putLong("Threshold", r.thresholdPrice());
            t.putLong("Day", r.day());
            tenders.add(t);
        }
        data.put("Tenders", tenders);
        root.put(ROOT, data);
    }

    public static void load(CompoundTag root) {
        NpcInvestorManager.clearDirect();
        NpcGovernanceManager.clearDirect();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag data = root.getCompound(ROOT);
        boolean invalid = data.getInt("Version") != VERSION;
        int skipped = 0;
        for (Tag raw : data.getList("Investors", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag t = (CompoundTag) raw;
                NpcInvestorArchetype archetype = NpcInvestorArchetype.valueOf(t.getString("Archetype"));
                Map<UUID, Integer> sentiments = new LinkedHashMap<>();
                Map<UUID, Integer> targets = new LinkedHashMap<>();
                for (Tag rawCompany : t.getList("Companies", Tag.TAG_COMPOUND)) {
                    try {
                        CompoundTag c = (CompoundTag) rawCompany;
                        UUID id = c.getUUID("Company");
                        sentiments.put(id, c.getInt("Sentiment"));
                        targets.put(id, c.getInt("TargetBps"));
                    } catch (Exception e) {
                        skipped++;
                    }
                }
                NpcInvestorManager.restoreState(archetype, t.getBoolean("Funded"), t.getLong("Contributed"),
                        sentiments, targets);
            } catch (Exception e) {
                skipped++;
            }
        }
        List<NpcInvestorManager.Decision> decisions = new ArrayList<>();
        ListTag rawDecisions = data.getList("Decisions", Tag.TAG_COMPOUND);
        int start = Math.max(0, rawDecisions.size() - NpcInvestorManager.MAX_DECISIONS);
        skipped += start;
        for (int i = start; i < rawDecisions.size(); i++) {
            try {
                CompoundTag t = rawDecisions.getCompound(i);
                decisions.add(new NpcInvestorManager.Decision(t.getLong("Day"),
                        NpcInvestorArchetype.valueOf(t.getString("Archetype")),
                        t.hasUUID("Company") ? t.getUUID("Company") : null,
                        t.getString("Symbol"), t.getString("Action"), t.getLong("Qty"), t.getLong("Price"),
                        NpcReason.valueOf(t.getString("Reason"))));
            } catch (Exception e) {
                skipped++;
            }
        }
        long lastDay = data.contains("LastProcessedDay", Tag.TAG_LONG) ? data.getLong("LastProcessedDay") : Long.MIN_VALUE;
        NpcInvestorManager.restoreDirect(lastDay, decisions);

        List<NpcGovernanceManager.VoteRecord> votes = new ArrayList<>();
        ListTag rawVotes = data.getList("Votes", Tag.TAG_COMPOUND);
        int voteStart = Math.max(0, rawVotes.size() - NpcGovernanceManager.MAX_VOTE_RECORDS);
        skipped += voteStart;
        for (int i = voteStart; i < rawVotes.size(); i++) {
            try {
                CompoundTag t = rawVotes.getCompound(i);
                votes.add(new NpcGovernanceManager.VoteRecord(t.getUUID("Proposal"),
                        NpcInvestorArchetype.valueOf(t.getString("Archetype")),
                        NpcVotingRules.Choice.valueOf(t.getString("Choice")),
                        NpcVotingRules.VoteReason.valueOf(t.getString("Reason")),
                        t.getLong("Power"), t.getLong("Day")));
            } catch (Exception e) {
                skipped++;
            }
        }
        List<NpcGovernanceManager.TenderRecord> tenders = new ArrayList<>();
        ListTag rawTenders = data.getList("Tenders", Tag.TAG_COMPOUND);
        int tenderStart = Math.max(0, rawTenders.size() - NpcGovernanceManager.MAX_TENDER_RECORDS);
        skipped += tenderStart;
        for (int i = tenderStart; i < rawTenders.size(); i++) {
            try {
                CompoundTag t = rawTenders.getCompound(i);
                tenders.add(new NpcGovernanceManager.TenderRecord(t.getUUID("Offer"),
                        NpcInvestorArchetype.valueOf(t.getString("Archetype")), t.getBoolean("Accepted"),
                        t.getLong("Shares"), t.getLong("OfferPrice"), t.getLong("Threshold"), t.getLong("Day")));
            } catch (Exception e) {
                skipped++;
            }
        }
        long governanceDay = data.contains("GovernanceDay", Tag.TAG_LONG) ? data.getLong("GovernanceDay") : Long.MIN_VALUE;
        NpcGovernanceManager.restoreDirect(governanceDay, votes, tenders);
        if (invalid || skipped > 0) {
            ModuleHealthRegistry.restrict(ModuleHealthRegistry.Module.NPC_ECONOMY,
                    ModuleRunState.READ_ONLY, "NPC 经济存档 " + skipped + " 条损坏已跳过", -1);
        }
    }
}
