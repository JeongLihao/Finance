package finance.gameplay.company.capital;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferResult;
import finance.money.MoneyTransferService;

import java.util.UUID;

/** Pays the budget directly from company cash into the project escrow. */
final class RetainedEarningsFunding implements CapitalFundingAdapter {

    static final RetainedEarningsFunding INSTANCE = new RetainedEarningsFunding();

    private RetainedEarningsFunding() {
    }

    @Override
    public CapitalProjectActionResult initiate(WorldCapitalProject project, Company company,
                                               UUID bankId, long day) {
        UUID escrow = project.escrowAccountId();
        finance.account.Account escrowAccount = AccountManager.getOrCreateSystemAccount(escrow);
        if (escrowAccount.getBalance() != 0 || !escrowAccount.canDeposit(project.budget())) {
            return CapitalProjectActionResult.fail("finance.capital_project.escrow_not_empty");
        }
        if (company.getCash() < project.budget()) {
            return CapitalProjectActionResult.fail("finance.capital_project.cash_insufficient");
        }
        MoneyTransferResult transfer = MoneyTransferService.transfer(
                MoneyEndpoints.company(company), MoneyEndpoints.account(escrow), project.budget());
        if (!transfer.success()) {
            return CapitalProjectActionResult.fail("finance.capital_project.transfer_failed");
        }
        project.setFundedAmount(project.budget());
        project.setFundingSettled(true);
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), escrow,
                project.budget(), TransactionType.CAPITAL_PROJECT_ESCROW, company.getOwnerId(),
                company.getName() + "/capital-project", 1));
        return CapitalProjectActionResult.ok(project.projectId(), "finance.capital_project.funded");
    }

    @Override
    public FundingSync sync(WorldCapitalProject project, Company company, long day) {
        return project.fundingSettled() && project.fundedAmount() == project.budget()
                ? FundingSync.funded() : FundingSync.pending();
    }
}
