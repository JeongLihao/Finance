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
 * /pay ＜target＞ ＜amount＞ —— 向其他玩家转账。
 */
public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("pay")

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

                                                            ServerPlayer sender =
                                                                    context.getSource().getPlayerOrException();

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

                                                            boolean success =
                                                                    AccountManager.transfer(
                                                                            sender.getUUID(),
                                                                            target.getUUID(),
                                                                            amount
                                                                    );

                                                            if (!success) {

                                                                sender.sendSystemMessage(
                                                                        Component.literal(
                                                                                "余额不足。"
                                                                        )
                                                                );

                                                                return 0;
                                                            }

                                                            sender.sendSystemMessage(
                                                                    Component.literal(
                                                                            "已向 "
                                                                                    + target.getName().getString()
                                                                                    + " 支付 "
                                                                                    + amount
                                                                    )
                                                            );

                                                            target.sendSystemMessage(
                                                                    Component.literal(
                                                                            "收到来自 "
                                                                                    + sender.getName().getString()
                                                                                    + " 的 "
                                                                                    + amount
                                                                    )
                                                            );

                                                            return 1;
                                                        })
                                        )
                        )
        );
    }
}
