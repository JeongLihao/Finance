package finance.gui;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.alert.PriceAlert;
import finance.alert.PriceAlertManager;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.stock.ConditionalStockOrder;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.Stock;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockPortfolioManager;
import finance.util.InventoryUtil;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * 金融 GUI 打开器 —— 收集全部数据并打开统一的 FinanceMenu。
 */
public class FinanceGuiOpener {
    /** 当前参与率（已投票数 / 快照表决股数），基点。 */
    static int participationBps(finance.company.CompanyProposal proposal) {
        long snapshot = proposal.getVotingSharesSnapshot();
        if (snapshot <= 0) return 0;
        return (int) Math.min(10_000, (proposal.getYesVotes() + proposal.getNoVotes()) * 10_000 / snapshot);
    }

    public static void open(ServerPlayer player) {
        open(player, FinanceScreenMode.ADVANCED, FinanceTerminalType.LEGACY_FULL_SCREEN, null);
    }

    public static void open(
            ServerPlayer player, FinanceScreenMode initialMode, FinanceTerminalType sourceType, BlockPos sourcePos) {
        long starter = finance.simple.SimpleCenterService.grantStarterIfNeeded(player);
        java.util.List<String> summary = finance.simple.SimpleCenterService.summarySinceLastOpen(player);
        FinanceProvider provider = collect(player, initialMode, sourceType, sourcePos);
        NetworkHooks.openScreen(player, provider, provider::write);
        finance.network.SimpleStatusPacket.send(player, summary);
        if (starter > 0) {
            finance.network.GuiFeedbackPacket.send(
                    player, "欢迎！送你 " + finance.util.MoneyFormat.pounds(starter) + " 启动资金，先去“买卖”页看看。", starter);
        }
    }

    /** 金融中心开着时，用最新数据替换服务端菜单并推送给客户端；没开着则什么都不做。 */
    public static void refresh(ServerPlayer player) {
        if (!(player.containerMenu instanceof FinanceMenu menu) || !menu.stillValid(player)) return;
        FinanceProvider provider = collect(player, menu.getInitialMode(), menu.getSourceType(), menu.getSourcePos());
        menu.replaceData(provider.createMenu(menu.containerId, player.getInventory(), player));
        finance.network.FinancePacketHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new finance.network.FinanceMenuRefreshPacket(menu.containerId, provider::write));
        finance.network.SimpleStatusPacket.send(player);
    }

    private static FinanceProvider collect(
            ServerPlayer player, FinanceScreenMode initialMode, FinanceTerminalType sourceType, BlockPos sourcePos) {
        UUID playerId = player.getUUID();
        boolean admin = player.hasPermissions(2);
        UUID boardroomCompanyId = null;
        if (sourceType == FinanceTerminalType.BOARDROOM_TABLE
                && sourcePos != null
                && player.serverLevel().isLoaded(sourcePos)
                && player.serverLevel().getBlockEntity(sourcePos)
                        instanceof finance.block.entity.BoardroomTableBlockEntity table) {
            boardroomCompanyId = table.companyId();
        }

        // 1. 市场行情
        List<MarketRows.MarketRow> marketData = new ArrayList<>();
        for (MarketPrice price : NpcMarketMaker.getAllMarketPrices().values()) {
            int stock = CommodityInventoryManager.getCommodityAmount(NpcMarketMaker.NPC_UUID, price.getCommodityId());
            marketData.add(new MarketRows.MarketRow(
                    price.getCommodityId(),
                    price.getMidPrice(),
                    price.getBidPrice(),
                    price.getAskPrice(),
                    price.getDayChange(),
                    price.getDayVolume(),
                    stock,
                    price.getDayHigh(),
                    price.getDayLow(),
                    FinanceRowConverters.commodityHistory(price),
                    FinanceRowConverters.commodityHourBars(price)));
        }

        // 2. 全市场订单（标记当前玩家自己的订单）
        List<MarketRows.OrderRow> orderRows = new ArrayList<>();
        List<Order> orders = MarketManager.getOrders();
        for (Order order : orders) {
            orderRows.add(new MarketRows.OrderRow(
                    order.getOrderId(),
                    order.getCommodityId(),
                    order.getType().name(),
                    order.getPrice(),
                    order.getQuantity(),
                    order.getPlayerId().equals(playerId)));
        }

        // 3. 账户
        Account account = AccountManager.getAccount(playerId);
        long balance = account.getBalance();
        long frozenBalance = account.getFrozenBalance();

        // 4. 库存
        Map<String, Integer> inventory = new LinkedHashMap<>(
                CommodityInventoryManager.getInventory(playerId).getAllCommodities());

        // 5. 公司
        List<CompanyRows.CompanyInfo> companyRows = new ArrayList<>();

        final CompanyRows.CompanyInfo companyInfo;
        Company company = CompanyManager.getCompanyByOwner(playerId);
        if (boardroomCompanyId != null) company = CompanyManager.getCompany(boardroomCompanyId);
        if (company == null && initialMode == finance.gameplay.FinanceScreenMode.COMPANY) {
            company = CompanyManager.getCompanies().stream()
                    .filter(candidate -> finance.gameplay.company.CompanyMembershipService.hasPermission(
                            candidate.getCompanyId(),
                            playerId,
                            finance.gameplay.company.CompanyPermission.VIEW_COMPANY))
                    .findFirst()
                    .orElse(null);
        }
        if (company != null) {
            boolean privateCompanyView = company.getOwnerId() != null
                            && company.getOwnerId().equals(playerId)
                    || finance.gameplay.company.CompanyMembershipService.hasPermission(
                            company.getCompanyId(), playerId, finance.gameplay.company.CompanyPermission.VIEW_COMPANY);
            companyInfo = FinanceRowConverters.toCompanyInfo(company, privateCompanyView);
        } else {
            companyInfo = null;
        }

        List<StockRows.StockRow> stockRows = new ArrayList<>();
        for (Stock stock : StockMarketManager.getListedStocks()) {
            Company stockCompany = CompanyManager.getCompany(stock.getCompanyId());
            FinanceRowConverters.DividendSummary dividendSummary =
                    FinanceRowConverters.dividendSummary(stockCompany, stock);
            stockRows.add(new StockRows.StockRow(
                    stock.getSymbol(),
                    stock.getName(),
                    stock.getLastPrice(),
                    stock.getDayChange(),
                    stock.getDayVolume(),
                    stock.getAvailableShares(),
                    stock.getFairValue(),
                    stock.getDayHigh(),
                    stock.getDayLow(),
                    FinanceRowConverters.stockHistory(stock),
                    dividendSummary.expectedPerShare(),
                    dividendSummary.lastPerShare(),
                    dividendSummary.lastTotal(),
                    // Phase 4：做市商状态（Phase 2 约定访问器：market.liquidity.QuoteEngine）
                    FinanceRowConverters.marketMakerBid(stock),
                    FinanceRowConverters.marketMakerAsk(stock),
                    FinanceRowConverters.marketMakerHalted(stock),
                    FinanceRowConverters.marketMakerInventoryLevel(stock),
                    FinanceRowConverters.stockHourBars(stock)));
        }

        List<StockRows.StockHoldingRow> stockHoldingRows = new ArrayList<>();
        for (Map.Entry<String, StockHolding> entry :
                StockPortfolioManager.getPortfolio(playerId).entrySet()) {
            stockHoldingRows.add(new StockRows.StockHoldingRow(
                    entry.getKey(),
                    entry.getValue().getQuantity(),
                    entry.getValue().getAverageCost()));
        }

        List<StockRows.StockOrderRow> stockOrderRows = new ArrayList<>();
        for (StockOrder order : StockMarketManager.getOrders()) {
            stockOrderRows.add(new StockRows.StockOrderRow(
                    order.getOrderId(),
                    order.getSymbol(),
                    order.getType().name(),
                    order.getPrice(),
                    order.getQuantity(),
                    order.getPlayerId().equals(playerId)));
        }

        FinanceAssetBuilder.AssetBundle assetBundle = FinanceAssetBuilder.buildAssetBundle(player, account, inventory);
        List<RecordRows.PriceAlertRow> priceAlertRows = new ArrayList<>();
        for (PriceAlert alert : PriceAlertManager.getAlertsForPlayer(playerId)) {
            priceAlertRows.add(new RecordRows.PriceAlertRow(
                    alert.getAlertId(),
                    alert.getType().name(),
                    alert.getTargetId(),
                    alert.getDirection().name(),
                    alert.getTargetPrice()));
        }
        List<StockRows.ConditionalStockOrderRow> conditionalStockOrderRows = new ArrayList<>();
        for (ConditionalStockOrder order : ConditionalStockOrderManager.getOrdersForPlayer(playerId)) {
            conditionalStockOrderRows.add(new StockRows.ConditionalStockOrderRow(
                    order.getOrderId(),
                    order.getSymbol(),
                    order.getType().name(),
                    order.getTriggerPrice(),
                    order.getQuantity()));
        }
        List<CompanyRows.CompanyFinancingRow> companyFinancingRows = new ArrayList<>();
        for (CompanyFinancingProject project : CompanyFinancingManager.getProjects()) {
            Company financingCompany = CompanyManager.getCompany(project.getCompanyId());
            if (financingCompany == null || !financingCompany.isPublic()) {
                continue;
            }
            companyFinancingRows.add(new CompanyRows.CompanyFinancingRow(
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
        List<CompanyRows.CompanyProposalRow> companyProposalRows = new ArrayList<>();
        for (CompanyProposal proposal : CompanyProposalManager.getProposals()) {
            if (boardroomCompanyId != null && !boardroomCompanyId.equals(proposal.getCompanyId())) continue;
            companyProposalRows.add(new CompanyRows.CompanyProposalRow(
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
                    finance.governance.CorporateRestructuringService.canExecute(playerId, proposal),
                    finance.npcinvestor.NpcGovernanceService.voteSummary(proposal.getProposalId()),
                    participationBps(proposal),
                    (int) Math.round(proposal.getMinParticipationRatio() * 10_000)));
        }

        List<RecordRows.TransactionRow> transactionRows = new ArrayList<>();
        List<TransactionRecord> transactions = AccountManager.getTransactions();
        int added = 0;
        for (int i = transactions.size() - 1; i >= 0 && added < 100; i--) {
            TransactionRecord record = transactions.get(i);
            UUID recordPlayer = record.getPlayerId();
            if (!admin && (recordPlayer == null || !recordPlayer.equals(playerId))) {
                continue;
            }
            transactionRows.add(new RecordRows.TransactionRow(
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

        DashboardRows.EconomyDashboardRow dashboard = admin
                ? FinanceRowConverters.buildDashboard()
                : new DashboardRows.EconomyDashboardRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", List.of());
        long warehouseUsed = finance.warehouse.WarehouseManager.usedCapacity(playerId);
        long warehouseCapacity = finance.warehouse.WarehouseManager.totalCapacity(playerId);
        boolean warehouseOverCapacity = warehouseUsed > warehouseCapacity;

        return new FinanceProvider(
                marketData,
                orderRows,
                balance,
                frozenBalance,
                inventory,
                companyInfo,
                companyRows,
                stockRows,
                stockHoldingRows,
                stockOrderRows,
                transactionRows,
                assetBundle.summary(),
                assetBundle.rows(),
                priceAlertRows,
                conditionalStockOrderRows,
                companyFinancingRows,
                companyProposalRows,
                dashboard,
                CompanyManager.getDividendRatio(),
                CompanyManager.getDividendCycleDays(),
                mcInv,
                initialMode,
                sourceType,
                player.serverLevel().dimension().location().toString(),
                sourcePos,
                warehouseUsed,
                warehouseCapacity,
                warehouseOverCapacity);
    }

    private record FinanceProvider(
            List<MarketRows.MarketRow> marketData,
            List<MarketRows.OrderRow> orderRows,
            long balance,
            long frozenBalance,
            Map<String, Integer> inventory,
            CompanyRows.CompanyInfo companyInfo,
            List<CompanyRows.CompanyInfo> allCompanies,
            List<StockRows.StockRow> stocks,
            List<StockRows.StockHoldingRow> stockHoldings,
            List<StockRows.StockOrderRow> stockOrders,
            List<RecordRows.TransactionRow> transactions,
            RecordRows.AssetSummary assetSummary,
            List<RecordRows.AssetRow> assetRows,
            List<RecordRows.PriceAlertRow> priceAlerts,
            List<StockRows.ConditionalStockOrderRow> conditionalStockOrders,
            List<CompanyRows.CompanyFinancingRow> companyFinancingRows,
            List<CompanyRows.CompanyProposalRow> companyProposalRows,
            DashboardRows.EconomyDashboardRow dashboard,
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

        void write(net.minecraft.network.FriendlyByteBuf buffer) {
            FinanceMenu.writeAll(
                    buffer,
                    marketData,
                    orderRows,
                    balance,
                    frozenBalance,
                    inventory,
                    companyInfo,
                    allCompanies,
                    stocks,
                    stockHoldings,
                    stockOrders,
                    transactions,
                    assetSummary,
                    assetRows,
                    priceAlerts,
                    conditionalStockOrders,
                    companyFinancingRows,
                    companyProposalRows,
                    dashboard,
                    dividendRatio,
                    dividendCycleDays,
                    mcInventory,
                    initialMode,
                    sourceType,
                    sourceDimension,
                    sourcePos,
                    warehouseUsed,
                    warehouseCapacity,
                    warehouseOverCapacity);
        }

        @Override
        public FinanceMenu createMenu(
                int containerId,
                net.minecraft.world.entity.player.Inventory inv,
                net.minecraft.world.entity.player.Player player) {
            return new FinanceMenu(
                    containerId,
                    marketData,
                    orderRows,
                    balance,
                    frozenBalance,
                    inventory,
                    companyInfo,
                    allCompanies,
                    stocks,
                    stockHoldings,
                    stockOrders,
                    transactions,
                    assetSummary,
                    assetRows,
                    priceAlerts,
                    conditionalStockOrders,
                    companyFinancingRows,
                    companyProposalRows,
                    dashboard,
                    dividendRatio,
                    dividendCycleDays,
                    mcInventory,
                    initialMode,
                    sourceType,
                    sourceDimension,
                    sourcePos,
                    warehouseUsed,
                    warehouseCapacity,
                    warehouseOverCapacity);
        }
    }
}
