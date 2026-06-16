package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.market.MarketManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import finance.market.Order;
import finance.market.OrderType;
import finance.market.Trade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import finance.commodity.CommodityInventoryManager;
import finance.account.AccountManager;

import java.util.Collection;
import java.util.List;

/**
 * /market 命令 —— 市场交易系统入口。
 *
 * <h3>子命令</h3>
 * <ul>
 *   <li>/market orders —— 查看所有未成交订单</li>
 *   <li>/market buy ＜commodity＞ ＜price＞ ＜quantity＞ —— 挂买单</li>
 *   <li>/market sell ＜commodity＞ ＜price＞ ＜quantity＞ —— 挂卖单</li>
 *   <li>/market cancel ＜index＞ —— 取消指定订单</li>
 *   <li>/market history —— 查看最近 20 条成交历史</li>
 *   <li>/market npc buy ＜commodity＞ ＜quantity＞ —— 卖给 NPC</li>
 *   <li>/market npc sell ＜commodity＞ ＜quantity＞ —— 从 NPC 购买</li>
 *   <li>/market npc prices —— 查看 NPC 报价</li>
 * </ul>
 */
public class MarketCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("market")

                        // ================================================
                        // /market orders —— 查看订单簿
                        // ================================================
                        .then(
                                Commands.literal("orders")
                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            List<Order> orders =
                                                    MarketManager.getOrders();

                                            if (orders.isEmpty()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "No open orders."
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== Open Orders ==="
                                                    )
                                            );

                                            for (int i = 0;
                                                 i < orders.size();
                                                 i++) {

                                                Order order =
                                                        orders.get(i);

                                                ServerPlayer ownerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        order.getPlayerId()
                                                                );

                                                // 在线玩家显示名字，离线玩家显示 UUID 前 8 位
                                                String owner =
                                                        ownerPlayer != null
                                                                ? ownerPlayer.getName()
                                                                        .getString()
                                                                : order.getPlayerId()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "#" + i
                                                                        + " ["
                                                                        + order.getType()
                                                                        + "] "
                                                                        + order.getCommodityId()
                                                                        + " x"
                                                                        + order.getQuantity()
                                                                        + " @"
                                                                        + order.getPrice()
                                                                        + " by "
                                                                        + owner
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market buy ＜commodity＞ ＜price＞ ＜quantity＞
                        // ================================================
                        .then(
                                Commands.literal("buy")

                                        .then(
                                                Commands.argument(
                                                                "commodity",
                                                                StringArgumentType.word()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "price",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            long price =
                                                                                                    LongArgumentType.getLong(
                                                                                                            context,
                                                                                                            "price"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            // 下单前检查余额是否足够
                                                                                            long totalCost =
                                                                                                    price * quantity;

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            player.getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough balance. "
                                                                                                                        + "Need: " + totalCost
                                                                                                                        + " Have: " + balance
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            Order order =
                                                                                                    new Order(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            OrderType.BUY,
                                                                                                            price,
                                                                                                            quantity
                                                                                                    );

                                                                                            MarketManager.placeOrder(order);

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Buy order placed: "
                                                                                                                    + quantity
                                                                                                                    + "x "
                                                                                                                    + commodity
                                                                                                                    + " @"
                                                                                                                    + price
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )

                        // ================================================
                        // /market sell ＜commodity＞ ＜price＞ ＜quantity＞
                        // ================================================
                        .then(
                                Commands.literal("sell")

                                        .then(
                                                Commands.argument(
                                                                "commodity",
                                                                StringArgumentType.word()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "price",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            long price =
                                                                                                    LongArgumentType.getLong(
                                                                                                            context,
                                                                                                            "price"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            // 下单前检查库存是否足够
                                                                                            int owned =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    player.getUUID(),
                                                                                                                    commodity
                                                                                                            );

                                                                                            if (owned < quantity) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough commodity. "
                                                                                                                        + "Have: " + owned
                                                                                                                        + " Need: " + quantity
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            Order order =
                                                                                                    new Order(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            OrderType.SELL,
                                                                                                            price,
                                                                                                            quantity
                                                                                                    );

                                                                                            MarketManager.placeOrder(order);

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Sell order placed: "
                                                                                                                    + quantity
                                                                                                                    + "x "
                                                                                                                    + commodity
                                                                                                                    + " @"
                                                                                                                    + price
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )

                        // ================================================
                        // /market cancel ＜index＞ —— 按索引取消订单
                        // ================================================
                        .then(
                                Commands.literal("cancel")

                                        .then(
                                                Commands.argument(
                                                                "index",
                                                                IntegerArgumentType.integer(0)
                                                        )

                                                        .executes(context -> {

                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            int index =
                                                                    IntegerArgumentType.getInteger(
                                                                            context,
                                                                            "index"
                                                                    );

                                                            boolean success =
                                                                    MarketManager.cancelOrder(
                                                                            index,
                                                                            player.getUUID()
                                                                    );

                                                            if (!success) {

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "Cancel failed. "
                                                                                        + "Check the index "
                                                                                        + "or that it is your order."
                                                                        )
                                                                );

                                                                return 0;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "Order #"
                                                                                    + index
                                                                                    + " cancelled."
                                                                    )
                                                            );

                                                            return 1;
                                                        })
                                        )
                        )

                        // ================================================
                        // /market history —— 查看最近 20 条成交记录
                        // ================================================
                        .then(
                                Commands.literal("history")
                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            List<Trade> trades =
                                                    MarketManager.getTradeHistory();

                                            if (trades.isEmpty()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                "No trade history."
                                                        )
                                                );

                                                return 1;
                                            }

                                            player.sendSystemMessage(
                                                    Component.literal(
                                                            "=== Trade History ==="
                                                    )
                                            );

                                            // 只显示最近 20 条
                                            int start = Math.max(
                                                    0,
                                                    trades.size() - 20
                                            );

                                            for (int i = start;
                                                 i < trades.size();
                                                 i++) {

                                                Trade trade =
                                                        trades.get(i);

                                                ServerPlayer buyerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        trade.getBuyer()
                                                                );

                                                ServerPlayer sellerPlayer =
                                                        context.getSource()
                                                                .getServer()
                                                                .getPlayerList()
                                                                .getPlayer(
                                                                        trade.getSeller()
                                                                );

                                                String buyerName =
                                                        buyerPlayer != null
                                                                ? buyerPlayer.getName()
                                                                        .getString()
                                                                : trade.getBuyer()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                String sellerName =
                                                        sellerPlayer != null
                                                                ? sellerPlayer.getName()
                                                                        .getString()
                                                                : trade.getSeller()
                                                                        .toString()
                                                                        .substring(0, 8);

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                trade.getCommodityId()
                                                                        + " x"
                                                                        + trade.getQuantity()
                                                                        + " @"
                                                                        + trade.getPrice()
                                                                        + " | "
                                                                        + buyerName
                                                                        + " ← "
                                                                        + sellerName
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                        )

                        // ================================================
                        // /market npc —— NPC 做市商交易
                        // ================================================
                        .then(
                                Commands.literal("npc")

                                        // /market npc buy <commodity> <quantity>
                                        .then(
                                                Commands.literal("buy")

                                                        .then(
                                                                Commands.argument(
                                                                                "commodity",
                                                                                StringArgumentType.word()
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            MarketPrice price =
                                                                                                    NpcMarketMaker.getMarketPrice(commodity);

                                                                                            if (price == null) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Unknown commodity: '"
                                                                                                                        + commodity + "'."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 检查玩家库存
                                                                                            int owned =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    player.getUUID(),
                                                                                                                    commodity
                                                                                                            );

                                                                                            if (owned < quantity) {

                                                                                                long bid = price.getBidPrice();

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough "
                                                                                                                        + commodity
                                                                                                                        + ". Have: " + owned
                                                                                                                        + " Need: " + quantity
                                                                                                                        + " (NPC buys @"
                                                                                                                        + bid + ")"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            boolean success =
                                                                                                    NpcMarketMaker.npcBuy(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            quantity
                                                                                                    );

                                                                                            if (!success) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC cannot buy right now."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            long bidPrice = price.getBidPrice();
                                                                                            long received = bidPrice * quantity;

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Sold "
                                                                                                                    + quantity + "x "
                                                                                                                    + commodity
                                                                                                                    + " to NPC @"
                                                                                                                    + bidPrice
                                                                                                                    + " each. Received: "
                                                                                                                    + received
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )

                                        // /market npc sell <commodity> <quantity>
                                        .then(
                                                Commands.literal("sell")

                                                        .then(
                                                                Commands.argument(
                                                                                "commodity",
                                                                                StringArgumentType.word()
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "quantity",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer player =
                                                                                                    context.getSource()
                                                                                                            .getPlayerOrException();

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            int quantity =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "quantity"
                                                                                                    );

                                                                                            MarketPrice price =
                                                                                                    NpcMarketMaker.getMarketPrice(commodity);

                                                                                            if (price == null) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Unknown commodity: '"
                                                                                                                        + commodity + "'."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 检查 NPC 库存
                                                                                            int npcStock =
                                                                                                    CommodityInventoryManager
                                                                                                            .getCommodityAmount(
                                                                                                                    NpcMarketMaker.NPC_UUID,
                                                                                                                    commodity
                                                                                                            );

                                                                                            if (npcStock < quantity) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC doesn't have enough "
                                                                                                                        + commodity
                                                                                                                        + ". Available: "
                                                                                                                        + npcStock
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            // 检查玩家余额
                                                                                            long askPrice = price.getAskPrice();
                                                                                            long totalCost = askPrice * quantity;

                                                                                            long balance =
                                                                                                    AccountManager.getBalance(
                                                                                                            player.getUUID()
                                                                                                    );

                                                                                            if (balance < totalCost) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "Not enough balance. "
                                                                                                                        + "Need: " + totalCost
                                                                                                                        + " Have: " + balance
                                                                                                                        + " (NPC sells @"
                                                                                                                        + askPrice + ")"
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            boolean success =
                                                                                                    NpcMarketMaker.npcSell(
                                                                                                            player.getUUID(),
                                                                                                            commodity,
                                                                                                            quantity
                                                                                                    );

                                                                                            if (!success) {

                                                                                                player.sendSystemMessage(
                                                                                                        Component.literal(
                                                                                                                "NPC cannot sell right now."
                                                                                                        )
                                                                                                );

                                                                                                return 0;
                                                                                            }

                                                                                            player.sendSystemMessage(
                                                                                                    Component.literal(
                                                                                                            "Bought "
                                                                                                                    + quantity + "x "
                                                                                                                    + commodity
                                                                                                                    + " from NPC @"
                                                                                                                    + askPrice
                                                                                                                    + " each. Paid: "
                                                                                                                    + totalCost
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )

                                        // /market npc prices —— 查看所有 NPC 报价
                                        .then(
                                                Commands.literal("prices")
                                                        .executes(context -> {

                                                            ServerPlayer player =
                                                                    context.getSource()
                                                                            .getPlayerOrException();

                                                            Collection<MarketPrice> allPrices =
                                                                    NpcMarketMaker.getAllMarketPrices()
                                                                            .values();

                                                            if (allPrices.isEmpty()) {

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                "No NPC prices available."
                                                                        )
                                                                );

                                                                return 1;
                                                            }

                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "=== NPC Market Prices ==="
                                                                    )
                                                            );

                                                            for (MarketPrice mp : allPrices) {

                                                                long bid = mp.getBidPrice();
                                                                long ask = mp.getAskPrice();

                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                mp.getCommodityId()
                                                                                        + "  Buy: " + bid
                                                                                        + "  Sell: " + ask
                                                                        )
                                                                );
                                                            }

                                                            return 1;
                                                        })
                                        )
                        )
        );
    }
}
