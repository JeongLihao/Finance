package finance.compat.ponder;

import finance.registry.ModBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Visual-only demonstrations. They never call Finance managers, mutate a real
 * account, or represent a tutorial scene as authoritative progress.
 */
public final class FinancePonderScenes {
    private static final BlockPos LEFT = new BlockPos(1, 1, 2);
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos RIGHT = new BlockPos(3, 1, 2);

    private FinancePonderScenes() {}

    public static void gettingStarted(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "getting_started", "Getting Started with Finance");
        show(scene, util, CENTER, ModBlocks.MARKET_TERMINAL.get());
        scene.overlay().showText(55).text("Finance starts with places in the world, not a giant menu.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(Items.BOOK));
        scene.overlay().showText(55).text("Use the ledger for your wallet, then visit a terminal for each activity.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.COMPANY_DESK.get());
        scene.overlay().showText(65).text("Start with storage and trading. Companies and advanced finance can wait.")
                .independent(55);
        scene.idle(70);
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("Return to the real world and follow the Finance advancements.").independent(55);
        finish(scene);
    }

    public static void warehouseBasics(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "warehouse_basics", "Storing Physical Goods");
        show(scene, util, CENTER, ModBlocks.WAREHOUSE_CONTROLLER.get());
        scene.overlay().showText(55).text("Place a warehouse controller to create a physical custody point.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showControls(util.vector().blockSurface(CENTER, Direction.WEST), Pointing.RIGHT, 45)
                .rightClick().withItem(new ItemStack(Items.IRON_INGOT, 10));
        scene.overlay().showText(55).text("Deposit real items nearby. The server removes them from your inventory.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().centerOf(RIGHT).add(0, .7, 0), Vec3.ZERO,
                new ItemStack(Items.IRON_INGOT, 4));
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("Withdrawals return the same authoritative units to your inventory.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.overlay().showText(55).colored(PonderPalette.RED)
                .text("Other players need explicit permission; a copied client request is not enough.")
                .independent(55);
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 45)
                .rightClick().whileSneaking().withItem(new ItemStack(Items.GOLD_INGOT));
        scene.overlay().showText(55).text("Sneak-use to inspect tier, capacity and server-calculated upgrade costs.")
                .pointAt(util.vector().topOf(CENTER));
        finish(scene);
    }

    public static void marketTrading(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "market_trading", "Selling Through the Market");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.MARKET_TERMINAL.get());
        scene.overlay().showText(50).text("First store the goods you really own.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(55);
        scene.world().createItemEntity(util.vector().topOf(LEFT).add(0, .3, 0), Vec3.ZERO,
                new ItemStack(Items.IRON_INGOT, 8));
        scene.overlay().showText(55).text("A sell order locks warehouse custody; locked goods cannot be withdrawn.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(RIGHT), Pointing.DOWN, 45).rightClick();
        scene.overlay().showText(55).text("Open the market terminal to choose a commodity, price and quantity.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().topOf(RIGHT).add(0, .3, 0), Vec3.ZERO,
                new ItemStack(Items.EMERALD, 4));
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("When another player buys, goods and existing money move exactly once.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.overlay().showText(55).text("Cancelled or unfilled orders release their locked goods.").independent(55);
        finish(scene);
    }

    public static void contractDelivery(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "contract_delivery", "Completing a Procurement Contract");
        show(scene, util, LEFT, ModBlocks.MARKET_TERMINAL.get());
        show(scene, util, RIGHT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        scene.overlay().showText(55).text("A valid contract moves its reward into escrow before anyone accepts it.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(RIGHT), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(Items.PAPER));
        scene.overlay().showText(55).text("Accept and deliver only while near the bound warehouse.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().topOf(RIGHT).add(0, .3, 0), Vec3.ZERO,
                new ItemStack(Items.IRON_INGOT, 5));
        scene.overlay().showText(55).colored(PonderPalette.RED)
                .text("Too few items means no goods move and no reward is paid.").independent(55);
        scene.idle(60);
        scene.world().createItemEntity(util.vector().centerOf(CENTER).add(0, 1, 0), Vec3.ZERO,
                new ItemStack(Items.EMERALD, 5));
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("A complete delivery moves the goods and escrow reward in one server transaction.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showText(55).text("Repeated clicks reuse an operation key and cannot pay twice.").independent(55);
        finish(scene);
    }

    public static void companyProduction(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "company_production", "Running a Company Facility");
        show(scene, util, LEFT, ModBlocks.COMPANY_DESK.get());
        show(scene, util, CENTER, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        scene.overlay().showText(55).text("Create or join a company at the desk, then assign member roles.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(60);
        scene.overlay().showText(55).text("Bind a company warehouse so inputs and outputs have one real custody owner.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().topOf(CENTER).add(0, .3, 0), Vec3.ZERO,
                new ItemStack(Items.IRON_INGOT, 8));
        scene.overlay().showText(55).text("Put the required materials in custody and wait for a natural Minecraft day.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.effects().indicateSuccess(RIGHT);
        scene.overlay().showText(60).colored(PonderPalette.GREEN)
                .text("The facility consumes inputs, charges maintenance and creates output once per day.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(65);
        scene.overlay().showText(60).colored(PonderPalette.RED)
                .text("Missing input, full storage or bankruptcy pauses production without partial movement.")
                .independent(55);
        finish(scene);
    }

    public static void logisticsDelivery(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "logistics_delivery", "Transporting Sealed Cargo");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        scene.overlay().showText(55).text("Bind an empty cargo crate to the destination warehouse first.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(LEFT), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(Items.CHEST));
        scene.overlay().showText(55).text("Load at the source. Goods move from source custody into transport custody.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().centerOf(CENTER).add(0, 1, 0), Vec3.ZERO,
                new ItemStack(Items.CHEST));
        scene.overlay().showText(55).text("Carry the sealed crate yourself or place it in a chest minecart.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.effects().indicateSuccess(RIGHT);
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("Unload nearby. Full destinations fail without deleting transport cargo.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.overlay().showText(60).colored(PonderPalette.RED)
                .text("Copied crate items are only credentials; the first valid token delivery invalidates every copy.")
                .independent(55);
        finish(scene);
    }

    public static void settlementHelp(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "settlement_help", "Helping a Village");
        show(scene, util, CENTER, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        scene.world().setBlock(LEFT, Blocks.BELL.defaultBlockState(), false);
        scene.world().showSection(util.select().position(LEFT), Direction.DOWN);
        scene.overlay().showText(55).text("Place a trade station near a village to create a stable local settlement identity.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 45).rightClick();
        scene.overlay().showText(55).text("Public demands use real NPC money placed in escrow before they appear.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.world().createItemEntity(util.vector().topOf(RIGHT), Vec3.ZERO, new ItemStack(Items.WHEAT, 16));
        scene.overlay().showText(55).text("Accept nearby, bring the requested physical items, then return to this station.")
                .pointAt(util.vector().topOf(RIGHT));
        scene.idle(60);
        scene.effects().indicateSuccess(CENTER);
        scene.overlay().showText(55).colored(PonderPalette.GREEN)
                .text("Delivery pays once and raises your bounded contribution with this settlement.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showText(55).text("Raid losses are aggregated into one rebuilding need instead of message spam.")
                .pointAt(util.vector().topOf(LEFT));
        finish(scene);
    }

    public static void fieldSurvey(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "field_survey", "Following a Field Survey");
        show(scene, util, LEFT, ModBlocks.SURVEY_BOARD.get());
        show(scene, util, RIGHT, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        scene.overlay().showText(55).text("Use a survey board to request one bounded target in your current dimension.")
                .pointAt(util.vector().topOf(LEFT));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(LEFT), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(Items.MAP));
        scene.overlay().showText(55).text("The note gives only a direction and estimated distance, not every world coordinate.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showText(55).colored(PonderPalette.RED)
                .text("A client claiming to be at the destination cannot complete the assignment.").independent(55);
        scene.idle(60);
        scene.effects().indicateSuccess(RIGHT);
        scene.overlay().showText(60).colored(PonderPalette.GREEN)
                .text("Reach and interact with the real registered facility to receive the escrow reward once.")
                .pointAt(util.vector().topOf(RIGHT));
        finish(scene);
    }

    public static void advancedFinance(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "advanced_finance", "Optional Advanced Finance");
        show(scene, util, CENTER, ModBlocks.SECURITIES_TERMINAL.get());
        scene.overlay().showText(55).text("Advanced finance is optional and intentionally placed behind a late-game terminal.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 45).rightClick();
        scene.overlay().showText(60).text("Stocks, bonds, funds, futures and insurance remain available for players who want them.")
                .pointAt(util.vector().topOf(CENTER));
        scene.idle(65);
        scene.overlay().showText(55).text("Learn one tab at a time; none is required for storage, contracts or company production.")
                .independent(55);
        scene.idle(60);
        scene.overlay().showText(55).colored(PonderPalette.RED)
                .text("The central-bank console is administrator-only and has no survival recipe.").independent(55);
        finish(scene);
    }

    private static void begin(SceneBuilder scene, SceneBuildingUtil util, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        scene.idle(10);
    }

    private static void show(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Block block) {
        scene.world().setBlock(pos, block.defaultBlockState(), false);
        scene.world().showSection(util.select().position(pos), Direction.DOWN);
        scene.idle(8);
    }

    private static void finish(SceneBuilder scene) {
        scene.idle(60);
        scene.markAsFinished();
    }
}
