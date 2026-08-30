package finance.gameplay.company.capital;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapitalProjectFinanceIntegrationTest {
    @BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.ensureStarted(); }
    @AfterEach void cleanup() { EconomySavedData.resetRuntimeState(); }

    @Test void retainedEarningsMovesExistingMoneyIntoZeroBalanceSystemEscrow() {
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "CapitalCo", CompanyType.RAW_MATERIALS, 10_000, owner);
        CompanyManager.registerDirect(company);
        WorldCapitalProject project = new WorldCapitalProject(UUID.randomUUID(), company.getCompanyId(),
                WorldCapitalProjectType.FACTORY_UPGRADE, UUID.randomUUID(), owner, 0, 30, 2,
                CapitalFundingSource.RETAINED_EARNINGS, 2_000, Map.of(Items.IRON_INGOT, 12),
                false, CapitalProjectStatus.DRAFT, 0);
        long totalBefore = company.getCash();
        var result = RetainedEarningsFunding.INSTANCE.initiate(project, company, null, 0);
        assertTrue(result.success(), result.messageKey());
        assertEquals(8_000, company.getCash());
        assertEquals(2_000, AccountManager.getAccounts().get(project.escrowAccountId()).getBalance());
        assertEquals(totalBefore, company.getCash() + AccountManager.getAccounts().get(project.escrowAccountId()).getBalance());
        assertTrue(project.fundingSettled());
    }

    @Test void nonEmptyEscrowCannotBeFundedTwice() {
        UUID owner = UUID.randomUUID();
        Company company = new Company(UUID.randomUUID(), "ReplayCo", CompanyType.RAW_MATERIALS, 10_000, owner);
        WorldCapitalProject project = CapitalProjectManagerTest.project(company.getCompanyId(), UUID.randomUUID(),
                owner, CapitalProjectStatus.DRAFT);
        assertTrue(RetainedEarningsFunding.INSTANCE.initiate(project, company, null, 0).success());
        long cash = company.getCash();
        assertFalse(RetainedEarningsFunding.INSTANCE.initiate(project, company, null, 0).success());
        assertEquals(cash, company.getCash());
    }
}
