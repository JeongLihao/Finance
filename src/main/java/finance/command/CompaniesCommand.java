package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import finance.company.Company;
import finance.company.CompanyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /companies —— 查看所有注册公司。
 */
public class CompaniesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("companies")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var companies = CompanyManager.getCompanies();

                            if (companies.isEmpty()) {
                                player.sendSystemMessage(Component.literal("No companies registered."));
                                return 1;
                            }

                            player.sendSystemMessage(Component.literal("=== Companies ==="));
                            for (Company c : companies) {
                                player.sendSystemMessage(Component.literal(
                                        c.getName() + " | " + c.getType() + " | Cash: " + c.getCash()));
                            }
                            return 1;
                        })
        );
    }
}
