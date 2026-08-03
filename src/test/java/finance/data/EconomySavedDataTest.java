package finance.data;

import finance.account.AccountManager;
import finance.account.AssetSnapshotManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertManager;
import finance.alert.PriceAlertType;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;
import finance.company.CompanyFinancialReport;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.CompanyManager;
import finance.company.CompanyStrategy;
import finance.company.CompanyType;
import finance.market.MarketManager;
import finance.stock.StockMarketManager;
import finance.stock.Stock;
import finance.stock.ConditionalStockOrder;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.ConditionalStockOrderType;
import finance.stock.StockPriceEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EconomySavedDataTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000001101");

    @BeforeEach
    void resetState() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @AfterEach
    void cleanup() {
        EconomySavedData.resetRuntimeState();
        CommodityRegistry.resetToDefaults();
    }

    @Test
    void loadSkipsBadUuidAndBadEnumRecordsInsteadOfFailingWholeEconomy() {
        CompoundTag root = new CompoundTag();
        root.put("Accounts", listOf(badUuidAccount()));
        root.put("Transactions", listOf(transactionWithBadType()));
        root.put("Orders", listOf(orderWithBadType()));
        root.put("Companies", listOf(companyWithBadType()));
        root.put("Stocks", listOf(stockWithBadCompanyUuid()));
        root.put("StockOrders", listOf(stockOrderWithBadType()));
        root.put("StockTrades", listOf(stockTradeWithBadBuyerUuid()));

        assertDoesNotThrow(() -> EconomySavedData.load(root));

        assertEquals(0, AccountManager.getAccounts().size());
        assertEquals(0, AccountManager.getTransactions().size());
        assertEquals(0, MarketManager.getOrders().size());
        assertEquals(0, CompanyManager.getCompanies().size());
        assertEquals(0, StockMarketManager.getStocks().size());
        assertEquals(0, StockMarketManager.getOrders().size());
        assertEquals(0, StockMarketManager.getStockTradeHistory().size());
    }

    @Test
    void loadFallsBackForRecoverableEnumFields() {
        CompoundTag root = new CompoundTag();
        root.put("Companies", listOf(companyWithBadStrategy()));
        root.put("CommodityDefinitions", listOf(commodityWithBadCategory()));

        assertDoesNotThrow(() -> EconomySavedData.load(root));

        assertNotNull(CompanyManager.getCompany(COMPANY_ID));
        assertEquals(CompanyStrategy.STABLE, CompanyManager.getCompany(COMPANY_ID).getStrategy());
        assertNotNull(CommodityRegistry.getCommodity("bad_category_test"));
        assertEquals(CommodityCategory.MISCELLANEOUS,
                CommodityRegistry.getCommodity("bad_category_test").getCategory());
    }

    @Test
    void saveWritesCurrentDataVersion() {
        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        assertEquals(12, saved.getInt("DataVersion"));
    }

    @Test
    void saveAndLoadPreservesDetailedTransactionRecordFields() {
        AccountManager.addTransactionRecord(new TransactionRecord(
                PLAYER_ID,
                OTHER_PLAYER_ID,
                250,
                TransactionType.STOCK_BUY,
                PLAYER_ID,
                "ABC",
                5));

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(1, AccountManager.getTransactions().size());
        TransactionRecord record = AccountManager.getTransactions().get(0);
        assertEquals(TransactionType.STOCK_BUY, record.getType());
        assertEquals(PLAYER_ID, record.getPlayerId());
        assertEquals("ABC", record.getObjectName());
        assertEquals(5, record.getQuantity());
        assertEquals(250, record.getAmount());
    }

    @Test
    void saveAndLoadPreservesStockPriceSnapshots() {
        CompanyManager.registerDirect(new finance.company.Company(
                COMPANY_ID,
                "Snapshot Company",
                CompanyType.RAW_MATERIALS,
                1000,
                PLAYER_ID));
        CompanyManager.getCompany(COMPANY_ID).setPublic(true);
        Stock stock = new Stock("SNAP", "Snapshot Company", COMPANY_ID,
                1000, 1000, 0, 10, 10);
        stock.addSnapshotDirect(new StockPriceEngine.PriceSnapshot(
                java.time.LocalDateTime.ofEpochSecond(10, 0, java.time.ZoneOffset.UTC),
                12,
                3));
        StockMarketManager.putStockDirect(stock);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        Stock loaded = StockMarketManager.getStock("SNAP");
        assertNotNull(loaded);
        assertEquals(1, loaded.getSnapshots().size());
        assertEquals(12, loaded.getSnapshots().get(0).getPrice());
        assertEquals(3, loaded.getSnapshots().get(0).getVolume());
    }

    @Test
    void saveAndLoadPreservesDividendPolicyAndCompanyDividendFields() {
        finance.company.Company company = new finance.company.Company(
                COMPANY_ID,
                "Dividend Fields Company",
                CompanyType.RAW_MATERIALS,
                2000,
                PLAYER_ID);
        company.addDistributableProfit(700);
        company.addDividendRecord(12, 300, 3);
        CompanyManager.registerDirect(company);
        CompanyManager.setDividendPolicy(0.25, 9);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(0.25, CompanyManager.getDividendRatio(), 0.0001);
        assertEquals(9, CompanyManager.getDividendCycleDays());
        finance.company.Company loaded = CompanyManager.getCompany(COMPANY_ID);
        assertNotNull(loaded);
        assertEquals(700, loaded.getDistributableProfit());
        assertEquals(1, loaded.getDividendHistory().size());
        assertEquals(300, loaded.getDividendHistory().get(0).totalAmount());
        assertEquals(3, loaded.getDividendHistory().get(0).perShare());
    }

    @Test
    void saveAndLoadPreservesAssetSnapshots() {
        AssetSnapshotManager.putSnapshotDirect(PLAYER_ID,
                new AssetSnapshotManager.AssetSnapshot(4, 12_345));

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(0, AssetSnapshotManager.getTodayProfit(PLAYER_ID, 12_345, 4));
        assertEquals(655, AssetSnapshotManager.getTodayProfit(PLAYER_ID, 13_000, 4));
    }

    @Test
    void saveAndLoadPreservesPriceAlerts() {
        PriceAlertManager.addAlertDirect(new finance.alert.PriceAlert(
                PLAYER_ID,
                PriceAlertType.STOCK,
                "ABC",
                PriceAlertDirection.BELOW,
                88));

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(1, PriceAlertManager.getAlertsForPlayer(PLAYER_ID).size());
        assertEquals("ABC", PriceAlertManager.getAlertsForPlayer(PLAYER_ID).get(0).getTargetId());
        assertEquals(88, PriceAlertManager.getAlertsForPlayer(PLAYER_ID).get(0).getTargetPrice());
    }

    @Test
    void saveAndLoadPreservesConditionalStockOrders() {
        ConditionalStockOrderManager.addOrderDirect(new ConditionalStockOrder(
                PLAYER_ID,
                "ABC",
                ConditionalStockOrderType.TAKE_PROFIT,
                120,
                4));

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(1, ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).size());
        ConditionalStockOrder loaded = ConditionalStockOrderManager.getOrdersForPlayer(PLAYER_ID).get(0);
        assertEquals("ABC", loaded.getSymbol());
        assertEquals(ConditionalStockOrderType.TAKE_PROFIT, loaded.getType());
        assertEquals(120, loaded.getTriggerPrice());
        assertEquals(4, loaded.getQuantity());
    }

    @Test
    void saveAndLoadPreservesCompanyFinancingProjects() {
        CompanyFinancingProject project = new CompanyFinancingProject(
                COMPANY_ID,
                "ABC",
                100,
                10,
                800,
                12);
        project.addSubscription(PLAYER_ID, 25);
        CompanyFinancingManager.addProjectDirect(project);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(1, CompanyFinancingManager.getProjects().size());
        CompanyFinancingProject loaded = CompanyFinancingManager.getProjects().get(0);
        assertEquals(COMPANY_ID, loaded.getCompanyId());
        assertEquals("ABC", loaded.getSymbol());
        assertEquals(100, loaded.getIssueQuantity());
        assertEquals(10, loaded.getIssuePrice());
        assertEquals(800, loaded.getFundingTarget());
        assertEquals(25, loaded.getSubscriptions().get(PLAYER_ID));
    }

    @Test
    void saveAndLoadPreservesCompanyFinancialReports() {
        finance.company.Company company = new finance.company.Company(
                COMPANY_ID,
                "Report Save Company",
                CompanyType.RAW_MATERIALS,
                1_000,
                PLAYER_ID);
        company.addFinancialReportDirect(new CompanyFinancialReport(
                8,
                300,
                120,
                180,
                1_500,
                20,
                1_000,
                100,
                30,
                "盈利180，资产增加100，现金余额1000",
                java.time.LocalDateTime.ofEpochSecond(10, 0, java.time.ZoneOffset.UTC)));
        CompanyManager.registerDirect(company);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        finance.company.Company loaded = CompanyManager.getCompany(COMPANY_ID);
        assertNotNull(loaded);
        assertEquals(1, loaded.getFinancialReports().size());
        CompanyFinancialReport report = loaded.getLatestFinancialReport();
        assertEquals(300, report.revenue());
        assertEquals(120, report.expenses());
        assertEquals(180, report.netProfit());
        assertEquals(1_500, report.assets());
        assertEquals(20, report.liabilities());
        assertEquals(30, report.profitChange());
    }

    @Test
    void saveAndLoadPreservesCompanyBankruptcyRiskState() {
        finance.company.Company company = new finance.company.Company(
                COMPANY_ID,
                "Risk Company",
                CompanyType.RAW_MATERIALS,
                100,
                PLAYER_ID);
        company.setBankruptcyRisk(true, 6);
        CompanyManager.registerDirect(company);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        finance.company.Company loaded = CompanyManager.getCompany(COMPANY_ID);
        assertNotNull(loaded);
        assertEquals(true, loaded.isBankruptcyRisk());
        assertEquals(6, loaded.getBankruptcyRiskStartDay());
    }

    @Test
    void saveAndLoadPreservesCompanyProposalsAndVotes() {
        CompanyProposal proposal = new CompanyProposal(
                COMPANY_ID,
                PLAYER_ID,
                CompanyProposalType.DIVIDEND,
                "调整分红",
                "",
                35,
                0,
                0,
                1,
                4,
                0.6);
        proposal.addVote(PLAYER_ID, true, 12);
        proposal.finish(CompanyProposalStatus.PASSED, "已通过");
        CompanyProposalManager.addProposalDirect(proposal);

        CompoundTag saved = new EconomySavedData().save(new CompoundTag());

        EconomySavedData.load(saved);

        assertEquals(1, CompanyProposalManager.getProposalsForCompany(COMPANY_ID).size());
        CompanyProposal loaded = CompanyProposalManager.getProposalsForCompany(COMPANY_ID).get(0);
        assertEquals(CompanyProposalType.DIVIDEND, loaded.getType());
        assertEquals(35, loaded.getValue1());
        assertEquals(12, loaded.getYesVotes());
        assertEquals(CompanyProposalStatus.PASSED, loaded.getStatus());
        assertEquals("已通过", loaded.getResultSummary());
    }

    private static ListTag listOf(CompoundTag tag) {
        ListTag list = new ListTag();
        list.add(tag);
        return list;
    }

    private static CompoundTag badUuidAccount() {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerUUID", "not-a-uuid");
        tag.putLong("Balance", 500);
        return tag;
    }

    private static CompoundTag transactionWithBadType() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("From", PLAYER_ID);
        tag.putUUID("To", OTHER_PLAYER_ID);
        tag.putLong("Amount", 100);
        tag.putString("Type", "RENAMED_TRANSACTION_TYPE");
        tag.putLong("Timestamp", 0);
        return tag;
    }

    private static CompoundTag orderWithBadType() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OrderId", UUID.randomUUID());
        tag.putUUID("PlayerUUID", PLAYER_ID);
        tag.putString("CommodityId", "iron");
        tag.putString("Type", "RENAMED_ORDER_TYPE");
        tag.putLong("Price", 10);
        tag.putInt("Quantity", 1);
        tag.putLong("Timestamp", 0);
        return tag;
    }

    private static CompoundTag companyWithBadType() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CompanyUUID", COMPANY_ID);
        tag.putString("Name", "Bad Type Company");
        tag.putString("Type", "RENAMED_COMPANY_TYPE");
        tag.putLong("Cash", 1000);
        return tag;
    }

    private static CompoundTag companyWithBadStrategy() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CompanyUUID", COMPANY_ID);
        tag.putString("Name", "Bad Strategy Company");
        tag.putString("Type", CompanyType.RAW_MATERIALS.name());
        tag.putLong("Cash", 1000);
        tag.putString("Strategy", "RENAMED_STRATEGY");
        tag.putInt("ProductionLevel", 1);
        tag.putInt("StorageLevel", 1);
        tag.putInt("ManagementLevel", 1);
        tag.putDouble("AutoSellRatio", 0.5);
        return tag;
    }

    private static CompoundTag commodityWithBadCategory() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", "bad_category_test");
        tag.putString("DisplayName", "Bad Category Test");
        tag.putString("Category", "RENAMED_CATEGORY");
        tag.putLong("BasePrice", 10);
        return tag;
    }

    private static CompoundTag stockWithBadCompanyUuid() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Symbol", "BAD");
        tag.putString("Name", "Bad Stock");
        tag.putString("CompanyUUID", "not-a-uuid");
        tag.putLong("TotalShares", 1000);
        tag.putLong("FloatShares", 1000);
        tag.putLong("OwnerShares", 0);
        tag.putLong("LastPrice", 10);
        tag.putLong("FairValue", 10);
        return tag;
    }

    private static CompoundTag stockOrderWithBadType() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("OrderId", UUID.randomUUID());
        tag.putUUID("PlayerId", PLAYER_ID);
        tag.putString("Symbol", "BAD");
        tag.putString("Type", "RENAMED_STOCK_ORDER_TYPE");
        tag.putLong("Price", 10);
        tag.putInt("Quantity", 1);
        tag.putLong("Timestamp", 0);
        return tag;
    }

    private static CompoundTag stockTradeWithBadBuyerUuid() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Buyer", "not-a-uuid");
        tag.putUUID("Seller", OTHER_PLAYER_ID);
        tag.putString("Symbol", "BAD");
        tag.putLong("Price", 10);
        tag.putInt("Quantity", 1);
        tag.putLong("Timestamp", 0);
        return tag;
    }
}
