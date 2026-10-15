package finance.npcinvestor;

import finance.company.CompanyProposalType;
import org.junit.jupiter.api.Test;

import static finance.npcinvestor.NpcInvestorArchetype.*;
import static finance.npcinvestor.NpcVotingRules.Choice.*;
import static org.junit.jupiter.api.Assertions.*;

class NpcVotingRulesTest {

    private static NpcVotingRules.Context ctx(CompanyProposalType type, long proposedDiv, long currentDiv, long issuePrice,
                                             long fps, long retained, boolean growth, boolean risk) {
        return new NpcVotingRules.Context(type, proposedDiv, currentDiv, issuePrice, fps, retained, growth, risk);
    }

    private static NpcVotingRules.Choice choice(NpcInvestorArchetype a, NpcVotingRules.Context c) {
        return NpcVotingRules.decide(a, c).choice();
    }

    @Test void dividendRaiseAndCut() {
        var raise = ctx(CompanyProposalType.DIVIDEND, 50, 30, 0, 0, 1_000, true, false);
        assertEquals(SUPPORT, choice(DIVIDEND, raise));
        assertEquals(ABSTAIN, choice(GROWTH, raise), "留存收益充足且利润增长时成长派不反对");
        var cut = ctx(CompanyProposalType.DIVIDEND, 10, 30, 0, 0, 1_000, true, false);
        assertEquals(OPPOSE, choice(DIVIDEND, cut));
        assertEquals(SUPPORT, choice(GROWTH, cut));
        var raiseNoRetained = ctx(CompanyProposalType.DIVIDEND, 50, 30, 0, 0, 0, true, false);
        assertEquals(OPPOSE, choice(GROWTH, raiseNoRetained));
        assertEquals(NpcVotingRules.VoteReason.RETAINED_EARNINGS_LOW, NpcVotingRules.decide(GROWTH, raiseNoRetained).reason());
        assertEquals(ABSTAIN, choice(DIVIDEND, ctx(CompanyProposalType.DIVIDEND, 30, 30, 0, 0, 0, true, false)));
    }

    @Test void shareIssueBelowFundamentalIsOpposedByDividendAndValue() {
        var cheap = ctx(CompanyProposalType.SHARE_ISSUE, 0, 0, 10, 50, 0, true, false);
        assertEquals(OPPOSE, choice(DIVIDEND, cheap));
        assertEquals(OPPOSE, choice(VALUE, cheap));
        assertEquals(SUPPORT, choice(GROWTH, cheap));
        assertEquals(ABSTAIN, choice(SPECULATOR, cheap));
        var fair = ctx(CompanyProposalType.SHARE_ISSUE, 0, 0, 60, 50, 0, false, false);
        assertEquals(ABSTAIN, choice(VALUE, fair));
        assertEquals(ABSTAIN, choice(GROWTH, fair), "利润没在增长时成长派不表态");
    }

    @Test void buybackRetirementAndExpansion() {
        var buyback = ctx(CompanyProposalType.SHARE_BUYBACK, 0, 0, 0, 0, 0, false, false);
        assertEquals(SUPPORT, choice(SPECULATOR, buyback));
        assertEquals(SUPPORT, choice(VALUE, buyback));
        assertEquals(ABSTAIN, choice(DIVIDEND, buyback));
        var retire = ctx(CompanyProposalType.TREASURY_RETIREMENT, 0, 0, 0, 0, 0, false, false);
        assertEquals(SUPPORT, choice(VALUE, retire));
        assertEquals(SUPPORT, choice(DIVIDEND, retire));
        for (CompanyProposalType t : new CompanyProposalType[]{CompanyProposalType.CAPITAL_PROJECT, CompanyProposalType.MAJOR_ASSET_PURCHASE}) {
            assertEquals(SUPPORT, choice(GROWTH, ctx(t, 0, 0, 0, 0, 0, true, false)));
            assertEquals(ABSTAIN, choice(VALUE, ctx(t, 0, 0, 0, 0, 0, true, false)));
            for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
                assertEquals(OPPOSE, choice(a, ctx(t, 0, 0, 0, 0, 0, true, true)), "破产风险时全部反对 " + a);
            }
        }
    }

    @Test void emergencyRecapAndNeutralTypes() {
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) {
            assertEquals(SUPPORT, choice(a, ctx(CompanyProposalType.EMERGENCY_RECAPITALIZATION, 0, 0, 0, 0, 0, false, true)));
            assertEquals(ABSTAIN, choice(a, ctx(CompanyProposalType.EMERGENCY_RECAPITALIZATION, 0, 0, 0, 0, 0, false, false)));
            assertEquals(ABSTAIN, choice(a, ctx(CompanyProposalType.RENAME, 0, 0, 0, 0, 0, true, false)));
            assertEquals(ABSTAIN, choice(a, ctx(CompanyProposalType.FUND_USAGE, 0, 0, 0, 0, 0, true, false)));
        }
        assertEquals(ABSTAIN, NpcVotingRules.decide(VALUE, null).choice());
    }

    @Test void tenderThresholdRoundsUp() {
        assertEquals(11, NpcGovernanceService.thresholdPrice(10, 10));
        assertEquals(13, NpcGovernanceService.thresholdPrice(10, 25));
        assertEquals(14, NpcGovernanceService.thresholdPrice(10, 35));
        assertEquals(Long.MAX_VALUE, NpcGovernanceService.thresholdPrice(0, 10));
    }
}
