package finance.gameplay.company.capital;

import finance.account.AccountManager;
import finance.account.Account;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.bank.BankCustomerAccount;
import finance.bank.BankingManager;
import finance.bank.CustomerType;
import finance.company.Company;
import finance.config.FinanceConfig;
import finance.debt.CompanyLoan;
import finance.debt.CompanyLoanManager;
import finance.debt.LoanStatus;
import finance.debt.LoanLenderType;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferResult;
import finance.money.MoneyTransferService;

import java.util.UUID;

/**
 * Funds the project with a commercial bank loan. The adapter only calls
 * {@link CompanyLoanManager#applyCommercial} and the existing deposit
 * withdrawal path; it never touches bank reserves or loan assets directly.
 */
final class CommercialLoanFunding implements CapitalFundingAdapter {

    static final CommercialLoanFunding INSTANCE = new CommercialLoanFunding();

    private CommercialLoanFunding() {
    }

    @Override
    public CapitalProjectActionResult initiate(WorldCapitalProject project, Company company,
                                               UUID bankId, long day) {
        if (bankId == null || BankingManager.bank(bankId) == null) {
            return CapitalProjectActionResult.fail("finance.capital_project.bank_missing");
        }
        long remainingDays = Math.max(0, project.deadlineDay() - day);
        int termDays = (int) Math.max(2, Math.min(remainingDays, FinanceConfig.maxLoanTermDays()));
        if (termDays > FinanceConfig.maxLoanTermDays()) termDays = FinanceConfig.maxLoanTermDays();
        int paymentIntervalDays = Math.max(1, termDays / 4);
        CompanyLoanManager.Result result = CompanyLoanManager.applyCommercial(
                company.getOwnerId(), company.getCompanyId(), bankId, project.budget(),
                day, termDays, paymentIntervalDays);
        if (!result.success()) {
            return CapitalProjectActionResult.fail("finance.capital_project.loan_denied");
        }
        project.setLoanId(result.id());
        project.setBankId(bankId);
        return CapitalProjectActionResult.ok(project.projectId(), "finance.capital_project.loan_granted");
    }

    @Override
    public FundingSync sync(WorldCapitalProject project, Company company, long day) {
        if (project.loanId() == null || project.bankId() == null) {
            return FundingSync.failed("finance.capital_project.loan_missing");
        }
        CompanyLoan loan = CompanyLoanManager.loans().get(project.loanId());
        if (loan == null) return FundingSync.failed("finance.capital_project.loan_missing");
        if (!project.companyId().equals(loan.companyId())
                || loan.lenderType() != LoanLenderType.COMMERCIAL_BANK
                || !project.bankId().equals(loan.lenderId())
                || loan.originalPrincipal() != project.budget()) {
            return FundingSync.failed("finance.capital_project.loan_mismatch");
        }
        if (loan.status() == LoanStatus.DEFAULTED) {
            return FundingSync.failed("finance.capital_project.loan_defaulted");
        }
        if (loan.status() == LoanStatus.REPAID || loan.status() == LoanStatus.CANCELLED) {
            return FundingSync.failed("finance.capital_project.loan_closed");
        }
        BankCustomerAccount account = BankingManager.demandAccount(
                project.bankId(), company.getCompanyId(), CustomerType.COMPANY, day, false);
        if (account == null || account.available() < project.budget()) {
            return FundingSync.pending();
        }
        Account escrow = AccountManager.getOrCreateSystemAccount(project.escrowAccountId());
        if (escrow.getBalance() != 0 || !escrow.canDeposit(project.budget())) {
            return FundingSync.failed("finance.capital_project.escrow_mismatch");
        }
        if (!BankingManager.withdrawCompanyToCash(company.getOwnerId(), project.bankId(),
                project.budget(), day)) {
            return FundingSync.pending();
        }
        return moveCompanyCashToEscrow(project, company);
    }

    static FundingSync moveCompanyCashToEscrow(WorldCapitalProject project, Company company) {
        UUID escrow = project.escrowAccountId();
        Account escrowAccount = AccountManager.getOrCreateSystemAccount(escrow);
        if (escrowAccount.getBalance() == project.budget()) {
            project.setFundedAmount(project.budget());
            project.setFundingSettled(true);
            return FundingSync.funded();
        }
        if (escrowAccount.getBalance() != 0 || !escrowAccount.canDeposit(project.budget())) {
            return FundingSync.failed("finance.capital_project.escrow_mismatch");
        }
        if (company.getCash() < project.budget()) return FundingSync.pending();
        MoneyTransferResult transfer = MoneyTransferService.transfer(
                MoneyEndpoints.company(company), MoneyEndpoints.account(escrow), project.budget());
        if (!transfer.success()) {
            return FundingSync.pending();
        }
        project.setFundedAmount(project.budget());
        project.setFundingSettled(true);
        AccountManager.addTransactionRecord(new TransactionRecord(company.getCompanyId(), escrow,
                project.budget(), TransactionType.CAPITAL_PROJECT_ESCROW, company.getOwnerId(),
                company.getName() + "/capital-project", 1));
        return FundingSync.funded();
    }
}
