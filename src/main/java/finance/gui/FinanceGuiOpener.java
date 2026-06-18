package finance.gui;

import finance.commodity.CommodityInventoryManager;
import finance.market.MarketPrice;
import finance.market.NpcMarketMaker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

public class FinanceGuiOpener {

    public static void openMarketOverview(ServerPlayer player) {
        List<MarketSnapshot> snapshots = new ArrayList<>();

        for (MarketPrice price : NpcMarketMaker.getAllMarketPrices().values()) {
            int stock = CommodityInventoryManager.getCommodityAmount(
                    NpcMarketMaker.NPC_UUID,
                    price.getCommodityId()
            );
            snapshots.add(MarketSnapshot.fromMarketPrice(price, stock));
        }

        NetworkHooks.openScreen(
                player,
                new MarketOverviewProvider(snapshots),
                buffer -> MarketSnapshot.writeList(buffer, snapshots)
        );
    }

    private record MarketOverviewProvider(List<MarketSnapshot> snapshots) implements net.minecraft.world.MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.literal("国际市场");
        }

        @Override
        public MarketOverviewMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                             net.minecraft.world.entity.player.Player player) {
            return new MarketOverviewMenu(containerId, snapshots);
        }
    }
}
