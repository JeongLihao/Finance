package finance.bank;

import finance.account.TransactionType;
import finance.bank.BankingManager.Result;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.policy.MonetaryPolicyService;
import finance.util.MathUtil;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 定期存款：开户、到期赎回、提前支取和每日计息。从 {@link BankingManager} 拆出。
 * 只经 BankingManager 加锁的入口方法调用，沿用它的锁；银行、账户、定存三张表仍归 BankingManager。
 */
final class TimeDepositOps {

    private TimeDepositOps() {}

    static Result openTimeDeposit(UUID owner, UUID demandId, long amount, int termDays, long day) {
        BankCustomerAccount demand = BankingManager.ACCOUNTS.get(demandId);
        CommercialBank bank = demand == null ? null : BankingManager.BANKS.get(demand.bankId());
        if (!FinanceConfig.bankingEnabled()
                || owner == null
                || demand == null
                || bank == null
                || !demand.ownerId().equals(owner)
                || demand.ownerType() != CustomerType.PLAYER
                || demand.type() != BankAccountType.DEMAND_DEPOSIT
                || amount <= 0
                || demand.available() < amount
                || !FinanceConfig.timeDepositTerms().contains(termDays)
                || BankingManager.TIME_DEPOSITS.size() >= BankingManager.MAX_TIME_DEPOSITS)
            return Result.fail("定期存款参数无效");
        if (amount < FinanceConfig.timeDepositMinAmount()) return Result.fail("定期存款未达起存金额");
        long activePrincipal = 0L;
        for (TimeDeposit t : BankingManager.TIME_DEPOSITS.values())
            if (t.ownerId().equals(owner) && t.status() == TimeDepositStatus.ACTIVE)
                activePrincipal = MathUtil.saturatedAddNonNegative(activePrincipal, t.principal());
        if (MathUtil.saturatedAddNonNegative(activePrincipal, amount) > FinanceConfig.timeDepositMaxPerPlayer())
            return Result.fail("定期存款已达个人上限");
        long maturity;
        try {
            maturity = Math.addExact(day, termDays);
        } catch (ArithmeticException e) {
            return Result.fail("到期日溢出");
        }
        int rate = Math.max(
                0,
                Math.min(
                        20_000,
                        MonetaryPolicyService.benchmarkRateBasisPoints()
                                - bank.policy().timeSpreadBps()
                                + Math.min(500, termDays * 3)));
        UUID id = UUID.randomUUID(), accountId = UUID.randomUUID(), ref = UUID.randomUUID();
        if (!bank.ledger()
                .canPost(
                        ref,
                        List.of(new BankLedger.Draft(
                                BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                                BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                                amount,
                                BankLedgerReason.TIME_DEPOSIT_OPEN)))) return Result.fail("银行账务拒绝");
        demand.debit(amount);
        BankCustomerAccount time = new BankCustomerAccount(
                accountId,
                bank.id(),
                owner,
                CustomerType.PLAYER,
                BankAccountType.TIME_DEPOSIT,
                amount,
                0,
                day,
                BankAccountStatus.ACTIVE);
        BankingManager.ACCOUNTS.put(accountId, time);
        BankingManager.indexAccount(time);
        BankingManager.TIME_DEPOSITS.put(
                id,
                new TimeDeposit(
                        id,
                        bank.id(),
                        owner,
                        accountId,
                        amount,
                        rate,
                        day,
                        maturity,
                        0,
                        day,
                        TimeDepositStatus.ACTIVE));
        if (!bank.ledger()
                .post(
                        day,
                        ref,
                        BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                        BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                        amount,
                        BankLedgerReason.TIME_DEPOSIT_OPEN)) throw new IllegalStateException();
        BankingManager.record(
                owner, bank.id(), amount, TransactionType.TIME_DEPOSIT_OPEN, owner, bank.name() + "/" + termDays + "d");
        EconomySavedData.markDirty();
        return Result.ok(id, "定期存款已创建");
    }

    static Result redeemTimeDeposit(UUID owner, UUID depositId, long day) {
        TimeDeposit td = BankingManager.TIME_DEPOSITS.get(depositId);
        if (td == null || owner == null || !td.ownerId().equals(owner) || td.status() != TimeDepositStatus.MATURED)
            return Result.fail("定期存款尚未到期或已兑付");
        BankCustomerAccount time = BankingManager.ACCOUNTS.get(td.accountId()),
                demand = BankingManager.demandAccount(td.bankId(), owner, CustomerType.PLAYER, day, true);
        CommercialBank bank = BankingManager.BANKS.get(td.bankId());
        if (bank == null
                || bank.status() != BankStatus.ACTIVE
                || time == null
                || time.status() != BankAccountStatus.ACTIVE) return Result.fail("账户或银行状态异常，无法兑付");
        long amount = time.balance();
        UUID ref = UUID.randomUUID();
        if (amount <= 0
                || demand == null
                || !demand.canCredit(amount)
                || !bank.ledger()
                        .canPost(
                                ref,
                                List.of(new BankLedger.Draft(
                                        BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                                        BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                                        amount,
                                        BankLedgerReason.TIME_DEPOSIT_REDEEM)))) return Result.fail("兑付预检失败");
        time.debit(amount);
        time.close();
        demand.credit(amount);
        td.setStatus(TimeDepositStatus.REDEEMED);
        bank.ledger()
                .post(
                        day,
                        ref,
                        BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                        BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                        amount,
                        BankLedgerReason.TIME_DEPOSIT_REDEEM);
        BankingManager.record(bank.id(), owner, amount, TransactionType.TIME_DEPOSIT_REDEEM, owner, bank.name());
        EconomySavedData.markDirty();
        return Result.ok(depositId, "定期存款已转入活期");
    }

    /** 定期提前支取：仅 ACTIVE 可办；本金转回活期，全部应计利息罚没（记银行手续费收入）。 */
    static Result earlyRedeemTimeDeposit(UUID owner, UUID depositId, long day) {
        TimeDeposit td = BankingManager.TIME_DEPOSITS.get(depositId);
        if (td == null || owner == null || !td.ownerId().equals(owner) || td.status() != TimeDepositStatus.ACTIVE)
            return Result.fail("定期存款不存在、已到期请走正常兑付或已处理");
        BankCustomerAccount time = BankingManager.ACCOUNTS.get(td.accountId()),
                demand = BankingManager.demandAccount(td.bankId(), owner, CustomerType.PLAYER, day, true);
        CommercialBank bank = BankingManager.BANKS.get(td.bankId());
        if (time == null || bank == null || demand == null || time.status() != BankAccountStatus.ACTIVE)
            return Result.fail("账户异常");
        long balance = time.balance(), principal = Math.min(td.principal(), balance), forfeited = balance - principal;
        if (principal <= 0 || !demand.canCredit(principal)) return Result.fail("本金异常或活期账户无法接收");
        UUID ref = UUID.randomUUID();
        List<BankLedger.Draft> drafts = new ArrayList<>();
        drafts.add(new BankLedger.Draft(
                BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                principal,
                BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        if (forfeited > 0)
            drafts.add(new BankLedger.Draft(
                    BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                    BankLedgerAccount.INCOME_FEE,
                    forfeited,
                    BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        if (!bank.ledger().canPost(ref, drafts)) return Result.fail("银行账务拒绝");
        if (!time.debit(balance)) return Result.fail("扣减失败");
        time.close();
        if (!demand.credit(principal)) throw new IllegalStateException("validated early redeem credit failed");
        td.setStatus(TimeDepositStatus.EARLY_REDEEMED);
        bank.ledger().postBatch(day, ref, drafts);
        BankingManager.record(
                owner, bank.id(), principal, TransactionType.TIME_DEPOSIT_EARLY_REDEEM, owner, bank.name() + "/early");
        EconomySavedData.markDirty();
        return Result.ok(depositId, "定期已提前支取：本金" + principal + "已转入活期，" + forfeited + "利息已罚没");
    }

    static Result earlyRedeemTimeDeposit(UUID owner, UUID depositId, long day, long fee) {
        TimeDeposit td = BankingManager.TIME_DEPOSITS.get(depositId);
        if (td == null || owner == null || !td.ownerId().equals(owner) || td.status() != TimeDepositStatus.ACTIVE)
            return Result.fail("定期存款不存在或不可提前支取");
        BankCustomerAccount time = BankingManager.ACCOUNTS.get(td.accountId()),
                demand = BankingManager.demandAccount(td.bankId(), owner, CustomerType.PLAYER, day, true);
        CommercialBank bank = BankingManager.BANKS.get(td.bankId());
        if (time == null || demand == null || bank == null || time.status() != BankAccountStatus.ACTIVE)
            return Result.fail("提前支取预检失败");
        long principal = td.principal(), accrued = Math.max(0L, td.accruedInterest());
        if (time.balance() < MathUtil.saturatedAddNonNegative(principal, accrued)) return Result.fail("定期账户余额异常");
        long safeFee = Math.min(Math.max(0L, fee), principal), payout = principal - safeFee;
        if (payout > 0 && !demand.canCredit(payout)) return Result.fail("活期账户无法入账");
        List<BankLedger.Draft> drafts = new ArrayList<>();
        if (accrued > 0)
            drafts.add(new BankLedger.Draft(
                    BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                    BankLedgerAccount.EXPENSE_INTEREST,
                    accrued,
                    BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        drafts.add(new BankLedger.Draft(
                BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                principal,
                BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        if (safeFee > 0)
            drafts.add(new BankLedger.Draft(
                    BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                    BankLedgerAccount.INCOME_FEE,
                    safeFee,
                    BankLedgerReason.TIME_DEPOSIT_EARLY_REDEEM));
        UUID ref = UUID.randomUUID();
        if (!bank.ledger().canPost(ref, drafts)) return Result.fail("银行账务拒绝");
        if (accrued > 0 && !time.debit(accrued)) return Result.fail("利息冲回失败");
        if (!time.debit(principal)) return Result.fail("本金转出失败");
        time.close();
        if (payout > 0 && !demand.credit(payout))
            throw new IllegalStateException("validated early redemption credit failed");
        if (!bank.ledger().postBatch(day, ref, drafts))
            throw new IllegalStateException("validated early redemption batch failed");
        td.setStatus(TimeDepositStatus.EARLY_REDEEMED);
        BankingManager.record(
                owner, bank.id(), payout, TransactionType.TIME_DEPOSIT_EARLY_REDEEM, owner, bank.name() + "/提前支取");
        EconomySavedData.markDirty();
        return Result.ok(depositId, "提前支取完成，已罚没全部利息，仅返还本金");
    }

    static void accrueTimeDeposits(long day) {
        for (TimeDeposit td : BankingManager.TIME_DEPOSITS.values()) {
            if (td.status() != TimeDepositStatus.ACTIVE || day <= td.lastAccrualDay()) continue;
            BankCustomerAccount account = BankingManager.ACCOUNTS.get(td.accountId());
            CommercialBank bank = BankingManager.BANKS.get(td.bankId());
            if (bank == null
                    || bank.status() != BankStatus.ACTIVE
                    || account == null
                    || account.status() != BankAccountStatus.ACTIVE) continue;
            long through = Math.min(day, td.maturityDay()), days = through - td.lastAccrualDay();
            if (days > 0) {
                BigInteger exact = BigInteger.valueOf(td.principal())
                        .multiply(BigInteger.valueOf(td.lockedRateBps()))
                        .multiply(BigInteger.valueOf(days))
                        .divide(BigInteger.valueOf((long) FinanceConfig.annualMcDays() * 10_000));
                long interest = exact.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                if (interest > 0 && account != null && account.canCredit(interest)) {
                    UUID ref =
                            UUID.nameUUIDFromBytes((td.id() + ":interest:" + through).getBytes(StandardCharsets.UTF_8));
                    if (bank.ledger()
                            .post(
                                    through,
                                    ref,
                                    BankLedgerAccount.EXPENSE_INTEREST,
                                    BankLedgerAccount.LIABILITY_TIME_DEPOSIT,
                                    interest,
                                    BankLedgerReason.TIME_DEPOSIT_INTEREST)) {
                        account.credit(interest);
                        td.addInterest(interest, through);
                    }
                } else td.addInterest(0, through);
            }
            if (day >= td.maturityDay()) td.setStatus(TimeDepositStatus.MATURED);
        }
    }
}
