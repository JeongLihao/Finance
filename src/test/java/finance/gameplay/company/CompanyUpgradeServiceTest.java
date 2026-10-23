package finance.gameplay.company;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompanyUpgradeServiceTest {
    @Test
    void facilityLevelsAreBoundedToThreeMinecraftVisibleTiers() {
        assertEquals(3, CompanyFacilityRecord.MAX_LEVEL);
        assertTrue(
                finance.config.FinanceConfig.factoryThroughput(1) < finance.config.FinanceConfig.factoryThroughput(3));
        assertTrue(finance.gameplay.company.CompanyUpgradeRequirementService.requirement(1)
                        .cash()
                < finance.gameplay.company.CompanyUpgradeRequirementService.requirement(2)
                        .cash());
        assertNull(finance.gameplay.company.CompanyUpgradeRequirementService.requirement(3), "3 档是最高档");
    }
}
