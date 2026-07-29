package finance.gui;

import finance.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 统一金融 GUI 菜单 —— 承载市场、订单、库存、公司等全部数据。
 */
public class FinanceMenu extends AbstractContainerMenu {

    // ---- 数据记录 ----

    public record MarketRow(String commodityId, long midPrice, long bidPrice, long askPrice,
                            double dayChange, int dayVolume, int marketStock) {}

    public record OrderRow(UUID orderId, String commodityId, String type, long price, int quantity, boolean ownedByPlayer) {}

    public record CompanyInfo(UUID companyId, String name, String type, long cash, long inventoryValue,
                               long totalValue, Map<String, Integer> inventory, boolean playerOwned,
                               boolean isPublic) {}

    public record StockRow(String symbol, String name, long lastPrice, double dayChange,
                           long dayVolume, long availableShares, long fairValue) {}

    public record StockHoldingRow(String symbol, long quantity, long averageCost) {}

    public record StockOrderRow(UUID orderId, String symbol, String type, long price,
                                int quantity, boolean ownedByPlayer) {}

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
    private final Map<String, Integer> mcInventory; // 商品ID → MC物品栏数量

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
                readStringIntMap(buffer));
    }

    /** 从服务端直接构造 */
    public FinanceMenu(int containerId, List<MarketRow> marketData, List<OrderRow> playerOrders,
                       long balance, long frozenBalance, Map<String, Integer> playerInventory,
                       CompanyInfo playerCompany, List<CompanyInfo> allCompanies,
                       List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                       List<StockOrderRow> stockOrders, Map<String, Integer> mcInventory) {
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
        this.mcInventory = mcInventory;
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
    public Map<String, Integer> getMcInventory() { return mcInventory; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ---- 序列化 ----

    public static void writeAll(FriendlyByteBuf buffer, List<MarketRow> marketData,
                                 List<OrderRow> playerOrders, long balance, long frozenBalance,
                                 Map<String, Integer> playerInventory, CompanyInfo playerCompany,
                                 List<CompanyInfo> allCompanies,
                                 List<StockRow> stocks, List<StockHoldingRow> stockHoldings,
                                 List<StockOrderRow> stockOrders,
                                 Map<String, Integer> mcInventory) {
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
        writeStringIntMap(buffer, mcInventory != null ? mcInventory : new LinkedHashMap<>());
    }

    private static void writeMarketData(FriendlyByteBuf buffer, List<MarketRow> list) {
        buffer.writeVarInt(list.size());
        for (MarketRow r : list) {
            buffer.writeUtf(r.commodityId());
            buffer.writeLong(r.midPrice());
            buffer.writeLong(r.bidPrice());
            buffer.writeLong(r.askPrice());
            buffer.writeDouble(r.dayChange());
            buffer.writeVarInt(r.dayVolume());
            buffer.writeVarInt(r.marketStock());
        }
    }

    private static List<MarketRow> readMarketData(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MarketRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MarketRow(
                    buffer.readUtf(), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt()));
        }
        return list;
    }

    private static void writeOrderRows(FriendlyByteBuf buffer, List<OrderRow> list) {
        buffer.writeVarInt(list.size());
        for (OrderRow r : list) {
            buffer.writeUUID(r.orderId());
            buffer.writeUtf(r.commodityId());
            buffer.writeUtf(r.type());
            buffer.writeLong(r.price());
            buffer.writeVarInt(r.quantity());
            buffer.writeBoolean(r.ownedByPlayer());
        }
    }

    private static List<OrderRow> readOrderRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<OrderRow> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new OrderRow(
                    buffer.readUUID(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return list;
    }

    private static void writeStringIntMap(FriendlyByteBuf buffer, Map<String, Integer> map) {
        buffer.writeVarInt(map.size());
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readStringIntMap(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buffer.readUtf(), buffer.readVarInt());
        }
        return map;
    }

    private static void writeCompanyInfo(FriendlyByteBuf buffer, CompanyInfo info) {
        buffer.writeBoolean(info != null);
        if (info != null) {
            buffer.writeUUID(info.companyId());
            buffer.writeUtf(info.name());
            buffer.writeUtf(info.type());
            buffer.writeLong(info.cash());
            buffer.writeLong(info.inventoryValue());
            buffer.writeLong(info.totalValue());
            writeStringIntMap(buffer, info.inventory());
            buffer.writeBoolean(info.playerOwned());
            buffer.writeBoolean(info.isPublic());
        }
    }

    private static CompanyInfo readCompanyInfo(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        return new CompanyInfo(
                buffer.readUUID(), buffer.readUtf(), buffer.readUtf(), buffer.readLong(),
                buffer.readLong(), buffer.readLong(), readStringIntMap(buffer),
                buffer.readBoolean(), buffer.readBoolean());
    }

    private static void writeCompanyInfoList(FriendlyByteBuf buffer, List<CompanyInfo> companies) {
        buffer.writeVarInt(companies.size());
        for (CompanyInfo company : companies) {
            writeCompanyInfo(buffer, company);
        }
    }

    private static List<CompanyInfo> readCompanyInfoList(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
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
        buffer.writeVarInt(rows.size());
        for (StockRow row : rows) {
            buffer.writeUtf(row.symbol());
            buffer.writeUtf(row.name());
            buffer.writeLong(row.lastPrice());
            buffer.writeDouble(row.dayChange());
            buffer.writeLong(row.dayVolume());
            buffer.writeLong(row.availableShares());
            buffer.writeLong(row.fairValue());
        }
    }

    private static List<StockRow> readStockRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<StockRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockRow(
                    buffer.readUtf(), buffer.readUtf(), buffer.readLong(),
                    buffer.readDouble(), buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }

    private static void writeStockHoldingRows(FriendlyByteBuf buffer, List<StockHoldingRow> rows) {
        buffer.writeVarInt(rows.size());
        for (StockHoldingRow row : rows) {
            buffer.writeUtf(row.symbol());
            buffer.writeLong(row.quantity());
            buffer.writeLong(row.averageCost());
        }
    }

    private static List<StockHoldingRow> readStockHoldingRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<StockHoldingRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockHoldingRow(buffer.readUtf(), buffer.readLong(), buffer.readLong()));
        }
        return rows;
    }

    private static void writeStockOrderRows(FriendlyByteBuf buffer, List<StockOrderRow> rows) {
        buffer.writeVarInt(rows.size());
        for (StockOrderRow row : rows) {
            buffer.writeUUID(row.orderId());
            buffer.writeUtf(row.symbol());
            buffer.writeUtf(row.type());
            buffer.writeLong(row.price());
            buffer.writeVarInt(row.quantity());
            buffer.writeBoolean(row.ownedByPlayer());
        }
    }

    private static List<StockOrderRow> readStockOrderRows(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<StockOrderRow> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new StockOrderRow(
                    buffer.readUUID(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return rows;
    }
}
