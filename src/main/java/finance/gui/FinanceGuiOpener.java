package finance.gui;

import finance.account.Account;
import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.commodity.CommodityInventoryManager;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.stock.Stock;
import finance.stock.StockHolding;
import finance.stock.StockMarketManager;
import finance.stock.StockPortfolioManager;
import finance.commodity.Commodity;
import finance.util.InventoryUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

import java.util.*;

/**
 * 金融 GUI 打开器 —— 收集全部数据并打开统一的 FinanceMenu。
 */
public class FinanceGuiOpener {

    public static void open(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // 1. 市场行情
        List<FinanceMenu.MarketRow> marketData = new ArrayList<>();
        for (MarketPrice price : NpcMarketMaker.getAllMarketPrices().values()) {
            int stock = CommodityInventoryManager.getCommodityAmount(
                    NpcMarketMaker.NPC_UUID, price.getCommodityId());
            marketData.add(new FinanceMenu.MarketRow(
                    price.getCommodityId(), price.getMidPrice(),
                    price.getBidPrice(), price.getAskPrice(),
                    price.getDayChange(), price.getDayVolume(), stock));
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
        for (Company company : CompanyManager.getCompanies()) {
            companyRows.add(toCompanyInfo(company));
        }

        final FinanceMenu.CompanyInfo companyInfo;
        Company company = CompanyManager.getCompanyByOwner(playerId);
        if (company != null) {
            companyInfo = toCompanyInfo(company);
        } else {
            companyInfo = null;
        }

        List<FinanceMenu.StockRow> stockRows = new ArrayList<>();
        for (Stock stock : StockMarketManager.getStocks()) {
            stockRows.add(new FinanceMenu.StockRow(
                    stock.getSymbol(),
                    stock.getName(),
                    stock.getLastPrice(),
                    stock.getDayChange(),
                    stock.getDayVolume(),
                    stock.getAvailableShares()));
        }

        List<FinanceMenu.StockHoldingRow> stockHoldingRows = new ArrayList<>();
        for (Map.Entry<String, StockHolding> entry :
                StockPortfolioManager.getPortfolio(playerId).entrySet()) {
            stockHoldingRows.add(new FinanceMenu.StockHoldingRow(
                    entry.getKey(),
                    entry.getValue().getQuantity(),
                    entry.getValue().getAverageCost()));
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

        // 打开菜单
        NetworkHooks.openScreen(player,
                new FinanceProvider(marketData, orderRows, balance, frozenBalance, inventory, companyInfo, companyRows, stockRows, stockHoldingRows, mcInv),
                buffer -> FinanceMenu.writeAll(buffer, marketData, orderRows, balance, frozenBalance, inventory, companyInfo, companyRows, stockRows, stockHoldingRows, mcInv));
    }

    private static FinanceMenu.CompanyInfo toCompanyInfo(Company company) {
        return new FinanceMenu.CompanyInfo(
                company.getName(), company.getType().getDisplayName(),
                company.getCash(), company.inventoryValue(),
                company.getEstimatedValue(),
                new LinkedHashMap<>(company.getInventory()),
                company.isPlayerOwned());
    }

    private record FinanceProvider(List<FinanceMenu.MarketRow> marketData,
                                    List<FinanceMenu.OrderRow> orderRows,
                                    long balance, long frozenBalance,
                                    Map<String, Integer> inventory,
                                    FinanceMenu.CompanyInfo companyInfo,
                                    List<FinanceMenu.CompanyInfo> allCompanies,
                                    List<FinanceMenu.StockRow> stocks,
                                    List<FinanceMenu.StockHoldingRow> stockHoldings,
                                    Map<String, Integer> mcInventory)
            implements net.minecraft.world.MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.literal("金融中心");
        }

        @Override
        public FinanceMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inv,
                                       net.minecraft.world.entity.player.Player player) {
            return new FinanceMenu(containerId, marketData, orderRows, balance, frozenBalance, inventory, companyInfo, allCompanies, stocks, stockHoldings, mcInventory);
        }
    }
}
