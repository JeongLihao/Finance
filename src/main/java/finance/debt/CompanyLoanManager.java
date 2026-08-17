package finance.debt;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.CentralBank;
import finance.money.MoneyEndpoints;
import finance.money.MoneyTransferService;
import finance.policy.MonetaryPolicyService;
import finance.bank.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CompanyLoanManager {
    private static final Map<UUID, CompanyLoan> LOANS = new LinkedHashMap<>();
    private CompanyLoanManager() { }

    public static Result apply(UUID ownerId, UUID companyId, long principal, long currentDay, int termDays, int paymentIntervalDays) {
        Company company = CompanyManager.getCompany(companyId);
        if (company == null || ownerId == null || !ownerId.equals(company.getOwnerId())) return Result.fail("无权申请");
        if (principal <= 0 || currentDay < 0 || termDays < 2 || termDays > FinanceConfig.maxLoanTermDays()
                || paymentIntervalDays < 1 || paymentIntervalDays >= termDays
                || LOANS.size() >= FinanceConfig.maxCompanyLoans()) return Result.fail("贷款参数无效");
        CreditRating rating = CompanyCreditService.rate(company);
        if (rating == CreditRating.D) return Result.fail("信用等级不足");
        BigInteger limit = BigInteger.valueOf(Math.max(1, company.getReportBasedAssetValue()))
                .multiply(BigInteger.valueOf(rating.maxDebtPercent())).divide(BigInteger.valueOf(100));
        if (BigInteger.valueOf(CompanyCreditService.totalDebt(companyId)).add(BigInteger.valueOf(principal)).compareTo(limit) > 0)
            return Result.fail("贷款超过信用额度");
        int termSpread = Math.min(500, termDays * 5);
        int rate = Math.min(FinanceConfig.maxContractRateBasisPoints(), MonetaryPolicyService.benchmarkRateBasisPoints() + rating.spreadBasisPoints() + termSpread);
        long maturity;
        long firstPayment;
        try {
            maturity = Math.addExact(currentDay, termDays);
            firstPayment = Math.addExact(currentDay, paymentIntervalDays);
        } catch (ArithmeticException overflow) {
            return Result.fail("贷款日期溢出");
        }
        CentralBank.seedIfNeeded();
        if (!MoneyTransferService.transfer(MoneyEndpoints.account(CentralBank.UUID), MoneyEndpoints.company(company), principal).success())
            return Result.fail("央行资金不足或收款溢出");
        UUID id = UUID.randomUUID();
        CompanyLoan loan = new CompanyLoan(id, companyId, principal, rate, currentDay, maturity,
                paymentIntervalDays, principal, 0, currentDay, firstPayment, -1, LoanStatus.ACTIVE);
        LOANS.put(id, loan); record(loan, ownerId, principal, TransactionType.LOAN_ISSUE, "公司贷款发放");
        return Result.ok(id, "贷款已发放");
    }

    public static Result applyCommercial(UUID ownerId, UUID companyId, UUID bankId, long principal,
                                         long currentDay, int termDays, int paymentIntervalDays) {
        Company company=CompanyManager.getCompany(companyId);CommercialBank bank=BankingManager.bank(bankId);
        if(!FinanceConfig.bankingEnabled()||company==null||bank==null||ownerId==null||!ownerId.equals(company.getOwnerId())||!bank.acceptsNewBusiness()
                ||principal<=0||currentDay<0||termDays<2||termDays>FinanceConfig.maxLoanTermDays()
                ||paymentIntervalDays<1||paymentIntervalDays>=termDays||LOANS.size()>=FinanceConfig.maxCompanyLoans())return Result.fail("商业银行贷款参数无效");
        CreditRating rating=CompanyCreditService.rate(company);if(rating==CreditRating.D)return Result.fail("信用等级不足");
        BigInteger companyLimit=BigInteger.valueOf(Math.max(1,company.getReportBasedAssetValue())).multiply(BigInteger.valueOf(rating.maxDebtPercent())).divide(BigInteger.valueOf(100));
        if(BigInteger.valueOf(CompanyCreditService.totalDebt(companyId)).add(BigInteger.valueOf(principal)).compareTo(companyLimit)>0)return Result.fail("超过公司信用额度");
        long bankEquity=bank.ledger().balanceSheet().equity();long borrowerLimit=BigInteger.valueOf(Math.max(0,bankEquity)).multiply(BigInteger.valueOf(bank.policy().singleBorrowerLimitBps())).divide(BigInteger.valueOf(10_000)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        long existing=LOANS.values().stream().filter(l->l.lenderType()==LoanLenderType.COMMERCIAL_BANK&&l.lenderId().equals(bankId)&&l.companyId().equals(companyId)&&l.status()!=LoanStatus.REPAID&&l.status()!=LoanStatus.CANCELLED).mapToLong(CompanyLoan::outstandingPrincipal).reduce(0,CompanyLoanManager::saturatedAdd);
        if(principal>borrowerLimit||existing>borrowerLimit-principal||BankRegulatoryService.capitalAdequacyBps(bank)<FinanceConfig.bankMinimumCapitalBps())return Result.fail("银行资本或集中度限制");
        int rate=Math.min(FinanceConfig.maxContractRateBasisPoints(),MonetaryPolicyService.benchmarkRateBasisPoints()+rating.spreadBasisPoints()+Math.min(500,termDays*5)+bank.policy().loanSpreadBps());
        long maturity,first;try{maturity=Math.addExact(currentDay,termDays);first=Math.addExact(currentDay,paymentIntervalDays);}catch(ArithmeticException e){return Result.fail("贷款日期溢出");}
        UUID id=UUID.randomUUID();if(!BankingManager.originateCompanyDepositLoan(bankId,companyId,principal,currentDay,id))return Result.fail("银行资产负债表拒绝发放");
        CompanyLoan loan=new CompanyLoan(id,companyId,principal,rate,currentDay,maturity,paymentIntervalDays,principal,0,currentDay,first,-1,LoanStatus.ACTIVE,LoanLenderType.COMMERCIAL_BANK,bankId);LOANS.put(id,loan);record(loan,ownerId,principal,TransactionType.LOAN_ISSUE,"商业银行贷款发放");EconomySavedData.markDirty();return Result.ok(id,"商业银行贷款已发放到公司存款账户");
    }

    public static Result repay(UUID ownerId, UUID loanId, long amount, long currentDay) {
        CompanyLoan loan = LOANS.get(loanId); Company company = loan == null ? null : CompanyManager.getCompany(loan.companyId());
        if (company == null || ownerId == null || !ownerId.equals(company.getOwnerId()) || amount <= 0
                || (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.DELINQUENT)) return Result.fail("还款无效");
        accrue(loan, currentDay);
        long due = saturatedAdd(loan.outstandingPrincipal(), loan.accruedInterest());
        long payment = Math.min(amount, due);
        long interestPaid = Math.min(payment, loan.accruedInterest());
        long principalPaid=payment-interestPaid;
        boolean settled=loan.lenderType()==LoanLenderType.COMMERCIAL_BANK
                ?BankingManager.repayCompanyLoan(loan.lenderId(),loan.companyId(),principalPaid,interestPaid,currentDay,UUID.randomUUID())
                :MoneyTransferService.transfer(MoneyEndpoints.company(company), MoneyEndpoints.account(CentralBank.UUID), payment).success();
        if(!settled)return Result.fail("还款结算失败");
        loan.setAccruedInterest(loan.accruedInterest() - interestPaid);
        loan.setOutstandingPrincipal(loan.outstandingPrincipal() - principalPaid);
        if (loan.outstandingPrincipal() == 0 && loan.accruedInterest() == 0) loan.setStatus(LoanStatus.REPAID);
        else if (loan.accruedInterest() == 0) {
            loan.setStatus(LoanStatus.ACTIVE); loan.setDelinquentSinceDay(-1);
            long next;
            try { next = Math.addExact(currentDay, loan.paymentIntervalDays()); }
            catch (ArithmeticException overflow) { next = loan.maturityDay(); }
            loan.setNextPaymentDay(Math.min(loan.maturityDay(), next));
        }
        record(loan, ownerId, payment, TransactionType.LOAN_REPAYMENT, "公司贷款还款"); EconomySavedData.markDirty();
        return Result.ok(loan.id(), "还款成功");
    }

    public static void processDay(long day) {
        for (CompanyLoan loan : new ArrayList<>(LOANS.values())) {
            if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.DELINQUENT) continue;
            accrue(loan, day);
            if (day >= loan.maturityDay() && (loan.outstandingPrincipal() > 0 || loan.accruedInterest() > 0)) markDelinquent(loan, day);
            else if (day >= loan.nextPaymentDay() && loan.accruedInterest() > 0) markDelinquent(loan, day);
            if (loan.status() == LoanStatus.DELINQUENT && day - loan.delinquentSinceDay() >= FinanceConfig.loanGraceDays()) {
                loan.setStatus(LoanStatus.DEFAULTED);
                provisionCommercialDefault(loan,day);
                record(loan, CompanyManager.getCompany(loan.companyId()) == null ? null : CompanyManager.getCompany(loan.companyId()).getOwnerId(),
                        saturatedAdd(loan.outstandingPrincipal(), loan.accruedInterest()), TransactionType.LOAN_DEFAULT, "公司贷款违约");
            }
        }
        EconomySavedData.markDirty();
    }
    private static void provisionCommercialDefault(CompanyLoan loan,long day){if(loan.lenderType()!=LoanLenderType.COMMERCIAL_BANK)return;CommercialBank bank=BankingManager.bank(loan.lenderId());if(bank==null)return;long current=bank.ledger().balance(BankLedgerAccount.CONTRA_LOAN_LOSS_RESERVE);BigInteger desired=LOANS.values().stream().filter(l->l.lenderType()==LoanLenderType.COMMERCIAL_BANK&&l.lenderId().equals(loan.lenderId())&&l.status()==LoanStatus.DEFAULTED).map(l->BigInteger.valueOf(l.outstandingPrincipal())).reduce(BigInteger.ZERO,BigInteger::add);long target=desired.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(),add=Math.max(0,target-current);if(add>0){UUID ref=UUID.nameUUIDFromBytes((loan.lenderId()+":provision:"+day).getBytes(java.nio.charset.StandardCharsets.UTF_8));bank.ledger().post(day,ref,BankLedgerAccount.EXPENSE_CREDIT_LOSS,BankLedgerAccount.CONTRA_LOAN_LOSS_RESERVE,add,BankLedgerReason.LOAN_LOSS_PROVISION);BankRegulatoryService.evaluate(bank);}}

    private static void accrue(CompanyLoan loan, long day) {
        if (day <= loan.lastAccrualDay() || loan.outstandingPrincipal() <= 0) return;
        long days = day - loan.lastAccrualDay();
        BigInteger interest = BigInteger.valueOf(loan.outstandingPrincipal())
                .multiply(BigInteger.valueOf(loan.annualRateBasisPoints())).multiply(BigInteger.valueOf(days))
                .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
        long add = interest.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        loan.setAccruedInterest(saturatedAdd(loan.accruedInterest(), add)); loan.setLastAccrualDay(day);
    }
    private static void markDelinquent(CompanyLoan loan, long day) {
        if (loan.status() == LoanStatus.ACTIVE) {
            loan.setStatus(LoanStatus.DELINQUENT); loan.setDelinquentSinceDay(day);
            Company company = CompanyManager.getCompany(loan.companyId());
            record(loan, company == null ? null : company.getOwnerId(), loan.accruedInterest(),
                    TransactionType.LOAN_DELINQUENT, "公司贷款逾期");
        }
    }
    public static long outstandingPrincipal(UUID companyId) {
        BigInteger sum = BigInteger.ZERO;
        for (CompanyLoan l : LOANS.values()) if (l.companyId().equals(companyId)
                && l.status() != LoanStatus.REPAID && l.status() != LoanStatus.CANCELLED) sum = sum.add(BigInteger.valueOf(l.outstandingPrincipal()));
        return sum.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static boolean canApplyBankruptcyRecovery(UUID companyId,UUID lenderId,long amount){if(companyId==null||lenderId==null||amount<0)return false;if(amount==0)return true;long claim=outstandingByLender(companyId).getOrDefault(lenderId,0L);if(amount>claim)return false;CommercialBank bank=BankingManager.bank(lenderId);return bank==null||BankingManager.canApplyLoanRecovery(lenderId,amount);}
    public static Map<UUID,Long> outstandingByLender(UUID companyId){Map<UUID,Long>out=new LinkedHashMap<>();for(CompanyLoan l:LOANS.values())if(l.companyId().equals(companyId)&&l.status()!=LoanStatus.REPAID&&l.status()!=LoanStatus.CANCELLED)out.merge(l.lenderId(),l.outstandingPrincipal(),CompanyLoanManager::saturatedAdd);return out;}
    public static boolean hasDefault(UUID companyId) { return LOANS.values().stream().anyMatch(l -> l.companyId().equals(companyId) && l.status() == LoanStatus.DEFAULTED); }
    public static synchronized boolean applyInsuranceRecovery(UUID loanId,long amount,long day,UUID reference){
        CompanyLoan loan=LOANS.get(loanId);
        if(loan==null||loan.status()!=LoanStatus.DEFAULTED||loan.lenderType()!=LoanLenderType.COMMERCIAL_BANK||amount<=0||amount>loan.outstandingPrincipal()||reference==null)return false;
        if(!BankingManager.canApplyLoanRecovery(loan.lenderId(),amount)||!BankingManager.applyLoanRecovery(loan.lenderId(),amount,day,reference))return false;
        loan.setOutstandingPrincipal(loan.outstandingPrincipal()-amount);
        if(loan.outstandingPrincipal()==0&&loan.accruedInterest()==0)loan.setStatus(LoanStatus.REPAID);
        EconomySavedData.markDirty();return true;
    }
    static void markBankruptcyDefault(UUID companyId) {
        for (CompanyLoan loan : LOANS.values()) if (loan.companyId().equals(companyId)
                && loan.status() != LoanStatus.REPAID && loan.status() != LoanStatus.CANCELLED) {
            loan.setStatus(LoanStatus.DEFAULTED);
            if (loan.delinquentSinceDay() < 0) loan.setDelinquentSinceDay(Math.max(loan.issueDay(), loan.lastAccrualDay()));
        }
    }
    static long applyBankruptcyRecovery(UUID companyId, long amount) {
        return applyBankruptcyRecovery(companyId,null,amount);
    }
    static long applyBankruptcyRecovery(UUID companyId,UUID lenderId,long amount) {
        long remaining = Math.max(0, amount);
        for (CompanyLoan loan : LOANS.values()) {
            if (remaining <= 0) break;
            if (!loan.companyId().equals(companyId)||(lenderId!=null&&!loan.lenderId().equals(lenderId)) || loan.status() == LoanStatus.REPAID || loan.status() == LoanStatus.CANCELLED) continue;
            long applied = Math.min(remaining, loan.outstandingPrincipal());
            if(loan.lenderType()==LoanLenderType.COMMERCIAL_BANK&&applied>0){UUID ref=UUID.nameUUIDFromBytes((loan.id()+":recovery:"+loan.outstandingPrincipal()).getBytes(java.nio.charset.StandardCharsets.UTF_8));if(!BankingManager.applyLoanRecovery(loan.lenderId(),applied,Math.max(loan.lastAccrualDay(),loan.issueDay()),ref))continue;}
            loan.setOutstandingPrincipal(loan.outstandingPrincipal() - applied);
            remaining -= applied;
        }
        return amount - remaining;
    }
    public static Map<UUID, CompanyLoan> loans() { return java.util.Collections.unmodifiableMap(LOANS); }
    public static void putDirect(CompanyLoan loan) { if (loan != null && LOANS.size() < FinanceConfig.maxCompanyLoans()) LOANS.put(loan.id(), loan); }
    public static void clearDirect() { LOANS.clear(); }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static void record(CompanyLoan loan, UUID owner, long amount, TransactionType type, String object) {
        AccountManager.addTransactionRecord(new TransactionRecord(loan.lenderId(), loan.companyId(), amount, type, owner, object + "/" + loan.id(), 1));
    }
    public record Result(boolean success, UUID id, String message) {
        static Result ok(UUID id, String m) { return new Result(true, id, m); }
        static Result fail(String m) { return new Result(false, null, m); }
    }
}
