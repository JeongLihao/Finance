package finance.gui;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.AssetSnapshotManager;
import finance.account.TransactionRecord;
import finance.alert.PriceAlert;
import finance.alert.PriceAlertManager;
import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;
import finance.company.CompanyFinancialReport;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.commodity.CommodityInventoryManager;
import finance.cycle.EconomyCycleService;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.metrics.EconomyMetricsService;
import finance.market.Order;
import finance.stock.Stock;
import finance.stock.ConditionalStockOrder;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockPortfolioManager;
import finance.commodity.Commodity;
import finance.util.InventoryUtil;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

import java.util.*;

/**
 * 金融 GUI 打开器 —— 收集全部数据并打开统一的 FinanceMenu。
 */
public class FinanceGuiOpener {

    public static void open(ServerPlayer player) {
        open(player, FinanceScreenMode.ADVANCED, FinanceTerminalType.LEGACY_FULL_SCREEN, null);
    }

    public static void open(ServerPlayer player, FinanceScreenMode initialMode,
                            FinanceTerminalType sourceType, BlockPos sourcePos) {
        UUID playerId = player.getUUID();
        boolean admin = player.hasPermissions(2);
        UUID boardroomCompanyId = null;
        if (sourceType == FinanceTerminalType.BOARDROOM_TABLE && sourcePos != null
                && player.serverLevel().isLoaded(sourcePos)
                && player.serverLevel().getBlockEntity(sourcePos) instanceof finance.block.entity.BoardroomTableBlockEntity table) {
            boardroomCompanyId = table.companyId();
        }

        // 1. 市场行情
        List<FinanceMenu.MarketRow> marketData = new ArrayList<>();
        for (MarketPrice price : NpcMarketMaker.getAllMarketPrices().values()) {
            int stock = CommodityInventoryManager.getCommodityAmount(
                    NpcMarketMaker.NPC_UUID, price.getCommodityId());
            marketData.add(new FinanceMenu.MarketRow(
                    price.getCommodityId(), price.getMidPrice(),
                    price.getBidPrice(), price.getAskPrice(),
                    price.getDayChange(), price.getDayVolume(), stock,
                    price.getDayHigh(), price.getDayLow(), commodityHistory(price)));
        }

        // 2. 全市场订单（标记当前玩家自己的订单）
        List<FinanceMenu.OrderRow> orderRows = new ArrayList<>();
        List<Order> orders = MarketManager.getOrders();
        for (Order order : orders) {
            orderRows.add(new FinanceMenu.OrderRow(
                    order.getOrderId(), order.getCommodityId(), order.getType().name(),
                    order.getPrice(), order.getQuantity(), order.getPlayerId().equals(playerId)));
        }

        // 3. 账户
        Account account = AccountManager.getAccount(playerId);
        long balance = account.getBalance();
        long frozenBalance = account.getFrozenBalance();

        // 4. 库存
        Map<String, Integer> inventory = new LinkedHashMap<>(
                CommodityInventoryManager.getInventory(playerId).getAllCommodities());

        // 5. 公司
        List<FinanceMenu.CompanyInfo> companyRows = new ArrayList<>();

        final FinanceMenu.CompanyInfo companyInfo;
        Company company = CompanyManager.getCompanyByOwner(playerId);
        if (boardroomCompanyId != null) company = CompanyManager.getCompany(boardroomCompanyId);
        if (company == null && initialMode == finance.gameplay.FinanceScreenMode.COMPANY) {
            company = CompanyManager.getCompanies().stream().filter(candidate ->
                    finance.gameplay.company.CompanyMembershipService.hasPermission(candidate.getCompanyId(), playerId,
                            finance.gameplay.company.CompanyPermission.VIEW_COMPANY)).findFirst().orElse(null);
        }
        if (company != null) {
            boolean privateCompanyView = company.getOwnerId()!=null&&company.getOwnerId().equals(playerId)
                    || finance.gameplay.company.CompanyMembershipService.hasPermission(company.getCompanyId(),playerId,
                    finance.gameplay.company.CompanyPermission.VIEW_COMPANY);
            companyInfo = toCompanyInfo(company,privateCompanyView);
        } else {
            companyInfo = null;
        }

        List<FinanceMenu.StockRow> stockRows = new ArrayList<>();
        for (Stock stock : StockMarketManager.getListedStocks()) {
            Company stockCompany = CompanyManager.getCompany(stock.getCompanyId());
            DividendSummary dividendSummary = dividendSummary(stockCompany, stock);
            stockRows.add(new FinanceMenu.StockRow(
                    stock.getSymbol(),
                    stock.getName(),
                    stock.getLastPrice(),
                    stock.getDayChange(),
                    stock.getDayVolume(),
                    stock.getAvailableShares(),
                    stock.getFairValue(),
                    stock.getDayHigh(),
                    stock.getDayLow(),
                    stockHistory(stock),
                    dividendSummary.expectedPerShare(),
                    dividendSummary.lastPerShare(),
                    dividendSummary.lastTotal()));
        }

        List<FinanceMenu.StockHoldingRow> stockHoldingRows = new ArrayList<>();
        for (Map.Entry<String, StockHolding> entry :
                StockPortfolioManager.getPortfolio(playerId).entrySet()) {
            stockHoldingRows.add(new FinanceMenu.StockHoldingRow(
                    entry.getKey(),
                    entry.getValue().getQuantity(),
                    entry.getValue().getAverageCost()));
        }

        List<FinanceMenu.StockOrderRow> stockOrderRows = new ArrayList<>();
        for (StockOrder order : StockMarketManager.getOrders()) {
            stockOrderRows.add(new FinanceMenu.StockOrderRow(
                    order.getOrderId(),
                    order.getSymbol(),
                    order.getType().name(),
                    order.getPrice(),
                    order.getQuantity(),
                    order.getPlayerId().equals(playerId)));
        }

        AssetBundle assetBundle = buildAssetBundle(player, account, inventory);
        List<FinanceMenu.PriceAlertRow> priceAlertRows = new ArrayList<>();
        for (PriceAlert alert : PriceAlertManager.getAlertsForPlayer(playerId)) {
            priceAlertRows.add(new FinanceMenu.PriceAlertRow(
                    alert.getAlertId(),
                    alert.getType().name(),
                    alert.getTargetId(),
                    alert.getDirection().name(),
                    alert.getTargetPrice()));
        }
        List<FinanceMenu.ConditionalStockOrderRow> conditionalStockOrderRows = new ArrayList<>();
        for (ConditionalStockOrder order : ConditionalStockOrderManager.getOrdersForPlayer(playerId)) {
            conditionalStockOrderRows.add(new FinanceMenu.ConditionalStockOrderRow(
                    order.getOrderId(),
                    order.getSymbol(),
                    order.getType().name(),
                    order.getTriggerPrice(),
                    order.getQuantity()));
        }
        List<FinanceMenu.CompanyFinancingRow> companyFinancingRows = new ArrayList<>();
        for (CompanyFinancingProject project : CompanyFinancingManager.getProjects()) {
            Company financingCompany = CompanyManager.getCompany(project.getCompanyId());
            if (financingCompany == null || !financingCompany.isPublic()) {
                continue;
            }
            companyFinancingRows.add(new FinanceMenu.CompanyFinancingRow(
                    project.getProjectId(),
                    project.getCompanyId(),
                    financingCompany.getName(),
                    project.getSymbol(),
                    project.getIssueQuantity(),
                    project.getIssuePrice(),
                    project.getFundingTarget(),
                    project.getRaisedAmount(),
                    project.getSubscribedShares(),
                    project.getSubscriptions().getOrDefault(playerId, 0L),
                    project.getDeadlineMcDay()));
        }
        List<FinanceMenu.CompanyProposalRow> companyProposalRows = new ArrayList<>();
        for (CompanyProposal proposal : CompanyProposalManager.getProposals()) {
            if(boardroomCompanyId!=null&&!boardroomCompanyId.equals(proposal.getCompanyId()))continue;
            companyProposalRows.add(new FinanceMenu.CompanyProposalRow(
                    proposal.getProposalId(),
                    proposal.getCompanyId(),
                    proposal.getType().name(),
                    proposal.getTitle(),
                    proposal.getTextValue(),
                    proposal.getValue1(),
                    proposal.getValue2(),
                    proposal.getValue3(),
                    proposal.getStartMcDay(),
                    proposal.getEndMcDay(),
                    proposal.getPassRatio(),
                    proposal.getYesVotes(),
                    proposal.getNoVotes(),
                    proposal.getVotes().containsKey(playerId),
                    proposal.getStatus().name(),
                    proposal.getResultSummary(),
                    finance.governance.CorporateRestructuringService.canExecute(playerId, proposal)));
        }

        List<FinanceMenu.TransactionRow> transactionRows = new ArrayList<>();
        List<TransactionRecord> transactions = AccountManager.getTransactions();
        int added = 0;
        for (int i = transactions.size() - 1; i >= 0 && added < 100; i--) {
            TransactionRecord record = transactions.get(i);
            UUID recordPlayer = record.getPlayerId();
            if (!admin && (recordPlayer == null || !recordPlayer.equals(playerId))) {
                continue;
            }
            transactionRows.add(new FinanceMenu.TransactionRow(
                    record.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC),
                    recordPlayer != null ? recordPlayer : new UUID(0L, 0L),
                    record.getType().name(),
                    record.getAmount(),
                    record.getQuantity(),
                    record.getObjectName()));
            added++;
        }

        // 6. MC 物品栏数据（商品ID → 对应物品在 MC 物品栏中的数量）
        Map<String, Integer> mcInv = new LinkedHashMap<>();
        for (Commodity commodity : finance.commodity.CommodityRegistry.getAllCommodities()) {
            String itemId = commodity.getItemId();
            if (itemId != null && !itemId.isEmpty()) {
                int count = InventoryUtil.countItemInInventory(player, itemId);
                if (count > 0) {
                    mcInv.put(commodity.getId(), count);
                }
            }
        }

        FinanceMenu.EconomyDashboardRow dashboard = admin ? buildDashboard()
                : new FinanceMenu.EconomyDashboardRow(0,0,0,0,0,0,0,0,0,0,"",List.of());
        long warehouseUsed = finance.warehouse.WarehouseManager.usedCapacity(playerId);
        long warehouseCapacity = finance.warehouse.WarehouseManager.totalCapacity(playerId);
        boolean warehouseOverCapacity = warehouseUsed > warehouseCapacity;

        // 打开菜单
        NetworkHooks.openScreen(player,
                new FinanceProvider(marketData, orderRows, balance, frozenBalance, inventory, companyInfo, companyRows, stockRows, stockHoldingRows, stockOrderRows, transactionRows, assetBundle.summary(), assetBundle.rows(), priceAlertRows, conditionalStockOrderRows, companyFinancingRows, companyProposalRows, dashboard, CompanyManager.getDividendRatio(), CompanyManager.getDividendCycleDays(), mcInv, initialMode, sourceType, player.serverLevel().dimension().location().toString(), sourcePos, warehouseUsed, warehouseCapacity, warehouseOverCapacity),
                buffer -> FinanceMenu.writeAll(buffer, marketData, orderRows, balance, frozenBalance, inventory, companyInfo, companyRows, stockRows, stockHoldingRows, stockOrderRows, transactionRows, assetBundle.summary(), assetBundle.rows(), priceAlertRows, conditionalStockOrderRows, companyFinancingRows, companyProposalRows, dashboard, CompanyManager.getDividendRatio(), CompanyManager.getDividendCycleDays(), mcInv, initialMode, sourceType, player.serverLevel().dimension().location().toString(), sourcePos, warehouseUsed, warehouseCapacity, warehouseOverCapacity));
    }

    private static FinanceMenu.CompanyInfo toCompanyInfo(Company company) { return toCompanyInfo(company,true); }
    private static FinanceMenu.CompanyInfo toCompanyInfo(Company company,boolean privateView) {
        boolean publicFinancials = company.isPublic();
        CompanyFinancialReport report = company.getLatestFinancialReport();
        List<finance.gameplay.company.capital.WorldCapitalProject> capitalProjects =
                finance.gameplay.company.capital.CapitalProjectManager.forCompany(company.getCompanyId()).stream()
                        .filter(project -> !project.status().terminal())
                        .toList();
        long capitalCommitted = 0;
        for (finance.gameplay.company.capital.WorldCapitalProject project : capitalProjects) {
            try { capitalCommitted = Math.addExact(capitalCommitted, project.fundedAmount()); }
            catch (ArithmeticException overflow) { capitalCommitted = Long.MAX_VALUE; break; }
        }
        String capitalSummary = capitalProjects.isEmpty() ? ""
                : capitalProjects.get(0).type().name() + " " + capitalProjects.get(0).status().name()
                + " " + capitalProjects.get(0).fundedAmount() + "/" + capitalProjects.get(0).budget();
        return new FinanceMenu.CompanyInfo(
                company.getCompanyId(),
                company.getName(), company.getType().getDisplayName(),
                privateView?company.getCash():(report!=null?report.cashBalance():0),
                publicFinancials ? company.inventoryValue() : 0,
                publicFinancials ? company.getEstimatedValue() : 0,
                privateView?new LinkedHashMap<>(company.getInventory()):Map.of(),
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
                capitalProjects.size(), capitalCommitted, capitalSummary);
    }

    private static List<Long> commodityHistory(MarketPrice price) {
        List<Long> history = new ArrayList<>();
        List<MarketPrice.PriceSnapshot> snapshots = price.getSnapshots();
        int start = Math.max(0, snapshots.size() - 24);
        for (int i = start; i < snapshots.size(); i++) {
            history.add(snapshots.get(i).getPrice());
        }
        if (history.isEmpty()) {
            history.add(price.getMidPrice());
        }
        return history;
    }

    private static List<Long> stockHistory(Stock stock) {
        List<Long> history = new ArrayList<>();
        List<finance.stock.StockPriceEngine.PriceSnapshot> snapshots = stock.getSnapshots();
        int start = Math.max(0, snapshots.size() - 24);
        for (int i = start; i < snapshots.size(); i++) {
            history.add(snapshots.get(i).getPrice());
        }
        if (history.isEmpty()) {
            history.add(stock.getLastPrice());
        }
        return history;
    }

    private static DividendSummary dividendSummary(Company company, Stock stock) {
        if (company == null || stock == null || stock.getTotalShares() <= 0) {
            return new DividendSummary(0, 0, 0);
        }
        long expectedTotal = Math.min(company.getCash(),
                Math.round(company.getDistributableProfit() * CompanyManager.getDividendRatio()));
        long expectedPerShare = expectedTotal > 0 ? expectedTotal / stock.getTotalShares() : 0;
        List<Company.DividendRecord> history = company.getDividendHistory();
        if (history.isEmpty()) {
            return new DividendSummary(expectedPerShare, 0, 0);
        }
        Company.DividendRecord last = history.get(history.size() - 1);
        return new DividendSummary(expectedPerShare, last.perShare(), last.totalAmount());
    }

    private record DividendSummary(long expectedPerShare, long lastPerShare, long lastTotal) {
    }

    private static AssetBundle buildAssetBundle(ServerPlayer player, Account account, Map<String, Integer> inventory) {
        List<FinanceMenu.AssetRow> rows = new ArrayList<>();
        long cash = account.getBalance();
        long frozenCash = account.getFrozenBalance();
        long commodityValue = 0;
        long stockValue = 0;

        rows.add(new FinanceMenu.AssetRow("现金", "可用现金", 1, cash, 0, cash, 1, 0));
        if (frozenCash > 0) {
            rows.add(new FinanceMenu.AssetRow("现金", "冻结资金", 1, frozenCash, 0, frozenCash, 1, 0));
        }

        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            MarketPrice price = NpcMarketMaker.getMarketPrice(entry.getKey());
            Commodity commodity = finance.commodity.CommodityRegistry.getCommodity(entry.getKey());
            if (price == null || entry.getValue() <= 0) {
                continue;
            }
            long value = safeMultiply(price.getMidPrice(), entry.getValue());
            commodityValue = finance.util.MathUtil.saturatedAddNonNegative(commodityValue, value);
            String name = commodity != null ? commodity.getDisplayName() : entry.getKey();
            rows.add(new FinanceMenu.AssetRow("商品", name, entry.getValue(), value, 0,
                    value, price.getMidPrice(), 0));
        }

        for (Map.Entry<String, StockHolding> entry :
                StockPortfolioManager.getPortfolio(player.getUUID()).entrySet()) {
            Stock stock = StockMarketManager.getStock(entry.getKey());
            StockHolding holding = entry.getValue();
            if (stock == null || holding.getQuantity() <= 0) {
                continue;
            }
            long value = safeMultiply(stock.getLastPrice(), holding.getQuantity());
            long cost = safeMultiply(holding.getAverageCost(), holding.getQuantity());
            long profit = value - cost;
            stockValue = finance.util.MathUtil.saturatedAddNonNegative(stockValue, value);
            rows.add(new FinanceMenu.AssetRow("股票", stock.getSymbol(), holding.getQuantity(),
                    value, 0, cost, stock.getLastPrice(), profit));
        }

        long mcDay = EconomyCycleService.currentMcDay(player.getServer());
        long fixedIncomeValue = 0;
        for (finance.debt.CorporateBond bond : finance.debt.CorporateBondManager.bonds().values()) {
            long quantity = bond.holdings().getOrDefault(player.getUUID(), 0L);
            if (quantity <= 0) continue;
            finance.debt.BondValuation valuation = finance.debt.FixedIncomeValuationService.value(bond, player.getUUID(), mcDay);
            fixedIncomeValue = finance.util.MathUtil.saturatedAddNonNegative(fixedIncomeValue, valuation.marketValue());
            long cost = finance.bondmarket.BondPortfolioManager.position(bond.id(), player.getUUID()).totalCost();
            rows.add(new FinanceMenu.AssetRow("债券", bond.code(), quantity, valuation.marketValue(), 0,
                    cost, valuation.marketPricePerUnit(), valuation.unrealizedProfit()));
        }
        for (finance.fixedincome.CentralBankBill bill : finance.fixedincome.CentralBankBillManager.bills().values()) {
            if (bill.status() != finance.fixedincome.CentralBankBillStatus.ACTIVE) continue;
            long principal = bill.principalByPlayer().getOrDefault(player.getUUID(), 0L);
            if (principal <= 0) continue;
            long expected = finance.fixedincome.CentralBankBillManager.expectedMaturityValue(bill, player.getUUID());
            fixedIncomeValue = finance.util.MathUtil.saturatedAddNonNegative(fixedIncomeValue, principal);
            rows.add(new FinanceMenu.AssetRow("票据", bill.termDays() + "日央行票据", principal, principal, 0,
                    principal, 1, Math.max(0, expected - principal)));
        }

        finance.futures.MarginAccount marginAccount = finance.futures.MarginManager.accounts().get(player.getUUID());
        long futuresEquity = marginAccount == null ? 0 : Math.max(0, finance.futures.FuturesRiskService.equity(player.getUUID()));
        if (marginAccount != null && (marginAccount.cashBalance() > 0
                || finance.futures.MarginManager.positions().keySet().stream().anyMatch(k -> k.ownerId().equals(player.getUUID())))) {
            rows.add(new FinanceMenu.AssetRow("衍生品", "期货保证金权益", 1, futuresEquity, 0,
                    marginAccount.cashBalance(), 1, futuresEquity - marginAccount.cashBalance()));
        }

        long fundValue = 0;
        Map<String, finance.fund.PlayerFundPosition> fundPositions = finance.fund.FundManager.positions().get(player.getUUID());
        if (fundPositions != null) {
            for (Map.Entry<String, finance.fund.PlayerFundPosition> entry : fundPositions.entrySet()) {
                finance.fund.FundState state = finance.fund.FundManager.states().get(entry.getKey());
                finance.fund.FundDefinition definition = finance.fund.FundManager.definitions().get(entry.getKey());
                if (state == null || definition == null || entry.getValue().shareUnits() <= 0) continue;
                long value = finance.fund.FundMath.ratioFloor(entry.getValue().shareUnits(), state.currentNav(), finance.fund.FundManager.SHARE_SCALE);
                fundValue = finance.util.MathUtil.saturatedAddNonNegative(fundValue, value);
                rows.add(new FinanceMenu.AssetRow("基金", definition.displayName(), entry.getValue().shareUnits(), value, 0,
                        entry.getValue().totalCost(), state.currentNav(), value - entry.getValue().totalCost()));
            }
        }

        long totalAsset = finance.util.MathUtil.saturatedAddNonNegative(cash, frozenCash);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, commodityValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, stockValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, fixedIncomeValue);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, futuresEquity);
        totalAsset = finance.util.MathUtil.saturatedAddNonNegative(totalAsset, fundValue);
        long todayProfit = AssetSnapshotManager.getTodayProfit(player.getUUID(), totalAsset, mcDay);
        List<FinanceMenu.AssetRow> withPercent = new ArrayList<>();
        for (FinanceMenu.AssetRow row : rows) {
            double percent = totalAsset > 0 ? (double) row.value() / totalAsset * 100 : 0;
            withPercent.add(new FinanceMenu.AssetRow(row.category(), row.name(), row.quantity(),
                    row.value(), percent, row.cost(), row.currentPrice(), row.floatingProfit()));
        }

        return new AssetBundle(
                new FinanceMenu.AssetSummary(cash, frozenCash, commodityValue, stockValue, totalAsset, todayProfit),
                withPercent);
    }

    private static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private record AssetBundle(FinanceMenu.AssetSummary summary, List<FinanceMenu.AssetRow> rows) {
    }

    private static FinanceMenu.EconomyDashboardRow buildDashboard() {
        EconomyMetricsService.CurrentMetrics current = EconomyMetricsService.getCurrentMetrics();
        List<FinanceMenu.EconomyTrendRow> trends = EconomyMetricsService.getDailySnapshots().stream()
                .map(snapshot -> new FinanceMenu.EconomyTrendRow(
                        snapshot.mcDay(), snapshot.commodityVolume(), snapshot.stockVolume(),
                        snapshot.priceIndex()))
                .toList();
        return new FinanceMenu.EconomyDashboardRow(
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


    private record FinanceProvider(List<FinanceMenu.MarketRow> marketData,
                                    List<FinanceMenu.OrderRow> orderRows,
                                    long balance, long frozenBalance,
                                    Map<String, Integer> inventory,
                                    FinanceMenu.CompanyInfo companyInfo,
                                    List<FinanceMenu.CompanyInfo> allCompanies,
                                    List<FinanceMenu.StockRow> stocks,
                                    List<FinanceMenu.StockHoldingRow> stockHoldings,
                                    List<FinanceMenu.StockOrderRow> stockOrders,
                                    List<FinanceMenu.TransactionRow> transactions,
                                    FinanceMenu.AssetSummary assetSummary,
                                    List<FinanceMenu.AssetRow> assetRows,
                                    List<FinanceMenu.PriceAlertRow> priceAlerts,
                                    List<FinanceMenu.ConditionalStockOrderRow> conditionalStockOrders,
                                    List<FinanceMenu.CompanyFinancingRow> companyFinancingRows,
                                    List<FinanceMenu.CompanyProposalRow> companyProposalRows,
                                    FinanceMenu.EconomyDashboardRow dashboard,
                                    double dividendRatio,
                                    int dividendCycleDays,
                                    Map<String, Integer> mcInventory,
                                    FinanceScreenMode initialMode,
                                    FinanceTerminalType sourceType,
                                    String sourceDimension,
                                    BlockPos sourcePos,
                                    long warehouseUsed,
                                    long warehouseCapacity,
                                    boolean warehouseOverCapacity)
            implements net.minecraft.world.MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.literal("金融中心");
        }

        @Override
        public FinanceMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inv,
                                       net.minecraft.world.entity.player.Player player) {
            return new FinanceMenu(containerId, marketData, orderRows, balance, frozenBalance, inventory, companyInfo, allCompanies, stocks, stockHoldings, stockOrders, transactions, assetSummary, assetRows, priceAlerts, conditionalStockOrders, companyFinancingRows, companyProposalRows, dashboard, dividendRatio, dividendCycleDays, mcInventory, initialMode, sourceType, sourceDimension, sourcePos, warehouseUsed, warehouseCapacity, warehouseOverCapacity);
        }
    }
}
