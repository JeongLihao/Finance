package finance.gameplay.company.capital;

import finance.company.Company;
import finance.config.FinanceConfig;
import finance.debt.BondStatus;
import finance.debt.CompanyCreditService;
import finance.debt.CorporateBond;
import finance.debt.CorporateBondManager;
import finance.policy.MonetaryPolicyService;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Funds the project by issuing a corporate bond through the existing
 * {@link CorporateBondManager}. "Issued" is never treated as "raised": the
 * budget only moves to escrow after the subscription actually pays the
 * company when the bond activates.
 */
final class CorporateBondFunding implements CapitalFundingAdapter {

    static final CorporateBondFunding INSTANCE = new CorporateBondFunding();
    private static final long FACE_VALUE = 100L;

    private CorporateBondFunding() {
    }

    @Override
    public CapitalProjectActionResult initiate(WorldCapitalProject project, Company company,
                                               UUID bankId, long day) {
        BigInteger quantityValue = BigInteger.valueOf(project.budget())
                .add(BigInteger.valueOf(FACE_VALUE - 1)).divide(BigInteger.valueOf(FACE_VALUE));
        if (quantityValue.signum() <= 0
                || quantityValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return CapitalProjectActionResult.fail("finance.capital_project.bond_invalid");
        }
        long quantity = quantityValue.longValueExact();
        int minimumCoupon = MonetaryPolicyService.benchmarkRateBasisPoints()
                + CompanyCreditService.rate(company).spreadBasisPoints();
        long remainingDays = Math.max(2, project.deadlineDay() - day);
        int subscriptionDays = (int) Math.max(1, Math.min(30, remainingDays / 3));
        int termDays = (int) Math.max(subscriptionDays + 1,
                Math.min(remainingDays, FinanceConfig.maxBondTermDays()));
        int couponIntervalDays = (int) Math.max(1, Math.min(90, termDays / 4));
        String code = ("CP" + Long.toHexString(project.projectId().getMostSignificantBits())
                .toUpperCase()).substring(0, Math.min(16,
                2 + Long.toHexString(project.projectId().getMostSignificantBits()).length()));
        CorporateBondManager.Result result = CorporateBondManager.issue(
                company.getOwnerId(), company.getCompanyId(), code, FACE_VALUE, quantity,
                minimumCoupon, day, subscriptionDays, termDays, couponIntervalDays);
        if (!result.success()) {
            return CapitalProjectActionResult.fail("finance.capital_project.bond_denied");
        }
        project.setBondId(result.id());
        return CapitalProjectActionResult.ok(project.projectId(), "finance.capital_project.bond_issued");
    }

    @Override
    public FundingSync sync(WorldCapitalProject project, Company company, long day) {
        if (project.bondId() == null) return FundingSync.failed("finance.capital_project.bond_missing");
        CorporateBond bond = CorporateBondManager.bonds().get(project.bondId());
        if (bond == null) return FundingSync.failed("finance.capital_project.bond_missing");
        if (!project.companyId().equals(bond.companyId()))
            return FundingSync.failed("finance.capital_project.bond_mismatch");
        return switch (bond.status()) {
            case DRAFT, SUBSCRIPTION -> FundingSync.pending();
            case ACTIVE -> activeBondSync(project, company, bond);
            case CANCELLED -> FundingSync.failed("finance.capital_project.bond_cancelled");
            case DEFAULTED -> FundingSync.failed("finance.capital_project.bond_defaulted");
            case MATURED -> FundingSync.failed("finance.capital_project.bond_matured");
        };
    }

    private FundingSync activeBondSync(WorldCapitalProject project, Company company, CorporateBond bond) {
        BigInteger raised = BigInteger.valueOf(bond.faceValue())
                .multiply(BigInteger.valueOf(bond.subscribedQuantity()));
        if (raised.compareTo(BigInteger.valueOf(project.budget())) < 0) {
            return FundingSync.failed("finance.capital_project.bond_underfunded");
        }
        return CommercialLoanFunding.moveCompanyCashToEscrow(project, company);
    }
}
