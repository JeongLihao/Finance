package finance.governance;

import finance.account.AccountManager;
import finance.company.*;
import finance.data.EconomySavedData;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorporateRestructuringServiceTest {
    private final UUID ownerA = UUID.fromString("00000000-0000-0000-0000-000000011001");
    private final UUID ownerB = UUID.fromString("00000000-0000-0000-0000-000000011002");
    private final UUID companyA = UUID.fromString("00000000-0000-0000-0000-000000011003");
    private final UUID companyB = UUID.fromString("00000000-0000-0000-0000-000000011004");

    @BeforeEach void setup() { EconomySavedData.resetRuntimeState(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void emergencyContributionUsesRealFundsAndIsLimitedPerRiskEvent() {
        Company company = new Company(companyA, "Risk", CompanyType.RAW_MATERIALS, 10, ownerA);
        company.setBankruptcyRisk(true, 7);
        CompanyManager.registerDirect(company);
        AccountManager.deposit(ownerB, 5_000);
        long before = AccountManager.getBalance(ownerB);
        CompanyProposal proposal=authorization(companyA,ownerA,CompanyProposalType.EMERGENCY_RECAPITALIZATION,"",2_000,0);
        assertTrue(CorporateRestructuringService.emergencyContribution(ownerB, proposal.getProposalId(),
                8, "recap-1").success());
        assertEquals(before - 2_000, AccountManager.getBalance(ownerB));
        assertEquals(2_010, company.getCash());
        assertFalse(CorporateRestructuringService.emergencyContribution(ownerB, proposal.getProposalId(),
                8, "recap-2").success());
    }

    @Test void assetPurchaseMovesExactlyOneCashAndInventoryLeg() {
        Company buyer = new Company(companyA, "Buyer", CompanyType.RAW_MATERIALS, 10_000, ownerA);
        Company seller = new Company(companyB, "Seller", CompanyType.RAW_MATERIALS, 1_000, ownerB);
        seller.addInventory("iron", 20);
        CompanyManager.registerDirect(buyer);
        CompanyManager.registerDirect(seller);
        CompanyProposal proposal=authorization(companyA,ownerA,CompanyProposalType.MAJOR_ASSET_PURCHASE,
                companyB+"|iron",2_000,10);
        assertTrue(CorporateRestructuringService.purchaseInventoryAsset(ownerB, proposal.getProposalId(),
                1, "asset-1").success());
        assertEquals(8_000, buyer.getCash());
        assertEquals(3_000, seller.getCash());
        assertEquals(10, buyer.getInventoryAmount("iron"));
        assertEquals(10, seller.getInventoryAmount("iron"));
        assertFalse(CorporateRestructuringService.purchaseInventoryAsset(ownerB, proposal.getProposalId(),
                1, "asset-1").success());
    }

    private static CompanyProposal authorization(UUID company,UUID creator,CompanyProposalType type,String text,long v1,long v2){
        CompanyProposal proposal=new CompanyProposal(company,creator,type,"approved",text,v1,v2,0,0,10,.5);
        proposal.finish(CompanyProposalStatus.PASSED,"approved");
        CompanyProposalManager.addProposalDirect(proposal);
        return proposal;
    }
}
