package finance.npcinvestor;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import finance.governance.CorporateActionManager;
import finance.governance.TakeoverService;
import finance.governance.TenderOffer;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * NPC 治理：对进行中的提案投票，对进行中的要约决定是否接受。
 *
 * <p>只调用 {@link CompanyProposalManager#vote} 和 {@link TakeoverService#acceptTender}，
 * 不改快照、不改票数。NPC 持仓是真实持仓，创建提案时已经拿到快照投票权。
 * 投票一旦提交不能更改，所以每个原型对每个提案只表态一次（在投票期内的第一个日结）。
 * 必须排在当天 {@code CompanyProposalManager.tick} 之前，截止日当天仍可投票。</p>
 */
public final class NpcGovernanceService {
    private NpcGovernanceService() { }

    public record Settings(boolean investorsEnabled, boolean votingEnabled,
                           Map<NpcInvestorArchetype, Integer> tenderPremiumPercent) {
        public Settings {
            Map<NpcInvestorArchetype, Integer> copy = new EnumMap<>(NpcInvestorArchetype.class);
            if (tenderPremiumPercent != null) copy.putAll(tenderPremiumPercent);
            tenderPremiumPercent = Map.copyOf(copy);
        }

        public static Settings fromConfig() {
            Map<NpcInvestorArchetype, Integer> premiums = new EnumMap<>(NpcInvestorArchetype.class);
            for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) premiums.put(a, FinanceConfig.npcTenderPremiumPercent(a));
            return new Settings(FinanceConfig.npcInvestorsEnabled(), FinanceConfig.npcVotingEnabled(), premiums);
        }

        int premiumFor(NpcInvestorArchetype a) {
            return Math.max(0, tenderPremiumPercent.getOrDefault(a, 0));
        }
    }

    public static void processDay(long day) {
        processDay(day, Settings.fromConfig());
    }

    public static synchronized void processDay(long day, Settings settings) {
        if (settings == null || !settings.investorsEnabled()) return;
        if (day <= NpcGovernanceManager.lastProcessedDay()) return;
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.NPC_ECONOMY)) return;
        NpcGovernanceManager.setLastProcessedDay(day);
        if (settings.votingEnabled()) voteOnProposals(day);
        respondToTenders(day, settings);
        EconomySavedData.markDirty();
    }

    // ---------------------------------------------------------------- 投票

    static void voteOnProposals(long day) {
        List<CompanyProposal> proposals = new ArrayList<>(CompanyProposalManager.getProposals());
        proposals.sort(Comparator.comparing(p -> p.getProposalId().toString()));
        for (CompanyProposal proposal : proposals) {
            if (proposal.getStatus() != CompanyProposalStatus.ACTIVE) continue;
            if (day < proposal.getStartMcDay() || day > proposal.getEndMcDay()) continue;
            Company company = CompanyManager.getCompany(proposal.getCompanyId());
            if (company == null) continue;
            NpcVotingRules.Context context = context(proposal, company);
            for (NpcInvestorArchetype archetype : NpcInvestorArchetype.values()) {
                long power = proposal.getSnapshotPower(archetype.accountId());
                if (power <= 0 || NpcGovernanceManager.hasVote(proposal.getProposalId(), archetype)) continue;
                if (proposal.getVotes().containsKey(archetype.accountId())) continue;
                NpcVotingRules.Decision decision = NpcVotingRules.decide(archetype, context);
                if (decision.choice() != NpcVotingRules.Choice.ABSTAIN) {
                    boolean support = decision.choice() == NpcVotingRules.Choice.SUPPORT;
                    if (!CompanyProposalManager.vote(archetype.accountId(), proposal.getProposalId(), support, day).success()) continue;
                }
                NpcGovernanceManager.putVote(new NpcGovernanceManager.VoteRecord(proposal.getProposalId(), archetype,
                        decision.choice(), decision.reason(), power, day));
            }
        }
    }

    static NpcVotingRules.Context context(CompanyProposal proposal, Company company) {
        Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
        long totalShares = stock == null ? 0 : stock.getTotalShares();
        long fundamentalPerShare = totalShares <= 0 ? 0 : company.getFundamentalAssetValue() / totalShares;
        long currentDividend = Math.round(company.effectiveDividendRatio(CompanyManager.getDividendRatio()) * 100);
        CompanyProposalType type = proposal.getType();
        return new NpcVotingRules.Context(type,
                type == CompanyProposalType.DIVIDEND ? proposal.getValue1() : 0,
                currentDividend,
                type == CompanyProposalType.SHARE_ISSUE ? proposal.getValue2() : 0,
                fundamentalPerShare, company.getRetainedEarnings(),
                profitTrendPositive(company.getRecentProfits()), company.isBankruptcyRisk());
    }

    static boolean profitTrendPositive(List<Long> profits) {
        if (profits == null || profits.size() < 2) return false;
        int half = profits.size() / 2;
        double early = 0, late = 0;
        for (int i = 0; i < half; i++) early += profits.get(i);
        for (int i = half; i < profits.size(); i++) late += profits.get(i);
        early /= half;
        late /= (profits.size() - half);
        return late > 0 && late > early;
    }

    // ---------------------------------------------------------------- 要约

    static void respondToTenders(long day, Settings settings) {
        for (TenderOffer offer : TakeoverService.openTenders()) {
            if (day > offer.endDay()) continue;
            Stock stock = StockMarketManager.getStock(offer.symbol());
            if (stock == null || stock.getFairValue() <= 0) continue;
            for (NpcInvestorArchetype archetype : NpcInvestorArchetype.values()) {
                if (archetype.accountId().equals(offer.buyerId())) continue;
                if (NpcGovernanceManager.hasTenderRecord(offer.id(), archetype)) continue;
                if (CorporateActionManager.lockedShares(offer.symbol()).getOrDefault(archetype.accountId(), 0L) > 0) continue;
                long free = StockPortfolioManager.getHolding(archetype.accountId(), offer.symbol()).getQuantity();
                if (free <= 0) continue;
                long threshold = thresholdPrice(stock.getFairValue(), settings.premiumFor(archetype));
                if (offer.price() < threshold) {
                    NpcGovernanceManager.putTender(new NpcGovernanceManager.TenderRecord(offer.id(), archetype,
                            false, 0, offer.price(), threshold, day));
                    continue;
                }
                String key = "npc-tender:" + offer.id() + ":" + archetype.name();
                if (TakeoverService.acceptTender(archetype.accountId(), offer.id(), free, day, key).success()) {
                    NpcGovernanceManager.putTender(new NpcGovernanceManager.TenderRecord(offer.id(), archetype,
                            true, free, offer.price(), threshold, day));
                }
            }
        }
    }

    /** 接受门槛 = 公允价 × (1 + 溢价%)，向上取整。 */
    static long thresholdPrice(long fairValue, int premiumPercent) {
        if (fairValue <= 0) return Long.MAX_VALUE;
        return (long) Math.ceil(fairValue * (100.0 + Math.max(0, premiumPercent)) / 100.0);
    }
}
