package finance.company;

import java.time.LocalDateTime;

public record CompanyFinancialReport(
        long mcDay,
        long revenue,
        long expenses,
        long netProfit,
        long assets,
        long liabilities,
        long cashBalance,
        long assetChange,
        long profitChange,
        String summary,
        LocalDateTime createdAt) {
}
