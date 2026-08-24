package finance.gui;

import finance.registry.ModMenus;
import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 统一金融 GUI 菜单 —— 承载市场、订单、库存、公司等全部数据。
 */
public class FinanceMenu extends AbstractContainerMenu {

    static final int DASHBOARD_TREND_LIMIT = 30;
    private static final int MAX_DECODED_DASHBOARD_TRENDS = 256;
    static final int MAX_MARKET_ROWS = 512;
    static final int MAX_ORDER_ROWS = 1_024;
    static final int MAX_MAP_ENTRIES = 1_024;
    static final int MAX_COMPANY_ROWS = 512;
    static final int MAX_STOCK_ROWS = 512;
    static final int MAX_HISTORY_POINTS = 1_024;
    static final int MAX_HOLDING_ROWS = 512;
    static final int MAX_STOCK_ORDER_ROWS = 1_024;
    static final int MAX_TRANSACTION_ROWS = 500;
    static final int MAX_ASSET_ROWS = 1_024;
    static final int MAX_ALERT_ROWS = 512;
    static final int MAX_CONDITIONAL_ORDER_ROWS = 512;
    static final int MAX_FINANCING_ROWS = 512;
    static final int MAX_PROPOSAL_ROWS = 1_024;

    // ---- 数据记录 ----

    public record MarketRow(String commodityId, long midPrice, long bidPrice, long askPrice,
                            double dayChange, int dayVolume, int marketStock,
                            long dayHigh, long dayLow, List<Long> priceHistory) {}

    public record OrderRow(UUID orderId, String commodityId, String type, long price, int quantity, boolean ownedByPlayer) {}

    public record CompanyInfo(UUID companyId, String name, String type, long cash, long inventoryValue,
                               long totalValue, Map<String, Integer> inventory, boolean playerOwned,
                               boolean isPublic, String strategy, int productionLevel,
                               int storageLevel, int managementLevel, double autoSellRatio,
                               long reportRevenue, long reportExpenses, long reportNetProfit,
                               long reportAssets, long reportLiabilities, long reportCash,
                               long reportProfitChange, long reportAssetChange, String reportSummary,
                               boolean bankruptcyRisk, long bankruptcyRiskStartDay) {}

    public record StockRow(String symbol, String name, long lastPrice, double dayChange,
                           long dayVolume, long availableShares, long fairValue,
                           long dayHigh, long dayLow, List<Long> priceHistory,
                           long expectedDividendPerShare, long lastDividendPerShare,
                           long lastDividendTotal) {}

    public record StockHoldingRow(String symbol, long quantity, long averageCost) {}

    public record StockOrderRow(UUID orderId, String symbol, String type, long price,
                                int quantity, boolean ownedByPlayer) {}

    public record TransactionRow(long timestamp, UUID playerId, String type,
                                 long amount, long quantity, String objectName) {}

    public record AssetSummary(long cash, long frozenCash, long commodityValue,
                               long stockValue, long totalAsset, long todayProfit) {}

    public record AssetRow(String category, String name, long quantity, long value,
                           double percent, long cost, long currentPrice, long floatingProfit) {}

    public record PriceAlertRow(UUID alertId, String type, String targetId,
                                String direction, long targetPrice) {}

    public record ConditionalStockOrderRow(UUID orderId, String symbol, String type,
                                           long triggerPrice, long quantity) {}

    public record CompanyFinancingRow(UUID projectId, UUID companyId, String companyName,
                                      String symbol, long issueQuantity, long issuePrice,
                                      long fundingTarget, long raisedAmount,
                                      long subscribedShares, long playerSubscribedShares,
                                      long deadlineMcDay) {}

    public record CompanyProposalRow(UUID proposalId, UUID companyId, String type, String title,
                                     String textValue, long value1, long value2, long value3,
                                     long startMcDay, long endMcDay, double passRatio,
                                     long yesVotes, long noVotes, boolean playerVoted,
                                     String status, String resultSummary, boolean playerCanExecute) {}

    public record EconomyTrendRow(long mcDay, long commodityVolume, long stockVolume,
                                  double priceIndex) {}

    public record EconomyDashboardRow(long playerCash, long playerFrozenFunds,
                                      long companyCash, long npcCash, long centralBankReserve,
                                      long totalMoney, long dailyCommodityVolume,
                                      long dailyStockVolume, double priceIndex,
                                      int bankruptcyRiskCompanies, String centralBankSummary,
                                      List<EconomyTrendRow> trends) {}

    // ---- 字段 ----

    private final List<MarketRow> marketData;
    private final List<OrderRow> playerOrders;
    private final long balance;
    private final long frozenBalance;
    private final Map<String, Integer> playerInventory;
    private final CompanyInfo playerCompany; // null = 无公司
    private final List<CompanyInfo> allCompanies;
    private final List<StockRow> stocks;
    private final List<StockHoldingRow> stockHoldings;
    private final List<StockOrderRow> stockOrders;
    private final List<TransactionRow> transactions;
    private final AssetSummary assetSummary;
    private final List<AssetRow> assetRows;
    private final List<PriceAlertRow> priceAlerts;
    private final List<ConditionalStockOrderRow> conditionalStockOrders;
    private final List<CompanyFinancingRow> companyFinancingRows;
    private final List<CompanyProposalRow> companyProposalRows;
    private final EconomyDashboardRow dashboard;
    private final double dividendRatio;
    private final int dividendCycleDays;
    private final Map<String, Integer> mcInventory; // 商品ID → MC物品栏数量
    private final FinanceScreenMode initialMode;
    private final FinanceTerminalType sourceType;
    private final String sourceDimension;
    private final BlockPos sourcePos;
    private final long warehouseUsed;
    private final long warehouseCapacity;
    private final boolean warehouseOverCapacity;

    // ---- 构造 ----

    /** 从网络数据包反序列化 */
    public FinanceMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId,
                readMarketData(buffer),
                readOrderRows(buffer),
                buffer.readVarLong(),
                buffer.readVarLong(),
                readStringIntMap(buffer),
                readCompanyInfo(buffer),
                readCompanyInfoList(buffer),
                readStockRows(buffer),
                readStockHoldingRows(buffer),
                readStockOrderRows(buffer),
                readTransactionRows(buffer),
                readAssetSummary(buffer),
                readAssetRows(buffer),
                readPriceAlertRows(buffer),
                readConditionalStockOrderRows(buffer),
                readCompanyFinancingRows(buffer),
                readCompanyProposalRows(buffer),
                readDashboard(buffer),
                buffer.readDouble(),
                buffer.readVarInt(),
                readStringIntMap(buffer),
                buffer.readEnum(FinanceScreenMode.class),
                buffer.readEnum(FinanceTerminalType.class),
                buffer.readUtf(128),
                buffer.readBoolean() ? buffer.readBlockPos() : null,
                buffer.readLong(), buffer.readLong(), buffer.readBoolean());
    }

    /** 从服务端直接构造 */
    public FinanceMenu(int containerId, List<MarketRow> marketData, List<OrderRow> playerOrders,
                       long balance, long frozenBalance, Map<String, Integer> playerInventory,
                       CompanyInfo playerCompany, List<CompanyInfo> allCompanies,
                       List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                       List<StockOrderRow> stockOrders, List<TransactionRow> transactions,
                       AssetSummary assetSummary, List<AssetRow> assetRows,
                       List<PriceAlertRow> priceAlerts,
                       List<ConditionalStockOrderRow> conditionalStockOrders,
                       List<CompanyFinancingRow> companyFinancingRows,
                       List<CompanyProposalRow> companyProposalRows,
                       EconomyDashboardRow dashboard,
                       double dividendRatio, int dividendCycleDays,
                       Map<String, Integer> mcInventory) {
        this(containerId, marketData, playerOrders, balance, frozenBalance, playerInventory,
                playerCompany, allCompanies, stocks, stockHoldings, stockOrders, transactions,
                assetSummary, assetRows, priceAlerts, conditionalStockOrders, companyFinancingRows,
                companyProposalRows, dashboard, dividendRatio, dividendCycleDays, mcInventory,
                FinanceScreenMode.ADVANCED, FinanceTerminalType.LEGACY_FULL_SCREEN, "", null, 0, 0, false);
    }

    public FinanceMenu(int containerId, List<MarketRow> marketData, List<OrderRow> playerOrders,
                       long balance, long frozenBalance, Map<String, Integer> playerInventory,
                       CompanyInfo playerCompany, List<CompanyInfo> allCompanies,
                       List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                       List<StockOrderRow> stockOrders, List<TransactionRow> transactions,
                       AssetSummary assetSummary, List<AssetRow> assetRows,
                       List<PriceAlertRow> priceAlerts,
                       List<ConditionalStockOrderRow> conditionalStockOrders,
                       List<CompanyFinancingRow> companyFinancingRows,
                       List<CompanyProposalRow> companyProposalRows,
                       EconomyDashboardRow dashboard,
                       double dividendRatio, int dividendCycleDays,
                       Map<String, Integer> mcInventory,
                       FinanceScreenMode initialMode, FinanceTerminalType sourceType,
                       String sourceDimension, BlockPos sourcePos,
                       long warehouseUsed, long warehouseCapacity, boolean warehouseOverCapacity) {
        super(ModMenus.FINANCE.get(), containerId);
        this.marketData = marketData;
        this.playerOrders = playerOrders;
        this.balance = balance;
        this.frozenBalance = frozenBalance;
        this.playerInventory = playerInventory;
        this.playerCompany = playerCompany;
        this.allCompanies = allCompanies;
        this.stocks = stocks;
        this.stockHoldings = stockHoldings;
        this.stockOrders = stockOrders;
        this.transactions = transactions;
        this.assetSummary = assetSummary;
        this.assetRows = assetRows;
        this.priceAlerts = priceAlerts;
        this.conditionalStockOrders = conditionalStockOrders;
        this.companyFinancingRows = companyFinancingRows;
        this.companyProposalRows = companyProposalRows;
        this.dashboard = dashboard;
        this.dividendRatio = dividendRatio;
        this.dividendCycleDays = dividendCycleDays;
        this.mcInventory = mcInventory;
        this.initialMode = initialMode == null ? FinanceScreenMode.ADVANCED : initialMode;
        this.sourceType = sourceType == null ? FinanceTerminalType.LEGACY_FULL_SCREEN : sourceType;
        this.sourceDimension = sourceDimension == null ? "" : sourceDimension;
        this.sourcePos = sourcePos == null ? null : sourcePos.immutable();
        this.warehouseUsed = Math.max(0, warehouseUsed);
        this.warehouseCapacity = Math.max(0, warehouseCapacity);
        this.warehouseOverCapacity = warehouseOverCapacity;
    }

    // ---- getter ----

    public List<MarketRow> getMarketData() { return marketData; }
    public List<OrderRow> getPlayerOrders() { return playerOrders; }
    public long getBalance() { return balance; }
    public long getFrozenBalance() { return frozenBalance; }
    public Map<String, Integer> getPlayerInventory() { return playerInventory; }
    public CompanyInfo getPlayerCompany() { return playerCompany; }
    public List<CompanyInfo> getAllCompanies() { return allCompanies; }
    public List<StockRow> getStocks() { return stocks; }
    public List<StockHoldingRow> getStockHoldings() { return stockHoldings; }
    public List<StockOrderRow> getStockOrders() { return stockOrders; }
    public List<TransactionRow> getTransactions() { return transactions; }
    public AssetSummary getAssetSummary() { return assetSummary; }
    public List<AssetRow> getAssetRows() { return assetRows; }
    public List<PriceAlertRow> getPriceAlerts() { return priceAlerts; }
    public List<ConditionalStockOrderRow> getConditionalStockOrders() { return conditionalStockOrders; }
    public List<CompanyFinancingRow> getCompanyFinancingRows() { return companyFinancingRows; }
    public List<CompanyProposalRow> getCompanyProposalRows() { return companyProposalRows; }
    public EconomyDashboardRow getDashboard() { return dashboard; }
    public double getDividendRatio() { return dividendRatio; }
    public int getDividendCycleDays() { return dividendCycleDays; }
    public Map<String, Integer> getMcInventory() { return mcInventory; }
    public FinanceScreenMode getInitialMode() { return initialMode; }
    public FinanceTerminalType getSourceType() { return sourceType; }
    public String getSourceDimension() { return sourceDimension; }
    public BlockPos getSourcePos() { return sourcePos; }
    public long getWarehouseUsed() { return warehouseUsed; }
    public long getWarehouseCapacity() { return warehouseCapacity; }
    public boolean isWarehouseOverCapacity() { return warehouseOverCapacity; }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer serverPlayer)
                || FinanceGameplayOpener.isValidTerminalSession(serverPlayer, sourceType, sourceDimension, sourcePos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ---- 序列化 ----

    public static void writeAll(FriendlyByteBuf buffer, List<MarketRow> marketData,
                                 List<OrderRow> playerOrders, long balance, long frozenBalance,
                                 Map<String, Integer> playerInventory, CompanyInfo playerCompany,
                                 List<CompanyInfo> allCompanies,
                                 List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                                 List<StockOrderRow> stockOrders, List<TransactionRow> transactions,
                                 AssetSummary assetSummary, List<AssetRow> assetRows,
                                 List<PriceAlertRow> priceAlerts,
                                 List<ConditionalStockOrderRow> conditionalStockOrders,
                                 List<CompanyFinancingRow> companyFinancingRows,
                                 List<CompanyProposalRow> companyProposalRows,
                                 EconomyDashboardRow dashboard,
                                 double dividendRatio, int dividendCycleDays,
                                 Map<String, Integer> mcInventory) {
        writeAll(buffer, marketData, playerOrders, balance, frozenBalance, playerInventory,
                playerCompany, allCompanies, stocks, stockHoldings, stockOrders, transactions,
                assetSummary, assetRows, priceAlerts, conditionalStockOrders, companyFinancingRows,
                companyProposalRows, dashboard, dividendRatio, dividendCycleDays, mcInventory,
                FinanceScreenMode.ADVANCED, FinanceTerminalType.LEGACY_FULL_SCREEN, "", null, 0, 0, false);
    }

    public static void writeAll(FriendlyByteBuf buffer, List<MarketRow> marketData,
                                 List<OrderRow> playerOrders, long balance, long frozenBalance,
                                 Map<String, Integer> playerInventory, CompanyInfo playerCompany,
                                 List<CompanyInfo> allCompanies,
                                 List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                                 List<StockOrderRow> stockOrders, List<TransactionRow> transactions,
                                 AssetSummary assetSummary, List<AssetRow> assetRows,
                                 List<PriceAlertRow> priceAlerts,
                                 List<ConditionalStockOrderRow> conditionalStockOrders,
                                 List<CompanyFinancingRow> companyFinancingRows,
                                 List<CompanyProposalRow> companyProposalRows,
                                 EconomyDashboardRow dashboard,
                                 double dividendRatio, int dividendCycleDays,
                                 Map<String, Integer> mcInventory,
                                 FinanceScreenMode initialMode, FinanceTerminalType sourceType,
                                 String sourceDimension, BlockPos sourcePos,
                                 long warehouseUsed, long warehouseCapacity, boolean warehouseOverCapacity) {
        writeMarketData(buffer, marketData);
        writeOrderRows(buffer, playerOrders);
        buffer.writeVarLong(balance);
        buffer.writeVarLong(frozenBalance);
        writeStringIntMap(buffer, playerInventory);
        writeCompanyInfo(buffer, playerCompany);
        writeCompanyInfoList(buffer, allCompanies);
        writeStockRows(buffer, stocks);
        writeStockHoldingRows(buffer, stockHoldings);
        writeStockOrderRows(buffer, stockOrders);
        writeTransactionRows(buffer, transactions);
        writeAssetSummary(buffer, assetSummary);
        writeAssetRows(buffer, assetRows);
        writePriceAlertRows(buffer, priceAlerts);
        writeConditionalStockOrderRows(buffer, conditionalStockOrders);
        writeCompanyFinancingRows(buffer, companyFinancingRows);
        writeCompanyProposalRows(buffer, companyProposalRows);
        writeDashboard(buffer, dashboard);
        buffer.writeDouble(dividendRatio);
        buffer.writeVarInt(dividendCycleDays);
        writeStringIntMap(buffer, mcInventory != null ? mcInventory : new LinkedHashMap<>());
        buffer.writeEnum(initialMode == null ? FinanceScreenMode.ADVANCED : initialMode);
        buffer.writeEnum(sourceType == null ? FinanceTerminalType.LEGACY_FULL_SCREEN : sourceType);
        buffer.writeUtf(limitString(sourceDimension, 128), 128);
        buffer.writeBoolean(sourcePos != null);
        if (sourcePos != null) buffer.writeBlockPos(sourcePos);
        buffer.writeLong(Math.max(0, warehouseUsed));
        buffer.writeLong(Math.max(0, warehouseCapacity));
        buffer.writeBoolean(warehouseOverCapacity);
    }

    static void writeMarketData(FriendlyByteBuf buffer, List<MarketRow> list) {
        List<MarketRow> safe = bounded(list, MAX_MARKET_ROWS);
        buffer.writeVarInt(safe.size());
        for (MarketRow r : safe) {
            buffer.writeUtf(limitString(r.commodityId(), 64), 64);
            buffer.writeLong(r.midPrice());
            buffer.writeLong(r.bidPrice());
            buffer.writeLong(r.askPrice());
            buffer.writeDouble(r.dayChange());
            buffer.writeVarInt(r.dayVolume());
            buffer.writeVarInt(r.marketStock());
            buffer.writeLong(r.dayHigh());
            buffer.writeLong(r.dayLow());
            writeLongList(buffer, r.priceHistory());
        }
    }

    static List<MarketRow> readMarketData(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "market rows", MAX_MARKET_ROWS);
        List<MarketRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MarketRow(
                    buffer.readUtf(64), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readLong(), buffer.readLong(), readLongList(buffer)));
        }
        return list;
    }

    private static void writeOrderRows(FriendlyByteBuf buffer, List<OrderRow> list) {
        List<OrderRow> safe = bounded(list, MAX_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (OrderRow r : safe) {
            buffer.writeUUID(r.orderId());
            buffer.writeUtf(limitString(r.commodityId(), 64), 64);
            buffer.writeUtf(limitString(r.type(), 16), 16);
            buffer.writeLong(r.price());
            buffer.writeVarInt(r.quantity());
            buffer.writeBoolean(r.ownedByPlayer());
        }
    }

    private static List<OrderRow> readOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "commodity orders", MAX_ORDER_ROWS);
        List<OrderRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new OrderRow(
                    buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(16),
                    buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return list;
    }

    private static void writeStringIntMap(FriendlyByteBuf buffer, Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = bounded(new ArrayList<>(map.entrySet()), MAX_MAP_ENTRIES);
        buffer.writeVarInt(entries.size());
        for (Map.Entry<String, Integer> entry : entries) {
            buffer.writeUtf(limitString(entry.getKey(), 64), 64);
            buffer.writeVarInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readStringIntMap(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "string map", MAX_MAP_ENTRIES);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buffer.readUtf(64), buffer.readVarInt());
        }
        return map;
    }

    private static void writeCompanyInfo(FriendlyByteBuf buffer, CompanyInfo info) {
        buffer.writeBoolean(info != null);
        if (info != null) {
            buffer.writeUUID(info.companyId());
            buffer.writeUtf(limitString(info.name(), 64), 64);
            buffer.writeUtf(limitString(info.type(), 32), 32);
            buffer.writeLong(info.cash());
            buffer.writeLong(info.inventoryValue());
            buffer.writeLong(info.totalValue());
            writeStringIntMap(buffer, info.inventory());
            buffer.writeBoolean(info.playerOwned());
            buffer.writeBoolean(info.isPublic());
            buffer.writeUtf(limitString(info.strategy(), 32), 32);
            buffer.writeVarInt(info.productionLevel());
            buffer.writeVarInt(info.storageLevel());
            buffer.writeVarInt(info.managementLevel());
            buffer.writeDouble(info.autoSellRatio());
            buffer.writeLong(info.reportRevenue());
            buffer.writeLong(info.reportExpenses());
            buffer.writeLong(info.reportNetProfit());
            buffer.writeLong(info.reportAssets());
            buffer.writeLong(info.reportLiabilities());
            buffer.writeLong(info.reportCash());
            buffer.writeLong(info.reportProfitChange());
            buffer.writeLong(info.reportAssetChange());
            buffer.writeUtf(limitString(info.reportSummary(), 128), 128);
            buffer.writeBoolean(info.bankruptcyRisk());
            buffer.writeLong(info.bankruptcyRiskStartDay());
        }
    }

    private static CompanyInfo readCompanyInfo(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        return new CompanyInfo(
                buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(32), buffer.readLong(),
                buffer.readLong(), buffer.readLong(), readStringIntMap(buffer),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(),
                buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), buffer.readLong(), buffer.readUtf(128),
                buffer.readBoolean(), buffer.readLong());
    }

    private static void writeCompanyInfoList(FriendlyByteBuf buffer, List<CompanyInfo> companies) {
        List<CompanyInfo> safe = bounded(companies, MAX_COMPANY_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyInfo company : safe) {
            writeCompanyInfo(buffer, company);
        }
    }

    private static List<CompanyInfo> readCompanyInfoList(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "companies", MAX_COMPANY_ROWS);
        List<CompanyInfo> companies = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompanyInfo company = readCompanyInfo(buffer);
            if (company != null) {
                companies.add(company);
            }
        }
        return companies;
    }

    private static void writeStockRows(FriendlyByteBuf buffer, List<StockRow> rows) {
        List<StockRow> safe = bounded(rows, MAX_STOCK_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockRow row : safe) {
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.name(), 64), 64);
            buffer.writeLong(row.lastPrice());
            buffer.writeDouble(row.dayChange());
            buffer.writeLong(row.dayVolume());
            buffer.writeLong(row.availableShares());
            buffer.writeLong(row.fairValue());
            buffer.writeLong(row.dayHigh());
            buffer.writeLong(row.dayLow());
            writeLongList(buffer, row.priceHistory());
            buffer.writeLong(row.expectedDividendPerShare());
            buffer.writeLong(row.lastDividendPerShare());
            buffer.writeLong(row.lastDividendTotal());
        }
    }

    private static List<StockRow> readStockRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stocks", MAX_STOCK_ROWS);
        List<StockRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockRow(
                    buffer.readUtf(16), buffer.readUtf(64), buffer.readLong(),
                    buffer.readDouble(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), readLongList(buffer),
                    buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }

    private static void writeLongList(FriendlyByteBuf buffer, List<Long> values) {
        List<Long> safe = tail(values, MAX_HISTORY_POINTS);
        buffer.writeVarInt(safe.size());
        for (Long value : safe) {
            buffer.writeLong(value != null ? value : 0L);
        }
    }

    private static List<Long> readLongList(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "price history", MAX_HISTORY_POINTS);
        List<Long> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readLong());
        }
        return values;
    }

    private static void writeStockHoldingRows(FriendlyByteBuf buffer, List<StockHoldingRow> rows) {
        List<StockHoldingRow> safe = bounded(rows, MAX_HOLDING_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockHoldingRow row : safe) {
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeLong(row.quantity());
            buffer.writeLong(row.averageCost());
        }
    }

    private static List<StockHoldingRow> readStockHoldingRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stock holdings", MAX_HOLDING_ROWS);
        List<StockHoldingRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockHoldingRow(buffer.readUtf(16), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }

    private static void writeStockOrderRows(FriendlyByteBuf buffer, List<StockOrderRow> rows) {
        List<StockOrderRow> safe = bounded(rows, MAX_STOCK_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (StockOrderRow row : safe) {
            buffer.writeUUID(row.orderId());
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeLong(row.price());
            buffer.writeVarInt(row.quantity());
            buffer.writeBoolean(row.ownedByPlayer());
        }
    }

    private static List<StockOrderRow> readStockOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "stock orders", MAX_STOCK_ORDER_ROWS);
        List<StockOrderRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockOrderRow(
                    buffer.readUUID(), buffer.readUtf(16), buffer.readUtf(16),
                    buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return rows;
    }

    private static void writeTransactionRows(FriendlyByteBuf buffer, List<TransactionRow> rows) {
        List<TransactionRow> safe = tail(rows, MAX_TRANSACTION_ROWS);
        buffer.writeVarInt(safe.size());
        for (TransactionRow row : safe) {
            buffer.writeLong(row.timestamp());
            buffer.writeUUID(row.playerId());
            buffer.writeUtf(limitString(row.type(), 32), 32);
            buffer.writeLong(row.amount());
            buffer.writeLong(row.quantity());
            buffer.writeUtf(limitString(row.objectName(), 96), 96);
        }
    }

    private static List<TransactionRow> readTransactionRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "transactions", MAX_TRANSACTION_ROWS);
        List<TransactionRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new TransactionRow(
                    buffer.readLong(),
                    buffer.readUUID(),
                    buffer.readUtf(32),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readUtf(96)));
        }
        return rows;
    }

    private static void writeAssetSummary(FriendlyByteBuf buffer, AssetSummary summary) {
        AssetSummary safe = summary != null ? summary : new AssetSummary(0, 0, 0, 0, 0, 0);
        buffer.writeLong(safe.cash());
        buffer.writeLong(safe.frozenCash());
        buffer.writeLong(safe.commodityValue());
        buffer.writeLong(safe.stockValue());
        buffer.writeLong(safe.totalAsset());
        buffer.writeLong(safe.todayProfit());
    }

    private static AssetSummary readAssetSummary(FriendlyByteBuf buffer) {
        return new AssetSummary(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong());
    }

    private static void writeAssetRows(FriendlyByteBuf buffer, List<AssetRow> rows) {
        List<AssetRow> safe = bounded(rows, MAX_ASSET_ROWS);
        buffer.writeVarInt(safe.size());
        for (AssetRow row : safe) {
            buffer.writeUtf(limitString(row.category(), 16), 16);
            buffer.writeUtf(limitString(row.name(), 64), 64);
            buffer.writeLong(row.quantity());
            buffer.writeLong(row.value());
            buffer.writeDouble(row.percent());
            buffer.writeLong(row.cost());
            buffer.writeLong(row.currentPrice());
            buffer.writeLong(row.floatingProfit());
        }
    }

    private static List<AssetRow> readAssetRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "asset rows", MAX_ASSET_ROWS);
        List<AssetRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new AssetRow(
                    buffer.readUtf(16),
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return rows;
    }

    private static void writePriceAlertRows(FriendlyByteBuf buffer, List<PriceAlertRow> rows) {
        List<PriceAlertRow> safe = bounded(rows, MAX_ALERT_ROWS);
        buffer.writeVarInt(safe.size());
        for (PriceAlertRow row : safe) {
            buffer.writeUUID(row.alertId());
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeUtf(limitString(row.targetId(), 64), 64);
            buffer.writeUtf(limitString(row.direction(), 16), 16);
            buffer.writeLong(row.targetPrice());
        }
    }

    private static List<PriceAlertRow> readPriceAlertRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "price alerts", MAX_ALERT_ROWS);
        List<PriceAlertRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new PriceAlertRow(
                    buffer.readUUID(),
                    buffer.readUtf(16),
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readLong()));
        }
        return rows;
    }

    private static void writeConditionalStockOrderRows(FriendlyByteBuf buffer, List<ConditionalStockOrderRow> rows) {
        List<ConditionalStockOrderRow> safe = bounded(rows, MAX_CONDITIONAL_ORDER_ROWS);
        buffer.writeVarInt(safe.size());
        for (ConditionalStockOrderRow row : safe) {
            buffer.writeUUID(row.orderId());
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeUtf(limitString(row.type(), 16), 16);
            buffer.writeLong(row.triggerPrice());
            buffer.writeLong(row.quantity());
        }
    }

    private static List<ConditionalStockOrderRow> readConditionalStockOrderRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "conditional stock orders", MAX_CONDITIONAL_ORDER_ROWS);
        List<ConditionalStockOrderRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new ConditionalStockOrderRow(
                    buffer.readUUID(),
                    buffer.readUtf(16),
                    buffer.readUtf(16),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return rows;
    }

    private static void writeCompanyFinancingRows(FriendlyByteBuf buffer, List<CompanyFinancingRow> rows) {
        List<CompanyFinancingRow> safe = bounded(rows, MAX_FINANCING_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyFinancingRow row : safe) {
            buffer.writeUUID(row.projectId());
            buffer.writeUUID(row.companyId());
            buffer.writeUtf(limitString(row.companyName(), 64), 64);
            buffer.writeUtf(limitString(row.symbol(), 16), 16);
            buffer.writeLong(row.issueQuantity());
            buffer.writeLong(row.issuePrice());
            buffer.writeLong(row.fundingTarget());
            buffer.writeLong(row.raisedAmount());
            buffer.writeLong(row.subscribedShares());
            buffer.writeLong(row.playerSubscribedShares());
            buffer.writeLong(row.deadlineMcDay());
        }
    }

    private static List<CompanyFinancingRow> readCompanyFinancingRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "company financing rows", MAX_FINANCING_ROWS);
        List<CompanyFinancingRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new CompanyFinancingRow(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return rows;
    }

    private static void writeCompanyProposalRows(FriendlyByteBuf buffer, List<CompanyProposalRow> rows) {
        List<CompanyProposalRow> safe = bounded(rows, MAX_PROPOSAL_ROWS);
        buffer.writeVarInt(safe.size());
        for (CompanyProposalRow row : safe) {
            buffer.writeUUID(row.proposalId());
            buffer.writeUUID(row.companyId());
            buffer.writeUtf(limitString(row.type(), 24), 24);
            buffer.writeUtf(limitString(row.title(), 32), 32);
            buffer.writeUtf(limitString(row.textValue(), 64), 64);
            buffer.writeLong(row.value1());
            buffer.writeLong(row.value2());
            buffer.writeLong(row.value3());
            buffer.writeLong(row.startMcDay());
            buffer.writeLong(row.endMcDay());
            buffer.writeDouble(row.passRatio());
            buffer.writeLong(row.yesVotes());
            buffer.writeLong(row.noVotes());
            buffer.writeBoolean(row.playerVoted());
            buffer.writeUtf(limitString(row.status(), 16), 16);
            buffer.writeUtf(limitString(row.resultSummary(), 96), 96);
            buffer.writeBoolean(row.playerCanExecute());
        }
    }

    private static List<CompanyProposalRow> readCompanyProposalRows(FriendlyByteBuf buffer) {
        int size = readBoundedSize(buffer, "company proposals", MAX_PROPOSAL_ROWS);
        List<CompanyProposalRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new CompanyProposalRow(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(24),
                    buffer.readUtf(32),
                    buffer.readUtf(64),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readBoolean(),
                    buffer.readUtf(16),
                    buffer.readUtf(96),
                    buffer.readBoolean()));
        }
        return rows;
    }

    static void writeDashboard(FriendlyByteBuf buffer, EconomyDashboardRow row) {
        EconomyDashboardRow safe = row != null ? row
                : new EconomyDashboardRow(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0, "", List.of());
        buffer.writeLong(safe.playerCash());
        buffer.writeLong(safe.playerFrozenFunds());
        buffer.writeLong(safe.companyCash());
        buffer.writeLong(safe.npcCash());
        buffer.writeLong(safe.centralBankReserve());
        buffer.writeLong(safe.totalMoney());
        buffer.writeLong(safe.dailyCommodityVolume());
        buffer.writeLong(safe.dailyStockVolume());
        buffer.writeDouble(safe.priceIndex());
        buffer.writeVarInt(safe.bankruptcyRiskCompanies());
        buffer.writeUtf(limitString(safe.centralBankSummary(), 128), 128);
        List<EconomyTrendRow> trends = safe.trends() == null ? List.of() : safe.trends();
        buffer.writeVarInt(Math.min(DASHBOARD_TREND_LIMIT, trends.size()));
        int start = Math.max(0, trends.size() - DASHBOARD_TREND_LIMIT);
        for (int index = start; index < trends.size(); index++) {
            EconomyTrendRow trend = trends.get(index);
            buffer.writeLong(trend.mcDay());
            buffer.writeLong(trend.commodityVolume());
            buffer.writeLong(trend.stockVolume());
            buffer.writeDouble(trend.priceIndex());
        }
    }

    static EconomyDashboardRow readDashboard(FriendlyByteBuf buffer) {
        return new EconomyDashboardRow(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readUtf(128),
                readDashboardTrends(buffer));
    }

    private static List<EconomyTrendRow> readDashboardTrends(FriendlyByteBuf buffer) {
        int encodedSize = buffer.readVarInt();
        if (encodedSize < 0 || encodedSize > MAX_DECODED_DASHBOARD_TRENDS) {
            throw new IllegalArgumentException("Invalid dashboard trend count: " + encodedSize);
        }
        int retainedSize = Math.min(DASHBOARD_TREND_LIMIT, encodedSize);
        List<EconomyTrendRow> trends = new ArrayList<>(retainedSize);
        for (int index = 0; index < encodedSize; index++) {
            EconomyTrendRow trend = new EconomyTrendRow(
                    buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readDouble());
            if (index >= encodedSize - retainedSize) {
                trends.add(trend);
            }
        }
        return trends;
    }

    static int readBoundedSize(FriendlyByteBuf buffer, String field, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + size);
        }
        return size;
    }

    private static <T> List<T> bounded(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return values.size() <= maximum ? values : values.subList(0, maximum);
    }

    private static <T> List<T> tail(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return values.size() <= maximum ? values : values.subList(values.size() - maximum, values.size());
    }

    private static String limitString(String text, int maxLength) {
        String safe = text == null ? "" : text;
        return safe.length() > maxLength ? safe.substring(0, maxLength) : safe;
    }
}
