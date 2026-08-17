package finance.bank;

import java.util.UUID;

public record BankBalanceSheetSnapshot(UUID bankId, long mcDay, BankBalanceSheet sheet, long dailyProfit,
                                       long nonPerformingLoans, int liquidityCoverageBps, int capitalAdequacyBps) {
    public BankBalanceSheetSnapshot {
        if (bankId == null || mcDay < 0 || sheet == null || !sheet.balanced() || nonPerformingLoans < 0) throw new IllegalArgumentException();
    }
}
