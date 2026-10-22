package finance.npcinvestor;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.*;
import finance.data.EconomySavedData;
import finance.governance.ShareholderRegistryService;
import finance.macro.EconomyAchievements;
import finance.rival.RivalProfile;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 单人经济 P5：界面数据（股东身份标签、NPC 投票摘要、竞争对手动作名）与“股东民主”成就。 */
class SoloInterfaceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private Company company;

    @BeforeEach
    void setup() {
        EconomySavedData.resetRuntimeState();
        NpcInvestorManager.ensureAccounts();
        company = new Company(UUID.randomUUID(), "玩家面包坊", CompanyType.FOOD_PROCESSING, 500_000L, OWNER);
        company.setPublic(true);
        CompanyManager.registerDirect(company);
        StockMarketManager.putStockDirect(
                new Stock("WJMB", "玩家面包坊", company.getCompanyId(), 10_000, 10_000, 0, 10, 10));
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
    }

    @Test
    void holderLabelsNameNpcArchetypesFoundersAndResidual() {
        assertEquals("分红派", HolderLabels.label(NpcInvestorArchetype.DIVIDEND.accountId(), company));
        assertEquals("创始人", HolderLabels.label(OWNER, company));
        assertEquals("未流通", HolderLabels.label(ShareholderRegistryService.SYSTEM_LIQUIDITY_HOLDER, company));
        assertEquals("", HolderLabels.label(UUID.randomUUID(), company), "普通玩家不标注");
        NpcInvestorManager.restoreState(
                NpcInvestorArchetype.VALUE, true, 1, Map.of(company.getCompanyId(), 40), Map.of());
        assertEquals(40, HolderLabels.sentiment(NpcInvestorArchetype.VALUE.accountId(), company));
        assertEquals(0, HolderLabels.sentiment(OWNER, company));
    }

    @Test
    void rivalInvestmentAccountIsLabelledWithRivalName() {
        Company rival = new Company(UUID.randomUUID(), "晨光食品", CompanyType.FOOD_PROCESSING, 100_000);
        CompanyManager.registerDirect(rival);
        finance.rival.RivalService.processDay(
                1, new finance.rival.RivalService.Settings(true, 0, 14, 0, 0, false, false));
        assertEquals("晨光食品", HolderLabels.label(RivalProfile.investmentAccount(rival.getCompanyId()), company));
    }

    @Test
    void voteSummaryListsChoicesAndReasons() {
        StockPortfolioManager.addHolding(OWNER, "WJMB", 3_000, 10);
        StockPortfolioManager.addHolding(NpcInvestorArchetype.DIVIDEND.accountId(), "WJMB", 2_000, 10);
        StockPortfolioManager.addHolding(NpcInvestorArchetype.GROWTH.accountId(), "WJMB", 1_000, 10);
        assertTrue(CompanyProposalManager.createProposal(
                        OWNER, company.getCompanyId(), CompanyProposalType.RENAME, "新名字", 0, 0, 0, 1, 3, 0.5)
                .success());
        CompanyProposal proposal = CompanyProposalManager.getProposalsForCompany(company.getCompanyId())
                .get(0);
        NpcGovernanceService.processDay(1, settings());
        String summary = NpcGovernanceService.voteSummary(proposal.getProposalId());
        assertTrue(summary.contains("分红派弃权") && summary.contains("成长派弃权"), summary);
        assertEquals("", NpcGovernanceService.voteSummary(UUID.randomUUID()));
    }

    @Test
    void passingAProposalWithNpcSupportUnlocksShareholderDemocracy() {
        StockPortfolioManager.addHolding(OWNER, "WJMB", 3_000, 10);
        StockPortfolioManager.addHolding(NpcInvestorArchetype.DIVIDEND.accountId(), "WJMB", 2_000, 10);
        // 分红派赞成提高分红
        assertTrue(CompanyProposalManager.createProposal(
                        OWNER, company.getCompanyId(), CompanyProposalType.DIVIDEND, "", 80, 0, 0, 1, 3, 0.5)
                .success());
        NpcGovernanceService.processDay(1, settings());
        CompanyProposalManager.tick(3);
        assertFalse(EconomyAchievements.pending(OWNER).contains(EconomyAchievements.SHAREHOLDER_DEMOCRACY), "结算后第二天才发");
        NpcGovernanceService.processDay(4, settings());
        assertTrue(EconomyAchievements.pending(OWNER).contains(EconomyAchievements.SHAREHOLDER_DEMOCRACY));
    }

    @Test
    void rivalActionCodesAreTranslatedForTheRivalList() {
        assertEquals("发起要约 MTNZ @7 ×2100", finance.client.MacroTabs.actionLabel("TENDER MTNZ @7 ×2100"));
        assertEquals("升级 产线 2 级", finance.client.MacroTabs.actionLabel("UPGRADE 产线 2 级"));
        assertEquals("—", finance.client.MacroTabs.actionLabel(""));
    }

    private static NpcGovernanceService.Settings settings() {
        Map<NpcInvestorArchetype, Integer> premiums = new EnumMap<>(NpcInvestorArchetype.class);
        for (NpcInvestorArchetype a : NpcInvestorArchetype.values()) premiums.put(a, 30);
        return new NpcGovernanceService.Settings(true, true, premiums);
    }
}
