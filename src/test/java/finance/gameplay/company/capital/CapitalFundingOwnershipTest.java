package finance.gameplay.company.capital;

import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.debt.BondStatus;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.debt.LoanLenderType;
import finance.debt.LoanStatus;
import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapitalFundingOwnershipTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void commercialLoanFromAnotherCompanyIsRejected() {
        Company company = company();
        UUID expectedBank = UUID.randomUUID();
        WorldCapitalProject project = project(company, CapitalFundingSource.COMMERCIAL_LOAN);
        CompanyLoan loan = new CompanyLoan(UUID.randomUUID(), UUID.randomUUID(), project.budget(), 800,
                0, 30, 7, project.budget(), 0, 0, 7, -1, LoanStatus.ACTIVE,
                LoanLenderType.COMMERCIAL_BANK, expectedBank);
        CompanyLoanManager.putDirect(loan);
        project.setLoanId(loan.id());
        project.setBankId(expectedBank);

        var sync = CommercialLoanFunding.INSTANCE.sync(project, company, 1);
        assertEquals(CapitalFundingAdapter.SyncState.FAILED, sync.state());
        assertEquals("finance.capital_project.loan_mismatch", sync.messageKey());
    }

    @Test void corporateBondFromAnotherCompanyIsRejected() {
        Company company = company();
        WorldCapitalProject project = project(company, CapitalFundingSource.CORPORATE_BOND);
        CorporateBond bond = new CorporateBond(UUID.randomUUID(), UUID.randomUUID(), "OTHER", 100, 20,
                800, 0, 3, 30, 7, 10, BondStatus.ACTIVE, 0);
        CorporateBondManager.putDirect(bond);
        project.setBondId(bond.id());

        var sync = CorporateBondFunding.INSTANCE.sync(project, company, 4);
        assertEquals(CapitalFundingAdapter.SyncState.FAILED, sync.state());
        assertEquals("finance.capital_project.bond_mismatch", sync.messageKey());
    }

    @Test void finalizedShareIssueFromAnotherCompanyIsRejected() {
        Company company = company();
        WorldCapitalProject project = project(company, CapitalFundingSource.SHARE_ISSUE);
        UUID financingId = UUID.randomUUID();
        project.setFinancingProjectId(financingId);
        CompanyFinancingManager.putFinalizedDirect(new CompanyFinancingManager.FinalizedFinancing(
                financingId, UUID.randomUUID(), project.budget(), 20, 3));

        var sync = ShareIssueFunding.INSTANCE.sync(project, company, 4);
        assertEquals(CapitalFundingAdapter.SyncState.FAILED, sync.state());
        assertEquals("finance.capital_project.share_issue_mismatch", sync.messageKey());
    }

    private static Company company() {
        return new Company(UUID.randomUUID(), "Ownership", CompanyType.RAW_MATERIALS,
                10_000, UUID.randomUUID());
    }

    private static WorldCapitalProject project(Company company, CapitalFundingSource source) {
        return new WorldCapitalProject(UUID.randomUUID(), company.getCompanyId(),
                WorldCapitalProjectType.FACTORY_UPGRADE, UUID.randomUUID(), company.getOwnerId(),
                0, 30, 2, source, 2_000, Map.of(Items.IRON_INGOT, 12),
                false, CapitalProjectStatus.FUNDING, 0);
    }
}
