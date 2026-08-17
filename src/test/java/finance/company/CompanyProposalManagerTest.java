package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionType;
import finance.data.EconomySavedData;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyProposalManagerTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000005001");
    private static final UUID HOLDER_A = UUID.fromString("00000000-0000-0000-0000-000000005002");
    private static final UUID HOLDER_B = UUID.fromString("00000000-0000-0000-0000-000000005003");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000005101");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        Company company = new Company(COMPANY_ID, "Vote Inc", CompanyType.RAW_MATERIALS, 1_000, OWNER_ID);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(new Stock("VOTE", "Vote Inc", COMPANY_ID,
                100, 100, 0, 10, 10));
        StockPortfolioManager.addHolding(HOLDER_A, "VOTE", 70, 10);
        StockPortfolioManager.addHolding(HOLDER_B, "VOTE", 30, 10);
    }

    @Test
    void shareholdersVoteWithHoldingPowerAndRenameExecutesWhenPassed() {
        assertTrue(CompanyProposalManager.createProposal(
                OWNER_ID, COMPANY_ID, CompanyProposalType.RENAME, "New Vote Inc",
                0, 0, 0, 1, 3, 0.60).success());
        CompanyProposal proposal = CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);

        assertTrue(CompanyProposalManager.vote(HOLDER_A, proposal.getProposalId(), true, 1).success());
        assertTrue(CompanyProposalManager.vote(HOLDER_B, proposal.getProposalId(), false, 1).success());
        CompanyProposalManager.tick(3);

        assertEquals(CompanyProposalStatus.EXECUTED, proposal.getStatus());
        assertEquals("New Vote Inc", CompanyManager.getCompany(COMPANY_ID).getName());
        assertEquals(70, proposal.getYesVotes());
        assertEquals(30, proposal.getNoVotes());
        assertTrue(AccountManager.getTransactions().stream()
                .anyMatch(record -> record.getType() == TransactionType.COMPANY_PROPOSAL_RESULT));
    }

    @Test
    void nonShareholderCannotVoteAndDuplicateVoteIsRejected() {
        CompanyProposalManager.createProposal(
                OWNER_ID, COMPANY_ID, CompanyProposalType.FUND_USAGE, "扩建仓库",
                100, 0, 0, 1, 3, 0.50);
        CompanyProposal proposal = CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);

        assertTrue(CompanyProposalManager.vote(HOLDER_A, proposal.getProposalId(), true, 1).success());
        assertFalse(CompanyProposalManager.vote(HOLDER_A, proposal.getProposalId(), false, 1).success());
        assertFalse(CompanyProposalManager.vote(UUID.randomUUID(), proposal.getProposalId(), true, 1).success());
    }

    @Test
    void passedShareIssueProposalStartsFinancingProject() {
        CompanyProposalManager.createProposal(
                OWNER_ID, COMPANY_ID, CompanyProposalType.SHARE_ISSUE, "",
                100, 10, 500, 1, 3, 0.50);
        CompanyProposal proposal = CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);

        CompanyProposalManager.vote(HOLDER_A, proposal.getProposalId(), true, 1);
        CompanyProposalManager.tick(3);

        assertEquals(CompanyProposalStatus.EXECUTED, proposal.getStatus());
        assertEquals(1, CompanyFinancingManager.getProjects().size());
        assertEquals(100, CompanyFinancingManager.getProjects().get(0).getIssueQuantity());
    }

    @Test
    void proposalFailsWhenParticipationIsBelowSnapshotThreshold() {
        StockPortfolioManager.clearPortfolios();
        StockPortfolioManager.addHolding(HOLDER_A, "VOTE", 10, 10);
        StockPortfolioManager.addHolding(HOLDER_B, "VOTE", 90, 10);
        CompanyProposalManager.createProposal(
                OWNER_ID, COMPANY_ID, CompanyProposalType.FUND_USAGE, "低参与测试",
                100, 0, 0, 1, 3, 0.50);
        CompanyProposal proposal = CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);

        assertTrue(CompanyProposalManager.vote(HOLDER_A, proposal.getProposalId(), true, 1).success());
        CompanyProposalManager.tick(3);

        assertEquals(CompanyProposalStatus.FAILED, proposal.getStatus());
        assertTrue(proposal.getResultSummary().contains("参与率不足"));
    }

    @Test
    void proposalDurationCannotOccupySlotBeyondNinetyDays() {
        assertFalse(CompanyProposalManager.createProposal(OWNER_ID, COMPANY_ID,
                CompanyProposalType.FUND_USAGE, "长期占位", 1, 0, 0,
                1, 92, 0.5).success());
        assertTrue(CompanyProposalManager.getProposals().isEmpty());
    }
}
