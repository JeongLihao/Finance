package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import finance.account.AccountManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /balance —— 查询自己的账户余额。
 */
public class BalanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("balance")
                        .executes(context -> {

                            ServerPlayer player = context.getSource().getPlayerOrException();

                            long balance = AccountManager.getBalance(player.getUUID());

                            player.sendSystemMessage(
                                    Component.literal("余额: " + balance)
                            );

                            return 1;})
        );
    }
}
