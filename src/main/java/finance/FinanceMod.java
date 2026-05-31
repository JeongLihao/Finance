package finance;

import net.minecraftforge.fml.common.Mod;
import finance.command.BalanceCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import finance.command.FinanceCommand;
import finance.command.PayCommand;
import finance.commodity.Commodity;
import finance.commodity.CommodityCategory;
import finance.commodity.CommodityRegistry;
import finance.command.MarketCommand;
import finance.command.CommodityCommand;
import finance.command.InventoryCommand;
import net.minecraftforge.event.server.ServerStartingEvent;
import finance.data.EconomySavedData;

@Mod(FinanceMod.MOD_ID)
public class FinanceMod {

    public static final String MOD_ID = "finance";

    public FinanceMod(){

        MinecraftForge.EVENT_BUS.register(this);

        CommodityRegistry.register(
                new Commodity(
                        "iron",
                        "Iron",
                        CommodityCategory.RAW_MATERIAL,
                        100
                )
        );

        CommodityRegistry.register(
                new Commodity(
                        "wheat",
                        "Wheat",
                        CommodityCategory.FOOD,
                        30
                )
        );

        CommodityRegistry.register(
                new Commodity(
                        "coal",
                        "Coal",
                        CommodityCategory.ENERGY,
                        50
                )
        );
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event){

        BalanceCommand.register(event.getDispatcher());

        FinanceCommand.register(event.getDispatcher());

        PayCommand.register(event.getDispatcher());

        MarketCommand.register(event.getDispatcher());

        CommodityCommand.register(event.getDispatcher());

        InventoryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        System.out.println("FINANCE SERVER START");

        EconomySavedData.get(event.getServer());
    }
}

