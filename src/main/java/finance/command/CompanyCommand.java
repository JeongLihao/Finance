package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.company.Company;
import finance.company.CompanyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * /company info <name> —— 查询公司详情、库存与估值。
 */
public class    CompanyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("company")
                        .then(
                                Commands.literal("info")
                                        .then(
                                                Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource()
                                                                    .getPlayerOrException();
                                                            String name = StringArgumentType.getString(context, "name");
                                                            Company c = CompanyManager.getCompanyByName(name);

                                                            if (c == null) {
                                                                player.sendSystemMessage(Component.literal(
                                                                        "未找到公司: '" + name + "'."));
                                                                return 0;
                                                            }

                                                            player.sendSystemMessage(Component.literal(
                                                                    "===================="));
                                                            player.sendSystemMessage(Component.literal(
                                                                    c.getName()));
                                                            player.sendSystemMessage(Component.literal(
                                                                    "行业: " + c.getType()));
                                                            player.sendSystemMessage(Component.literal(
                                                                    "现金: " + c.getCash()));

                                                            // 库存明细
                                                            if (!c.getInventory().isEmpty()) {
                                                                player.sendSystemMessage(Component.literal(
                                                                        "--- 库存 ---"));
                                                                for (Map.Entry<String, Integer> entry :
                                                                        c.getInventory().entrySet()) {
                                                                    player.sendSystemMessage(Component.literal(
                                                                            "  " + entry.getKey()
                                                                                    + ": " + entry.getValue()));
                                                                }
                                                            }

                                                            long invValue = c.inventoryValue();
                                                            player.sendSystemMessage(Component.literal(
                                                                    "库存市值: " + invValue));
                                                            player.sendSystemMessage(Component.literal(
                                                                    "公司估值: " + c.getEstimatedValue()));
                                                            player.sendSystemMessage(Component.literal(
                                                                    "===================="));
                                                            return 1;
                                                        })
                                        )
                        )
        );
    }
}
