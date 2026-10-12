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
                    Math.max(0, benchmark + bank.policy().timeSpreadBps() + 90), 0,
                    PlayerBankService.isPlayerBank(bank.id()));
        }
        // P2：rwa==0 时 capitalAdequacyBps 恒为 100000 具欺骗性，改用真实杠杆口径展示。
        int capitalBps = BankRegulatoryService.riskWeightedAssets(bank) == 0
                ? BankRegulatoryService.leverageRatioBps(bank)
                : BankRegulatoryService.capitalAdequacyBps(bank);
        return new PublicBankView(bank.id(), bank.code(), bank.name(), bank.status(),
                sheet.totalAssets(), sheet.reserves(), BankRegulatoryService.requiredReserves(bank),
                safeAdd(sheet.demandDeposits(), sheet.timeDeposits()), sheet.equity(),
                capitalBps, BankRegulatoryService.liquidityCoverageBps(bank),
                Math.max(0, benchmark - bank.policy().demandSpreadBps()),
                Math.max(0, benchmark + bank.policy().timeSpreadBps() + 90), sheet.centralBankBorrowing(),
                PlayerBankService.isPlayerBank(bank.id()));
    }

    private static long safeAdd(long a, long b) {
        if (a < 0 || b < 0) return 0;
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public record PublicBankView(java.util.UUID id, String code, String name, BankStatus status,
                                 long assets, long reserves, long requiredReserves, long deposits,
                                 long equity, int capitalBps, int liquidityBps,
                                 int demandRateBps, int timeRateBps, long centralBorrowing,
                                 boolean playerOwned) {}
}
