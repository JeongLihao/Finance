package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import finance.company.Company;
import finance.company.CompanyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /companies —— 查看所有注册公司。未上市公司不公开估值。
 */
public class CompaniesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("companies")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            var companies = CompanyManager.getCompanies();

                            if (companies.isEmpty()) {
                                player.sendSystemMessage(Component.literal("暂无注册公司。"));
                                return 1;
                            }

                            player.sendSystemMessage(Component.literal("=== 公司列表 ==="));
                            for (Company c : companies) {
                                player.sendSystemMessage(Component.literal(
                                        c.getName()
                                                + " | " + c.getType()
                                                + " | " + (c.isPlayerOwned() ? "玩家公司" : "系统公司")
                                                + " | " + (c.isPublic() ? "已上市" : "未上市")
                                                + " | 现金: " + c.getCash()
                                                + " | 公开估值: " + (c.isPublic() ? c.getEstimatedValue() : "未披露")));
                            }
                            return 1;
                        })
        );
    }
}
