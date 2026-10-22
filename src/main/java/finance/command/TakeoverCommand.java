package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import finance.company.Company;
import finance.company.CompanyManager;
import finance.cycle.EconomyCycleService;
import finance.governance.CapitalActionStatus;
import finance.governance.CorporateActionManager;
import finance.governance.TakeoverService;
import finance.governance.TenderOffer;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /takeover —— 公司并购 / 要约收购。
 *
 * <p>子命令：offer（发起要约）、accept（接受要约）、cancel（撤销要约）、
 * list（要约列表）、info（要约详情）、squeeze（强制私有化）。
 * 普通玩家即可使用（并购是玩家玩法，不是管理功能）。
 */
public class TakeoverCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("takeover")
                .then(Commands.literal("offer")
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                                .then(Commands.argument("price", LongArgumentType.longArg(1))
                                        .then(Commands.argument("targetShares", LongArgumentType.longArg(1))
                                                .then(Commands.argument("minShares", LongArgumentType.longArg(1))
                                                        .then(Commands.argument(
                                                                        "days", IntegerArgumentType.integer(1, 90))
                                                                .executes(TakeoverCommand::offer)))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("offerId", StringArgumentType.word())
                                .then(Commands.argument("shares", LongArgumentType.longArg(1))
                                        .executes(TakeoverCommand::accept))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("offerId", StringArgumentType.word())
                                .executes(TakeoverCommand::cancel)))
                .then(Commands.literal("list")
                        .executes(TakeoverCommand::list)
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                                .executes(TakeoverCommand::listFor)))
                .then(Commands.literal("info")
                        .then(Commands.argument("offerId", StringArgumentType.word())
                                .executes(TakeoverCommand::info)))
                .then(Commands.literal("squeeze")
                        .then(Commands.argument("company", StringArgumentType.greedyString())
                                .executes(TakeoverCommand::squeeze))));
    }

    private static int offer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
        String companyName = StringArgumentType.getString(ctx, "company").trim();
        long price = LongArgumentType.getLong(ctx, "price");
        long targetShares = LongArgumentType.getLong(ctx, "targetShares");
        long minShares = LongArgumentType.getLong(ctx, "minShares");
        int days = IntegerArgumentType.getInteger(ctx, "days");

        Company company = CompanyManager.getCompanyByName(companyName);
        if (company == null) {
            player.sendSystemMessage(Component.literal("未找到公司: '" + companyName + "'。"));
            return 0;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        CorporateActionManager.Result result = TakeoverService.offerTender(
                player.getUUID(),
                company.getCompanyId(),
                price,
                targetShares,
                minShares,
                days,
                day,
                UUID.randomUUID().toString());
        player.sendSystemMessage(Component.literal(result.message()));
        if (result.success()) {
            player.sendSystemMessage(Component.literal("要约已公告，目标公司股票停牌 " + days + " 天。要约 ID: " + result.id()));
        }
        return result.success() ? 1 : 0;
    }

    private static int accept(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
        UUID offerId;
        try {
            offerId = UUID.fromString(StringArgumentType.getString(ctx, "offerId"));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("要约 ID 格式错误。"));
            return 0;
        }
        long shares = LongArgumentType.getLong(ctx, "shares");
        long day = EconomyCycleService.currentMcDay(player.server);
        CorporateActionManager.Result result = TakeoverService.acceptTender(
                player.getUUID(), offerId, shares, day, UUID.randomUUID().toString());
        player.sendSystemMessage(Component.literal(result.message()));
        if (result.success()) {
            player.sendSystemMessage(Component.literal("已锁定 " + shares + " 股，交割前不可交易；若要约失败将按原成本价返还。"));
        }
        return result.success() ? 1 : 0;
    }

    private static int cancel(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
        UUID offerId;
        try {
            offerId = UUID.fromString(StringArgumentType.getString(ctx, "offerId"));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("要约 ID 格式错误。"));
            return 0;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        CorporateActionManager.Result result = TakeoverService.cancelTender(
                player.getUUID(), offerId, day, UUID.randomUUID().toString());
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return listInternal(ctx, null);
    }

    private static int listFor(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String companyName = StringArgumentType.getString(ctx, "company").trim();
        Company company = CompanyManager.getCompanyByName(companyName);
        if (company == null) {
            ctx.getSource().sendFailure(Component.literal("未找到公司: '" + companyName + "'。"));
            return 0;
        }
        return listInternal(ctx, company.getCompanyId());
    }

    private static int listInternal(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, UUID companyId) {
        List<TenderOffer> offers = TakeoverService.openTenders().stream()
                .filter(o -> companyId == null || o.targetCompanyId().equals(companyId))
                .toList();
        if (offers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("当前没有进行中的要约收购。"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("=== 进行中的要约收购 ==="), false);
        for (TenderOffer o : offers) {
            Company c = CompanyManager.getCompany(o.targetCompanyId());
            String name = c == null ? "?" : c.getName();
            long accepted =
                    o.accepted().values().stream().mapToLong(Long::longValue).sum();
            ctx.getSource()
                    .sendSuccess(
                            () -> Component.literal(o.id() + " | " + name + " | 价格 " + o.price()
                                    + " | 已接受 " + accepted + "/" + o.targetShares()
                                    + (o.isSqueezeOut() ? " | 强制私有化" : "")),
                            false);
        }
        return 1;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        UUID offerId;
        try {
            offerId = UUID.fromString(StringArgumentType.getString(ctx, "offerId"));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("要约 ID 格式错误。"));
            return 0;
        }
        TenderOffer o = CorporateActionManager.tenders().get(offerId);
        if (o == null) {
            ctx.getSource().sendFailure(Component.literal("未找到要约。"));
            return 0;
        }
        Company c = CompanyManager.getCompany(o.targetCompanyId());
        String name = c == null ? "?" : c.getName();
        long accepted =
                o.accepted().values().stream().mapToLong(Long::longValue).sum();
        long day;
        try {
            day = EconomyCycleService.currentMcDay(ctx.getSource().getServer());
        } catch (Exception e) {
            day = o.startDay();
        }
        long remaining = Math.max(0, o.endDay() - day);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 要约详情 ===\n");
        sb.append("目标公司: ").append(name).append("\n");
        sb.append("要约价: ").append(o.price()).append("\n");
        sb.append("目标股数: ")
                .append(o.targetShares())
                .append(" | 已接受: ")
                .append(accepted)
                .append(" | 最低接受: ")
                .append(o.minShares())
                .append("\n");
        sb.append("托管金额: ").append(o.maxFunds()).append("\n");
        sb.append("状态: ").append(o.status()).append("\n");
        if (o.status() == CapitalActionStatus.OPEN) {
            sb.append("剩余天数: ").append(remaining).append(" 天\n");
        }
        if (o.isSqueezeOut()) {
            sb.append("类型: 强制私有化（交割后退市）\n");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int squeeze(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("该命令只能由玩家执行。"));
            return 0;
        }
        String companyName = StringArgumentType.getString(ctx, "company").trim();
        Company company = CompanyManager.getCompanyByName(companyName);
        if (company == null) {
            player.sendSystemMessage(Component.literal("未找到公司: '" + companyName + "'。"));
            return 0;
        }
        long day = EconomyCycleService.currentMcDay(player.server);
        CorporateActionManager.Result result = TakeoverService.squeezeOut(
                player.getUUID(), company.getCompanyId(), day, UUID.randomUUID().toString());
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }
}
