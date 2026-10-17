package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import finance.account.AccountManager;
import finance.cash.CashService;
import finance.cash.PlayerCashPurse;
import finance.cycle.EconomyCycleService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /cash —— 实体现金。与“更多操作 → 现金兑换”页调用同一套 {@link CashService}。
 * <ul>
 *   <li>/cash：查看余额、随身现金、绿宝石汇率与今日额度</li>
 *   <li>/cash withdraw ＜金额＞：从账户取出现金</li>
 *   <li>/cash deposit [金额]：把现金存入账户，不填金额则全部存入</li>
 *   <li>/cash sell ＜颗数＞ / /cash buy ＜颗数＞：用绿宝石换现金 / 用现金买绿宝石</li>
 * </ul>
 */
public final class CashCommand {
    private interface Op {
        CashService.Result run(ServerPlayer player, PlayerCashPurse purse, long value, long day);
    }

    private CashCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cash")
                .executes(CashCommand::info)
                .then(Commands.literal("withdraw").then(Commands.argument("amount", LongArgumentType.longArg(1, CashService.MAX_WITHDRAW))
                        .executes(c -> run(c, LongArgumentType.getLong(c, "amount"),
                                (p, purse, v, day) -> CashService.withdraw(p.getUUID(), purse, v)))))
                .then(Commands.literal("deposit")
                        .executes(c -> run(c, 0, (p, purse, v, day) -> CashService.deposit(p.getUUID(), purse, v)))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(c -> run(c, LongArgumentType.getLong(c, "amount"),
                                        (p, purse, v, day) -> CashService.deposit(p.getUUID(), purse, v)))))
                .then(Commands.literal("sell").then(Commands.argument("count", LongArgumentType.longArg(1, CashService.MAX_EMERALDS_PER_ACTION))
                        .executes(c -> run(c, LongArgumentType.getLong(c, "count"),
                                (p, purse, v, day) -> CashService.sellEmeralds(p.getUUID(), purse, v, day)))))
                .then(Commands.literal("buy").then(Commands.argument("count", LongArgumentType.longArg(1, CashService.MAX_EMERALDS_PER_ACTION))
                        .executes(c -> run(c, LongArgumentType.getLong(c, "count"),
                                (p, purse, v, day) -> CashService.buyEmeralds(p.getUUID(), purse, v, day))))));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        PlayerCashPurse purse = new PlayerCashPurse(player);
        CashService.Quote q = CashService.quote(purse, EconomyCycleService.currentMcDay(player.server));
        c.getSource().sendSuccess(() -> Component.literal("账户余额 " + AccountManager.getBalance(player.getUUID())
                + "，随身现金 " + purse.cashTotal() + "，绿宝石 " + purse.emeralds() + " 颗\n"
                + "卖出 1 颗绿宝石得 " + q.emeraldToCash() + " 金币，买入 1 颗付 " + q.cashToEmerald() + " 金币\n"
                + "今日还可卖出 " + q.sellLeft() + "/" + q.sellLimit() + " 颗，买入 " + q.buyLeft() + "/" + q.buyLimit() + " 颗"
                + (q.enabled() ? "" : "\n实体现金未启用（配置 cash.enabled）")), false);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, long value, Op op) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        CashService.Result r = op.run(player, new PlayerCashPurse(player), value,
                EconomyCycleService.currentMcDay(player.server));
        if (r.success()) {
            c.getSource().sendSuccess(() -> Component.literal(r.message()), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(r.message()));
        return 0;
    }
}
