package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import finance.account.AccountManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BalanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("balance")
                        .executes(context -> {

                            ServerPlayer player = context.getSource().getPlayerOrException();

                            long balance = AccountManager.getBalance(player.getUUID());

                            player.sendSystemMessage(
                                    Component.literal("Balance: " + balance)
                            );

                            return 1;
                        })
        );
    }
}
