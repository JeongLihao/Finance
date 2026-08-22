package finance.registry;

import finance.FinanceMod;
import finance.item.PortableLedgerItem;
import finance.item.FinanceTerminalBlockItem;
import finance.item.FinanceGuideItem;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FinanceMod.MOD_ID);

    public static final RegistryObject<Item> PORTABLE_LEDGER = ITEMS.register("portable_ledger",
            () -> new PortableLedgerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FINANCE_GUIDE=ITEMS.register("finance_guide",()->new FinanceGuideItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MARKET_TERMINAL = blockItem("market_terminal", ModBlocks.MARKET_TERMINAL, FinanceTerminalType.MARKET_TERMINAL);
    public static final RegistryObject<Item> WAREHOUSE_CONTROLLER = blockItem("warehouse_controller", ModBlocks.WAREHOUSE_CONTROLLER, FinanceTerminalType.WAREHOUSE_CONTROLLER);
    public static final RegistryObject<Item> BANK_COUNTER = blockItem("bank_counter", ModBlocks.BANK_COUNTER, FinanceTerminalType.BANK_COUNTER);
    public static final RegistryObject<Item> COMPANY_DESK = blockItem("company_desk", ModBlocks.COMPANY_DESK, FinanceTerminalType.COMPANY_DESK);
    public static final RegistryObject<Item> COMPANY_FACTORY_CONTROLLER = blockItem("company_factory_controller", ModBlocks.COMPANY_FACTORY_CONTROLLER, FinanceTerminalType.COMPANY_DESK);
    public static final RegistryObject<Item> SECURITIES_TERMINAL = blockItem("securities_terminal", ModBlocks.SECURITIES_TERMINAL, FinanceTerminalType.SECURITIES_TERMINAL);
    public static final RegistryObject<Item> CENTRAL_BANK_CONSOLE = blockItem("central_bank_console", ModBlocks.CENTRAL_BANK_CONSOLE, FinanceTerminalType.CENTRAL_BANK_CONSOLE);
    public static final RegistryObject<Item> BOARDROOM_TABLE = blockItem("boardroom_table",ModBlocks.BOARDROOM_TABLE,FinanceTerminalType.BOARDROOM_TABLE);

    private ModItems() {}

    private static RegistryObject<Item> blockItem(String name, RegistryObject<net.minecraft.world.level.block.Block> block,
                                                  FinanceTerminalType type) {
        return ITEMS.register(name, () -> new FinanceTerminalBlockItem(block.get(), new Item.Properties(), type));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PORTABLE_LEDGER);
            event.accept(FINANCE_GUIDE);
            event.accept(MARKET_TERMINAL);
            event.accept(WAREHOUSE_CONTROLLER);
            event.accept(BANK_COUNTER);
            event.accept(COMPANY_DESK);
            event.accept(COMPANY_FACTORY_CONTROLLER);
            event.accept(SECURITIES_TERMINAL);
            event.accept(CENTRAL_BANK_CONSOLE);
            event.accept(BOARDROOM_TABLE);
        }
    }
}
