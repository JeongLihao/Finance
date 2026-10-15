package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.bank.BankingManager;
import finance.bank.CommercialBank;
import finance.bank.PlayerBankService;
import finance.bank.PublicBankViewService;
import finance.cycle.EconomyCycleService;
import finance.data.EconomySavedData;
import finance.diagnostic.ModuleHealthRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * /bank —— 玩家银行：申请牌照、自定利率、银行分红、查看名下银行。
 * （银行 GUI 管理页按选项 A 暂缓，待 FinanceScreen 拆分 PR 合并后移植。）
 */
public class PlayerBankCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bank")
                        .then(Commands.literal("charter")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .then(Commands.argument("capital", LongArgumentType.longArg(1))
                                                        .executes(ctx -> {
                                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                            if (!mayWrite(player)) return 0;
                                                            long day = EconomyCycleService.currentMcDay(player.server);
                                                            String code = StringArgumentType.getString(ctx, "code");
                                                            String name = StringArgumentType.getString(ctx, "name");
                                                            long capital = LongArgumentType.getLong(ctx, "capital");
                                                            BankingManager.Result r = PlayerBankService.charter(
                                                                    player.getUUID(), code, name, capital, day);
                                                            player.sendSystemMessage(Component.literal(r.message()));
                                                            return r.success() ? 1 : 0;
                                                        })))))
                        .then(Commands.literal("rates")
                                .then(Commands.argument("demandSpread", IntegerArgumentType.integer(0, 10_000))
                                        .then(Commands.argument("timeSpread", IntegerArgumentType.integer(0, 10_000))
                                                .then(Commands.argument("loanSpread", IntegerArgumentType.integer(0, 10_000))
                                                        .executes(ctx -> executeRates(ctx, null))
                                                        .then(Commands.argument("bankId", StringArgumentType.word())
                                                                .executes(ctx -> executeRates(ctx, parseBankId(ctx))))))))
                        .then(Commands.literal("dividend")
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> executeDividend(ctx, null))
                                        .then(Commands.argument("bankId", StringArgumentType.word())
                                                .executes(ctx -> executeDividend(ctx, parseBankId(ctx))))))
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    UUID bankId = PlayerBankService.bankOfOwner(player.getUUID());
                                    CommercialBank bank = bankId == null ? null : BankingManager.bank(bankId);
                                    if (bank == null) {
                                        player.sendSystemMessage(Component.literal("名下没有银行，用 /bank charter 申请牌照"));
                                        return 0;
                                    }
                                    var v = PublicBankViewService.crop(bank, player.hasPermissions(2));
                                    player.sendSystemMessage(Component.literal(
                                            "银行 " + v.code() + "「" + v.name() + "」 状态=" + v.status()
                                                    + " 权益=" + v.equity() + " 存款=" + v.deposits()
                                                    + " 资本充足率=" + v.capitalBps() + "bps"
                                                    + " 活期利率=" + v.demandRateBps() + "bps"
                                                    + " 定期利率=" + v.timeRateBps() + "bps"));
                                    return 1;
                                }))
        );
    }

    private static boolean mayWrite(ServerPlayer player) {
        if (!ModuleHealthRegistry.mayWrite(ModuleHealthRegistry.Module.BANKING)) {
            player.sendSystemMessage(Component.literal("银行模块已因一致性问题暂停"));
            return false;
        }
        return true;
    }

    private static UUID parseBankId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            return UUID.fromString(StringArgumentType.getString(ctx, "bankId"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int executeRates(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, UUID bankId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!mayWrite(player)) return 0;
        if (bankId == null && hasBankIdArg(ctx)) {
            player.sendSystemMessage(Component.literal("bankId 格式无效"));
            return 0;
        }
        BankingManager.Result r = PlayerBankService.setSpreads(
                player.getUUID(), bankId,
                IntegerArgumentType.getInteger(ctx, "demandSpread"),
                IntegerArgumentType.getInteger(ctx, "timeSpread"),
                IntegerArgumentType.getInteger(ctx, "loanSpread"));
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static int executeDividend(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, UUID bankId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!mayWrite(player)) return 0;
        if (bankId == null && hasBankIdArg(ctx)) {
            player.sendSystemMessage(Component.literal("bankId 格式无效"));
            return 0;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        BankingManager.Result r = PlayerBankService.dividend(
                player.getUUID(), bankId, LongArgumentType.getLong(ctx, "amount"), day);
        player.sendSystemMessage(Component.literal(r.message()));
        return r.success() ? 1 : 0;
    }

    private static boolean hasBankIdArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ctx.getArgument("bankId", String.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
