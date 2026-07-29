package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.company.Company;
import finance.company.CompanyCreationService;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Map;

/**
 * /company —— 创建与查询公司。
 */
public class CompanyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("company")
                        .then(
                                Commands.literal("create")
                                        .requires(source -> source.hasPermission(2))
                                        .then(
                                                Commands.argument("type", StringArgumentType.word())
                                                        .then(
                                                                Commands.argument("name", StringArgumentType.greedyString())
                                                                        .executes(context -> {
                                                                            ServerPlayer player = context.getSource()
                                                                                    .getPlayerOrException();
                                                                            String typeName = StringArgumentType.getString(context, "type");
                                                                            String name = StringArgumentType.getString(context, "name").trim();

                                                                            CompanyType type = parseCompanyType(typeName);
                                                                            if (type == null) {
                                                                                player.sendSystemMessage(Component.literal(
                                                                                        "未知行业: '" + typeName + "'。可用行业: " + availableTypes()));
                                                                                return 0;
                                                                            }

                                                                            CompanyCreationService.Result result =
                                                                                    CompanyCreationService.createPlayerCompany(
                                                                                            player.getUUID(), type, name);
                                                                            player.sendSystemMessage(Component.literal(result.message()));
                                                                            return result.success() ? 1 : 0;
                                                                        })
                                                        )
                                        )
                        )
                        .then(
                                Commands.literal("mine")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource()
                                                    .getPlayerOrException();
                                            Company c = CompanyManager.getCompanyByOwner(player.getUUID());

                                            if (c == null) {
                                                player.sendSystemMessage(Component.literal(
                                                        "你还没有公司。使用 /company create <type> <name> 创建。"));
                                                return 0;
                                            }

                                            sendCompanyInfo(player, c);
                                            return 1;
                                        })
                        )
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

                                                            sendCompanyInfo(player, c);
                                                            return 1;
                                                        })
                                        )
                        )
        );
    }

    private static void sendCompanyInfo(ServerPlayer player, Company c) {
        player.sendSystemMessage(Component.literal("===================="));
        player.sendSystemMessage(Component.literal(c.getName()));
        player.sendSystemMessage(Component.literal("行业: " + c.getType().getDisplayName()));
        player.sendSystemMessage(Component.literal(
                c.isPlayerOwned() ? "归属: 玩家公司" : "归属: 系统公司"));
        player.sendSystemMessage(Component.literal("状态: " + (c.isPublic() ? "已上市" : "未上市")));
        player.sendSystemMessage(Component.literal("现金: " + c.getCash()));

        boolean isOwner = c.getOwnerId() != null && c.getOwnerId().equals(player.getUUID());
        boolean canSeeInventory = c.isPublic() || isOwner;

        if (canSeeInventory && !c.getInventory().isEmpty()) {
            player.sendSystemMessage(Component.literal("--- 库存 ---"));
            for (Map.Entry<String, Integer> entry : c.getInventory().entrySet()) {
                player.sendSystemMessage(Component.literal(
                        "  " + entry.getKey() + ": " + entry.getValue()));
            }
        }

        if (c.isPublic()) {
            long invValue = c.inventoryValue();
            player.sendSystemMessage(Component.literal("库存市值: " + invValue));
            player.sendSystemMessage(Component.literal("公开估值: " + c.getEstimatedValue()));
        } else {
            player.sendSystemMessage(Component.literal("公开估值: 未披露"));
        }
        player.sendSystemMessage(Component.literal("===================="));
    }

    private static CompanyType parseCompanyType(String typeName) {
        // 先按英文枚举名匹配
        try {
            return CompanyType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        // 再按中文显示名匹配
        for (CompanyType t : CompanyType.values()) {
            if (t.getDisplayName().equals(typeName)) return t;
        }
        return null;
    }

    private static String availableTypes() {
        return String.join(", ", Arrays.stream(CompanyType.values())
                .map(t -> t.getDisplayName() + "(" + t.name() + ")")
                .toList());
    }

}
