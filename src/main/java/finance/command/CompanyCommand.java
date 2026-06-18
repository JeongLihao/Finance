package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.account.AccountManager;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.company.CompanyType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * /company —— 创建与查询公司。
 */
public class CompanyCommand {

    private static final long CREATE_COMPANY_COST = 10_000L;
    private static final long INITIAL_PLAYER_COMPANY_CASH = 5_000L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("company")
                        .then(
                                Commands.literal("create")
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

                                                                            if (name.isEmpty() || name.length() > 32) {
                                                                                player.sendSystemMessage(Component.literal(
                                                                                        "公司名称长度需为 1-32 个字符。"));
                                                                                return 0;
                                                                            }

                                                                            if (CompanyManager.getCompanyByOwner(player.getUUID()) != null) {
                                                                                player.sendSystemMessage(Component.literal(
                                                                                        "你已经拥有一家公司，暂时不能重复创建。"));
                                                                                return 0;
                                                                            }

                                                                            if (CompanyManager.hasCompanyNamed(name)) {
                                                                                player.sendSystemMessage(Component.literal(
                                                                                        "公司名称已被占用: '" + name + "'。"));
                                                                                return 0;
                                                                            }

                                                                            if (!AccountManager.withdraw(player.getUUID(), CREATE_COMPANY_COST)) {
                                                                                player.sendSystemMessage(Component.literal(
                                                                                        "余额不足，创建公司需要 " + CREATE_COMPANY_COST + "。"));
                                                                                return 0;
                                                                            }

                                                                            Company company = new Company(
                                                                                    UUID.randomUUID(),
                                                                                    name,
                                                                                    type,
                                                                                    INITIAL_PLAYER_COMPANY_CASH,
                                                                                    player.getUUID()
                                                                            );
                                                                            seedInitialInventory(company);
                                                                            CompanyManager.register(company);

                                                                            player.sendSystemMessage(Component.literal(
                                                                                    "公司已创建: " + company.getName()
                                                                                            + " | 行业: " + company.getType()
                                                                                            + " | 启动资金: " + INITIAL_PLAYER_COMPANY_CASH));
                                                                            return 1;
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
        player.sendSystemMessage(Component.literal("行业: " + c.getType()));
        player.sendSystemMessage(Component.literal(
                c.isPlayerOwned() ? "归属: 玩家公司" : "归属: 系统公司"));
        player.sendSystemMessage(Component.literal("现金: " + c.getCash()));

        if (!c.getInventory().isEmpty()) {
            player.sendSystemMessage(Component.literal("--- 库存 ---"));
            for (Map.Entry<String, Integer> entry : c.getInventory().entrySet()) {
                player.sendSystemMessage(Component.literal(
                        "  " + entry.getKey() + ": " + entry.getValue()));
            }
        }

        long invValue = c.inventoryValue();
        player.sendSystemMessage(Component.literal("库存市值: " + invValue));
        player.sendSystemMessage(Component.literal("公司估值: " + c.getEstimatedValue()));
        player.sendSystemMessage(Component.literal("===================="));
    }

    private static CompanyType parseCompanyType(String typeName) {
        try {
            return CompanyType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String availableTypes() {
        return String.join(", ", Arrays.stream(CompanyType.values())
                .map(Enum::name)
                .toList());
    }

    private static void seedInitialInventory(Company company) {
        for (String commodityId : company.getType().getCommodityIds()) {
            company.addInventory(commodityId, 50);
        }
    }
}
