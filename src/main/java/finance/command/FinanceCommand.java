package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import finance.account.AccountManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /finance give ＜target＞ ＜amount＞ —— 管理员命令，凭空创造货币发给玩家（需要 OP 2 级权限）。
 */
public class FinanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("finance")
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
                                                                                "amount",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .executes(context -> {

                                                                            ServerPlayer target =
                                                                                    EntityArgument.getPlayer(
                                                                                            context,
                                                                                            "target"
                                                                                    );

                                                                            long amount =
                                                                                    LongArgumentType.getLong(
                                                                                            context,
                                                                                            "amount"
                                                                                    );

                                                                            AccountManager.deposit(
                                                                                    target.getUUID(),
                                                                                    amount
                                                                            );

                                                                            context.getSource().sendSuccess(
                                                                                    () -> Component.literal(
                                                                                            "Gave "
                                                                                                    + amount
                                                                                                    + " to "
                                                                                                    + target.getName().getString()
                                                                                    ),
                                                                                    true
                                                                            );

                                                                            return 1;
                                                                        })
                                                        )
                                        )
                        )
        );
    }
}