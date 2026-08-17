package finance.fixedincome;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.market.CentralBank;

import java.math.BigInteger;
import java.util.*;

/** Primary-only central-bank bills with locked rates and idempotent maturity. */
public final class CentralBankBillManager {
    public static final int MAX_BILLS = 4_096;
    private static final Set<Integer> TERMS = Set.of(7, 30, 90);
    private static final Map<UUID, CentralBankBill> BILLS = new LinkedHashMap<>();
    private static long cumulativePolicyIssuance;
    private static long lastPolicyIssuance;
    private static long lastPolicyIssuanceDay = -1;
    private CentralBankBillManager() { }

    public static Result subscribe(UUID playerId, int termDays, long principal, long currentDay) {
        if (playerId == null || playerId.equals(CentralBank.UUID) || !TERMS.contains(termDays)
                || principal <= 0 || currentDay < 0) return Result.fail("央行票据认购参数无效");
        long maturity;
        try { maturity = Math.addExact(currentDay, termDays); }
        catch (ArithmeticException ignored) { return Result.fail("到期日溢出"); }
        CentralBankBill bill = BILLS.values().stream().filter(b -> b.status() == CentralBankBillStatus.ACTIVE
                && b.issueDay() == currentDay && b.termDays() == termDays).findFirst().orElse(null);
        if (bill == null) {
            if (BILLS.size() >= MAX_BILLS) return Result.fail("央行票据数量达到上限");
            bill = new CentralBankBill(UUID.randomUUID(), termDays, YieldCurveService.yieldBasisPoints(termDays),
                    currentDay, maturity, CentralBankBillStatus.ACTIVE);
            BILLS.put(bill.id(), bill);
        }
        long old = bill.principalByPlayer().getOrDefault(playerId, 0L);
        if (old > Long.MAX_VALUE - principal || !AccountManager.moveFunds(playerId, CentralBank.UUID, principal)) {
            if (bill.principalByPlayer().isEmpty()) BILLS.remove(bill.id());
            return Result.fail("资金不足或票据持仓溢出");
        }
        if (!bill.addPrincipal(playerId, principal)) {
            AccountManager.moveFunds(CentralBank.UUID, playerId, principal);
            if (bill.principalByPlayer().isEmpty()) BILLS.remove(bill.id());
            return Result.fail("票据持仓结算失败");
        }
        AccountManager.addTransactionRecord(new TransactionRecord(playerId, CentralBank.UUID, principal,
                TransactionType.CENTRAL_BANK_BILL_SUBSCRIBE, playerId, "央行票据/" + termDays + "日", principal));
        EconomySavedData.markDirty();
        return Result.ok(bill.id(), "央行票据认购成功");
    }

    public static void processDay(long day) {
        if (day < 0) return;
        for (CentralBankBill bill : BILLS.values()) {
            if (bill.status() == CentralBankBillStatus.ACTIVE && day >= bill.maturityDay()) settle(bill, day);
        }
    }

    private static boolean settle(CentralBankBill bill, long day) {
        Map<UUID, Long> payouts = new LinkedHashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (Map.Entry<UUID, Long> holding : bill.principalByPlayer().entrySet()) {
            BigInteger principal = BigInteger.valueOf(holding.getValue());
            BigInteger interest = principal.multiply(BigInteger.valueOf(bill.annualRateBasisPoints()))
                    .multiply(BigInteger.valueOf(bill.termDays()))
                    .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
            BigInteger due = principal.add(interest);
            if (due.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
            long amount = due.longValue();
            if (!AccountManager.canDeposit(holding.getKey(), amount)) return false;
            payouts.put(holding.getKey(), amount); total = total.add(due);
        }
        if (total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return false;
        long due = total.longValue(), reserve = AccountManager.getBalance(CentralBank.UUID);
        long issuance = Math.max(0, due - reserve);
        if (issuance > 0 && !AccountManager.deposit(CentralBank.UUID, issuance)) return false;
        List<Map.Entry<UUID, Long>> paid = new ArrayList<>();
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) {
            if (!AccountManager.moveFunds(CentralBank.UUID, payout.getKey(), payout.getValue())) {
                for (Map.Entry<UUID, Long> rollback : paid) AccountManager.moveFunds(rollback.getKey(), CentralBank.UUID, rollback.getValue());
                if (issuance > 0) AccountManager.withdraw(CentralBank.UUID, issuance);
                return false;
            }
            paid.add(payout);
        }
        if (issuance > 0) {
            cumulativePolicyIssuance = saturatedAdd(cumulativePolicyIssuance, issuance);
            lastPolicyIssuance = issuance; lastPolicyIssuanceDay = day;
            AccountManager.addTransactionRecord(new TransactionRecord(CentralBank.UUID, CentralBank.UUID, issuance,
                    TransactionType.CENTRAL_BANK_MONETARY_ISSUE, CentralBank.UUID, "票据兑付缺口投放", 1));
        }
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) AccountManager.addTransactionRecord(
                new TransactionRecord(CentralBank.UUID, payout.getKey(), payout.getValue(),
                        TransactionType.CENTRAL_BANK_BILL_MATURITY, payout.getKey(), "央行票据到期/" + bill.termDays() + "日", 1));
        bill.setStatus(CentralBankBillStatus.MATURED); EconomySavedData.markDirty(); return true;
    }

    public static long expectedMaturityValue(CentralBankBill bill, UUID player) {
        if (bill == null || player == null) return 0;
        long principal = bill.principalByPlayer().getOrDefault(player, 0L);
        BigInteger interest = BigInteger.valueOf(principal).multiply(BigInteger.valueOf(bill.annualRateBasisPoints()))
                .multiply(BigInteger.valueOf(bill.termDays()))
                .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000L));
        return interest.add(BigInteger.valueOf(principal)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    public static Map<UUID, CentralBankBill> bills() { return Collections.unmodifiableMap(BILLS); }
    public static long cumulativePolicyIssuance() { return cumulativePolicyIssuance; }
    public static long lastPolicyIssuance() { return lastPolicyIssuance; }
    public static long lastPolicyIssuanceDay() { return lastPolicyIssuanceDay; }
    public static void restorePolicyIssuance(long cumulative, long last, long day) {
        cumulativePolicyIssuance = Math.max(0, cumulative);
        lastPolicyIssuance = Math.min(cumulativePolicyIssuance, Math.max(0, last));
        lastPolicyIssuanceDay = lastPolicyIssuance > 0 ? Math.max(0, day) : -1;
    }
    public static void putDirect(CentralBankBill bill) { if (bill != null && BILLS.size() < MAX_BILLS) BILLS.put(bill.id(), bill); }
    public static void clearDirect() { BILLS.clear(); cumulativePolicyIssuance = 0; lastPolicyIssuance = 0; lastPolicyIssuanceDay = -1; }
    private static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    public record Result(boolean success, UUID billId, String message) {
        static Result ok(UUID id, String message) { return new Result(true, id, message); }
        static Result fail(String message) { return new Result(false, null, message); }
    }
}
