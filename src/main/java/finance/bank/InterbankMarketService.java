package finance.bank;

import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.policy.MonetaryPolicyService;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InterbankMarketService {
    public static final int MAX_LOANS = 2_000;
    private static final Map<UUID, InterbankLoan> LOANS = new LinkedHashMap<>();
    private static long lastProcessedDay = -1;
    private static int overnightRateBps;

    private InterbankMarketService() { }

    public static synchronized long borrowFor(UUID borrowerId, long minimumReserve, long day) {
        CommercialBank borrower = BankingManager.bank(borrowerId);
        if (!FinanceConfig.bankingEnabled() || borrower == null
                || minimumReserve <= borrower.ledger().balance(BankLedgerAccount.ASSET_RESERVE)) return 0;
        long need = minimumReserve - borrower.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        long borrowed = 0;
        List<CommercialBank> lenders = BankingManager.banks().values().stream()
                .filter(bank -> !bank.id().equals(borrowerId) && bank.acceptsNewBusiness())
                .sorted(Comparator.comparing(CommercialBank::code)).toList();
        for (CommercialBank lender : lenders) {
            long excess = Math.max(0, lender.ledger().balance(BankLedgerAccount.ASSET_RESERVE)
                    - BankRegulatoryService.requiredReserves(lender));
            long amount = Math.min(need - borrowed, excess);
            if (amount > 0 && issue(lender.id(), borrowerId, amount, day) != null)
                borrowed = safeAdd(borrowed, amount);
            if (borrowed >= need) break;
        }
        return borrowed;
    }

    public static synchronized UUID issue(UUID lenderId, UUID borrowerId, long principal, long day) {
        if (!FinanceConfig.bankingEnabled() || LOANS.size() >= MAX_LOANS || principal <= 0 || day < 0) return null;
        CommercialBank lender = BankingManager.bank(lenderId), borrower = BankingManager.bank(borrowerId);
        if (lender == null || borrower == null || lenderId.equals(borrowerId)
                || !lender.acceptsNewBusiness() || !borrower.acceptsNewBusiness()
                || lender.ledger().balance(BankLedgerAccount.ASSET_RESERVE) < principal) return null;
        long maturity;
        try { maturity = Math.addExact(day, 1); }
        catch (ArithmeticException overflow) { return null; }
        int rate = Math.min(20_000, MonetaryPolicyService.benchmarkRateBasisPoints() + 100
                + (borrower.status() == BankStatus.WATCH ? 200 : 0));
        UUID id = UUID.randomUUID();
        List<BankLedger.Draft> lenderDrafts = List.of(new BankLedger.Draft(
                BankLedgerAccount.ASSET_INTERBANK, BankLedgerAccount.ASSET_RESERVE,
                principal, BankLedgerReason.INTERBANK_ISSUE));
        List<BankLedger.Draft> borrowerDrafts = List.of(new BankLedger.Draft(
                BankLedgerAccount.ASSET_RESERVE, BankLedgerAccount.LIABILITY_INTERBANK,
                principal, BankLedgerReason.INTERBANK_ISSUE));
        if (!lender.ledger().canPost(id, lenderDrafts) || !borrower.ledger().canPost(id, borrowerDrafts)) return null;
        if (!lender.ledger().postBatch(day, id, lenderDrafts)
                || !borrower.ledger().postBatch(day, id, borrowerDrafts))
            throw new IllegalStateException("prevalidated interbank issue failed");
        LOANS.put(id, new InterbankLoan(id, lenderId, borrowerId, principal, rate,
                day, maturity, InterbankLoanStatus.ACTIVE));
        EconomySavedData.markDirty();
        return id;
    }

    public static synchronized void processDay(long day) {
        if (day <= lastProcessedDay) return;
        for (InterbankLoan loan : LOANS.values())
            if (loan.status() == InterbankLoanStatus.ACTIVE && day >= loan.maturityDay()) repayOrDefault(loan, day);
        lastProcessedDay = day;
        computeRate(day - 1);
        EconomySavedData.markDirty();
    }

    private static void repayOrDefault(InterbankLoan loan, long day) {
        CommercialBank lender = BankingManager.bank(loan.lenderBankId());
        CommercialBank borrower = BankingManager.bank(loan.borrowerBankId());
        if (lender == null || borrower == null) { loan.setStatus(InterbankLoanStatus.DEFAULTED); return; }
        long interest = BigInteger.valueOf(loan.principal()).multiply(BigInteger.valueOf(loan.rateBasisPoints()))
                .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        long total;
        try { total = Math.addExact(loan.principal(), interest); }
        catch (ArithmeticException e) { defaultLoan(loan, lender, borrower, day); return; }
        List<BankLedger.Draft> lenderDrafts = new ArrayList<>(), borrowerDrafts = new ArrayList<>();
        lenderDrafts.add(new BankLedger.Draft(BankLedgerAccount.ASSET_RESERVE,
                BankLedgerAccount.ASSET_INTERBANK, loan.principal(), BankLedgerReason.INTERBANK_REPAYMENT));
        borrowerDrafts.add(new BankLedger.Draft(BankLedgerAccount.LIABILITY_INTERBANK,
                BankLedgerAccount.ASSET_RESERVE, loan.principal(), BankLedgerReason.INTERBANK_REPAYMENT));
        if (interest > 0) {
            lenderDrafts.add(new BankLedger.Draft(BankLedgerAccount.ASSET_RESERVE,
                    BankLedgerAccount.INCOME_INTEREST, interest, BankLedgerReason.INTERBANK_INTEREST));
            borrowerDrafts.add(new BankLedger.Draft(BankLedgerAccount.EXPENSE_INTEREST,
                    BankLedgerAccount.ASSET_RESERVE, interest, BankLedgerReason.INTERBANK_INTEREST));
        }
        UUID ref = UUID.nameUUIDFromBytes((loan.id() + ":repayment").getBytes(StandardCharsets.UTF_8));
        if (borrower.ledger().balance(BankLedgerAccount.ASSET_RESERVE) < total
                || !lender.ledger().canPost(ref, lenderDrafts) || !borrower.ledger().canPost(ref, borrowerDrafts)) {
            defaultLoan(loan, lender, borrower, day); return;
        }
        lender.ledger().postBatch(day, ref, lenderDrafts);
        borrower.ledger().postBatch(day, ref, borrowerDrafts);
        loan.setStatus(InterbankLoanStatus.REPAID);
    }

    private static void defaultLoan(InterbankLoan loan, CommercialBank lender,
                                    CommercialBank borrower, long day) {
        UUID ref = UUID.nameUUIDFromBytes((loan.id() + ":default").getBytes(StandardCharsets.UTF_8));
        lender.ledger().post(day, ref, BankLedgerAccount.EXPENSE_CREDIT_LOSS,
                BankLedgerAccount.ASSET_INTERBANK, loan.principal(), BankLedgerReason.INTERBANK_DEFAULT);
        borrower.setStatus(BankStatus.RESOLUTION);
        loan.setStatus(InterbankLoanStatus.DEFAULTED);
        BankRegulatoryService.evaluate(lender);
    }

    private static void computeRate(long issueDay) {
        BigInteger weighted = BigInteger.ZERO, volume = BigInteger.ZERO;
        for (InterbankLoan loan : LOANS.values()) if (loan.issueDay() == issueDay) {
            weighted = weighted.add(BigInteger.valueOf(loan.principal())
                    .multiply(BigInteger.valueOf(loan.rateBasisPoints())));
            volume = volume.add(BigInteger.valueOf(loan.principal()));
        }
        overnightRateBps = volume.signum() == 0 ? MonetaryPolicyService.benchmarkRateBasisPoints()
                : weighted.divide(volume).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    public static Map<UUID, InterbankLoan> loans() { return Collections.unmodifiableMap(LOANS); }
    public static int overnightRateBasisPoints() { return overnightRateBps; }
    public static long lastProcessedDay() { return lastProcessedDay; }
    public static void putDirect(InterbankLoan loan) { if (loan != null && LOANS.size() < MAX_LOANS) LOANS.put(loan.id(), loan); }
    public static void restoreDay(long day) { lastProcessedDay = Math.max(-1, day); }
    public static void clearDirect() { LOANS.clear(); lastProcessedDay = -1; overnightRateBps = 0; }
    private static long safeAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
}
