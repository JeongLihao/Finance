package finance.gameplay.company.capital;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.gameplay.company.CompanyFacilityManager;
import finance.gameplay.company.CompanyFacilityRecord;
import finance.gameplay.company.CompanyFacilityStatus;
import finance.gameplay.company.CompanyFacilityType;
import finance.gameplay.company.CompanyGameplayManager;
import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectServiceTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void dailyReconciliationPausesProjectWhenCompanyReferenceDisappears() {
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), UUID.randomUUID(),
                WorldCapitalProjectType.FACTORY_UPGRADE, UUID.randomUUID(), UUID.randomUUID(),
                0, 20, 2, CapitalFundingSource.COMMERCIAL_LOAN, 2_000,
                Map.of(Items.IRON_INGOT, 12), false, CapitalProjectStatus.FUNDING, 0);
        assertTrue(CapitalProjectManager.register(project));
        CapitalProjectService.processDay(1);
        assertEquals(CapitalProjectStatus.FAILED_RECOVERABLE, project.status());
        assertEquals("finance.capital_project.company_missing", project.failureKey());
    }

    @Test void operationKeysAreBoundedAndRejectOversizedValues() {
        WorldCapitalProject project = CapitalProjectManagerTest.project(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CapitalProjectStatus.DRAFT);
        for (int i = 0; i < 200; i++) project.recordOperation("op-" + i);
        project.recordOperation("x".repeat(97));
        assertEquals(WorldCapitalProject.MAX_OPERATION_KEYS, project.operationKeys().size());
        assertFalse(project.operationKeys().contains("op-0"));
    }

    @Test void recoverRequiresExactEscrowAndIsIdempotentAfterTransition() {
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "Recovery", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayManager.createForNewCompany(company);
        CompanyFacilityRecord facility = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(),
                "minecraft:overworld", BlockPos.ZERO, CompanyFacilityType.FACTORY_CONTROLLER, 1,
                CompanyFacilityStatus.ACTIVE, 0, null);
        CompanyFacilityManager.restore(facility);
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), company.getCompanyId(),
                WorldCapitalProjectType.FACTORY_UPGRADE, facility.facilityId(), owner, 0, 20, 2,
                CapitalFundingSource.RETAINED_EARNINGS, 2_000, Map.of(Items.IRON_INGOT, 12), false,
                CapitalProjectStatus.FAILED_RECOVERABLE, 1);
        project.restoreReferences(2_000, null, null, null, null, null, false,
                "finance.capital_project.escrow_changed");
        assertTrue(CapitalProjectManager.register(project));

        CapitalProjectActionResult rejected = CapitalProjectService.recover(owner, project.projectId(), 2, "recover");
        assertFalse(rejected.success());
        assertEquals(CapitalProjectStatus.FAILED_RECOVERABLE, project.status());

        assertTrue(AccountManager.getOrCreateSystemAccount(project.escrowAccountId()).deposit(2_000));
        CapitalProjectActionResult recovered = CapitalProjectService.recover(owner, project.projectId(), 2, "recover");
        assertTrue(recovered.success());
        assertEquals(CapitalProjectStatus.MATERIALS_PENDING, project.status());
        assertTrue(project.fundingSettled());
        assertTrue(CapitalProjectService.recover(owner, project.projectId(), 2, "recover").success());
        assertEquals(2_000, AccountManager.getBalance(project.escrowAccountId()));
    }

    @Test void expiredFailedProjectCannotBeRecoveredEvenWithIntactEscrow() {
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "Expired", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayManager.createForNewCompany(company);
        CompanyFacilityRecord facility = new CompanyFacilityRecord(UUID.randomUUID(), company.getCompanyId(),
                "minecraft:overworld", BlockPos.ZERO, CompanyFacilityType.FACTORY_CONTROLLER, 1,
                CompanyFacilityStatus.ACTIVE, 0, null);
        CompanyFacilityManager.restore(facility);
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), company.getCompanyId(),
                WorldCapitalProjectType.FACTORY_UPGRADE, facility.facilityId(), owner, 0, 5, 2,
                CapitalFundingSource.RETAINED_EARNINGS, 1_000, Map.of(Items.IRON_INGOT, 4), false,
                CapitalProjectStatus.FAILED_RECOVERABLE, 5);
        project.restoreReferences(1_000, null, null, null, null, null, true,
                "finance.capital_project.deadline_expired");
        CapitalProjectManager.register(project);
        AccountManager.getOrCreateSystemAccount(project.escrowAccountId()).deposit(1_000);

        assertFalse(CapitalProjectService.recover(owner, project.projectId(), 6, "late").success());
        assertEquals(CapitalProjectStatus.FAILED_RECOVERABLE, project.status());
        assertEquals(1_000, AccountManager.getBalance(project.escrowAccountId()));
    }

    @Test void passedProposalCannotAuthorizeAnotherProjectWithMatchingBudgetAndType() {
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "Authorization", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        CompanyGameplayManager.createForNewCompany(company);
        WorldCapitalProject approvedProject = authorizationProject(company.getCompanyId(), owner);
        WorldCapitalProject otherProject = authorizationProject(company.getCompanyId(), owner);
        assertTrue(CapitalProjectManager.register(approvedProject));
        assertTrue(CapitalProjectManager.register(otherProject));
        CompanyProposal proposal = new CompanyProposal(company.getCompanyId(), owner,
                CompanyProposalType.CAPITAL_PROJECT, "Capital project", approvedProject.projectId().toString(),
                approvedProject.budget(), approvedProject.type().ordinal(), 0, 0, 3, 0.6D);
        proposal.finish(CompanyProposalStatus.PASSED, "approved");
        CompanyProposalManager.addProposalDirect(proposal);

        CapitalProjectActionResult rejected = CapitalProjectService.authorize(owner, otherProject.projectId(),
                proposal.getProposalId(), 4, "wrong-project");

        assertFalse(rejected.success());
        assertEquals(CapitalProjectStatus.AUTHORIZATION_REQUIRED, otherProject.status());
        assertEquals(CompanyProposalStatus.PASSED, proposal.getStatus());
        assertTrue(CapitalProjectService.authorize(owner, approvedProject.projectId(), proposal.getProposalId(),
                4, "right-project").success());
        assertEquals(CapitalProjectStatus.DRAFT, approvedProject.status());
        assertEquals(CompanyProposalStatus.EXECUTED, proposal.getStatus());
    }

    private static WorldCapitalProject authorizationProject(UUID companyId, UUID owner) {
        return new WorldCapitalProject(UUID.randomUUID(), companyId, WorldCapitalProjectType.FACTORY_UPGRADE,
                UUID.randomUUID(), owner, 0, 20, 2, CapitalFundingSource.RETAINED_EARNINGS, 2_000,
                Map.of(Items.IRON_INGOT, 12), true, CapitalProjectStatus.AUTHORIZATION_REQUIRED, 0);
    }
}
