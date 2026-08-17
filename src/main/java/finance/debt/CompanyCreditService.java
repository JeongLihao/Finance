package finance.debt;

import finance.company.Company;

/** Deterministic rating derived from solvency, profitability and defaults. */
public final class CompanyCreditService {
    private CompanyCreditService() {
    }

    public static CreditRating rate(Company company) {
        if (company == null || company.isBankruptcyRisk()) return CreditRating.D;
        long assets = Math.max(1, company.getReportBasedAssetValue());
        long debt = totalDebt(company.getCompanyId());
        if (hasDefault(company.getCompanyId())) return CreditRating.D;
        double leverage = (double) debt / assets;
        long profit = company.getSmoothedDailyProfit();
        double liquidity = (double) company.getCash() / assets;
        int score = 50;
        score += profit > 0 ? 20 : profit < 0 ? -20 : 0;
        score += liquidity >= .50 ? 15 : liquidity >= .20 ? 8 : liquidity < .05 ? -15 : 0;
        score += leverage <= .10 ? 15 : leverage <= .30 ? 8 : leverage <= .50 ? 0 : leverage <= .75 ? -20 : -40;
        if (score >= finance.config.FinanceConfig.creditAaaMinimumScore()) return CreditRating.AAA;
        if (score >= finance.config.FinanceConfig.creditAaMinimumScore()) return CreditRating.AA;
        if (score >= finance.config.FinanceConfig.creditAMinimumScore()) return CreditRating.A;
        if (score >= finance.config.FinanceConfig.creditBbbMinimumScore()) return CreditRating.BBB;
        if (score >= finance.config.FinanceConfig.creditBbMinimumScore()) return CreditRating.BB;
        if (score >= finance.config.FinanceConfig.creditBMinimumScore()) return CreditRating.B;
        return CreditRating.CCC;
    }

    public static long totalDebt(java.util.UUID companyId) {
        return finance.util.MathUtil.saturatedAddNonNegative(
                CorporateBondManager.outstandingPrincipal(companyId), CompanyLoanManager.outstandingPrincipal(companyId));
    }

    private static boolean hasDefault(java.util.UUID companyId) {
        return CorporateBondManager.hasDefault(companyId) || CompanyLoanManager.hasDefault(companyId);
    }
}
