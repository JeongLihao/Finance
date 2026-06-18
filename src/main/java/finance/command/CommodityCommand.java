package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.commodity.CommodityInventoryManager;
import finance.commodity.CommodityRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /commodity give ＜target＞ ＜commodityId＞ ＜amount＞ —— 管理员命令，给玩家发放商品（需要 OP 2 级权限）。
 */
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

                                                                                            if (CommodityRegistry.getCommodity(commodity) == null) {
                                                                                                context.getSource()
                                                                                                        .sendFailure(
                                                                                                                Component.literal(
                                                                                                                        "未知商品: '" + commodity + "'。"
                                                                                                                )
                                                                                                        );

                                                                                                return 0;
                                                                                            }

                                                                                            CommodityInventoryManager
                                                                                                    .addCommodity(
                                                                                                            target.getUUID(),
                                                                                                            commodity,
                                                                                                            amount
                                                                                                    );

                                                                                            context.getSource()
                                                                                                    .sendSuccess(
                                                                                                            () -> Component.literal(
                                                                                                                    "已发放 "
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
