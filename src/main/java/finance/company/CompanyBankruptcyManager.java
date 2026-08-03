package finance.company;

import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.config.FinanceConfig;
import finance.data.EconomySavedData;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.util.ProportionalAllocator;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class CompanyBankruptcyManager {

    private CompanyBankruptcyManager() {
    }

    public static void tick(long currentMcDay) {
        for (Company company : new ArrayList<>(CompanyManager.getCompanies())) {
            if (!company.isPublic()) {
                company.setBankruptcyRisk(false, -1);
                continue;
            }
            long safetyLine = Math.max(1, Math.round(company.estimateDailyOperatingCost()
                    * FinanceConfig.bankruptcyCashRiskMultiplier()));
            boolean risky = company.getCash() < safetyLine;
            if (!risky) {
                if (company.isBankruptcyRisk()) {
                    company.setBankruptcyRisk(false, -1);
                }
                continue;
            }
            if (!company.isBankruptcyRisk()) {
                company.setBankruptcyRisk(true, currentMcDay);
                record(company, TransactionType.COMPANY_BANKRUPTCY, 0, 0,
                        "进入风险状态，现金 " + company.getCash() + " / 安全线 " + safetyLine);
                continue;
            }
            if (currentMcDay - company.getBankruptcyRiskStartDay() >= FinanceConfig.bankruptcyRiskDays()) {
                bankrupt(company, currentMcDay);
            }
        }
        EconomySavedData.markDirty();
    }

    public static LiquidationResult bankrupt(Company company, long currentMcDay) {
        if (company == null) {
            return new LiquidationResult(false, 0, 0, 0, 0);
        }
        Stock stock = StockMarketManager.getStockByCompanyId(company.getCompanyId());
        String symbol = stock != null ? stock.getSymbol() : "";
        long liquidationValue = Math.max(0, company.getCash() + company.inventoryValue());
        int cancelledOrders = 0;
        int liquidatedHolders = 0;
        long paid = 0;

        if (stock != null) {
            cancelledOrders = StockMarketManager.cancelStockOrdersForSymbol(symbol);
            ConditionalStockOrderManager.cancelOrdersForSymbol(symbol, "股票退市");
            CompanyFinancingManager.cancelProjectsForCompany(company.getCompanyId());
            CompanyProposalManager.cancelActiveProposalsForCompany(company.getCompanyId(), "公司破产退市");
            Map<UUID, Long> holdings = StockPortfolioManager.getHoldingsForCompany(symbol);
            long totalHeld = holdings.values().stream().mapToLong(Long::longValue).sum();
            if (liquidationValue > 0 && totalHeld > 0) {
                for (ProportionalAllocator.Allocation share
                        : ProportionalAllocator.allocate(liquidationValue, holdings, totalHeld)) {
                    long payout = share.amount();
                    if (payout > 0) {
                        AccountManager.deposit(share.id(), payout);
                        paid += payout;
                        AccountManager.addTransactionRecord(new TransactionRecord(
                                company.getCompanyId(),
                                share.id(),
                                payout,
                                TransactionType.COMPANY_LIQUIDATION,
                                share.id(),
                                company.getName() + "/" + symbol,
                                share.weight()));
                    }
                }
            }
            liquidatedHolders = StockPortfolioManager.liquidateHolding(symbol, 0);
            StockMarketManager.removeStockByCompanyId(company.getCompanyId());
        }

        record(company, TransactionType.COMPANY_BANKRUPTCY, paid, liquidatedHolders,
                "破产退市，清算资产 " + liquidationValue + "，撤单 " + cancelledOrders);
        CompanyManager.removeCompany(company.getCompanyId());
        EconomySavedData.markDirty();
        return new LiquidationResult(true, liquidationValue, paid, liquidatedHolders, cancelledOrders);
    }

    private static void record(Company company, TransactionType type, long amount, long quantity, String objectName) {
        AccountManager.addTransactionRecord(new TransactionRecord(
                company.getCompanyId(),
                company.getCompanyId(),
                amount,
                type,
                company.getOwnerId(),
                company.getName() + " " + objectName,
                quantity));
    }

    public record LiquidationResult(boolean success, long liquidationValue, long paid,
                                    long liquidatedHolders, long cancelledOrders) {
    }
}
