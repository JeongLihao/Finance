package finance.registry;

import finance.FinanceMod;
import finance.block.FinanceTerminalBlock;
import finance.block.WarehouseControllerBlock;
import finance.block.CompanyDeskBlock;
import finance.block.CompanyFactoryControllerBlock;
import finance.block.BoardroomTableBlock;
import finance.block.SettlementTradeStationBlock;
import finance.block.SurveyBoardBlock;
import finance.gameplay.FinanceTerminalType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FinanceMod.MOD_ID);

    private static BlockBehaviour.Properties terminalProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F)
                .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }

    public static final RegistryObject<Block> MARKET_TERMINAL = BLOCKS.register("market_terminal",
            () -> new FinanceTerminalBlock(terminalProperties(), FinanceTerminalType.MARKET_TERMINAL));
    public static final RegistryObject<Block> WAREHOUSE_CONTROLLER = BLOCKS.register("warehouse_controller",
            () -> new WarehouseControllerBlock(terminalProperties()));
    public static final RegistryObject<Block> BANK_COUNTER = BLOCKS.register("bank_counter",
            () -> new FinanceTerminalBlock(terminalProperties(), FinanceTerminalType.BANK_COUNTER));
    public static final RegistryObject<Block> COMPANY_DESK = BLOCKS.register("company_desk",
            () -> new CompanyDeskBlock(terminalProperties()));
    public static final RegistryObject<Block> COMPANY_FACTORY_CONTROLLER = BLOCKS.register("company_factory_controller",
            () -> new CompanyFactoryControllerBlock(terminalProperties()));
    public static final RegistryObject<Block> SECURITIES_TERMINAL = BLOCKS.register("securities_terminal",
            () -> new FinanceTerminalBlock(terminalProperties(), FinanceTerminalType.SECURITIES_TERMINAL));
    public static final RegistryObject<Block> CENTRAL_BANK_CONSOLE = BLOCKS.register("central_bank_console",
            () -> new FinanceTerminalBlock(terminalProperties(), FinanceTerminalType.CENTRAL_BANK_CONSOLE));
    public static final RegistryObject<Block> BOARDROOM_TABLE = BLOCKS.register("boardroom_table",
            () -> new BoardroomTableBlock(terminalProperties()));
    public static final RegistryObject<Block> SETTLEMENT_TRADE_STATION = BLOCKS.register("settlement_trade_station",
            () -> new SettlementTradeStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5F).sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> SURVEY_BOARD = BLOCKS.register("survey_board",
            () -> new SurveyBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0F).sound(SoundType.WOOD)));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
