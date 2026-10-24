package finance.gui;

import static finance.gui.CompanyRows.*;
import static finance.gui.DashboardRows.*;
import static finance.gui.MarketRows.*;
import static finance.gui.MenuCodec.*;
import static finance.gui.RecordRows.*;
import static finance.gui.StockRows.*;

import finance.gameplay.FinanceGameplayOpener;
import finance.gameplay.FinanceScreenMode;
import finance.gameplay.FinanceTerminalType;
import finance.registry.ModMenus;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 统一金融 GUI 菜单 —— 承载市场、订单、库存、公司等全部数据。
 */
public class FinanceMenu extends AbstractContainerMenu {

    // ---- 字段 ----
    // 数据字段不是 final：界面开着时由 replaceData 换成服务端推来的新快照；入口信息（模式、来源方块）保持不变。

    private List<MarketRow> marketData;
    private List<OrderRow> playerOrders;
    private long balance;
    private long frozenBalance;
    private Map<String, Integer> playerInventory;
    private CompanyInfo playerCompany; // null = 无公司
    private List<CompanyInfo> allCompanies;
    private List<StockRow> stocks;
    private List<StockHoldingRow> stockHoldings;
    private List<StockOrderRow> stockOrders;
    private List<TransactionRow> transactions;
    private AssetSummary assetSummary;
    private List<AssetRow> assetRows;
    private List<PriceAlertRow> priceAlerts;
    private List<ConditionalStockOrderRow> conditionalStockOrders;
    private List<CompanyFinancingRow> companyFinancingRows;
    private List<CompanyProposalRow> companyProposalRows;
    private EconomyDashboardRow dashboard;
    private double dividendRatio;
    private int dividendCycleDays;
    private Map<String, Integer> mcInventory; // 商品ID → MC物品栏数量
    private final FinanceScreenMode initialMode;
    private final FinanceTerminalType sourceType;
    private final String sourceDimension;
    private final BlockPos sourcePos;
    private long warehouseUsed;
    private long warehouseCapacity;
    private boolean warehouseOverCapacity;

    // ---- 构造 ----

    /** 从网络数据包反序列化 */
    public FinanceMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(
                containerId,
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
                buffer.readLong(),
                buffer.readLong(),
                buffer.readBoolean());
    }

    /** 从服务端直接构造 */
    public FinanceMenu(
            int containerId,
            List<MarketRow> marketData,
            List<OrderRow> playerOrders,
            long balance,
            long frozenBalance,
            Map<String, Integer> playerInventory,
            CompanyInfo playerCompany,
            List<CompanyInfo> allCompanies,
            List<StockRow> stocks,
            List<StockHoldingRow> stockHoldings,
            List<StockOrderRow> stockOrders,
            List<TransactionRow> transactions,
            AssetSummary assetSummary,
            List<AssetRow> assetRows,
            List<PriceAlertRow> priceAlerts,
            List<ConditionalStockOrderRow> conditionalStockOrders,
            List<CompanyFinancingRow> companyFinancingRows,
            List<CompanyProposalRow> companyProposalRows,
            EconomyDashboardRow dashboard,
            double dividendRatio,
            int dividendCycleDays,
            Map<String, Integer> mcInventory) {
        this(
                containerId,
                marketData,
                playerOrders,
                balance,
                frozenBalance,
                playerInventory,
                playerCompany,
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
                FinanceScreenMode.ADVANCED,
                FinanceTerminalType.LEGACY_FULL_SCREEN,
                "",
                null,
                0,
                0,
                false);
    }

    public FinanceMenu(
            int containerId,
            List<MarketRow> marketData,
            List<OrderRow> playerOrders,
            long balance,
            long frozenBalance,
            Map<String, Integer> playerInventory,
            CompanyInfo playerCompany,
            List<CompanyInfo> allCompanies,
            List<StockRow> stocks,
            List<StockHoldingRow> stockHoldings,
            List<StockOrderRow> stockOrders,
            List<TransactionRow> transactions,
            AssetSummary assetSummary,
            List<AssetRow> assetRows,
            List<PriceAlertRow> priceAlerts,
            List<ConditionalStockOrderRow> conditionalStockOrders,
            List<CompanyFinancingRow> companyFinancingRows,
            List<CompanyProposalRow> companyProposalRows,
            EconomyDashboardRow dashboard,
            double dividendRatio,
            int dividendCycleDays,
            Map<String, Integer> mcInventory,
            FinanceScreenMode initialMode,
            FinanceTerminalType sourceType,
            String sourceDimension,
            BlockPos sourcePos,
            long warehouseUsed,
            long warehouseCapacity,
            boolean warehouseOverCapacity) {
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

    public List<MarketRow> getMarketData() {
        return marketData;
    }

    public List<OrderRow> getPlayerOrders() {
        return playerOrders;
    }

    public long getBalance() {
        return balance;
    }

    public long getFrozenBalance() {
        return frozenBalance;
    }

    public Map<String, Integer> getPlayerInventory() {
        return playerInventory;
    }

    public CompanyInfo getPlayerCompany() {
        return playerCompany;
    }

    public List<CompanyInfo> getAllCompanies() {
        return allCompanies;
    }

    public List<StockRow> getStocks() {
        return stocks;
    }

    public List<StockHoldingRow> getStockHoldings() {
        return stockHoldings;
    }

    public List<StockOrderRow> getStockOrders() {
        return stockOrders;
    }

    public List<TransactionRow> getTransactions() {
        return transactions;
    }

    public AssetSummary getAssetSummary() {
        return assetSummary;
    }

    public List<AssetRow> getAssetRows() {
        return assetRows;
    }

    public List<PriceAlertRow> getPriceAlerts() {
        return priceAlerts;
    }

    public List<ConditionalStockOrderRow> getConditionalStockOrders() {
        return conditionalStockOrders;
    }

    public List<CompanyFinancingRow> getCompanyFinancingRows() {
        return companyFinancingRows;
    }

    public List<CompanyProposalRow> getCompanyProposalRows() {
        return companyProposalRows;
    }

    public EconomyDashboardRow getDashboard() {
        return dashboard;
    }

    public double getDividendRatio() {
        return dividendRatio;
    }

    public int getDividendCycleDays() {
        return dividendCycleDays;
    }

    public Map<String, Integer> getMcInventory() {
        return mcInventory;
    }

    public FinanceScreenMode getInitialMode() {
        return initialMode;
    }

    public FinanceTerminalType getSourceType() {
        return sourceType;
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    public long getWarehouseUsed() {
        return warehouseUsed;
    }

    public long getWarehouseCapacity() {
        return warehouseCapacity;
    }

    public boolean isWarehouseOverCapacity() {
        return warehouseOverCapacity;
    }

    /** 用新快照替换数据（服务端刷新和客户端收到刷新包时调用），不改入口模式与来源方块。 */
    public void replaceData(FinanceMenu fresh) {
        this.marketData = fresh.marketData;
        this.playerOrders = fresh.playerOrders;
        this.balance = fresh.balance;
        this.frozenBalance = fresh.frozenBalance;
        this.playerInventory = fresh.playerInventory;
        this.playerCompany = fresh.playerCompany;
        this.allCompanies = fresh.allCompanies;
        this.stocks = fresh.stocks;
        this.stockHoldings = fresh.stockHoldings;
        this.stockOrders = fresh.stockOrders;
        this.transactions = fresh.transactions;
        this.assetSummary = fresh.assetSummary;
        this.assetRows = fresh.assetRows;
        this.priceAlerts = fresh.priceAlerts;
        this.conditionalStockOrders = fresh.conditionalStockOrders;
        this.companyFinancingRows = fresh.companyFinancingRows;
        this.companyProposalRows = fresh.companyProposalRows;
        this.dashboard = fresh.dashboard;
        this.dividendRatio = fresh.dividendRatio;
        this.dividendCycleDays = fresh.dividendCycleDays;
        this.mcInventory = fresh.mcInventory;
        this.warehouseUsed = fresh.warehouseUsed;
        this.warehouseCapacity = fresh.warehouseCapacity;
        this.warehouseOverCapacity = fresh.warehouseOverCapacity;
    }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer serverPlayer)
                || FinanceGameplayOpener.isValidTerminalSession(serverPlayer, sourceType, sourceDimension, sourcePos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // ---- 序列化 ----

    public static void writeAll(
            FriendlyByteBuf buffer,
            List<MarketRow> marketData,
            List<OrderRow> playerOrders,
            long balance,
            long frozenBalance,
            Map<String, Integer> playerInventory,
            CompanyInfo playerCompany,
            List<CompanyInfo> allCompanies,
            List<StockRow> stocks,
            List<StockHoldingRow> stockHoldings,
            List<StockOrderRow> stockOrders,
            List<TransactionRow> transactions,
            AssetSummary assetSummary,
            List<AssetRow> assetRows,
            List<PriceAlertRow> priceAlerts,
            List<ConditionalStockOrderRow> conditionalStockOrders,
            List<CompanyFinancingRow> companyFinancingRows,
            List<CompanyProposalRow> companyProposalRows,
            EconomyDashboardRow dashboard,
            double dividendRatio,
            int dividendCycleDays,
            Map<String, Integer> mcInventory) {
        writeAll(
                buffer,
                marketData,
                playerOrders,
                balance,
                frozenBalance,
                playerInventory,
                playerCompany,
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
                FinanceScreenMode.ADVANCED,
                FinanceTerminalType.LEGACY_FULL_SCREEN,
                "",
                null,
                0,
                0,
                false);
    }

    public static void writeAll(
            FriendlyByteBuf buffer,
            List<MarketRow> marketData,
            List<OrderRow> playerOrders,
            long balance,
            long frozenBalance,
            Map<String, Integer> playerInventory,
            CompanyInfo playerCompany,
            List<CompanyInfo> allCompanies,
            List<StockRow> stocks,
            List<StockHoldingRow> stockHoldings,
            List<StockOrderRow> stockOrders,
            List<TransactionRow> transactions,
            AssetSummary assetSummary,
            List<AssetRow> assetRows,
            List<PriceAlertRow> priceAlerts,
            List<ConditionalStockOrderRow> conditionalStockOrders,
            List<CompanyFinancingRow> companyFinancingRows,
            List<CompanyProposalRow> companyProposalRows,
            EconomyDashboardRow dashboard,
            double dividendRatio,
            int dividendCycleDays,
            Map<String, Integer> mcInventory,
            FinanceScreenMode initialMode,
            FinanceTerminalType sourceType,
            String sourceDimension,
            BlockPos sourcePos,
            long warehouseUsed,
            long warehouseCapacity,
            boolean warehouseOverCapacity) {
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
}
