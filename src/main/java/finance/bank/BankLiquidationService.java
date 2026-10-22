package finance.bank;

import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.data.EconomySavedData;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 破产清算：桥接处置搬走保险部分后，对剩余未保险存款按
 * recoveryRate = min(1, 剩余准备金 / 未保险负债) 现金赔付，差额销账。
 * 玩家进钱包、公司进公司账户；NPC 零售池不承保，全额核销。
 * 全程复式平衡，不印钱。
 */
public final class BankLiquidationService {
    private BankLiquidationService() {}

    public static synchronized void settleUninsured(UUID bankId, long day) {
        CommercialBank bank = BankingManager.bank(bankId);
        if (bank == null || bank.status() != BankStatus.FAILED || day < 0) return;
        List<BankCustomerAccount> claims = new ArrayList<>();
        List<BankCustomerAccount> npcAccounts = new ArrayList<>();
        for (BankCustomerAccount a : BankingManager.accounts().values()) {
            if (!a.bankId().equals(bankId) || a.status() != BankAccountStatus.ACTIVE || a.balance() <= 0) continue;
            if (a.ownerType() == CustomerType.NPC) npcAccounts.add(a);
            else claims.add(a);
        }
        long totalClaims =
                claims.stream().mapToLong(BankCustomerAccount::balance).reduce(0L, BankLiquidationService::safeAdd);
        long reserves = bank.ledger().balance(BankLedgerAccount.ASSET_RESERVE);
        UUID ref = UUID.nameUUIDFromBytes(("finance-liquidation-" + bankId).getBytes(StandardCharsets.UTF_8));
        if (bank.ledger().hasReference(ref)) return;
        List<BankLedger.Draft> drafts = new ArrayList<>();
        List<Runnable> payouts = new ArrayList<>();
        for (BankCustomerAccount a : claims) {
            long balance = a.balance();
            // payout = balance × reserves / totalClaims（BigInteger 防溢出）
            long payout = totalClaims <= 0
                    ? 0
                    : BigInteger.valueOf(balance)
                            .multiply(BigInteger.valueOf(reserves))
                            .divide(BigInteger.valueOf(totalClaims))
                            .min(BigInteger.valueOf(balance))
                            .longValue();
            long haircut = balance - payout;
            BankLedgerAccount liability = a.type() == BankAccountType.TIME_DEPOSIT
                    ? BankLedgerAccount.LIABILITY_TIME_DEPOSIT
                    : BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT;
            if (payout > 0)
                drafts.add(new BankLedger.Draft(
                        liability, BankLedgerAccount.ASSET_RESERVE, payout, BankLedgerReason.LIQUIDATION_PAYOUT));
            if (haircut > 0)
                drafts.add(new BankLedger.Draft(
                        liability, BankLedgerAccount.EQUITY_RETAINED, haircut, BankLedgerReason.LIQUIDATION_PAYOUT));
            if (payout > 0) payouts.add(() -> payOut(a, payout, day));
        }
        // NPC 池：不承保，全额核销
        for (BankCustomerAccount a : npcAccounts) {
            BankLedgerAccount liability = a.type() == BankAccountType.TIME_DEPOSIT
                    ? BankLedgerAccount.LIABILITY_TIME_DEPOSIT
                    : BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT;
            drafts.add(new BankLedger.Draft(
                    liability, BankLedgerAccount.EQUITY_RETAINED, a.balance(), BankLedgerReason.LIQUIDATION_PAYOUT));
        }
        if (!drafts.isEmpty()) {
            if (!bank.ledger().canPost(ref, drafts)) return;
            bank.ledger().postBatch(day, ref, drafts);
        }
        for (BankCustomerAccount a : claims) {
            a.debit(a.available());
            a.close();
        }
        for (BankCustomerAccount a : npcAccounts) {
            a.debit(a.available());
            a.close();
        }
        // 定期存款对象与账户解耦：账户已关，对应的 TimeDeposit 必须同步终结，
        // 否则 accrueTimeDeposits 会继续给已关闭账户计息（幻影利息）。
        markTimeDepositsRedeemed(bankId);
        for (Runnable r : payouts) r.run();
        EconomySavedData.markDirty();
    }

    /** 将某银行名下全部定期存款对象置为已终结（清算/处置后调用，防幻影利息）。 */
    static void markTimeDepositsRedeemed(UUID bankId) {
        for (TimeDeposit td : BankingManager.timeDeposits().values())
            if (td.bankId().equals(bankId)) td.setStatus(TimeDepositStatus.REDEEMED);
    }

    private static void payOut(BankCustomerAccount a, long payout, long day) {
        if (a.ownerType() == CustomerType.PLAYER) {
            if (AccountManager.canDeposit(a.ownerId(), payout)) {
                AccountManager.deposit(a.ownerId(), payout);
                return;
            }
            // 钱包装不下：转桥接银行 suspense 挂账，不丢弃
        } else if (a.ownerType() == CustomerType.COMPANY) {
            Company c = CompanyManager.getCompany(a.ownerId());
            if (c != null && c.deposit(payout)) return;
            // 公司已注销或入账失败：转桥接银行 suspense 挂账，不丢弃
        }
        suspensePayout(a, payout, day);
    }

    /** 桥接银行 suspense 挂账 owner（确定性派生）：无法直接赔付的款项记为 unclaimed 债权，永不静默丢弃。 */
    static UUID suspenseOwnerId(UUID bankId) {
        return UUID.nameUUIDFromBytes(("finance-liquidation-suspense-" + bankId).getBytes(StandardCharsets.UTF_8));
    }

    private static void suspensePayout(BankCustomerAccount a, long payout, long day) {
        CommercialBank bridge = DepositInsuranceService.bridgeBank();
        BankCustomerAccount target =
                BankingManager.demandAccount(bridge.id(), suspenseOwnerId(a.bankId()), CustomerType.PLAYER, day, true);
        if (target == null || !target.canCredit(payout))
            throw new IllegalStateException("liquidation suspense account unavailable");
        UUID ref = UUID.nameUUIDFromBytes(("finance-liquidation-suspense-" + a.id()).getBytes(StandardCharsets.UTF_8));
        List<BankLedger.Draft> drafts = List.of(new BankLedger.Draft(
                BankLedgerAccount.ASSET_RESERVE,
                BankLedgerAccount.LIABILITY_DEMAND_DEPOSIT,
                payout,
                BankLedgerReason.LIQUIDATION_PAYOUT));
        if (!bridge.ledger().canPost(ref, drafts) || !bridge.ledger().postBatch(day, ref, drafts))
            throw new IllegalStateException("liquidation suspense post failed");
        if (!target.credit(payout)) throw new IllegalStateException("liquidation suspense credit failed");
    }

    private static long safeAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
