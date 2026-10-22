package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.account.AccountManager;
import finance.margin.MarginAccount;
import finance.margin.MarginPoolManager;
import finance.margin.MarginService;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /margin —— 融资融券。
 *
 * <p>子命令：info（账户总览）、deposit（存保证金）、withdraw（取保证金）、
 * buy（融资买入）、repay（融资还款）、short（融券卖出）、cover（买券还券）、
 * repaybaddebt（偿还不良债务）。
 * 普通玩家即可使用（券商为系统，不需要对手盘）。
 */
public class MarginCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("margin")
                .then(Commands.literal("info").executes(MarginCommand::info))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(MarginCommand::deposit)))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(MarginCommand::withdraw)))
                .then(Commands.literal("buy")
                        .then(Commands.argument("symbol", StringArgumentType.word())
                                .then(Commands.argument("qty", LongArgumentType.longArg(1))
                                        .executes(MarginCommand::buy))))
                .then(Commands.literal("repay")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(MarginCommand::repay)))
                .then(Commands.literal("short")
                        .then(Commands.argument("symbol", StringArgumentType.word())
                                .then(Commands.argument("qty", LongArgumentType.longArg(1))
                                        .executes(MarginCommand::shortSell))))
                .then(Commands.literal("cover")
                        .then(Commands.argument("symbol", StringArgumentType.word())
                                .executes(ctx -> cover(ctx, 0))
                                .then(Commands.argument("qty", LongArgumentType.longArg(1))
                                        .executes(ctx -> cover(ctx, LongArgumentType.getLong(ctx, "qty"))))))
                .then(Commands.literal("repaybaddebt")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(MarginCommand::repayBadDebt))));
    }

    private static ServerPlayer playerOrNull(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
            return null;
        }
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        UUID id = player.getUUID();
        MarginAccount acct = MarginPoolManager.getAccount(id);
        double ratio = MarginService.marginRatio(id);
        String ratioStr = ratio == Double.MAX_VALUE ? "—（无负债）" : String.format("%.1f%%", ratio);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 融资融券账户 ===\n");
        sb.append("维持保证金率：").append(ratioStr);
        if (MarginService.isWarning(id)) sb.append("  ⚠ 低于预警线，请补保证金或减仓");
        sb.append("\n现金保证金：").append(acct.getCashCollateral());
        sb.append(" | 融资负债：").append(acct.getFinancedDebt());
        sb.append(" | 融资利息：").append(acct.getFinancedInterestOwed());
        if (acct.getBadDebt() > 0)
            sb.append("\n⚠ 不良债务：").append(acct.getBadDebt()).append("（还清前不可新开仓）");
        if (!acct.getFinancedShares().isEmpty()) {
            sb.append("\n融资持仓：");
            for (Map.Entry<String, Long> e : acct.getFinancedShares().entrySet()) {
                sb.append(e.getKey()).append("×").append(e.getValue()).append(" ");
            }
        }
        if (!acct.getShortPositions().isEmpty()) {
            sb.append("\n融券仓位：");
            for (Map.Entry<String, Long> e : acct.getShortPositions().entrySet()) {
                sb.append(e.getKey()).append("×").append(e.getValue()).append(" ");
            }
        }
        sb.append("\n自由资金：").append(AccountManager.getBalance(id));
        player.sendSystemMessage(Component.literal(sb.toString()));
        return 1;
    }

    private static int deposit(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        long amount = LongArgumentType.getLong(ctx, "amount");
        MarginService.Result r = MarginService.depositCollateral(player.getUUID(), amount);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int withdraw(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        long amount = LongArgumentType.getLong(ctx, "amount");
        MarginService.Result r = MarginService.withdrawCollateral(player.getUUID(), amount);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int buy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        String symbol = StringArgumentType.getString(ctx, "symbol");
        long qty = LongArgumentType.getLong(ctx, "qty");
        MarginService.Result r = MarginService.marginBuy(player.getUUID(), symbol, qty);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int repay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        long amount = LongArgumentType.getLong(ctx, "amount");
        MarginService.Result r = MarginService.repayFinancing(player.getUUID(), amount);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int shortSell(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        String symbol = StringArgumentType.getString(ctx, "symbol");
        long qty = LongArgumentType.getLong(ctx, "qty");
        MarginService.Result r = MarginService.shortSell(player.getUUID(), symbol, qty);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int cover(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, long qty) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        String symbol = StringArgumentType.getString(ctx, "symbol");
        MarginService.Result r = MarginService.coverShort(player.getUUID(), symbol, qty);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int repayBadDebt(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        long amount = LongArgumentType.getLong(ctx, "amount");
        MarginService.Result r = MarginService.repayBadDebt(player.getUUID(), amount);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }
}
