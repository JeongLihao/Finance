package finance.gui;

import finance.company.Company;
import finance.company.CompanyFinancialReport;
import finance.company.CompanyManager;
import finance.market.MarketPrice;
import finance.metrics.EconomyMetricsService;
import finance.stock.Stock;
import java.util.*;

/** 把公司、商品、股票等服务端对象转成菜单数据行，以及经济看板的汇总。从 {@link FinanceGuiOpener} 拆出。 */
final class FinanceRowConverters {

    /** 走势图的点数：快照只在价格变动时记，所以就是最近这么多次价格变动。 */
    static final int HISTORY_POINTS = 60;

    private FinanceRowConverters() {}

    static CompanyRows.CompanyInfo toCompanyInfo(Company company) {
        return toCompanyInfo(company, true);
    }

    static CompanyRows.CompanyInfo toCompanyInfo(Company company, boolean privateView) {
        boolean publicFinancials = company.isPublic();
        CompanyFinancialReport report = company.getLatestFinancialReport();
        List<finance.gameplay.company.capital.WorldCapitalProject> capitalProjects =
                finance.gameplay.company.capital.CapitalProjectManager.forCompany(company.getCompanyId()).stream()
                        .filter(project -> !project.status().terminal())
                        .toList();
        long capitalCommitted = 0;
        for (finance.gameplay.company.capital.WorldCapitalProject project : capitalProjects) {
            try {
                capitalCommitted = Math.addExact(capitalCommitted, project.fundedAmount());
            } catch (ArithmeticException overflow) {
                capitalCommitted = Long.MAX_VALUE;
                break;
            }
        }
        String capitalSummary = capitalProjects.isEmpty()
                ? ""
                : capitalProjects.get(0).type().name() + " "
                        + capitalProjects.get(0).status().name() + " "
                        + capitalProjects.get(0).fundedAmount() + "/"
                        + capitalProjects.get(0).budget();
        return new CompanyRows.CompanyInfo(
                company.getCompanyId(),
                company.getName(),
                company.getType().getDisplayName(),
                privateView ? company.getCash() : (report != null ? report.cashBalance() : 0),
                publicFinancials ? company.inventoryValue() : 0,
                publicFinancials ? company.getEstimatedValue() : 0,
                privateView ? new LinkedHashMap<>(company.getInventory()) : Map.of(),
                company.isPlayerOwned(),
                company.isPublic(),
                company.getStrategy().getDisplayName(),
                company.getProductionLevel(),
                company.getStorageLevel(),
                company.getManagementLevel(),
                company.getAutoSellRatio(),
                report != null ? report.revenue() : 0,
                report != null ? report.expenses() : 0,
                report != null ? report.netProfit() : 0,
                report != null ? report.assets() : 0,
                report != null ? report.liabilities() : 0,
                report != null ? report.cashBalance() : company.getCash(),
                report != null ? report.profitChange() : 0,
                report != null ? report.assetChange() : 0,
                report != null ? report.summary() : "暂无财报。",
                company.isBankruptcyRisk(),
                company.getBankruptcyRiskStartDay(),
                capitalProjects.size(),
                capitalCommitted,
                capitalSummary);
    }

    static List<Long> commodityHistory(MarketPrice price) {
        List<Long> history = new ArrayList<>();
        List<MarketPrice.PriceSnapshot> snapshots = price.getSnapshots();
        int start = Math.max(0, snapshots.size() - HISTORY_POINTS);
        for (int i = start; i < snapshots.size(); i++) {
            history.add(snapshots.get(i).getPrice());
        }
        if (history.isEmpty()) {
            history.add(price.getMidPrice());
        }
        return history;
    }

    static List<Long> stockHistory(Stock stock) {
        List<Long> history = new ArrayList<>();
        List<finance.stock.StockPriceEngine.PriceSnapshot> snapshots = stock.getSnapshots();
        int start = Math.max(0, snapshots.size() - HISTORY_POINTS);
        for (int i = start; i < snapshots.size(); i++) {
            history.add(snapshots.get(i).getPrice());
        }
        if (history.isEmpty()) {
            history.add(stock.getLastPrice());
        }
        return history;
    }

    static long millis(java.time.LocalDateTime time) {
        return time == null
                ? 0
                : time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static List<Long> commodityHourBars(MarketPrice price) {
        List<Long> times = new ArrayList<>(), prices = new ArrayList<>();
        for (MarketPrice.PriceSnapshot s : price.getSnapshots()) {
            times.add(millis(s.getTimestamp()));
            prices.add(s.getPrice());
        }
        return finance.chart.HourBars.build(times, prices, finance.chart.HourBars.MAX_BARS);
    }

    static List<Long> stockHourBars(Stock stock) {
        List<Long> times = new ArrayList<>(), prices = new ArrayList<>();
        for (finance.stock.StockPriceEngine.PriceSnapshot s : stock.getSnapshots()) {
            times.add(millis(s.getTimestamp()));
            prices.add(s.getPrice());
        }
        return finance.chart.HourBars.build(times, prices, finance.chart.HourBars.MAX_BARS);
    }

    /**
     * Phase 4：股票做市商状态（服务端取数，客户端只展示）。
     * 取数走 Phase 2 约定访问器 {@code market.liquidity.QuoteEngine#isHalted/#getQuote}，
     * 库存水位走 Phase 1 的 {@link finance.market.MarketMakerLedger}。
     * 水位：0=充足，1=紧张（≥80% 上限），2=熔断。
     */
    static long marketMakerBid(Stock stock) {
        var quote = finance.market.liquidity.QuoteEngine.getQuote("stock", stock.getSymbol());
        return quote == null ? 0 : Math.max(0, quote.bid());
    }

    static long marketMakerAsk(Stock stock) {
        var quote = finance.market.liquidity.QuoteEngine.getQuote("stock", stock.getSymbol());
        return quote == null ? 0 : Math.max(0, quote.ask());
    }

    static boolean marketMakerHalted(Stock stock) {
        return finance.market.liquidity.QuoteEngine.isHalted("stock", stock.getSymbol());
    }

    static int marketMakerInventoryLevel(Stock stock) {
        if (marketMakerHalted(stock)) {
            return 2;
        }
        long max = finance.market.MarketMakerLedger.maxInventory(stock.getFloatShares());
        if (max <= 0) {
            return 0;
        }
        long inventory = finance.market.MarketMakerLedger.getStockInventory(stock.getSymbol());
        // 库存 ≥80% 上限视为紧张（触顶后做市商只卖不买）。
        return inventory * 5 >= max * 4 ? 1 : 0;
    }

    static DividendSummary dividendSummary(Company company, Stock stock) {
        if (company == null || stock == null || stock.getTotalShares() <= 0) {
            return new DividendSummary(0, 0, 0);
        }
        long expectedTotal = Math.min(
                company.getCash(), Math.round(company.getDistributableProfit() * CompanyManager.getDividendRatio()));
        long expectedPerShare = expectedTotal > 0 ? expectedTotal / stock.getTotalShares() : 0;
        List<Company.DividendRecord> history = company.getDividendHistory();
        if (history.isEmpty()) {
            return new DividendSummary(expectedPerShare, 0, 0);
        }
        Company.DividendRecord last = history.get(history.size() - 1);
        return new DividendSummary(expectedPerShare, last.perShare(), last.totalAmount());
    }

    record DividendSummary(long expectedPerShare, long lastPerShare, long lastTotal) {}

    static DashboardRows.EconomyDashboardRow buildDashboard() {
        EconomyMetricsService.CurrentMetrics current = EconomyMetricsService.getCurrentMetrics();
        List<DashboardRows.EconomyTrendRow> trends = EconomyMetricsService.getDailySnapshots().stream()
                .map(snapshot -> new DashboardRows.EconomyTrendRow(
                        snapshot.mcDay(), snapshot.commodityVolume(), snapshot.stockVolume(), snapshot.priceIndex()))
                .toList();
        return new DashboardRows.EconomyDashboardRow(
                current.playerCash(),
                current.playerFrozenFunds(),
                current.companyCash(),
                current.npcCash(),
                current.centralBankReserve(),
                current.totalMoney(),
                current.dailyCommodityVolume(),
                current.dailyStockVolume(),
                current.priceIndex(),
                current.bankruptcyRiskCompanies(),
                current.centralBankSummary() + " | " + finance.risk.FinancialRiskService.compactSummary(),
                trends);
    }
}
