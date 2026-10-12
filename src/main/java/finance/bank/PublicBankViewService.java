package finance.bank;

/** Server-side privacy boundary for venue responses. Hidden values are never serialized. */
public final class PublicBankViewService {
    private PublicBankViewService() {}

    public static PublicBankView crop(CommercialBank bank, boolean administrator) {
        if (bank == null) throw new IllegalArgumentException("bank");
        var sheet = bank.ledger().balanceSheet();
        int benchmark = finance.policy.MonetaryPolicyService.benchmarkRateBasisPoints();
        if (!administrator) {
            return new PublicBankView(bank.id(), bank.code(), bank.name(), bank.status(),
                    0, 0, 0, 0, 0, 0, 0,
                    Math.max(0, benchmark - bank.policy().demandSpreadBps()),
                    Math.max(0, benchmark + bank.policy().timeSpreadBps() + 90), 0);
        }
        return new PublicBankView(bank.id(), bank.code(), bank.name(), bank.status(),
                sheet.totalAssets(), sheet.reserves(), BankRegulatoryService.requiredReserves(bank),
                safeAdd(sheet.demandDeposits(), sheet.timeDeposits()), sheet.equity(),
                BankRegulatoryService.capitalAdequacyBps(bank), BankRegulatoryService.liquidityCoverageBps(bank),
                Math.max(0, benchmark - bank.policy().demandSpreadBps()),
                Math.max(0, benchmark + bank.policy().timeSpreadBps() + 90), sheet.centralBankBorrowing());
    }

    private static long safeAdd(long a, long b) {
        if (a < 0 || b < 0) return 0;
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public record PublicBankView(java.util.UUID id, String code, String name, BankStatus status,
                                 long assets, long reserves, long requiredReserves, long deposits,
                                 long equity, int capitalBps, int liquidityBps,
                                 int demandRateBps, int timeRateBps, long centralBorrowing) {}

    /** 恐慌信息发布面：档位/恐慌值/排队金额/是否限提/最新谣言（网络与 GUI 接线记二期）。 */
    public static BankPanicView panicView(CommercialBank bank) {
        if (bank == null) throw new IllegalArgumentException("bank");
        var state = BankRunService.stateOf(bank.id());
        var tier = BankPanicTier.of(state.panic(),
                finance.config.FinanceConfig.bankRunCapPanicThreshold(),
                finance.config.FinanceConfig.bankRunFailPanicThreshold());
        return new BankPanicView(bank.id(), bank.code(), tier, state.panic(), state.queued(),
                BankRunService.withdrawalCapped(bank.id()),
                state.lastRumorDay() >= 0 ? state.lastRumorText() : "");
    }

    public record BankPanicView(java.util.UUID id, String code, BankPanicTier tier,
                                int panic, long queuedAmount, boolean withdrawalCapped, String rumor) {}
}
