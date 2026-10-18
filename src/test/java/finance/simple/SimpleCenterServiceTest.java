package finance.simple;

import finance.company.Company;
import finance.company.CompanyManagementAction;
import finance.company.CompanyStrategy;
import finance.company.CompanyType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCenterServiceTest {

    private static Company company(int production, int storage, int management) {
        Company c = new Company(UUID.randomUUID(), "测试", CompanyType.RAW_MATERIALS, 1_000L, UUID.randomUUID());
        c.restoreManagement(CompanyStrategy.STABLE, production, storage, management, 0.5);
        return c;
    }

    @Test void upgradeFactoryRaisesTheLowestOfTheThreeLevels() {
        assertEquals(CompanyManagementAction.UPGRADE_PRODUCTION, SimpleCenterService.nextUpgrade(company(0, 0, 0)));
        assertEquals(CompanyManagementAction.UPGRADE_STORAGE, SimpleCenterService.nextUpgrade(company(1, 0, 0)));
        assertEquals(CompanyManagementAction.UPGRADE_MANAGEMENT, SimpleCenterService.nextUpgrade(company(2, 1, 0)));
        assertNull(SimpleCenterService.nextUpgrade(company(5, 5, 5)), "三项都满 5 级就不能再升");
    }

    @Test void factoryLevelIsTheSumAndCostFollowsTheLowestLevel() {
        Company c = company(2, 1, 1);
        assertEquals(4, SimpleCenterService.factoryLevel(c));
        assertEquals(c.getUpgradeCost("STORAGE"), SimpleCenterService.nextUpgradeCost(c));
        assertEquals(0, SimpleCenterService.nextUpgradeCost(company(5, 5, 5)));
        assertEquals(15, SimpleCenterService.MAX_FACTORY_LEVEL);
    }
}
