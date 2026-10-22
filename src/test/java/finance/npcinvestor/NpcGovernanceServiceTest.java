package finance.npcinvestor;

import static org.junit.jupiter.api.Assertions.*;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.governance.CorporateActionManager;
import finance.governance.TakeoverService;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcGovernanceServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000b002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-00000000b003");
    private static final String SYMBOL = "GOVN";
    private static final long TOTAL = 10_000L;

    private Company company;

    private static NpcGovernanceService.Settings settings(boolean investors, boolean voting) {
        Map<NpcInvestorArchetype, Integer> premiums = new EnumMap<>(NpcInvestorArchetype.class);
        premiums.put(NpcInvestorArchetype.DIVIDEND, 35);
        premiums.put(NpcInvestorArchetype.GROWTH, 30);
        premiums.put(NpcInvestorArchetype.SPECULATOR, 10);
        premiums.put(NpcInvestorArchetype.VALUE, 25);
        return new NpcGovernanceService.Settings(investors, voting, premiums);
    }

    private static final NpcGovernanceService.Settings ON = settings(true, true);

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        NpcInvestorManager.ensureAccounts();
        // 公司现金 500k → 每股基本面 50；股价和公允价 10
        company = new Company(COMPANY_ID, "Gov Target", CompanyType.RAW_MATERIALS, 500_000L, OWNER);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock(SYMBOL, "Gov Target", COMPANY_ID, TOTAL, TOTAL, 0L, 10L, 10L));
    }

    @AfterEach
    void clean() {
        EconomySavedData.resetRuntimeState();
    }

    private static void give(UUID holder, long shares) {
        assertTrue(StockPortfolioManager.addHolding(holder, SYMBOL, shares, 10L));
    }

    private CompanyProposal propose(CompanyProposalType type, String text, long v1, long v2, long v3) {
        var result = CompanyProposalManager.createProposal(OWNER, COMPANY_ID, type, text, v1, v2, v3, 1, 3, 0.5);
        assertTrue(result.success(), result.message());
        return CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);
    }

    @Test
    void npcShareholdersCanVetoAThirtyPercentPlayer() {
        give(OWNER, 3_000); // 玩家 30%
        give(NpcInvestorArchetype.DIVIDEND.accountId(), 2_000);
        give(NpcInvestorArchetype.VALUE.accountId(), 2_000);
        // 发行价 10 低于每股基本面 50
        CompanyProposal proposal = propose(CompanyProposalType.SHARE_ISSUE, "", 1_000, 10, 10_000);
        assertTrue(CompanyProposalManager.vote(OWNER, proposal.getProposalId(), true, 1)
                .success());

        NpcGovernanceService.processDay(1, ON);
        CompanyProposalManager.tick(3);

        assertEquals(CompanyProposalStatus.FAILED, proposal.getStatus(), "NPC 合计 40% 反对应否决玩家 30%");
        var votes = NpcGovernanceManager.votesFor(proposal.getProposalId());
        assertEquals(2, votes.size());
        for (var v : votes) {
            assertEquals(NpcVotingRules.Choice.OPPOSE, v.choice());
            assertEquals(NpcVotingRules.VoteReason.ISSUE_BELOW_FUNDAMENTAL, v.reason());
            assertEquals(2_000L, v.power());
        }
    }

    @Test
    void abstentionLeavesPlayerInControlWhenParticipationSuffices() {
        give(OWNER, 3_000);
        give(NpcInvestorArchetype.VALUE.accountId(), 4_000);
        CompanyProposal proposal = propose(CompanyProposalType.RENAME, "New Name", 0, 0, 0);
        assertTrue(CompanyProposalManager.vote(OWNER, proposal.getProposalId(), true, 1)
                .success());
        NpcGovernanceService.processDay(1, ON);
        CompanyProposalManager.tick(3);
        assertNotEquals(CompanyProposalStatus.FAILED, proposal.getStatus(), "NPC 弃权时，30%/70% 参与率仍高于 25%");
        assertEquals(
                NpcVotingRules.Choice.ABSTAIN,
                NpcGovernanceManager.votesFor(proposal.getProposalId()).get(0).choice());
        assertFalse(proposal.getVotes().containsKey(NpcInvestorArchetype.VALUE.accountId()), "弃权不提交票");
    }

    @Test
    void votingIsOncePerProposalAndIdempotentPerDay() {
        give(OWNER, 3_000);
        give(NpcInvestorArchetype.DIVIDEND.accountId(), 2_000);
        CompanyProposal proposal = propose(CompanyProposalType.DIVIDEND, "", 80, 0, 0);
        NpcGovernanceService.processDay(1, ON);
        NpcGovernanceService.processDay(1, ON);
        NpcGovernanceService.processDay(2, ON);
        assertEquals(1, proposal.getVotes().size());
        assertTrue(
                proposal.getVotes()
                        .get(NpcInvestorArchetype.DIVIDEND.accountId())
                        .support(),
                "分红派赞成上调");
    }

    @Test
    void disabledSettingsAreNoOp() {
        give(OWNER, 3_000);
        give(NpcInvestorArchetype.VALUE.accountId(), 4_000);
        CompanyProposal proposal = propose(CompanyProposalType.SHARE_ISSUE, "", 1_000, 10, 10_000);
        NpcGovernanceService.processDay(1, settings(false, true));
        NpcGovernanceService.processDay(2, settings(true, false));
        assertTrue(proposal.getVotes().isEmpty());
        assertTrue(NpcGovernanceManager.votesFor(proposal.getProposalId()).isEmpty());
    }

    @Test
    void tenderAcceptedOnlyAbovePremiumThreshold() {
        // 系统公司（无主）上的要约：收购方先持有 30%
        Company system = new Company(UUID.randomUUID(), "Sys", CompanyType.FOOD, 100_000L);
        system.setPublic(true);
        CompanyManager.registerDirect(system);
        StockMarketManager.putStockDirect(new Stock("SYSX", "Sys", system.getCompanyId(), TOTAL, TOTAL, 0L, 10L, 10L));
        assertTrue(StockPortfolioManager.addHolding(BUYER, "SYSX", 3_000, 10L));
        assertTrue(StockPortfolioManager.addHolding(NpcInvestorArchetype.SPECULATOR.accountId(), "SYSX", 1_000, 10L));
        assertTrue(StockPortfolioManager.addHolding(NpcInvestorArchetype.VALUE.accountId(), "SYSX", 2_000, 10L));
        AccountManager.deposit(BUYER, 1_000_000L);

        // 要约价 11 = 公允价 +10%：短线派（门槛 11）接受，价值派（门槛 13）拒绝
        var offer = TakeoverService.offerTender(BUYER, system.getCompanyId(), 11L, 3_000L, 100L, 5, 1L, "gov-t1");
        assertTrue(offer.success(), offer.message());
        UUID offerId = TakeoverService.openTenders().get(0).id();
        NpcGovernanceService.processDay(1, ON);

        var locked = CorporateActionManager.lockedShares("SYSX");
        assertEquals(1_000L, locked.getOrDefault(NpcInvestorArchetype.SPECULATOR.accountId(), 0L));
        assertEquals(0L, locked.getOrDefault(NpcInvestorArchetype.VALUE.accountId(), 0L));
        var responses = NpcGovernanceManager.tenderResponsesFor(offerId);
        assertTrue(responses.stream()
                .anyMatch(
                        r -> r.archetype() == NpcInvestorArchetype.VALUE && !r.accepted() && r.thresholdPrice() == 13));
        // 第二天重复处理不会重复申报
        NpcGovernanceService.processDay(2, ON);
        assertEquals(
                1_000L, CorporateActionManager.lockedShares("SYSX").get(NpcInvestorArchetype.SPECULATOR.accountId()));
    }

    @Test
    void higherOfferWinsValueInvestors() {
        Company system = new Company(UUID.randomUUID(), "Sys2", CompanyType.FOOD, 100_000L);
        system.setPublic(true);
        CompanyManager.registerDirect(system);
        StockMarketManager.putStockDirect(new Stock("SYSY", "Sys2", system.getCompanyId(), TOTAL, TOTAL, 0L, 10L, 10L));
        assertTrue(StockPortfolioManager.addHolding(BUYER, "SYSY", 3_000, 10L));
        assertTrue(StockPortfolioManager.addHolding(NpcInvestorArchetype.VALUE.accountId(), "SYSY", 2_000, 10L));
        AccountManager.deposit(BUYER, 1_000_000L);
        assertTrue(TakeoverService.offerTender(BUYER, system.getCompanyId(), 13L, 3_000L, 100L, 5, 1L, "gov-t2")
                .success());
        NpcGovernanceService.processDay(1, ON);
        assertEquals(
                2_000L,
                CorporateActionManager.lockedShares("SYSY").getOrDefault(NpcInvestorArchetype.VALUE.accountId(), 0L));
    }

    @Test
    void governanceRecordsSurviveSaveLoad() {
        give(OWNER, 3_000);
        give(NpcInvestorArchetype.DIVIDEND.accountId(), 2_000);
        CompanyProposal proposal = propose(CompanyProposalType.SHARE_ISSUE, "", 1_000, 10, 10_000);
        NpcGovernanceService.processDay(1, ON);
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());
        EconomySavedData.load(saved);
        assertEquals(1, NpcGovernanceManager.lastProcessedDay());
        var votes = NpcGovernanceManager.votesFor(proposal.getProposalId());
        assertEquals(1, votes.size());
        assertEquals(
                NpcVotingRules.VoteReason.ISSUE_BELOW_FUNDAMENTAL, votes.get(0).reason());
    }
}
