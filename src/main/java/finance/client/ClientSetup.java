package finance.client;

import finance.FinanceMod;
import finance.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = FinanceMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSetup.class);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MARKET_OVERVIEW.get(), MarketOverviewScreen::new);
            MenuScreens.register(ModMenus.FINANCE.get(), FinanceScreen::new);
            MenuScreens.register(ModMenus.WALLET.get(), WalletScreen::new);
            MenuScreens.register(ModMenus.WAREHOUSE.get(), WarehouseScreen::new);
            MenuScreens.register(ModMenus.COMPANY_GAMEPLAY.get(), CompanyGameplayScreen::new);
            MenuScreens.register(ModMenus.SETTLEMENT.get(), SettlementScreen::new);
            registerOptionalPonderTutorials();
        });
    }

    /**
     * Keep every Ponder reference behind reflection. This class is loaded on all
     * Finance clients, including installations that intentionally use only the
     * dependency-free handbook and advancements.
     */
    private static void registerOptionalPonderTutorials() {
        if (!ModList.get().isLoaded("ponder")) return;
        try {
            Class<?> bootstrap = Class.forName("finance.compat.ponder.FinancePonderBootstrap");
            bootstrap.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.error("Ponder is installed but Finance tutorial scenes could not be registered", error);
        }
    }
}
