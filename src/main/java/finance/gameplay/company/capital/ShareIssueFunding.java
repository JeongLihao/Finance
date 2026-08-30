package finance.gameplay.company.capital;

import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;

import java.util.UUID;

/**
 * Funds the project from a shareholder-approved share issue. The financing
 * project is started by the existing proposal execution path; this adapter
 * only links it, then consumes the finalized record once real raised cash has
 * reached the company. Unreached issues are refunded by the original module.
 */
final class ShareIssueFunding implements CapitalFundingAdapter {

    static final ShareIssueFunding INSTANCE = new ShareIssueFunding();

    private ShareIssueFunding() {
    }

    @Override
    public CapitalProjectActionResult initiate(WorldCapitalProject project, Company company,
                                               UUID bankId, long day) {
        CompanyFinancingProject financing = CompanyFinancingManager.getProjectByCompany(company.getCompanyId());
        if (financing == null) {
            return CapitalProjectActionResult.fail("finance.capital_project.share_issue_missing");
        }
        if (financing.getFundingTarget() != project.budget()) {
            return CapitalProjectActionResult.fail("finance.capital_project.share_issue_target_mismatch");
        }
        project.setFinancingProjectId(financing.getProjectId());
        return CapitalProjectActionResult.ok(project.projectId(), "finance.capital_project.share_issue_linked");
    }

    @Override
    public FundingSync sync(WorldCapitalProject project, Company company, long day) {
        if (project.financingProjectId() == null) {
            return FundingSync.failed("finance.capital_project.share_issue_missing");
        }
        CompanyFinancingManager.FinalizedFinancing finalized =
                CompanyFinancingManager.getFinalized(project.financingProjectId());
        if (finalized != null) {
            if (!project.companyId().equals(finalized.companyId()))
                return FundingSync.failed("finance.capital_project.share_issue_mismatch");
            if (finalized.raisedAmount() < project.budget()) {
                return FundingSync.failed("finance.capital_project.share_issue_underfunded");
            }
            return CommercialLoanFunding.moveCompanyCashToEscrow(project, company);
        }
        CompanyFinancingProject active = CompanyFinancingManager.getProject(project.financingProjectId());
        if (active != null && project.companyId().equals(active.getCompanyId())) {
            return FundingSync.pending();
        }
        if (active != null) return FundingSync.failed("finance.capital_project.share_issue_mismatch");
        return FundingSync.failed("finance.capital_project.share_issue_refunded");
    }
}
