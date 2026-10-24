package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.network.GuiFeedbackPacket;
import finance.simple.SimpleCenterService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /sell hand —— 按手上拿的东西的种类，卖掉仓库里（个人托管）的一组；/sell hand all —— 仓库里这种货全卖。
 * 背包里的东西不直接卖：要先在自己的仓库控制器或市场终端旁存进去。
 * 和金融中心“马上买卖”的卖出是同一个服务，价格和限制完全一样。
 */
public final class SellCommand {

    private SellCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sell")
                .then(Commands.literal("hand")
                        .executes(c -> sell(c, false))
                        .then(Commands.literal("all").executes(c -> sell(c, true)))));
    }

    private static int sell(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack hand = player.getMainHandItem();
        Commodity commodity = CommodityRegistry.forItem(hand);
        if (commodity == null) {
            c.getSource().sendFailure(Component.literal(hand.isEmpty() ? "手上没有东西。" : "市场不收这种东西。"));
            return 0;
        }
        int stored =
                finance.commodity.CommodityInventoryManager.getCommodityAmount(player.getUUID(), commodity.getId());
        int quantity = all ? stored : Math.min(stored, hand.getMaxStackSize());
        SimpleCenterService.Result r = SimpleCenterService.quickSell(player, commodity.getId(), quantity);
        if (!r.success()) {
            c.getSource().sendFailure(Component.literal(r.message()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        GuiFeedbackPacket.send(player, r.message(), r.moneyDelta());
        return quantity;
    }

    /** 物品对应的商品；不是市场商品返回 null。 */
}
