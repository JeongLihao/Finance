package finance.company;

import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.data.EconomySavedData;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.StockMarketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyFinancialReportTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000004101");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
        NpcMarketMaker.putMarketPrice("iron", new MarketPrice("iron", 10, 0.05));
    }

    @Test
    void settleDailyProfitsGeneratesFinancialReportWithChanges() {
        Company company = new Company(COMPANY_ID, "Report Inc", CompanyType.RAW_MATERIALS, 1_000);
        company.addInventory("iron", 10);
        company.restoreFinancials(100, 40, 0, 0, 0, java.util.List.of(), java.util.List.of());

        company.settleDailyProfits(7);

        CompanyFinancialReport report = company.getLatestFinancialReport();
        assertNotNull(report);
        assertEquals(7, report.mcDay());
        assertEquals(100, report.revenue());
        assertTrue(report.expenses() >= 40);
        assertEquals(report.revenue() - report.expenses(), report.netProfit());
        assertEquals(1_000 + company.inventoryValue(), report.assets());
        assertEquals(0, report.liabilities());
        assertEquals(1_000, report.cashBalance());
    }

    @Test
    void stockFairValueUsesFinancialReportAssetAndProfitData() {
        Company company = new Company(COMPANY_ID, "Report Stock Inc", CompanyType.RAW_MATERIALS, 1_000);
        company.setPublic(true);
        company.addFinancialReportDirect(new CompanyFinancialReport(
                1, 500, 100, 400, 2_000, 200, 1_200, 100, 50,
                "盈利400，资产增加100，现金余额1200",
                java.time.LocalDateTime.now()));
        CompanyManager.registerDirect(company);
        Stock stock = new Stock("RPT", "Report Stock Inc", COMPANY_ID,
                100, 100, 0, 10, 10);
        StockMarketManager.putStockDirect(stock);

        StockMarketManager.updateFairValuesAndResetDay();

        assertTrue(stock.getFairValue() > 10,
                "财报中的净资产和利润应进入股票 fair value，而不是继续停留在旧估值");
    }
}
