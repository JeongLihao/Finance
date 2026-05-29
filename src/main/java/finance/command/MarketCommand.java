package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.market.MarketManager;
import finance.market.Order;
import finance.market.OrderType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import finance.market.Order;

public class MarketCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("market")
                        .then(
                                Commands.literal("orders")

                                        .executes(context -> {

                                            ServerPlayer player =
                                                    context.getSource()
                                                            .getPlayerOrException();

                                            for (Order order : MarketManager.getOrders()) {

                                                player.sendSystemMessage(
                                                        Component.literal(
                                                                order.getType()
                                                                        + " "
                                                                        + order.getCommodityId()
                                                                        + " Price:"
                                                                        + order.getPrice()
                                                                        + " Qty:"
                                                                        + order.getQuantity()
                                                        )
                                                );
                                            }

                                            return 1;
                                        })
                        )
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
                                                                                                            "Buy order placed."
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )

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
                                                                                                            "Sell order placed."
                                                                                                    )
                                                                                            );

                                                                                            return 1;
                                                                                        })
                                                                        )
                                                        )
                                        )
                        )
        );
    }
}
