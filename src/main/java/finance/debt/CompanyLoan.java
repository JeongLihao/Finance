package finance.debt;

import java.util.UUID;

public final class CompanyLoan {
    private final UUID id;
    private final UUID companyId;
    private final long originalPrincipal;
    private final int annualRateBasisPoints;
    private final long issueDay;
    private final long maturityDay;
    private final int paymentIntervalDays;
    private long outstandingPrincipal;
    private long accruedInterest;
    private long lastAccrualDay;
    private long nextPaymentDay;
    private long delinquentSinceDay;
    private LoanStatus status;
    private final LoanLenderType lenderType;
    private final UUID lenderId;

    public CompanyLoan(UUID id, UUID companyId, long originalPrincipal, int annualRateBasisPoints,
                       long issueDay, long maturityDay, int paymentIntervalDays, long outstandingPrincipal,
                       long accruedInterest, long lastAccrualDay, long nextPaymentDay,
                       long delinquentSinceDay, LoanStatus status) {
        this(id, companyId, originalPrincipal, annualRateBasisPoints, issueDay, maturityDay, paymentIntervalDays,
                outstandingPrincipal, accruedInterest, lastAccrualDay, nextPaymentDay, delinquentSinceDay, status,
                LoanLenderType.CENTRAL_BANK_DIRECT, finance.market.CentralBank.UUID);
    }
    public CompanyLoan(UUID id, UUID companyId, long originalPrincipal, int annualRateBasisPoints,
                       long issueDay, long maturityDay, int paymentIntervalDays, long outstandingPrincipal,
                       long accruedInterest, long lastAccrualDay, long nextPaymentDay,
                       long delinquentSinceDay, LoanStatus status, LoanLenderType lenderType, UUID lenderId) {
        if(id==null||companyId==null||lenderType==null||lenderId==null)throw new IllegalArgumentException();
        this.id = id; this.companyId = companyId; this.originalPrincipal = originalPrincipal;
        this.annualRateBasisPoints = annualRateBasisPoints; this.issueDay = issueDay; this.maturityDay = maturityDay;
        this.paymentIntervalDays = paymentIntervalDays; this.outstandingPrincipal = outstandingPrincipal;
        this.accruedInterest = accruedInterest; this.lastAccrualDay = lastAccrualDay; this.nextPaymentDay = nextPaymentDay;
        this.delinquentSinceDay = delinquentSinceDay; this.status = status;this.lenderType=lenderType;this.lenderId=lenderId;
    }
    public UUID id() { return id; } public UUID companyId() { return companyId; }
    public long originalPrincipal() { return originalPrincipal; } public int annualRateBasisPoints() { return annualRateBasisPoints; }
    public long issueDay() { return issueDay; } public long maturityDay() { return maturityDay; }
    public int paymentIntervalDays() { return paymentIntervalDays; } public long outstandingPrincipal() { return outstandingPrincipal; }
    public long accruedInterest() { return accruedInterest; } public long lastAccrualDay() { return lastAccrualDay; }
    public long nextPaymentDay() { return nextPaymentDay; } public long delinquentSinceDay() { return delinquentSinceDay; }
    public LoanStatus status() { return status; }
    public LoanLenderType lenderType(){return lenderType;}public UUID lenderId(){return lenderId;}
    void setOutstandingPrincipal(long v) { outstandingPrincipal = Math.max(0, v); }
    void setAccruedInterest(long v) { accruedInterest = Math.max(0, v); }
    void setLastAccrualDay(long v) { lastAccrualDay = v; }
    void setNextPaymentDay(long v) { nextPaymentDay = v; }
    void setDelinquentSinceDay(long v) { delinquentSinceDay = v; }
    void setStatus(LoanStatus v) { status = v; }
}
