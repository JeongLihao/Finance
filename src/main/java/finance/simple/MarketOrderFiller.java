package finance.simple;

import finance.FinanceMod;
import finance.commodity.Commodity;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import finance.market.MarketManager;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.network.GuiFeedbackPacket;
import finance.util.InventoryUtil;
import finance.util.MoneyFormat;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 让挂单在单人游戏里也能成交：市场（国际做市商）的收购价涨到卖单价格、或出售价跌到买单价格时，
 * 由市场按它的报价吃下挂单。成交价总是对玩家更有利或相等（卖单按收购价、买单按出售价）。
 */
@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID)
public final class MarketOrderFiller {

    static final int PERIOD_TICKS = 40;

    private MarketOrderFiller() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        if (event.getServer().getTickCount() % PERIOD_TICKS != 0) return;
        if (!finance.diagnostic.ModuleHealthRegistry.mayWrite(finance.diagnostic.ModuleHealthRegistry.Module.MARKET))
            return;
        fillCrossedOrders(event.getServer());
    }

    /** 处理所有已经“到价”的挂单；返回成交了几张。 */
    public static int fillCrossedOrders(MinecraftServer server) {
        int filled = 0;
        for (Order order : new ArrayList<>(MarketManager.getOrders())) {
            if (crossed(order) && fill(server, order)) filled++;
        }
        return filled;
    }

    /** 按做市商实际成交用的报价判断，保证卖单不会低于玩家定的价成交、买单不会高于玩家定的价成交。 */
    static boolean crossed(Order order) {
        var quote = finance.market.liquidity.MarketMakerQuotes.getQuote(
                finance.market.liquidity.MarketMakerQuotes.MARKET_COMMODITY, order.getCommodityId());
        if (quote == null || quote.halted()) return false;
        return order.getType() == OrderType.SELL
                ? quote.bid() >= order.getPrice()
                : quote.ask() > 0 && quote.ask() <= order.getPrice();
    }

    private static boolean fill(MinecraftServer server, Order order) {
        String id = order.getCommodityId();
        if (order.getType() == OrderType.SELL
                ? NpcMarketMaker.npcBuyBlockReason(id) != null
                : NpcMarketMaker.npcSellBlockReason(id) != null) {
            return false;
        }
        // 先撤下挂单（退回冻结的钱或货，不记“撤单”流水），再和市场成交，剩下的按原价重新挂上
        if (!MarketManager.releaseOrder(order)) return false;
        var player = order.getPlayerId();
        long before = finance.account.AccountManager.getBalance(player);
        int done = order.getType() == OrderType.SELL
                ? NpcMarketMaker.npcBuyFilled(player, id, order.getQuantity())
                : NpcMarketMaker.npcSellFilled(player, id, order.getQuantity());
        int left = order.getQuantity() - Math.max(0, done);
        if (left > 0) {
            MarketManager.placeOrder(new Order(player, id, order.getType(), order.getPrice(), left));
        }
        if (done <= 0) return false;
        long delta = finance.account.AccountManager.getBalance(player) - before;
        notify(server, order, done, delta);
        return true;
    }

    private static void notify(MinecraftServer server, Order order, int done, long delta) {
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(order.getPlayerId());
        if (online == null) return;
        Commodity c = CommodityRegistry.getCommodity(order.getCommodityId());
        String name = c == null ? order.getCommodityId() : c.getDisplayName();
        String message;
        if (order.getType() == OrderType.SELL) {
            message = "你的卖单成交了：卖出 " + done + " 个" + name + "，得 " + MoneyFormat.pounds(delta) + "。";
        } else {
            // 在线时直接放进背包
            if (c != null
                    && c.getItemId() != null
                    && !c.getItemId().isEmpty()
                    && CommodityInventoryManager.removeCommodity(order.getPlayerId(), order.getCommodityId(), done)) {
                InventoryUtil.addToInventory(online, c.getItemId(), done);
            }
            message = "你的买单成交了：买到 " + done + " 个" + name + "，花 " + MoneyFormat.pounds(-delta) + "，已放进背包。";
        }
        GuiFeedbackPacket.send(online, message, delta);
    }
}
