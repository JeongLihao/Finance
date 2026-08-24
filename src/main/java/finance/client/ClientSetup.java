package finance.client;

import finance.FinanceMod;
import finance.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MARKET_OVERVIEW.get(), MarketOverviewScreen::new);
            MenuScreens.register(ModMenus.FINANCE.get(), FinanceScreen::new);
            MenuScreens.register(ModMenus.WALLET.get(), WalletScreen::new);
            MenuScreens.register(ModMenus.WAREHOUSE.get(), WarehouseScreen::new);
            MenuScreens.register(ModMenus.COMPANY_GAMEPLAY.get(), CompanyGameplayScreen::new);
            MenuScreens.register(ModMenus.SETTLEMENT.get(), SettlementScreen::new);
        });
    }
}
