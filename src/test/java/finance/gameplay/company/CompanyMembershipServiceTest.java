package finance.gameplay.company;

import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompanyMembershipServiceTest {
    private UUID owner, member; private Company company;
    @BeforeEach void setup() { CompanyManager.clearCompaniesDirect(); owner = UUID.randomUUID(); member = UUID.randomUUID();
        company = new Company(UUID.randomUUID(), "World Co", CompanyType.RAW_MATERIALS, 100_000, owner);
        CompanyManager.registerDirect(company); CompanyGameplayManager.createForNewCompany(company); }
    @AfterEach void cleanup() { CompanyManager.clearCompaniesDirect(); }

    @Test void roleMatrixAndInvitationLifecycleAreServerAuthoritative() {
        assertTrue(CompanyMembershipService.hasPermission(company.getCompanyId(), owner, CompanyPermission.MANAGE_MEMBERS));
        assertFalse(CompanyMembershipService.hasPermission(company.getCompanyId(), member, CompanyPermission.VIEW_COMPANY));
        assertTrue(CompanyMembershipService.invite(owner, company.getCompanyId(), member,
                CompanyMemberRole.WAREHOUSE_WORKER, 1, "invite").success());
        assertTrue(CompanyMembershipService.acceptInvite(member, company.getCompanyId(), 2, "accept").success());
        assertTrue(CompanyMembershipService.hasPermission(company.getCompanyId(), member, CompanyPermission.WITHDRAW_WAREHOUSE));
        assertFalse(CompanyMembershipService.hasPermission(company.getCompanyId(), member, CompanyPermission.SPEND_COMPANY_CASH));
        assertFalse(CompanyMembershipService.changeRole(member, company.getCompanyId(), owner,
                CompanyMemberRole.MEMBER, "attack").success());
    }

    @Test void duplicateAndExpiredInvitesCannotCreateTwoMemberships() {
        assertTrue(CompanyMembershipService.invite(owner, company.getCompanyId(), member,
                CompanyMemberRole.MEMBER, 1, "one").success());
        assertFalse(CompanyMembershipService.invite(owner, company.getCompanyId(), member,
                CompanyMemberRole.MANAGER, 1, "one").success());
        assertFalse(CompanyMembershipService.acceptInvite(member, company.getCompanyId(), 20, "late").success());
    }

    @Test void automaticSalePolicyRequiresProductionPermissionAndIsIdempotent() {
        double before = company.getAutoSellRatio();
        assertFalse(CompanyOperatingModeService.cycleAutoSell(member, company.getCompanyId(), "denied").success());
        assertEquals(before, company.getAutoSellRatio());
        assertTrue(CompanyOperatingModeService.cycleAutoSell(owner, company.getCompanyId(), "policy").success());
        double changed = company.getAutoSellRatio();
        assertNotEquals(before, changed);
        assertFalse(CompanyOperatingModeService.cycleAutoSell(owner, company.getCompanyId(), "policy").success());
        assertEquals(changed, company.getAutoSellRatio());
    }
}
