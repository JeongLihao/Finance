package finance.command;

import com.mojang.brigadier.CommandDispatcher;
import finance.commodity.CommodityInventory;
import finance.commodity.CommodityInventoryManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

public class InventoryCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(

                Commands.literal("inventory")

                        .executes(context -> {

                            ServerPlayer player =
                                    context.getSource()
                                            .getPlayerOrException();

                            CommodityInventory inventory =
                                    CommodityInventoryManager
                                            .getInventory(player.getUUID());

                            player.sendSystemMessage(
                                    Component.literal("=== Inventory ===")
                            );

                            for (Map.Entry<String, Integer> entry :
                                    inventory.getAllCommodities().entrySet()) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                entry.getKey()
                                                        + ": "
                                                        + entry.getValue()
                                        )
                                );
                            }

                            return 1;
                        })
        );
    }
}
