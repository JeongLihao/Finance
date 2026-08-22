package finance.gui;

import finance.commodity.CommodityInventoryManager;
import finance.cycle.EconomyCycleService;
import finance.market.MarketOpportunityService;
import finance.market.NpcMarketMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/** Opens the bounded public market venue. It never serializes orders or player identities. */
public final class MarketOverviewGuiOpener {
    private MarketOverviewGuiOpener() {}
    public static void open(ServerPlayer player, BlockPos sourcePos) {
        if (player == null || sourcePos == null) return;
        List<MarketSnapshot> rows = NpcMarketMaker.getAllMarketPrices().values().stream()
                .limit(MarketSnapshot.MAX_ROWS)
                .map(price -> MarketSnapshot.fromMarketPrice(price, CommodityInventoryManager.getCommodityAmount(
                        NpcMarketMaker.NPC_UUID, price.getCommodityId()))).toList();
        var summary = MarketOpportunityService.summary(EconomyCycleService.currentMcDay(player.server),
                id -> CommodityInventoryManager.getCommodityAmount(player.getUUID(), id));
        var opportunity = new MarketOverviewMenu.Opportunity(summary.shortageCommodity(), summary.shortageStock(),
                summary.bestContractCommodity(), summary.bestContractReward(), summary.deliverableCommodity(),
                summary.deliverableReward(), summary.moverCommodity(), summary.moverChange());
        String dimension = player.serverLevel().dimension().location().toString();
        MenuProvider provider = new MenuProvider() {
            public Component getDisplayName(){return Component.translatable("screen.finance.market_overview");}
            public MarketOverviewMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                    net.minecraft.world.entity.player.Player menuPlayer){return new MarketOverviewMenu(id,rows,dimension,sourcePos,opportunity);}
        };
        NetworkHooks.openScreen(player,provider,b->MarketOverviewMenu.write(b,rows,dimension,sourcePos,opportunity));
    }
}
