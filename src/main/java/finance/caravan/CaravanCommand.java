package finance.caravan;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /caravan 命令 —— 抽象商队管理入口（GUI 按惯例暂缓）。
 *
 * <ul>
 *   <li>/caravan create &lt;源聚落&gt; &lt;目的聚落&gt; &lt;商品&gt; &lt;数量&gt; [保镖0-3] [insure] —— 建路线</li>
 *   <li>/caravan list —— 我的路线（状态/累计盈亏）</li>
 *   <li>/caravan pause|resume|close &lt;id前8位&gt;</li>
 * </ul>
 */
public final class CaravanCommand {

    private CaravanCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("caravan")
                // 玩家自己的商路：和贸易站“商路”页一样，每人路线数有上限
                .then(Commands.literal("create")
                        .then(Commands.argument("source", StringArgumentType.string())
                                .then(Commands.argument("dest", StringArgumentType.string())
                                        .then(Commands.argument("commodity", StringArgumentType.string())
                                                .then(Commands.argument("qty", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument(
                                                                        "guard", IntegerArgumentType.integer(0, 3))
                                                                .then(Commands.argument(
                                                                                "insure", BoolArgumentType.bool())
                                                                        .executes(ctx -> create(
                                                                                ctx.getSource(),
                                                                                StringArgumentType.getString(
                                                                                        ctx, "source"),
                                                                                StringArgumentType.getString(
                                                                                        ctx, "dest"),
                                                                                StringArgumentType.getString(
                                                                                        ctx, "commodity"),
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx, "qty"),
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx, "guard"),
                                                                                BoolArgumentType.getBool(
                                                                                        ctx, "insure"))))
                                                                .executes(ctx -> create(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "source"),
                                                                        StringArgumentType.getString(ctx, "dest"),
                                                                        StringArgumentType.getString(ctx, "commodity"),
                                                                        IntegerArgumentType.getInteger(ctx, "qty"),
                                                                        IntegerArgumentType.getInteger(ctx, "guard"),
                                                                        false)))
                                                        .executes(ctx -> create(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "source"),
                                                                StringArgumentType.getString(ctx, "dest"),
                                                                StringArgumentType.getString(ctx, "commodity"),
                                                                IntegerArgumentType.getInteger(ctx, "qty"),
                                                                0,
                                                                false)))))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("pause")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx ->
                                        manage(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "pause"))))
                .then(Commands.literal("resume")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx ->
                                        manage(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "resume"))))
                .then(Commands.literal("close")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx ->
                                        manage(ctx.getSource(), StringArgumentType.getString(ctx, "id"), "close")))));
    }

    private static int create(
            CommandSourceStack source,
            String srcName,
            String dstName,
            String commodityId,
            int qty,
            int guard,
            boolean insure) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("仅玩家可建商队路线。"));
            return 0;
        }
        // 和经济系统同一个日期来源（世界累计时间），睡觉跳夜和 /time set 不影响
        long day = finance.cycle.EconomyCycleService.currentMcDay(source.getServer());
        CaravanService.Result r =
                CaravanService.createRoute(player, srcName, dstName, commodityId, qty, guard, insure, day);
        if (r.ok()) {
            source.sendSuccess(
                    () -> Component.literal(
                            r.message() + "（id: " + r.route().id().toString().substring(0, 8) + "）"),
                    false);
            return 1;
        }
        source.sendFailure(Component.literal(r.message()));
        return 0;
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("仅玩家可用。"));
            return 0;
        }
        List<CaravanRoute> routes = CaravanManager.routesForOwner(player.getUUID());
        if (routes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("你还没有商队路线。/caravan create 建一条。"), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder("你的商队路线：");
        for (CaravanRoute r : routes) {
            sb.append("\n- ")
                    .append(r.id().toString(), 0, 8)
                    .append(" [")
                    .append(r.status())
                    .append("] ")
                    .append(r.commodityId())
                    .append(" ×")
                    .append(r.qtyPerTrip())
                    .append(" 保镖")
                    .append(r.guardLevel())
                    .append(r.insured() ? " 已保价" : "")
                    .append(" 累计盈亏 ")
                    .append(r.totalProfit());
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int manage(CommandSourceStack source, String idPrefix, String op) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("仅玩家可用。"));
            return 0;
        }
        UUID id = findRoute(player.getUUID(), idPrefix);
        if (id == null) {
            source.sendFailure(Component.literal("找不到路线：" + idPrefix + "（用 id 前 8 位）。"));
            return 0;
        }
        CaravanService.Result r;
        switch (op) {
            case "pause":
                r = CaravanService.pauseRoute(player, id);
                break;
            case "resume":
                r = CaravanService.resumeRoute(player, id);
                break;
            default:
                r = CaravanService.closeRoute(player, id);
                break;
        }
        if (r.ok()) {
            source.sendSuccess(() -> Component.literal(r.message()), false);
            return 1;
        }
        source.sendFailure(Component.literal(r.message()));
        return 0;
    }

    private static UUID findRoute(UUID ownerId, String idPrefix) {
        if (idPrefix == null) {
            return null;
        }
        String p = idPrefix.length() > 8 ? idPrefix.substring(0, 8) : idPrefix;
        for (CaravanRoute r : CaravanManager.routesForOwner(ownerId)) {
            if (r.id().toString().startsWith(p)) {
                return r.id();
            }
        }
        return null;
    }
}
