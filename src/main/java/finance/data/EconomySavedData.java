package finance.data;

import finance.account.AccountManager;
import finance.account.AssetSnapshotManager;
import finance.alert.PriceAlertManager;
import finance.company.Company;
import finance.company.CompanyFinancingManager;
import finance.company.CompanyManager;
import finance.company.CompanyProposalManager;
import finance.event.EventManager;
import finance.market.MarketManager;
import finance.market.NpcMarketMaker;
import finance.stock.Stock;
import finance.stock.ConditionalStockOrderManager;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockOrder;
import finance.stock.StockOrderType;
import finance.stock.StockPortfolioManager;
import finance.stock.StockPriceEngine;
import finance.stock.StockTrade;
import finance.data.serializer.AccountDataSerializer;
import finance.data.serializer.CandlestickDataSerializer;
import finance.data.serializer.CompanyDataSerializer;
import finance.data.serializer.MarketDataSerializer;
import finance.data.serializer.MetricsDataSerializer;
import finance.data.serializer.PlayerFeatureDataSerializer;
import finance.data.serializer.FinancialDataSerializer;
import finance.data.serializer.DebtDataSerializer;
import finance.data.serializer.BondMarketDataSerializer;
import finance.data.serializer.CentralBankBillDataSerializer;
import finance.data.serializer.FuturesDataSerializer;
import finance.data.serializer.BankingDataSerializer;
import finance.data.serializer.DiagnosticDataSerializer;
import finance.data.serializer.FundDataSerializer;
import finance.data.serializer.InsuranceDataSerializer;
import finance.data.serializer.GovernanceDataSerializer;
import finance.cycle.FinancialCycleService;
import finance.metrics.EconomyMetricsService;
import finance.marketdata.RecentTradeService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private static final int DATA_VERSION = 26;
    public static int currentDataVersion() { return DATA_VERSION; }

    // ================================================================
    // 保存
    // ================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        tag.putDouble("DividendRatio", CompanyManager.getDividendRatio());
        tag.putInt("DividendCycleDays", CompanyManager.getDividendCycleDays());

        // ---- 保存账户余额 ----
        AccountDataSerializer.save(tag);
        PlayerFeatureDataSerializer.save(tag);
        CompanyDataSerializer.save(tag);
        MarketDataSerializer.save(tag);
        MetricsDataSerializer.save(tag);
        CandlestickDataSerializer.save(tag);
        FinancialDataSerializer.save(tag);
        DebtDataSerializer.save(tag);
        BondMarketDataSerializer.save(tag);
        CentralBankBillDataSerializer.save(tag);
        FuturesDataSerializer.save(tag);
        BankingDataSerializer.save(tag);
        DiagnosticDataSerializer.save(tag);
        FundDataSerializer.save(tag);
        InsuranceDataSerializer.save(tag);
        GovernanceDataSerializer.save(tag);



        // ---- 保存交易记录（仅最近 500 条） ----
        // ---- 保存成交历史 ----

        // ---- 保存活跃订单 ----

        // ---- 保存系统公司 ----

        // ---- 保存国际市场价格 ----

        // ---- 保存价格快照 ----

        // ---- 保存商品定义（管理员添加的自定义商品） ----

        // ---- 保存事件状态 ----

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
            stockTag.putLong("TreasuryShares",stock.getTreasuryShares());

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
        AccountDataSerializer.load(tag);
        PlayerFeatureDataSerializer.load(tag);
        // Company inventory keys are validated against the commodity registry.
        // Restore world-defined commodities first so those quantities are not lost.
        MarketDataSerializer.loadCommodityDefinitions(tag);
        CompanyDataSerializer.load(tag);
        MarketDataSerializer.loadMarketState(tag);
        MetricsDataSerializer.load(tag);
        CandlestickDataSerializer.load(tag);
        FinancialDataSerializer.load(tag);
        BankingDataSerializer.load(tag);
        DebtDataSerializer.load(tag);
        BondMarketDataSerializer.load(tag);
        CentralBankBillDataSerializer.load(tag);
        FuturesDataSerializer.load(tag);
        DiagnosticDataSerializer.load(tag);
        FundDataSerializer.load(tag);
        InsuranceDataSerializer.load(tag);
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
                if(stockTag.contains("TreasuryShares"))stock.restoreTreasuryShares(stockTag.getLong("TreasuryShares"));
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

        GovernanceDataSerializer.load(tag);
        RecentTradeService.rebuildFromHistories();
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
        EconomyMetricsService.clearDirect();
        RecentTradeService.clear();
        finance.chart.CandlestickService.clearDirect();
        FinancialCycleService.clearDirect();
        finance.index.MarketIndexService.clearDirect();
        finance.policy.MonetaryPolicyService.clearDirect();
        finance.debt.CorporateBondManager.clearDirect();
        finance.debt.CompanyLoanManager.clearDirect();
        finance.bondmarket.BondMarketManager.clearDirect();
        finance.bondmarket.BondPortfolioManager.clearDirect();
        finance.fixedincome.CentralBankBillManager.clearDirect();
        finance.futures.FuturesMarketManager.clearDirect();
        finance.futures.MarginManager.clearDirect();
        finance.futures.FuturesClearingService.clearDirect();
        finance.bank.BankingManager.clearDirect();
        finance.bank.InterbankMarketService.clearDirect();
        finance.bank.CentralBankLiquidityService.clearDirect();
        finance.bank.DepositInsuranceService.clearDirect();
        finance.diagnostic.DiagnosticManager.clear();
        finance.diagnostic.ModuleHealthRegistry.clear();
        finance.diagnostic.StartupSelfCheckService.clear();
        finance.fund.FundManager.clearDirect();
        finance.insurance.InsuranceManager.clearDirect();
        finance.governance.CorporateActionManager.clearDirect();
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
