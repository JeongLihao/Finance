package finance.simple;

import static org.junit.jupiter.api.Assertions.*;

import finance.company.Company;
import finance.company.CompanyManagementAction;
import finance.company.CompanyStrategy;
import finance.company.CompanyType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleCenterServiceTest {

    private static Company company(int production, int storage, int management) {
        Company c = new Company(UUID.randomUUID(), "测试", CompanyType.RAW_MATERIALS, 1_000L, UUID.randomUUID());
        c.restoreManagement(CompanyStrategy.STABLE, production, storage, management, 0.5);
        return c;
    }

    @Test
    void depositsStayVisibleWhenTheSimpleModeBankChanges() {
        finance.data.EconomySavedData.resetRuntimeState();
        finance.bank.BankingManager.clearDirect();
        UUID player = UUID.randomUUID();
        finance.account.AccountManager.deposit(player, 10_000);
        finance.bank.CommercialBank first = SimpleCenterService.mainBank();
        assertTrue(finance.bank.BankingManager.depositPlayer(player, first.id(), 3_000, 0)
                .success());

        // 这家银行被监管降级、不再收新业务，简单模式换到下一家
        first.setStatus(finance.bank.BankStatus.RESTRICTED);
        finance.bank.CommercialBank second = SimpleCenterService.mainBank();
        assertNotEquals(first.id(), second.id());
        assertTrue(finance.bank.BankingManager.depositPlayer(player, second.id(), 2_000, 0)
                .success());

        assertEquals(5_000, SimpleCenterService.bankBalance(player), "以前那家的存款照样算上");
        var accounts = SimpleCenterService.demandAccounts(player);
        assertEquals(2, accounts.size());
        assertEquals(second.id(), accounts.get(0).bankId(), "取钱先取现在用的那家");
        assertEquals(first.id(), accounts.get(1).bankId(), "不够再从以前那家取");
    }

    @Test
    void upgradeFactoryAlternatesStorageAndManagementAndSkipsProduction() {
        assertEquals(CompanyManagementAction.UPGRADE_STORAGE, SimpleCenterService.nextUpgrade(company(0, 0, 0)));
        assertEquals(CompanyManagementAction.UPGRADE_MANAGEMENT, SimpleCenterService.nextUpgrade(company(0, 1, 0)));
        assertEquals(CompanyManagementAction.UPGRADE_STORAGE, SimpleCenterService.nextUpgrade(company(0, 1, 1)));
        assertEquals(
                CompanyManagementAction.UPGRADE_STORAGE,
                SimpleCenterService.nextUpgrade(company(5, 0, 0)),
                "生产线等级只管 NPC 公司的抽象日产，不算也不升");
        assertNull(SimpleCenterService.nextUpgrade(company(0, 5, 5)), "仓储、管理都满 5 级就不能再升");
    }

    @Test
    void factoryLevelIsStoragePlusManagementAndCostFollowsTheLowerOne() {
        Company c = company(2, 1, 2);
        assertEquals(3, SimpleCenterService.factoryLevel(c));
        assertEquals(c.getUpgradeCost("STORAGE"), SimpleCenterService.nextUpgradeCost(c));
        assertEquals(0, SimpleCenterService.nextUpgradeCost(company(0, 5, 5)));
        assertEquals(10, SimpleCenterService.MAX_FACTORY_LEVEL);
    }
}
