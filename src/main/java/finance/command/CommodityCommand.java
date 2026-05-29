package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.commodity.CommodityInventoryManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommodityCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(

                Commands.literal("commodity")
                        .requires(source -> source.hasPermission(2))

                        .then(
                                Commands.literal("give")

                                        .then(
                                                Commands.argument(
                                                                "target",
                                                                EntityArgument.player()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "commodity",
                                                                                StringArgumentType.word()
                                                                        )

                                                                        .then(
                                                                                Commands.argument(
                                                                                                "amount",
                                                                                                IntegerArgumentType.integer(1)
                                                                                        )

                                                                                        .executes(context -> {

                                                                                            ServerPlayer target =
                                                                                                    EntityArgument.getPlayer(
                                                                                                            context,
                                                                                                            "target"
                                                                                                    );

                                                                                            String commodity =
                                                                                                    StringArgumentType.getString(
                                                                                                            context,
                                                                                                            "commodity"
                                                                                                    );

                                                                                            int amount =
                                                                                                    IntegerArgumentType.getInteger(
                                                                                                            context,
                                                                                                            "amount"
                                                                                                    );

                                                                                            CommodityInventoryManager
                                                                                                    .addCommodity(
                                                                                                            target.getUUID(),
                                                                                                            commodity,
                                                                                                            amount
                                                                                                    );

                                                                                            context.getSource()
                                                                                                    .sendSuccess(
                                                                                                            () -> Component.literal(
                                                                                                                    "Given "
                                                                                                                            + amount
                                                                                                                            + " "
                                                                                                                            + commodity
                                                                                                            ),
                                                                                                            true
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
