package finance.debt;

import finance.bondmarket.BondPortfolioManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import finance.data.EconomySavedData;
import finance.policy.MonetaryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FixedIncomeValuationServiceTest {
    private final UUID companyId = UUID.fromString("00000000-0000-0000-0000-000000004101");
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000004102");
    private final UUID player = UUID.fromString("00000000-0000-0000-0000-000000004103");
    private CorporateBond bond;

    @BeforeEach void setup() {
        EconomySavedData.resetRuntimeState();
        CompanyManager.registerDirect(new Company(companyId, "Value Co", CompanyType.FOOD, 1_000_000, owner));
        bond = new CorporateBond(UUID.randomUUID(), companyId, "VAL1", 10_000, 100, 1_000,
                0, 1, 31, 10, 11, BondStatus.ACTIVE, 0);
        bond.putHoldingDirect(player, 10);
        CorporateBondManager.putDirect(bond);
    }

    @Test void accruedInterestUsesActualDaysAndMaturityPriceConvergesToPrincipal() {
        BondValuation mid = FixedIncomeValuationService.value(bond, player, 6);
        assertEquals(136, mid.accruedInterest());
        assertEquals(5, mid.daysToNextCoupon());
        assertEquals(10_000, FixedIncomeValuationService.referencePricePerUnit(
                bond, bond.maturityDay(), mid.referenceYieldBasisPoints()));
    }

    @Test void benchmarkRateIncreaseLowersFixedCouponReferencePrice() {
        MonetaryPolicyService.restore(200, java.util.List.of());
        long lowRate = FixedIncomeValuationService.value(bond, player, 2).referencePricePerUnit();
        MonetaryPolicyService.restore(2_000, java.util.List.of());
        long highRate = FixedIncomeValuationService.value(bond, player, 2).referencePricePerUnit();
        assertTrue(highRate < lowRate);
        assertEquals(1_000, bond.couponBasisPoints());
    }

    @Test void creditDeteriorationLowersPriceAndDefaultUsesRecoveryDiscount() {
        long healthy = FixedIncomeValuationService.value(bond, player, 2).referencePricePerUnit();
        CompanyManager.getCompany(companyId).setBankruptcyRisk(true, 1);
        long risky = FixedIncomeValuationService.value(bond, player, 2).referencePricePerUnit();
        assertTrue(risky < healthy);
        bond.setStatus(BondStatus.DEFAULTED);
        bond.putRecoveredPrincipalDirect(player, 20_000);
        BondValuation value = FixedIncomeValuationService.value(bond, player, 2);
        assertEquals(80_000, value.remainingPrincipal());
        assertEquals(4_000, value.referencePricePerUnit());
    }

    @Test void portfolioAverageCostAndExtremeMarketValueStaySafe() {
        assertEquals(10_000, BondPortfolioManager.averageCost(bond.id(), player));
        assertTrue(BondPortfolioManager.freeze(bond.id(), player, 4));
        assertEquals(6, BondPortfolioManager.available(bond.id(), player));
        assertFalse(BondPortfolioManager.freeze(bond.id(), player, 7));
        assertTrue(BondPortfolioManager.unfreeze(bond.id(), player, 4));
    }

    @Test void nonHolderStillReceivesInstrumentPriceAndYield() {
        UUID observer = UUID.fromString("00000000-0000-0000-0000-000000004104");
        BondValuation value = FixedIncomeValuationService.value(bond, observer, 2);
        assertTrue(value.referencePricePerUnit() > 0);
        assertTrue(value.referenceYieldBasisPoints() > 0);
        assertTrue(value.marketYieldBasisPoints() >= 0);
        assertEquals(0, value.marketValue());
        assertEquals(0, value.unrealizedProfit());
    }

    @Test void lowerMarketPriceProducesHigherImpliedYield() {
        int premiumYield = FixedIncomeValuationService.marketYieldBasisPoints(bond, 2, 11_000);
        int discountYield = FixedIncomeValuationService.marketYieldBasisPoints(bond, 2, 9_000);
        assertTrue(discountYield > premiumYield);
    }
}
