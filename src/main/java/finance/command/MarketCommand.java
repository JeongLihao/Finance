package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.market.MarketManager;
import finance.market.Order;
import finance.market.OrderType;
import finance.market.Trade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import finance.commodity.CommodityInventoryManager;
import finance.account.AccountManager;

import java.util.List;

public class MarketCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("market")

                        // /market orders — list all open orders
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

                        // /market buy <commodity> <price> <quantity>
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

                        // /market sell <commodity> <price> <quantity>
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

                        // /market cancel <index>
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

                        // /market history — view trade history
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
        );
    }
}
