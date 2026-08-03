package finance.data;

import finance.account.Account;
import finance.account.AccountManager;
import finance.account.AssetSnapshotManager;
import finance.account.TransactionRecord;
import finance.alert.PriceAlert;
import finance.alert.PriceAlertDirection;
import finance.alert.PriceAlertManager;
import finance.alert.PriceAlertType;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyFinancingProject;
import finance.company.CompanyFinancialReport;
import finance.company.CompanyManager;
import finance.company.CompanyProposal;
import finance.company.CompanyProposalManager;
import finance.company.CompanyProposalStatus;
import finance.company.CompanyProposalType;
import finance.company.CompanyType;
import finance.event.EventManager;
import finance.event.EventTier;
import finance.event.MarketEvent;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.account.TransactionType;
import finance.market.Trade;
import finance.stock.Stock;
import finance.stock.ConditionalStockOrder;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.ConditionalStockOrderType;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockOrderType;
import finance.stock.StockPortfolioManager;
import finance.stock.StockPriceEngine;
import finance.stock.StockTrade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 经济数据持久化 —— 将账户余额、交易记录、市场订单和成交历史
 * 写入 Minecraft 的世界存档中，服务器重启后自动恢复。
 *
 * <h3>保存内容</h3>
 * <ul>
 *   <li>账户余额（含冻结金额）</li>
 *   <li>最近 500 条交易记录</li>
 *   <li>成交历史</li>
 *   <li>未成交的活跃订单</li>
 *   <li>国际市场价格</li>
 * </ul>
 */
public class EconomySavedData extends SavedData {

    public static final String DATA_NAME = "finance_data";
    private static final int DATA_VERSION = 13;

    // ================================================================
    // 保存
    // ================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putDouble("DividendRatio", CompanyManager.getDividendRatio());
        tag.putInt("DividendCycleDays", CompanyManager.getDividendCycleDays());

        // ---- 保存账户余额 ----
        ListTag accountsTag = new ListTag();

        for (Map.Entry<UUID, Account> entry :
                AccountManager.getAccounts().entrySet()) {

            CompoundTag accountTag = new CompoundTag();

            accountTag.putUUID(
                    "PlayerUUID",
                    entry.getKey()
            );

            accountTag.putLong(
                    "Balance",
                    entry.getValue().getBalance()
            );

            accountTag.putLong(
                    "FrozenBalance",
                    entry.getValue().getFrozenBalance()
            );

            accountsTag.add(accountTag);
        }

        tag.put("Accounts", accountsTag);

        ListTag assetSnapshotsTag = new ListTag();
        for (Map.Entry<UUID, AssetSnapshotManager.AssetSnapshot> entry :
                AssetSnapshotManager.getSnapshots().entrySet()) {
            CompoundTag snapshotTag = new CompoundTag();
            snapshotTag.putUUID("PlayerUUID", entry.getKey());
            snapshotTag.putLong("McDay", entry.getValue().mcDay());
            snapshotTag.putLong("TotalAsset", entry.getValue().totalAsset());
            assetSnapshotsTag.add(snapshotTag);
        }
        tag.put("AssetSnapshots", assetSnapshotsTag);

        ListTag alertsTag = new ListTag();
        for (PriceAlert alert : PriceAlertManager.getAlerts()) {
            CompoundTag alertTag = new CompoundTag();
            alertTag.putUUID("AlertId", alert.getAlertId());
            alertTag.putUUID("PlayerUUID", alert.getPlayerId());
            alertTag.putString("Type", alert.getType().name());
            alertTag.putString("TargetId", alert.getTargetId());
            alertTag.putString("Direction", alert.getDirection().name());
            alertTag.putLong("TargetPrice", alert.getTargetPrice());
            alertTag.putLong("CreatedAt", alert.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            alertsTag.add(alertTag);
        }
        tag.put("PriceAlerts", alertsTag);

        ListTag conditionalStockOrdersTag = new ListTag();
        for (ConditionalStockOrder order : ConditionalStockOrderManager.getOrders()) {
            CompoundTag orderTag = new CompoundTag();
            orderTag.putUUID("OrderId", order.getOrderId());
            orderTag.putUUID("PlayerUUID", order.getPlayerId());
            orderTag.putString("Symbol", order.getSymbol());
            orderTag.putString("Type", order.getType().name());
            orderTag.putLong("TriggerPrice", order.getTriggerPrice());
            orderTag.putLong("Quantity", order.getQuantity());
            orderTag.putLong("CreatedAt", order.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            conditionalStockOrdersTag.add(orderTag);
        }
        tag.put("ConditionalStockOrders", conditionalStockOrdersTag);

        ListTag financingProjectsTag = new ListTag();
        for (CompanyFinancingProject project : CompanyFinancingManager.getProjects()) {
            CompoundTag projectTag = new CompoundTag();
            projectTag.putUUID("ProjectId", project.getProjectId());
            projectTag.putUUID("CompanyUUID", project.getCompanyId());
            projectTag.putString("Symbol", project.getSymbol());
            projectTag.putLong("IssueQuantity", project.getIssueQuantity());
            projectTag.putLong("IssuePrice", project.getIssuePrice());
            projectTag.putLong("FundingTarget", project.getFundingTarget());
            projectTag.putLong("DeadlineMcDay", project.getDeadlineMcDay());
            projectTag.putLong("CreatedAt", project.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            ListTag subscriptionsTag = new ListTag();
            for (Map.Entry<UUID, Long> entry : project.getSubscriptions().entrySet()) {
                CompoundTag subscriptionTag = new CompoundTag();
                subscriptionTag.putUUID("PlayerUUID", entry.getKey());
                subscriptionTag.putLong("Quantity", entry.getValue());
                subscriptionsTag.add(subscriptionTag);
            }
            projectTag.put("Subscriptions", subscriptionsTag);
            financingProjectsTag.add(projectTag);
        }
        tag.put("CompanyFinancingProjects", financingProjectsTag);

        ListTag proposalsTag = new ListTag();
        for (CompanyProposal proposal : CompanyProposalManager.getProposals()) {
            CompoundTag proposalTag = new CompoundTag();
            proposalTag.putUUID("ProposalId", proposal.getProposalId());
            proposalTag.putUUID("CompanyUUID", proposal.getCompanyId());
            proposalTag.putUUID("CreatorUUID", proposal.getCreatorId());
            proposalTag.putString("Type", proposal.getType().name());
            proposalTag.putString("Title", proposal.getTitle());
            proposalTag.putString("TextValue", proposal.getTextValue());
            proposalTag.putLong("Value1", proposal.getValue1());
            proposalTag.putLong("Value2", proposal.getValue2());
            proposalTag.putLong("Value3", proposal.getValue3());
            proposalTag.putLong("StartMcDay", proposal.getStartMcDay());
            proposalTag.putLong("EndMcDay", proposal.getEndMcDay());
            proposalTag.putDouble("PassRatio", proposal.getPassRatio());
            proposalTag.putDouble("MinParticipationRatio", proposal.getMinParticipationRatio());
            proposalTag.putLong("VotingSharesSnapshot", proposal.getVotingSharesSnapshot());
            proposalTag.putLong("CreatedAt", proposal.getCreatedAt().toEpochSecond(ZoneOffset.UTC));
            proposalTag.putString("Status", proposal.getStatus().name());
            proposalTag.putString("ResultSummary", proposal.getResultSummary());
            ListTag votesTag = new ListTag();
            for (Map.Entry<UUID, CompanyProposal.VoteRecord> entry : proposal.getVotes().entrySet()) {
                CompoundTag voteTag = new CompoundTag();
                voteTag.putUUID("PlayerUUID", entry.getKey());
                voteTag.putBoolean("Support", entry.getValue().support());
                voteTag.putLong("Power", entry.getValue().power());
                votesTag.add(voteTag);
            }
            proposalTag.put("Votes", votesTag);
            proposalsTag.add(proposalTag);
        }
        tag.put("CompanyProposals", proposalsTag);

        // ---- 保存交易记录（仅最近 500 条） ----
        ListTag transactionsTag = new ListTag();

        List<TransactionRecord> allTxns =
                AccountManager.getTransactions();

        int txStart = Math.max(
                0,
                allTxns.size() - 500
        );

        for (int i = txStart; i < allTxns.size(); i++) {

            TransactionRecord record = allTxns.get(i);

            CompoundTag txTag = new CompoundTag();

            txTag.putUUID("From", record.getFrom());
            txTag.putUUID("To", record.getTo());
            txTag.putLong("Amount", record.getAmount());
            txTag.putString("Type", record.getType().name());
            if (record.getPlayerId() != null) {
                txTag.putUUID("PlayerUUID", record.getPlayerId());
            }
            txTag.putString("ObjectName", record.getObjectName());
            txTag.putLong("Quantity", record.getQuantity());

            txTag.putLong(
                    "Timestamp",
                    record.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            transactionsTag.add(txTag);
        }

        tag.put("Transactions", transactionsTag);

        // ---- 保存成交历史 ----
        ListTag tradesTag = new ListTag();

        for (Trade trade : MarketManager.getTradeHistory()) {

            CompoundTag tradeTag = new CompoundTag();

            tradeTag.putUUID("Buyer", trade.getBuyer());
            tradeTag.putUUID("Seller", trade.getSeller());
            tradeTag.putString(
                    "CommodityId",
                    trade.getCommodityId()
            );
            tradeTag.putLong("Price", trade.getPrice());
            tradeTag.putInt("Quantity", trade.getQuantity());

            tradeTag.putLong(
                    "Timestamp",
                    trade.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            tradesTag.add(tradeTag);
        }

        tag.put("Trades", tradesTag);

        // ---- 保存活跃订单 ----
        ListTag ordersTag = new ListTag();

        for (Order order : MarketManager.getOrders()) {

            CompoundTag orderTag = new CompoundTag();

            orderTag.putUUID(
                    "OrderId",
                    order.getOrderId()
            );

            orderTag.putUUID(
                    "PlayerUUID",
                    order.getPlayerId()
            );

            orderTag.putString(
                    "CommodityId",
                    order.getCommodityId()
            );

            orderTag.putString(
                    "Type",
                    order.getType().name()
            );

            orderTag.putLong(
                    "Price",
                    order.getPrice()
            );

            orderTag.putInt(
                    "Quantity",
                    order.getQuantity()
            );

            orderTag.putLong(
                    "Timestamp",
                    order.getTimestamp()
                            .toEpochSecond(
                                    ZoneOffset.UTC
                            )
            );

            ordersTag.add(orderTag);
        }

        tag.put("Orders", ordersTag);

        // ---- 保存系统公司 ----
        ListTag companiesTag = new ListTag();

        for (Company company : CompanyManager.getCompanies()) {
            CompoundTag companyTag = new CompoundTag();
            companyTag.putUUID("CompanyUUID", company.getCompanyId());
            companyTag.putString("Name", company.getName());
            companyTag.putString("Type", company.getType().name());
            companyTag.putLong("Cash", company.getCash());
            if (company.getOwnerId() != null) {
                companyTag.putUUID("OwnerUUID", company.getOwnerId());
            }

            CompoundTag inventoryTag = new CompoundTag();
            for (Map.Entry<String, Integer> entry : company.getInventory().entrySet()) {
                inventoryTag.putInt(entry.getKey(), entry.getValue());
            }
            companyTag.put("Inventory", inventoryTag);

            // P3：保存盈利和分红字段
            companyTag.putLong("DailyRevenue", company.getDailyRevenue());
            companyTag.putLong("DailyCost", company.getDailyCost());
            companyTag.putLong("RetainedEarnings", company.getRetainedEarnings());
            companyTag.putLong("DistributableProfit", company.getDistributableProfit());
            companyTag.putLong("LastDividendDay", company.getLastDividendDay());
            companyTag.putDouble("CompanyDividendRatio", company.getDividendRatio());
            companyTag.putInt("CompanyDividendCycleDays", company.getDividendCycleDays());
            companyTag.putString("Strategy", company.getStrategy().name());
            companyTag.putInt("ProductionLevel", company.getProductionLevel());
            companyTag.putInt("StorageLevel", company.getStorageLevel());
            companyTag.putInt("ManagementLevel", company.getManagementLevel());
            companyTag.putDouble("AutoSellRatio", company.getAutoSellRatio());
            companyTag.putBoolean("BankruptcyRisk", company.isBankruptcyRisk());
            companyTag.putLong("BankruptcyRiskStartDay", company.getBankruptcyRiskStartDay());
            ListTag recentProfitsTag = new ListTag();
            for (Long profit : company.getRecentProfits()) {
                CompoundTag profitTag = new CompoundTag();
                profitTag.putLong("Profit", profit);
                recentProfitsTag.add(profitTag);
            }
            companyTag.put("RecentProfits", recentProfitsTag);
            ListTag dividendHistoryTag = new ListTag();
            for (Company.DividendRecord record : company.getDividendHistory()) {
                CompoundTag recordTag = new CompoundTag();
                recordTag.putLong("McDay", record.mcDay());
                recordTag.putLong("TotalAmount", record.totalAmount());
                recordTag.putLong("PerShare", record.perShare());
                dividendHistoryTag.add(recordTag);
            }
            companyTag.put("DividendHistory", dividendHistoryTag);
            ListTag financialReportsTag = new ListTag();
            for (CompanyFinancialReport report : company.getFinancialReports()) {
                CompoundTag reportTag = new CompoundTag();
                reportTag.putLong("McDay", report.mcDay());
                reportTag.putLong("Revenue", report.revenue());
                reportTag.putLong("Expenses", report.expenses());
                reportTag.putLong("NetProfit", report.netProfit());
                reportTag.putLong("Assets", report.assets());
                reportTag.putLong("Liabilities", report.liabilities());
                reportTag.putLong("CashBalance", report.cashBalance());
                reportTag.putLong("AssetChange", report.assetChange());
                reportTag.putLong("ProfitChange", report.profitChange());
                reportTag.putString("Summary", report.summary());
                reportTag.putLong("CreatedAt", report.createdAt().toEpochSecond(ZoneOffset.UTC));
                financialReportsTag.add(reportTag);
            }
            companyTag.put("FinancialReports", financialReportsTag);

            // P4：保存上市状态
            companyTag.putBoolean("IsPublic", company.isPublic());

            companiesTag.add(companyTag);
        }

        tag.put("Companies", companiesTag);

        // ---- 保存国际市场价格 ----
        ListTag pricesTag = new ListTag();

        for (MarketPrice mp :
                NpcMarketMaker.getAllMarketPrices().values()) {

            CompoundTag priceTag = new CompoundTag();
            priceTag.putString("CommodityId", mp.getCommodityId());
            priceTag.putLong("MidPrice", mp.getMidPrice());
            priceTag.putLong("BasePrice", mp.getBasePrice());
            priceTag.putDouble("Spread", mp.getSpread());

            // 24h 统计
            priceTag.putLong("DayHigh", mp.getDayHigh());
            priceTag.putLong("DayLow", mp.getDayLow());
            priceTag.putInt("DayVolume", mp.getDayVolume());
            priceTag.putLong("DayOpen", mp.getDayOpen());

            // 动量与噪音
            priceTag.putDouble("TradeMomentum", mp.getTradeMomentum());
            priceTag.putDouble("TrendMomentum", mp.getTrendMomentum());
            priceTag.putInt("NoiseOffset", mp.getNoiseOffset());

            pricesTag.add(priceTag);
        }

        tag.put("MarketPrices", pricesTag);

        // ---- 保存价格快照 ----
        ListTag snapshotsTag = new ListTag();

        for (MarketPrice mp :
                NpcMarketMaker.getAllMarketPrices().values()) {

            CompoundTag commoditySnapTag = new CompoundTag();
            commoditySnapTag.putString("CommodityId", mp.getCommodityId());

            ListTag snapsList = new ListTag();
            for (MarketPrice.PriceSnapshot snap : mp.getSnapshots()) {
                CompoundTag snapTag = new CompoundTag();
                snapTag.putLong("Timestamp",
                        snap.getTimestamp().toEpochSecond(ZoneOffset.UTC));
                snapTag.putLong("Price", snap.getPrice());
                snapTag.putInt("Volume", snap.getVolume());
                snapsList.add(snapTag);
            }
            commoditySnapTag.put("Snapshots", snapsList);
            snapshotsTag.add(commoditySnapTag);
        }

        tag.put("PriceSnapshots", snapshotsTag);

        // ---- 保存商品定义（管理员添加的自定义商品） ----
        ListTag commoditiesTag = new ListTag();
        for (Commodity commodity : CommodityRegistry.getAllCommodities()) {
            if (CommodityRegistry.isDefaultCommodity(commodity.getId())) {
                continue;
            }
            CompoundTag cTag = new CompoundTag();
            cTag.putString("Id", commodity.getId());
            cTag.putString("DisplayName", commodity.getDisplayName());
            cTag.putString("Category", commodity.getCategory().name());
            cTag.putLong("BasePrice", commodity.getBasePrice());
            if (commodity.getItemId() != null) {
                cTag.putString("ItemId", commodity.getItemId());
            }
            commoditiesTag.add(cTag);
        }
        tag.put("CommodityDefinitions", commoditiesTag);

        // ---- 保存事件状态 ----
        tag.putInt("TimerMinor", EventManager.getTimerMinor());
        tag.putInt("TimerMajor", EventManager.getTimerMajor());
        tag.putInt("TimerBlackSwan", EventManager.getTimerBlackSwan());

        ListTag eventsTag = new ListTag();
        for (MarketEvent ev : EventManager.getActiveEvents()) {
            CompoundTag evTag = new CompoundTag();
            evTag.putString("Name", ev.getName());
            evTag.putString("Description", ev.getDescription());
            evTag.putString("Tier", ev.getTier().name());
            if (ev.getCommodityId() != null) {
                evTag.putString("CommodityId", ev.getCommodityId());
            }
            evTag.putDouble("PriceMultiplier", ev.getPriceMultiplier());
            evTag.putInt("TotalTicks", ev.getTotalTicks());
            evTag.putInt("RemainingTicks", ev.getRemainingTicks());
            eventsTag.add(evTag);
        }
        tag.put("ActiveEvents", eventsTag);

        // ---- 保存股票 ----
        ListTag stocksTag = new ListTag();
        for (Stock stock : StockMarketManager.getStocks()) {
            CompoundTag stockTag = new CompoundTag();
            stockTag.putString("Symbol", stock.getSymbol());
            stockTag.putString("Name", stock.getName());
            stockTag.putUUID("CompanyUUID", stock.getCompanyId());
            stockTag.putLong("TotalShares", stock.getTotalShares());
            stockTag.putLong("FloatShares", stock.getFloatShares());
            stockTag.putLong("OwnerShares", stock.getOwnerShares());

            // 新引擎字段
            stockTag.putLong("LastPrice", stock.getLastPrice());
            stockTag.putLong("FairValue", stock.getFairValue());
            stockTag.putDouble("TradeMomentum", stock.getTradeMomentum());
            stockTag.putInt("NoiseOffset", stock.getNoiseOffset());
            stockTag.putLong("PreviousClose", stock.getPreviousClose());
            stockTag.putLong("DayVolume", stock.getDayVolume());
            stockTag.putLong("DayHigh", stock.getDayHigh());
            stockTag.putLong("DayLow", stock.getDayLow());

            stocksTag.add(stockTag);
        }
        tag.put("Stocks", stocksTag);

        // ---- 保存股票价格快照 ----
        ListTag stockSnapshotsTag = new ListTag();
        for (Stock stock : StockMarketManager.getStocks()) {
            CompoundTag stockSnapTag = new CompoundTag();
            stockSnapTag.putString("Symbol", stock.getSymbol());
            ListTag snapsList = new ListTag();
            for (StockPriceEngine.PriceSnapshot snap : stock.getSnapshots()) {
                CompoundTag snapTag = new CompoundTag();
                snapTag.putLong("Timestamp", snap.getTimestamp().toEpochSecond(ZoneOffset.UTC));
                snapTag.putLong("Price", snap.getPrice());
                snapTag.putLong("Volume", snap.getVolume());
                snapsList.add(snapTag);
            }
            stockSnapTag.put("Snapshots", snapsList);
            stockSnapshotsTag.add(stockSnapTag);
        }
        tag.put("StockPriceSnapshots", stockSnapshotsTag);

        // ---- 保存股票持仓 ----
        ListTag portfoliosTag = new ListTag();
        for (Map.Entry<UUID, Map<String, StockHolding>> portfolioEntry :
                StockPortfolioManager.getPortfolios().entrySet()) {
            CompoundTag portfolioTag = new CompoundTag();
            portfolioTag.putUUID("PlayerUUID", portfolioEntry.getKey());
            ListTag holdingsTag = new ListTag();
            for (Map.Entry<String, StockHolding> holdingEntry : portfolioEntry.getValue().entrySet()) {
                StockHolding holding = holdingEntry.getValue();
                CompoundTag holdingTag = new CompoundTag();
                holdingTag.putString("Symbol", holdingEntry.getKey());
                holdingTag.putLong("Quantity", holding.getQuantity());
                holdingTag.putLong("AverageCost", holding.getAverageCost());
                holdingsTag.add(holdingTag);
            }
            portfolioTag.put("Holdings", holdingsTag);
            portfoliosTag.add(portfolioTag);
        }
        tag.put("StockPortfolios", portfoliosTag);

        // ---- 保存股票订单（P2）----
        ListTag stockOrdersTag = new ListTag();
        for (StockOrder order : StockMarketManager.getOrders()) {
            CompoundTag orderTag = new CompoundTag();
            orderTag.putUUID("OrderId", order.getOrderId());
            orderTag.putUUID("PlayerId", order.getPlayerId());
            orderTag.putString("Symbol", order.getSymbol());
            orderTag.putString("Type", order.getType().name());
            orderTag.putLong("Price", order.getPrice());
            orderTag.putInt("Quantity", order.getQuantity());
            orderTag.putLong("Timestamp", order.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC));
            stockOrdersTag.add(orderTag);
        }
        tag.put("StockOrders", stockOrdersTag);

        // ---- 保存股票成交记录（P2）----
        ListTag stockTradesTag = new ListTag();
        for (StockTrade trade : StockMarketManager.getStockTradeHistory()) {
            CompoundTag tradeTag = new CompoundTag();
            tradeTag.putUUID("Buyer", trade.getBuyer());
            tradeTag.putUUID("Seller", trade.getSeller());
            tradeTag.putString("Symbol", trade.getSymbol());
            tradeTag.putLong("Price", trade.getPrice());
            tradeTag.putInt("Quantity", trade.getQuantity());
            tradeTag.putLong("Timestamp", trade.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC));
            stockTradesTag.add(tradeTag);
        }
        tag.put("StockTrades", stockTradesTag);

        return tag;
    }

    // ================================================================
    // 加载
    // ================================================================

    /**
     * 从 NBT 数据恢复全部经济状态。
     * 注意：加载时如果发现 FrozenBalance，需要将 balance+frozen 设为总余额后重新冻结。
     */
    public static EconomySavedData load(CompoundTag tag) {

        EconomySavedData data = new EconomySavedData();

        resetRuntimeState();
        CompanyManager.setDividendPolicy(
                tag.contains("DividendRatio") ? tag.getDouble("DividendRatio") : CompanyManager.getDividendRatio(),
                tag.contains("DividendCycleDays") ? tag.getInt("DividendCycleDays") : CompanyManager.getDividendCycleDays());

        // ---- 加载账户余额 ----
        ListTag accountsTag = tag.getList(
                "Accounts",
                Tag.TAG_COMPOUND
        );

        for (Tag rawTag : accountsTag) {

            CompoundTag accountTag = (CompoundTag) rawTag;

            UUID playerUUID = readUuidOrNull(accountTag, "PlayerUUID");
            if (playerUUID == null) {
                continue;
            }
            long balance = accountTag.getLong("Balance");

            Account account = AccountManager.getAccount(playerUUID);

            if (accountTag.contains("FrozenBalance")) {
                long frozen =
                        accountTag.getLong("FrozenBalance");

                // 已保存的 Balance 是可用余额，总余额 = 可用 + 冻结
                account.setBalance(balance + frozen);

                if (frozen > 0) {
                    account.freezeFunds(frozen);
                }

            } else {
                account.setBalance(balance);
            }
        }

        // ---- 加载玩家今日资产基准 ----
        if (tag.contains("AssetSnapshots")) {
            ListTag assetSnapshotsTag = tag.getList("AssetSnapshots", Tag.TAG_COMPOUND);
            for (Tag rawTag : assetSnapshotsTag) {
                CompoundTag snapshotTag = (CompoundTag) rawTag;
                UUID playerUUID = readUuidOrNull(snapshotTag, "PlayerUUID");
                if (playerUUID == null) {
                    continue;
                }
                AssetSnapshotManager.putSnapshotDirect(playerUUID,
                        new AssetSnapshotManager.AssetSnapshot(
                                snapshotTag.getLong("McDay"),
                                snapshotTag.getLong("TotalAsset")));
            }
        }

        // ---- 加载价格提醒 ----
        if (tag.contains("PriceAlerts")) {
            ListTag alertsTag = tag.getList("PriceAlerts", Tag.TAG_COMPOUND);
            for (Tag rawTag : alertsTag) {
                CompoundTag alertTag = (CompoundTag) rawTag;
                UUID alertId = readUuidOrNull(alertTag, "AlertId");
                UUID playerUUID = readUuidOrNull(alertTag, "PlayerUUID");
                PriceAlertType type = safeEnum(PriceAlertType.class, alertTag.getString("Type"), null);
                PriceAlertDirection direction = safeEnum(PriceAlertDirection.class, alertTag.getString("Direction"), null);
                if (alertId == null || playerUUID == null || type == null || direction == null) {
                    continue;
                }
                PriceAlertManager.addAlertDirect(new PriceAlert(
                        alertId,
                        playerUUID,
                        type,
                        alertTag.getString("TargetId"),
                        direction,
                        alertTag.getLong("TargetPrice"),
                        LocalDateTime.ofEpochSecond(alertTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
            }
        }

        // ---- 加载股票条件委托 ----
        if (tag.contains("ConditionalStockOrders")) {
            ListTag conditionalOrdersTag = tag.getList("ConditionalStockOrders", Tag.TAG_COMPOUND);
            for (Tag rawTag : conditionalOrdersTag) {
                CompoundTag orderTag = (CompoundTag) rawTag;
                UUID orderId = readUuidOrNull(orderTag, "OrderId");
                UUID playerUUID = readUuidOrNull(orderTag, "PlayerUUID");
                ConditionalStockOrderType type = safeEnum(
                        ConditionalStockOrderType.class,
                        orderTag.getString("Type"),
                        null);
                if (orderId == null || playerUUID == null || type == null) {
                    continue;
                }
                ConditionalStockOrderManager.addOrderDirect(new ConditionalStockOrder(
                        orderId,
                        playerUUID,
                        orderTag.getString("Symbol"),
                        type,
                        orderTag.getLong("TriggerPrice"),
                        orderTag.getLong("Quantity"),
                        LocalDateTime.ofEpochSecond(orderTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
            }
        }

        // ---- 加载公司融资项目 ----
        if (tag.contains("CompanyFinancingProjects")) {
            ListTag projectsTag = tag.getList("CompanyFinancingProjects", Tag.TAG_COMPOUND);
            for (Tag rawTag : projectsTag) {
                CompoundTag projectTag = (CompoundTag) rawTag;
                UUID projectId = readUuidOrNull(projectTag, "ProjectId");
                UUID companyUUID = readUuidOrNull(projectTag, "CompanyUUID");
                if (projectId == null || companyUUID == null) {
                    continue;
                }
                CompanyFinancingProject project = new CompanyFinancingProject(
                        projectId,
                        companyUUID,
                        projectTag.getString("Symbol"),
                        projectTag.getLong("IssueQuantity"),
                        projectTag.getLong("IssuePrice"),
                        projectTag.getLong("FundingTarget"),
                        projectTag.getLong("DeadlineMcDay"),
                        LocalDateTime.ofEpochSecond(projectTag.getLong("CreatedAt"), 0, ZoneOffset.UTC));
                ListTag subscriptionsTag = projectTag.getList("Subscriptions", Tag.TAG_COMPOUND);
                for (Tag subscriptionRaw : subscriptionsTag) {
                    CompoundTag subscriptionTag = (CompoundTag) subscriptionRaw;
                    UUID playerUUID = readUuidOrNull(subscriptionTag, "PlayerUUID");
                    long quantity = subscriptionTag.getLong("Quantity");
                    if (playerUUID != null && quantity > 0) {
                        project.addSubscription(playerUUID, quantity);
                    }
                }
                CompanyFinancingManager.addProjectDirect(project);
            }
        }

        // ---- 加载公司股东提案 ----
        if (tag.contains("CompanyProposals")) {
            ListTag proposalsTag = tag.getList("CompanyProposals", Tag.TAG_COMPOUND);
            for (Tag rawTag : proposalsTag) {
                CompoundTag proposalTag = (CompoundTag) rawTag;
                UUID proposalId = readUuidOrNull(proposalTag, "ProposalId");
                UUID companyUUID = readUuidOrNull(proposalTag, "CompanyUUID");
                UUID creatorUUID = readUuidOrNull(proposalTag, "CreatorUUID");
                CompanyProposalType type = safeEnum(CompanyProposalType.class, proposalTag.getString("Type"), null);
                CompanyProposalStatus status = safeEnum(CompanyProposalStatus.class,
                        proposalTag.getString("Status"), CompanyProposalStatus.ACTIVE);
                if (proposalId == null || companyUUID == null || creatorUUID == null || type == null) {
                    continue;
                }
                CompanyProposal proposal = new CompanyProposal(
                        proposalId,
                        companyUUID,
                        creatorUUID,
                        type,
                        proposalTag.getString("Title"),
                        proposalTag.getString("TextValue"),
                        proposalTag.getLong("Value1"),
                        proposalTag.getLong("Value2"),
                        proposalTag.getLong("Value3"),
                        proposalTag.getLong("StartMcDay"),
                        proposalTag.getLong("EndMcDay"),
                        proposalTag.getDouble("PassRatio"),
                        proposalTag.contains("MinParticipationRatio")
                                ? proposalTag.getDouble("MinParticipationRatio")
                                : 0.0,
                        proposalTag.contains("VotingSharesSnapshot")
                                ? proposalTag.getLong("VotingSharesSnapshot")
                                : 0,
                        LocalDateTime.ofEpochSecond(proposalTag.getLong("CreatedAt"), 0, ZoneOffset.UTC),
                        status,
                        proposalTag.getString("ResultSummary"));
                ListTag votesTag = proposalTag.getList("Votes", Tag.TAG_COMPOUND);
                for (Tag voteRaw : votesTag) {
                    CompoundTag voteTag = (CompoundTag) voteRaw;
                    UUID playerUUID = readUuidOrNull(voteTag, "PlayerUUID");
                    if (playerUUID != null && voteTag.getLong("Power") > 0) {
                        proposal.addVote(playerUUID, voteTag.getBoolean("Support"), voteTag.getLong("Power"));
                    }
                }
                CompanyProposalManager.addProposalDirect(proposal);
            }
        }

        // ---- 加载交易记录 ----
        if (tag.contains("Transactions")) {

            AccountManager.clearTransactions();

            ListTag transactionsTag = tag.getList(
                    "Transactions",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : transactionsTag) {

                CompoundTag txTag = (CompoundTag) rawTag;

                UUID from = readUuidOrNull(txTag, "From");
                UUID to = readUuidOrNull(txTag, "To");
                if (from == null || to == null) {
                    continue;
                }
                UUID playerId = readUuidOrNull(txTag, "PlayerUUID");
                long amount = txTag.getLong("Amount");
                long quantity = txTag.contains("Quantity") ? txTag.getLong("Quantity") : 0;
                String objectName = txTag.contains("ObjectName") ? txTag.getString("ObjectName") : "";
                TransactionType type = safeEnum(TransactionType.class, txTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }

                long epochSeconds = txTag.getLong("Timestamp");

                TransactionRecord record =
                        new TransactionRecord(
                                from, to, amount, type,
                                LocalDateTime.ofEpochSecond(
                                        epochSeconds,
                                        0,
                                        ZoneOffset.UTC
                                ),
                                playerId,
                                objectName,
                                quantity
                        );

                AccountManager.addTransactionRecord(record);
            }
        }

        // ---- 加载成交历史 ----
        if (tag.contains("Trades")) {

            MarketManager.clearTradeHistory();

            ListTag tradesTag = tag.getList(
                    "Trades",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : tradesTag) {

                CompoundTag tradeTag = (CompoundTag) rawTag;

                UUID buyer = readUuidOrNull(tradeTag, "Buyer");
                UUID seller = readUuidOrNull(tradeTag, "Seller");
                if (buyer == null || seller == null) {
                    continue;
                }
                String commodityId =
                        tradeTag.getString("CommodityId");
                long price = tradeTag.getLong("Price");
                int quantity = tradeTag.getInt("Quantity");

                long epochSeconds =
                        tradeTag.getLong("Timestamp");

                Trade trade = new Trade(
                        buyer, seller, commodityId,
                        price, quantity,
                        LocalDateTime.ofEpochSecond(
                                epochSeconds,
                                0,
                                ZoneOffset.UTC
                        )
                );

                MarketManager.addTradeToHistory(trade);
            }
        }

        // ---- 加载活跃订单 ----
        if (tag.contains("Orders")) {

            MarketManager.clearOrders();

            ListTag ordersTag = tag.getList(
                    "Orders",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : ordersTag) {

                CompoundTag orderTag =
                        (CompoundTag) rawTag;

                UUID orderId = readUuidOrNull(orderTag, "OrderId");
                if (orderId == null) {
                    orderId = UUID.randomUUID();
                }

                UUID playerUUID = readUuidOrNull(orderTag, "PlayerUUID");
                if (playerUUID == null) {
                    continue;
                }

                String commodityId =
                        orderTag.getString("CommodityId");

                OrderType type = safeEnum(OrderType.class, orderTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }

                long price = orderTag.getLong("Price");
                int quantity = orderTag.getInt("Quantity");

                long epochSeconds =
                        orderTag.getLong("Timestamp");

                Order order = new Order(
                        orderId,
                        playerUUID,
                        commodityId,
                        type,
                        price,
                        quantity,
                        LocalDateTime.ofEpochSecond(
                                epochSeconds,
                                0,
                                ZoneOffset.UTC
                        )
                );

                // 使用 addOrderDirect 跳过资产冻结
                // （资产在上次运行时已冻结）
                MarketManager.addOrderDirect(order);
            }
        }

        // ---- 加载系统公司 ----
        if (tag.contains("Companies")) {
            CompanyManager.clearCompaniesDirect();

            ListTag companiesTag = tag.getList(
                    "Companies",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : companiesTag) {
                CompoundTag companyTag = (CompoundTag) rawTag;

                UUID companyUUID = readUuidOrNull(companyTag, "CompanyUUID");
                if (companyUUID == null) {
                    continue;
                }
                String name = companyTag.getString("Name");
                CompanyType type = safeEnum(CompanyType.class, companyTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }
                long cash = companyTag.getLong("Cash");
                UUID ownerUUID = readUuidOrNull(companyTag, "OwnerUUID");

                Company company = new Company(companyUUID, name, type, cash, ownerUUID);

                CompoundTag inventoryTag = companyTag.getCompound("Inventory");
                for (String key : inventoryTag.getAllKeys()) {
                    if (CommodityRegistry.getCommodity(key) != null) {
                        company.addInventory(key, inventoryTag.getInt(key));
                    }
                }

                List<Long> recentProfits = new java.util.ArrayList<>();
                if (companyTag.contains("RecentProfits")) {
                    ListTag recentProfitsTag = companyTag.getList("RecentProfits", Tag.TAG_COMPOUND);
                    for (Tag profitRaw : recentProfitsTag) {
                        recentProfits.add(((CompoundTag) profitRaw).getLong("Profit"));
                    }
                }
                List<Company.DividendRecord> dividendHistory = new java.util.ArrayList<>();
                if (companyTag.contains("DividendHistory")) {
                    ListTag dividendHistoryTag = companyTag.getList("DividendHistory", Tag.TAG_COMPOUND);
                    for (Tag recordRaw : dividendHistoryTag) {
                        CompoundTag recordTag = (CompoundTag) recordRaw;
                        dividendHistory.add(new Company.DividendRecord(
                                recordTag.getLong("McDay"),
                                recordTag.getLong("TotalAmount"),
                                recordTag.getLong("PerShare")));
                    }
                }
                company.restoreFinancials(
                        companyTag.getLong("DailyRevenue"),
                        companyTag.getLong("DailyCost"),
                        companyTag.getLong("RetainedEarnings"),
                        companyTag.contains("DistributableProfit")
                                ? companyTag.getLong("DistributableProfit")
                                : Math.max(0, companyTag.getLong("RetainedEarnings")),
                        companyTag.getLong("LastDividendDay"),
                        recentProfits,
                        dividendHistory);
                company.restoreDividendPolicy(
                        companyTag.contains("CompanyDividendRatio")
                                ? companyTag.getDouble("CompanyDividendRatio")
                                : -1.0,
                        companyTag.contains("CompanyDividendCycleDays")
                                ? companyTag.getInt("CompanyDividendCycleDays")
                                : -1);
                if (companyTag.contains("FinancialReports")) {
                    ListTag reportsTag = companyTag.getList("FinancialReports", Tag.TAG_COMPOUND);
                    for (Tag reportRaw : reportsTag) {
                        CompoundTag reportTag = (CompoundTag) reportRaw;
                        company.addFinancialReportDirect(new CompanyFinancialReport(
                                reportTag.getLong("McDay"),
                                reportTag.getLong("Revenue"),
                                reportTag.getLong("Expenses"),
                                reportTag.getLong("NetProfit"),
                                reportTag.getLong("Assets"),
                                reportTag.getLong("Liabilities"),
                                reportTag.getLong("CashBalance"),
                                reportTag.getLong("AssetChange"),
                                reportTag.getLong("ProfitChange"),
                                reportTag.getString("Summary"),
                                LocalDateTime.ofEpochSecond(reportTag.getLong("CreatedAt"), 0, ZoneOffset.UTC)));
                    }
                }
                company.restoreManagement(
                        companyTag.contains("Strategy")
                                ? safeEnum(finance.company.CompanyStrategy.class,
                                companyTag.getString("Strategy"),
                                finance.company.CompanyStrategy.STABLE)
                                : finance.company.CompanyStrategy.STABLE,
                        companyTag.getInt("ProductionLevel"),
                        companyTag.getInt("StorageLevel"),
                        companyTag.getInt("ManagementLevel"),
                        companyTag.contains("AutoSellRatio") ? companyTag.getDouble("AutoSellRatio") : 0.5);
                if (companyTag.contains("BankruptcyRisk")) {
                    company.setBankruptcyRisk(
                            companyTag.getBoolean("BankruptcyRisk"),
                            companyTag.getLong("BankruptcyRiskStartDay"));
                }

                // P4：恢复上市状态
                if (companyTag.contains("IsPublic")) {
                    company.setPublic(companyTag.getBoolean("IsPublic"));
                }

                CompanyManager.registerDirect(company);
            }
        }

        // ---- 加载商品定义（管理员添加的自定义商品，必须在市场价格之前） ----
        if (tag.contains("CommodityDefinitions")) {
            ListTag commoditiesTag = tag.getList("CommodityDefinitions", Tag.TAG_COMPOUND);
            for (Tag rawTag : commoditiesTag) {
                CompoundTag cTag = (CompoundTag) rawTag;
                String id = cTag.getString("Id");
                if (CommodityRegistry.isRegistered(id)) continue;

                String displayName = cTag.getString("DisplayName");
                CommodityCategory category = safeEnum(
                        CommodityCategory.class,
                        cTag.getString("Category"),
                        CommodityCategory.MISCELLANEOUS);
                long basePrice = cTag.getLong("BasePrice");
                String itemId = cTag.contains("ItemId") ? cTag.getString("ItemId") : null;

                Commodity commodity = new Commodity(id, itemId, displayName, category, basePrice);
                CommodityRegistry.register(commodity);
            }
        }

        // ---- 加载国际市场价格 ----
        if (tag.contains("MarketPrices")) {

            NpcMarketMaker.clearMarketPrices();

            ListTag pricesTag = tag.getList(
                    "MarketPrices",
                    Tag.TAG_COMPOUND
            );

            for (Tag rawTag : pricesTag) {

                CompoundTag priceTag = (CompoundTag) rawTag;

                String commodityId =
                        priceTag.getString("CommodityId");

                // 跳过已从注册表中移除的商品
                Commodity commodity =
                        CommodityRegistry.getCommodity(commodityId);
                if (commodity == null) {
                    continue;
                }

                long midPrice = priceTag.getLong("MidPrice");
                double spread = priceTag.getDouble("Spread");

                // basePrice 始终从 CommodityRegistry 取最新值
                long basePrice = commodity.getBasePrice();

                MarketPrice mp = new MarketPrice(
                        commodityId, basePrice, spread
                );
                mp.setMidPrice(midPrice);

                // 恢复 24h 统计
                if (priceTag.contains("DayOpen")) {
                    mp.setDayOpen(priceTag.getLong("DayOpen"));
                }

                // 恢复动量与噪音（向后兼容：旧存档无此字段则保持默认 0）
                if (priceTag.contains("TradeMomentum")) {
                    mp.setTradeMomentum(priceTag.getDouble("TradeMomentum"));
                }
                if (priceTag.contains("TrendMomentum")) {
                    mp.setTrendMomentum(priceTag.getDouble("TrendMomentum"));
                }
                if (priceTag.contains("NoiseOffset")) {
                    mp.setNoiseOffset(priceTag.getInt("NoiseOffset"));
                }

                NpcMarketMaker.putMarketPrice(commodityId, mp);
            }
        }

        // ---- 加载价格快照 ----
        if (tag.contains("PriceSnapshots")) {
            ListTag snapshotsTag = tag.getList("PriceSnapshots", Tag.TAG_COMPOUND);
            for (Tag rawTag : snapshotsTag) {
                CompoundTag commoditySnapTag = (CompoundTag) rawTag;
                String commodityId = commoditySnapTag.getString("CommodityId");
                MarketPrice mp = NpcMarketMaker.getAllMarketPrices().get(commodityId);
                if (mp == null) continue;

                ListTag snapsList = commoditySnapTag.getList("Snapshots", Tag.TAG_COMPOUND);
                for (Tag snapRaw : snapsList) {
                    CompoundTag snapTag = (CompoundTag) snapRaw;
                    long epochSeconds = snapTag.getLong("Timestamp");
                    long price = snapTag.getLong("Price");
                    int volume = snapTag.getInt("Volume");
                    LocalDateTime ts = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
                    mp.addSnapshotDirect(new MarketPrice.PriceSnapshot(ts, price, volume));
                }
                mp.recomputeDayStats();
            }
        }

        // ---- 加载事件状态 ----
        if (tag.contains("TimerMinor")) {
            EventManager.setTimers(
                    tag.getInt("TimerMinor"),
                    tag.getInt("TimerMajor"),
                    tag.getInt("TimerBlackSwan"));
        }

        if (tag.contains("ActiveEvents")) {
            EventManager.clearActiveEvents();
            ListTag eventsTag = tag.getList("ActiveEvents", Tag.TAG_COMPOUND);
            for (Tag rawTag : eventsTag) {
                CompoundTag evTag = (CompoundTag) rawTag;
                String name = evTag.getString("Name");
                String description = evTag.getString("Description");
                EventTier tier = safeEnum(EventTier.class, evTag.getString("Tier"), EventTier.MINOR);
                String commodityId = evTag.contains("CommodityId")
                        ? evTag.getString("CommodityId") : null;
                double multiplier = evTag.getDouble("PriceMultiplier");
                int totalTicks = evTag.getInt("TotalTicks");
                int remainingTicks = evTag.getInt("RemainingTicks");

                MarketEvent ev = new MarketEvent(name, description, tier,
                        commodityId, multiplier, totalTicks, remainingTicks);
                EventManager.addActiveEventDirect(ev);

                if (ev.affectsAll()) {
                    NpcMarketMaker.applyEventToAll(ev);
                } else {
                    NpcMarketMaker.applyEvent(ev.getCommodityId(), ev);
                }
            }
        }

        // ---- 加载股票 ----
        if (tag.contains("Stocks")) {
            StockMarketManager.clearStocks();
            ListTag stocksTag = tag.getList("Stocks", Tag.TAG_COMPOUND);
            for (Tag rawTag : stocksTag) {
                CompoundTag stockTag = (CompoundTag) rawTag;

                String symbol = stockTag.getString("Symbol");
                String name = stockTag.getString("Name");
                UUID companyUuid = readUuidOrNull(stockTag, "CompanyUUID");
                if (companyUuid == null) {
                    continue;
                }
                long totalShares = stockTag.getLong("TotalShares");

                // 新字段，旧存档缺失时用默认值
                long floatShares = stockTag.contains("FloatShares")
                        ? stockTag.getLong("FloatShares")
                        : stockTag.getLong("AvailableShares"); // 向后兼容：用旧的 AvailableShares
                long ownerShares = stockTag.contains("OwnerShares")
                        ? stockTag.getLong("OwnerShares")
                        : 0;

                long currentPrice = stockTag.getLong("LastPrice");
                long fairValue = stockTag.contains("FairValue")
                        ? stockTag.getLong("FairValue")
                        : currentPrice; // 旧存档：fairValue = currentPrice

                Stock stock = new Stock(symbol, name, companyUuid, totalShares,
                        floatShares, ownerShares, currentPrice, fairValue);
                Company company = CompanyManager.getCompany(companyUuid);
                if (company == null || !company.isPublic()) {
                    continue;
                }

                // 恢复定价引擎字段
                if (stockTag.contains("TradeMomentum")) {
                    stock.setTradeMomentum(stockTag.getDouble("TradeMomentum"));
                }
                if (stockTag.contains("NoiseOffset")) {
                    stock.setNoiseOffset(stockTag.getInt("NoiseOffset"));
                }
                if (stockTag.contains("PreviousClose")) {
                    stock.setDayOpen(stockTag.getLong("PreviousClose"));
                }

                StockMarketManager.putStockDirect(stock);
            }
        }

        // ---- 加载股票价格快照 ----
        if (tag.contains("StockPriceSnapshots")) {
            ListTag snapshotsTag = tag.getList("StockPriceSnapshots", Tag.TAG_COMPOUND);
            for (Tag rawTag : snapshotsTag) {
                CompoundTag stockSnapTag = (CompoundTag) rawTag;
                Stock stock = StockMarketManager.getStock(stockSnapTag.getString("Symbol"));
                if (stock == null) {
                    continue;
                }

                ListTag snapsList = stockSnapTag.getList("Snapshots", Tag.TAG_COMPOUND);
                for (Tag snapRaw : snapsList) {
                    CompoundTag snapTag = (CompoundTag) snapRaw;
                    stock.addSnapshotDirect(new StockPriceEngine.PriceSnapshot(
                            LocalDateTime.ofEpochSecond(snapTag.getLong("Timestamp"), 0, ZoneOffset.UTC),
                            snapTag.getLong("Price"),
                            snapTag.getLong("Volume")));
                }
            }
        }

        // ---- 加载股票持仓 ----
        if (tag.contains("StockPortfolios")) {
            StockPortfolioManager.clearPortfolios();
            ListTag portfoliosTag = tag.getList("StockPortfolios", Tag.TAG_COMPOUND);
            for (Tag rawTag : portfoliosTag) {
                CompoundTag portfolioTag = (CompoundTag) rawTag;
                UUID playerUUID = readUuidOrNull(portfolioTag, "PlayerUUID");
                if (playerUUID == null) {
                    continue;
                }
                ListTag holdingsTag = portfolioTag.getList("Holdings", Tag.TAG_COMPOUND);
                for (Tag holdingRaw : holdingsTag) {
                    CompoundTag holdingTag = (CompoundTag) holdingRaw;
                    StockPortfolioManager.putHoldingDirect(
                            playerUUID,
                            holdingTag.getString("Symbol"),
                            new StockHolding(
                                    holdingTag.getLong("Quantity"),
                                    holdingTag.getLong("AverageCost")
                            )
                    );
                }
            }
        }

        // ---- 加载股票订单（P2）----
        if (tag.contains("StockOrders")) {
            StockMarketManager.clearStockOrders();
            ListTag ordersTag = tag.getList("StockOrders", Tag.TAG_COMPOUND);
            for (Tag rawTag : ordersTag) {
                CompoundTag orderTag = (CompoundTag) rawTag;
                UUID orderId = readUuidOrNull(orderTag, "OrderId");
                UUID playerId = readUuidOrNull(orderTag, "PlayerId");
                if (orderId == null || playerId == null) {
                    continue;
                }
                String symbol = orderTag.getString("Symbol");
                StockOrderType type = safeEnum(StockOrderType.class, orderTag.getString("Type"), null);
                if (type == null) {
                    continue;
                }
                long price = orderTag.getLong("Price");
                int quantity = orderTag.getInt("Quantity");
                long timestamp = orderTag.getLong("Timestamp");

                StockOrder order = new StockOrder(orderId, playerId, symbol, type, price, quantity,
                        java.time.LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC));
                StockMarketManager.addStockOrderDirect(order);
            }
        }

        // ---- 加载股票成交记录（P2）----
        if (tag.contains("StockTrades")) {
            StockMarketManager.clearStockTradeHistory();
            ListTag tradesTag = tag.getList("StockTrades", Tag.TAG_COMPOUND);
            for (Tag rawTag : tradesTag) {
                CompoundTag tradeTag = (CompoundTag) rawTag;
                UUID buyer = readUuidOrNull(tradeTag, "Buyer");
                UUID seller = readUuidOrNull(tradeTag, "Seller");
                if (buyer == null || seller == null) {
                    continue;
                }
                String symbol = tradeTag.getString("Symbol");
                long price = tradeTag.getLong("Price");
                int quantity = tradeTag.getInt("Quantity");
                long timestamp = tradeTag.getLong("Timestamp");

                StockTrade trade = new StockTrade(buyer, seller, symbol, price, quantity,
                        java.time.LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC));
                StockMarketManager.addStockTradeDirect(trade);
            }
        }

        return data;
    }

    // ================================================================
    // 实例管理
    // ================================================================

    /**
     * 清空所有绑定到具体世界的运行时经济状态。
     *
     * <p>本模组大量管理器仍是静态内存态。服务器在同一个 JVM 中切换世界时，
     * 如果新世界没有现成 SavedData，Minecraft 会直接调用空构造器而不是 load()。
     * 因此必须把“清空旧世界状态”提升为显式生命周期步骤。</p>
     */
    public static void resetRuntimeState() {
        AccountManager.clearAccountsDirect();
        AccountManager.clearTransactions();
        AssetSnapshotManager.clearSnapshotsDirect();
        PriceAlertManager.clearAlertsDirect();
        ConditionalStockOrderManager.clearOrdersDirect();
        CompanyFinancingManager.clearProjectsDirect();
        CompanyProposalManager.clearProposalsDirect();
        MarketManager.clearTradeHistory();
        MarketManager.clearOrders();
        CompanyManager.clearCompaniesDirect();
        CompanyManager.resetDividendPolicyDirect();
        NpcMarketMaker.clearMarketPrices();
        EventManager.clearActiveEvents();
        StockMarketManager.clearStocks();
        StockMarketManager.clearStockOrders();
        StockMarketManager.clearStockTradeHistory();
        StockPortfolioManager.clearPortfolios();
    }

    private static EconomySavedData createFresh() {
        resetRuntimeState();
        return new EconomySavedData();
    }

    private static UUID readUuidOrNull(CompoundTag tag, String key) {
        try {
            return tag.hasUUID(key) ? tag.getUUID(key) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** 获取或创建 EconomySavedData 实例（服务器启动时调用） */
    public static EconomySavedData get(MinecraftServer server) {

        DimensionDataStorage storage =
                server.overworld().getDataStorage();

        INSTANCE = storage.computeIfAbsent(
                EconomySavedData::load,
                EconomySavedData::createFresh,
                DATA_NAME
        );

        return INSTANCE;
    }

    private static EconomySavedData INSTANCE;

    public static void unload() {
        INSTANCE = null;
        resetRuntimeState();
    }

    /** 标记数据已修改，下次存档时写入磁盘 */
    public static void markDirty() {
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }
}
