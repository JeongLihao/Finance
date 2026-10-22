package finance.gameplay.company;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompanyUpgradeServiceTest {
    @Test
    void facilityLevelsAreBoundedToThreeMinecraftVisibleTiers() {
        assertEquals(3, CompanyFacilityRecord.MAX_LEVEL);
        assertTrue(
                finance.config.FinanceConfig.factoryThroughput(1) < finance.config.FinanceConfig.factoryThroughput(3));
        assertTrue(finance.config.FinanceConfig.factoryMaintenanceBasisPoints(3)
                <= finance.config.FinanceConfig.factoryMaintenanceBasisPoints(1));
    }
}
