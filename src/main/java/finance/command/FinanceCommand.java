package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import finance.account.AccountManager;
import finance.account.TransactionRecord;
import finance.account.TransactionType;
import finance.gameplay.FinanceGameplayService;
import finance.market.NpcMarketMaker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import finance.cycle.EconomyCycleService;
import finance.data.EconomySavedData;
import finance.diagnostic.*;

/**
 * /finance give ＜target＞ ＜amount＞ —— 管理员命令，凭空创造货币发给玩家（需要 OP 2 级权限）。
 */
public class FinanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
                Commands.literal("finance")
                        .then(
                                Commands.literal("gui")
                                        .executes(context -> {
                                            ServerPlayer player =
                                                    context.getSource().getPlayerOrException();
                                            return FinanceGameplayService.openLegacyCommand(player).success() ? 1 : 0;
                                        })
                        )

                        .then(
                                Commands.literal("give")
                                        .requires(source -> source.hasPermission(2))

                                        .then(
                                                Commands.argument(
                                                                "target",
                                                                EntityArgument.player()
                                                        )

                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                LongArgumentType.longArg(1)
                                                                        )

                                                                        .executes(context -> {

                                                                            ServerPlayer target =
                                                                                    EntityArgument.getPlayer(
                                                                                            context,
                                                                                            "target"
                                                                                    );

                                                                            long amount =
                                                                                    LongArgumentType.getLong(
                                                                                            context,
                                                                                            "amount"
                                                                                    );

                                                                            if (!AccountManager.deposit(target.getUUID(), amount)) {
                                                                                context.getSource().sendFailure(Component.literal(
                                                                                        "Target account cannot accept that amount."));
                                                                                return 0;
                                                                            }

                                                                            ServerPlayer source =
                                                                                    context.getSource()
                                                                                            .getPlayer();

                                                                            AccountManager.addTransactionRecord(
                                                                                    new TransactionRecord(
                                                                                            source != null
                                                                                                    ? source.getUUID()
                                                                                                    : NpcMarketMaker.NPC_UUID,
                                                                                            target.getUUID(),
                                                                                            amount,
                                                                                            TransactionType.ADMIN_GIVE
                                                                                    )
                                                                            );

                                                                            context.getSource().sendSuccess(
                                                                                    () -> Component.literal(
                                                                                            "已向 "
                                                                                                    + target.getName().getString()
                                                                                                    + " 发放 "
                                                                                                    + amount
                                                                                    ),
                                                                                    true
                                                                            );

                                                                            return 1;
                                                                        })
                                                        )
                                        )
                        )

                        .then(Commands.literal("diagnose")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    long day = EconomyCycleService.currentMcDay(context.getSource().getServer());
                                    DiagnosticReport report = DiagnosticManager.runFull(day);
                                    context.getSource().sendSuccess(() -> Component.literal("Finance DataVersion="
                                            + EconomySavedData.currentDataVersion() + " " + report.summary()), false);
                                    int shown = 0;
                                    for (DiagnosticIssue issue : report.issues()) {
                                        if (issue.severity() == DiagnosticSeverity.INFO || shown++ >= 10) continue;
                                        context.getSource().sendSuccess(() -> Component.literal("[" + issue.severity()
                                                + "][" + issue.module() + "][" + issue.code() + "] "
                                                + issue.subject() + " - " + issue.message()), false);
                                    }
                                    return report.healthy() ? 1 : 0;
                                }))

                        .then(Commands.literal("status")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("Finance DataVersion="
                                            + EconomySavedData.currentDataVersion()), false);
                                    ModuleHealthRegistry.statuses().forEach((module, status) ->
                                            context.getSource().sendSuccess(() -> Component.literal(module + "="
                                                    + status.state() + (status.reason().isBlank() ? "" : " " + status.reason())), false));
                                    DiagnosticReport latest = DiagnosticManager.latest();
                                    if (latest != null) context.getSource().sendSuccess(() -> Component.literal("Latest " + latest.summary()), false);
                                    return 1;
                                }))

                        .then(Commands.literal("resume")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("account").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.ACCOUNT)))
                                .then(Commands.literal("market").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.MARKET)))
                                .then(Commands.literal("warehouse").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.WAREHOUSE)))
                                .then(Commands.literal("contract").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.CONTRACT)))
                                .then(Commands.literal("company_gameplay").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.COMPANY_GAMEPLAY)))
                                .then(Commands.literal("stock").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.STOCK)))
                                .then(Commands.literal("debt").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.DEBT)))
                                .then(Commands.literal("banking").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.BANKING)))
                                .then(Commands.literal("futures").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.FUTURES)))
                                .then(Commands.literal("fund").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.FUND)))
                                .then(Commands.literal("insurance").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.INSURANCE)))
                                .then(Commands.literal("history").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.HISTORY)))
                                .then(Commands.literal("cycle").executes(c -> resume(c.getSource(), ModuleHealthRegistry.Module.CYCLE))))
        );
    }

    private static int resume(CommandSourceStack source, ModuleHealthRegistry.Module module) {
        long day = EconomyCycleService.currentMcDay(source.getServer());
        DiagnosticReport report = EconomyConsistencyService.runModule(module, day);
        DiagnosticManager.add(report);
        if (!report.healthy()) { source.sendFailure(Component.literal(module + " 仍有一致性错误，不能恢复：" + report.summary())); return 0; }
        ModuleHealthRegistry.resume(module); EconomySavedData.markDirty();
        source.sendSuccess(() -> Component.literal(module + " 已恢复 ACTIVE"), true); return 1;
    }

}
