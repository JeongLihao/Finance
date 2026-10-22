package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import finance.commodity.Commodity;
import finance.commodity.CommodityRegistry;
import finance.network.GuiFeedbackPacket;
import finance.simple.SimpleCenterService;
import finance.util.InventoryUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /sell hand —— 把手上这组东西按市场价卖掉；/sell hand all —— 背包里同样的东西全卖。
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
        int quantity = all ? InventoryUtil.countItemInInventory(player, commodity.getItemId()) : hand.getCount();
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
