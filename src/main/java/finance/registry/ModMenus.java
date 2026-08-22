package finance.registry;

import finance.FinanceMod;
import finance.gui.FinanceMenu;
import finance.gui.MarketOverviewMenu;
import finance.gui.WalletMenu;
import finance.gui.WarehouseMenu;
import finance.gui.CompanyGameplayMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FinanceMod.MOD_ID);

    public static final RegistryObject<MenuType<MarketOverviewMenu>> MARKET_OVERVIEW =
            MENUS.register("market_overview", () -> IForgeMenuType.create(MarketOverviewMenu::new));

    public static final RegistryObject<MenuType<FinanceMenu>> FINANCE =
            MENUS.register("finance", () -> IForgeMenuType.create(FinanceMenu::new));

    public static final RegistryObject<MenuType<WalletMenu>> WALLET =
            MENUS.register("wallet", () -> IForgeMenuType.create(WalletMenu::new));
    public static final RegistryObject<MenuType<WarehouseMenu>> WAREHOUSE =
            MENUS.register("warehouse", () -> IForgeMenuType.create(WarehouseMenu::new));
    public static final RegistryObject<MenuType<CompanyGameplayMenu>> COMPANY_GAMEPLAY =
            MENUS.register("company_gameplay", () -> IForgeMenuType.create(CompanyGameplayMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
