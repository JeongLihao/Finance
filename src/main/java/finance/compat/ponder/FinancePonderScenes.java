package finance.compat.ponder;

import finance.registry.ModBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Visual-only scenes: these animations never touch live Finance state. */
public final class FinancePonderScenes {
    private static final BlockPos LEFT = new BlockPos(1, 1, 2);
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos RIGHT = new BlockPos(3, 1, 2);
    private static final BlockPos FRONT = new BlockPos(2, 1, 3);

    private FinancePonderScenes() {}

    public static void gettingStarted(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "getting_started", "Finance in the World");
        show(scene, util, CENTER, ModBlocks.MARKET_TERMINAL.get());
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 28)
                .rightClick().withItem(new ItemStack(Items.BOOK));
        scene.idle(32);
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.COMPANY_DESK.get());
        connect(scene, util, LEFT, CENTER, PonderPalette.BLUE);
        connect(scene, util, CENTER, RIGHT, PonderPalette.GREEN);
        scene.rotateCameraY(35); scene.idle(28);
        success(scene, LEFT); success(scene, CENTER); success(scene, RIGHT);
        label(scene, util, CENTER, "Explore → store → trade", PonderPalette.GREEN, 38);
        finish(scene);
    }

    public static void warehouseBasics(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "warehouse_basics", "Physical Warehouse");
        show(scene, util, CENTER, ModBlocks.WAREHOUSE_CONTROLLER.get());
        scene.addKeyframe();
        label(scene, util, LEFT, "Deposit", PonderPalette.BLUE, 24);
        flow(scene, util, LEFT, CENTER, new ItemStack(Items.IRON_INGOT, 8), PonderPalette.BLUE, 28);
        success(scene, CENTER);
        scene.addKeyframe();
        label(scene, util, RIGHT, "Withdraw", PonderPalette.GREEN, 24);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.IRON_INGOT, 8), PonderPalette.GREEN, 28);
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 25).rightClick().whileSneaking();
        scene.idle(28);
        blocked(scene, util, CENTER);
        label(scene, util, CENTER, "Permission checked", PonderPalette.RED, 28);
        finish(scene);
    }

    public static void marketTrading(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "market_trading", "Market Exchange");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.MARKET_TERMINAL.get());
        flow(scene, util, LEFT, RIGHT, new ItemStack(Items.IRON_INGOT, 6), PonderPalette.BLUE, 32);
        outline(scene, util, LEFT, PonderPalette.OUTPUT, 30);
        label(scene, util, LEFT, "Goods locked", PonderPalette.BLUE, 28);
        scene.overlay().showControls(util.vector().topOf(RIGHT), Pointing.DOWN, 24).rightClick();
        scene.idle(28);
        flow(scene, util, RIGHT, LEFT, new ItemStack(Items.EMERALD, 4), PonderPalette.GREEN, 32);
        success(scene, LEFT); success(scene, RIGHT);
        label(scene, util, CENTER, "One atomic exchange", PonderPalette.GREEN, 32);
        finish(scene);
    }

    public static void contractDelivery(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "contract_delivery", "Escrow Contract");
        show(scene, util, LEFT, ModBlocks.MARKET_TERMINAL.get());
        scene.world().setBlock(CENTER, Blocks.GOLD_BLOCK.defaultBlockState(), false);
        scene.world().showSection(util.select().position(CENTER), Direction.DOWN);
        show(scene, util, RIGHT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        flow(scene, util, LEFT, CENTER, new ItemStack(Items.EMERALD, 5), PonderPalette.MEDIUM, 26);
        outline(scene, util, CENTER, PonderPalette.MEDIUM, 38);
        label(scene, util, CENTER, "Escrow", PonderPalette.MEDIUM, 24);
        scene.addKeyframe();
        flow(scene, util, RIGHT, CENTER, new ItemStack(Items.IRON_INGOT, 5), PonderPalette.BLUE, 26);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.EMERALD, 5), PonderPalette.GREEN, 26);
        success(scene, RIGHT);
        blocked(scene, util, CENTER);
        label(scene, util, CENTER, "No double payment", PonderPalette.RED, 26);
        finish(scene);
    }

    public static void companyProduction(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "company_production", "Daily Production");
        show(scene, util, LEFT, ModBlocks.COMPANY_DESK.get());
        show(scene, util, CENTER, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        connect(scene, util, LEFT, CENTER, PonderPalette.BLUE);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.IRON_INGOT, 8), PonderPalette.INPUT, 28);
        label(scene, util, RIGHT, "One MC day", PonderPalette.MEDIUM, 30);
        scene.rotateCameraY(25); scene.idle(28); scene.rotateCameraY(-25);
        success(scene, RIGHT);
        flow(scene, util, RIGHT, CENTER, new ItemStack(Items.GOLD_INGOT, 3), PonderPalette.OUTPUT, 28);
        blocked(scene, util, CENTER);
        label(scene, util, CENTER, "No input = pause", PonderPalette.RED, 28);
        finish(scene);
    }

    public static void logisticsDelivery(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "logistics_delivery", "Sealed Cargo Route");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        scene.world().setBlock(CENTER, Blocks.CHEST.defaultBlockState(), false);
        ElementLink<WorldSectionElement> crate = scene.world().showIndependentSection(
                util.select().position(CENTER), Direction.DOWN);
        scene.idle(10);
        flow(scene, util, LEFT, CENTER, new ItemStack(Items.IRON_INGOT, 8), PonderPalette.INPUT, 24);
        outline(scene, util, CENTER, PonderPalette.BLUE, 28);
        scene.addKeyframe();
        scene.world().moveSection(crate, new Vec3(1, 0, 0), 36);
        connect(scene, util, CENTER, RIGHT, PonderPalette.BLUE);
        scene.idle(38);
        flow(scene, util, RIGHT, FRONT, new ItemStack(Items.IRON_INGOT, 8), PonderPalette.OUTPUT, 24);
        success(scene, RIGHT);
        label(scene, util, RIGHT, "Token consumed", PonderPalette.GREEN, 26);
        finish(scene);
    }

    public static void settlementHelp(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "settlement_help", "Village Demand");
        scene.world().setBlock(LEFT, Blocks.BELL.defaultBlockState(), false);
        scene.world().showSection(util.select().position(LEFT), Direction.DOWN);
        show(scene, util, CENTER, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        flow(scene, util, LEFT, CENTER, new ItemStack(Items.EMERALD, 6), PonderPalette.MEDIUM, 25);
        outline(scene, util, CENTER, PonderPalette.MEDIUM, 30);
        label(scene, util, CENTER, "Funded demand", PonderPalette.MEDIUM, 25);
        scene.addKeyframe();
        flow(scene, util, RIGHT, CENTER, new ItemStack(Items.WHEAT, 16), PonderPalette.INPUT, 28);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.EMERALD, 6), PonderPalette.GREEN, 28);
        success(scene, CENTER);
        scene.effects().indicateRedstone(LEFT);
        label(scene, util, LEFT, "Raid → rebuild", PonderPalette.RED, 25);
        finish(scene);
    }

    public static void fieldSurvey(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "field_survey", "Field Survey");
        show(scene, util, LEFT, ModBlocks.SURVEY_BOARD.get());
        show(scene, util, RIGHT, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        scene.overlay().showControls(util.vector().topOf(LEFT), Pointing.DOWN, 24)
                .rightClick().withItem(new ItemStack(Items.MAP));
        scene.idle(26);
        flow(scene, util, LEFT, RIGHT, new ItemStack(Items.FILLED_MAP), PonderPalette.BLUE, 42);
        scene.rotateCameraY(45); scene.idle(20); scene.rotateCameraY(-45);
        blocked(scene, util, LEFT); success(scene, RIGHT);
        label(scene, util, RIGHT, "Reach the real target", PonderPalette.GREEN, 30);
        finish(scene);
    }

    public static void advancedFinance(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "advanced_finance", "Advanced Finance Terminal");
        show(scene, util, CENTER, ModBlocks.SECURITIES_TERMINAL.get());
        scene.overlay().showControls(util.vector().topOf(CENTER), Pointing.DOWN, 24).rightClick();
        scene.idle(25);
        orbit(scene, util, LEFT, new ItemStack(Items.EMERALD), PonderPalette.GREEN);
        orbit(scene, util, RIGHT, new ItemStack(Items.PAPER), PonderPalette.BLUE);
        orbit(scene, util, FRONT, new ItemStack(Items.GOLD_INGOT), PonderPalette.MEDIUM);
        scene.rotateCameraY(60); scene.idle(30);
        success(scene, CENTER);
        label(scene, util, CENTER, "Stocks • bonds • futures", PonderPalette.BLUE, 32);
        finish(scene);
    }

    public static void regionalTradeFlow(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "regional_trade_flow", "Regional Trade Flow");
        show(scene, util, LEFT, ModBlocks.SURVEY_BOARD.get());
        show(scene, util, CENTER, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.SETTLEMENT_TRADE_STATION.get());
        outline(scene, util, RIGHT, PonderPalette.RED, 30);
        flow(scene, util, LEFT, CENTER, new ItemStack(Items.MAP), PonderPalette.BLUE, 24);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.WHEAT, 16), PonderPalette.INPUT, 34);
        scene.addKeyframe(); success(scene, RIGHT);
        outline(scene, util, RIGHT, PonderPalette.GREEN, 32);
        flow(scene, util, RIGHT, CENTER, new ItemStack(Items.EMERALD, 5), PonderPalette.GREEN, 26);
        label(scene, util, RIGHT, "Next day: shortage ↓", PonderPalette.GREEN, 30);
        finish(scene);
    }

    public static void inventoryCollateral(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "inventory_collateral", "Inventory Collateral");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.BANK_COUNTER.get());
        flow(scene, util, FRONT, LEFT, new ItemStack(Items.IRON_INGOT, 16), PonderPalette.INPUT, 24);
        outline(scene, util, LEFT, PonderPalette.MEDIUM, 46);
        label(scene, util, LEFT, "Pledged", PonderPalette.MEDIUM, 24);
        blocked(scene, util, LEFT);
        flow(scene, util, RIGHT, FRONT, new ItemStack(Items.EMERALD, 8), PonderPalette.GREEN, 30);
        scene.addKeyframe();
        flow(scene, util, FRONT, RIGHT, new ItemStack(Items.EMERALD, 8), PonderPalette.BLUE, 30);
        success(scene, LEFT);
        flow(scene, util, LEFT, FRONT, new ItemStack(Items.IRON_INGOT, 16), PonderPalette.OUTPUT, 24);
        label(scene, util, LEFT, "Released", PonderPalette.GREEN, 24);
        finish(scene);
    }

    public static void companyHedge(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "company_hedge", "Company Price Hedge");
        show(scene, util, LEFT, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.SECURITIES_TERMINAL.get());
        flow(scene, util, FRONT, LEFT, new ItemStack(Items.IRON_INGOT, 12), PonderPalette.INPUT, 24);
        connect(scene, util, LEFT, RIGHT, PonderPalette.BLUE);
        label(scene, util, LEFT, "Real exposure", PonderPalette.MEDIUM, 24);
        blocked(scene, util, RIGHT);
        label(scene, util, RIGHT, "Objective ≠ trade", PonderPalette.RED, 26);
        scene.overlay().showControls(util.vector().topOf(RIGHT), Pointing.DOWN, 24).rightClick();
        scene.idle(26);
        flow(scene, util, RIGHT, LEFT, new ItemStack(Items.PAPER), PonderPalette.BLUE, 28);
        success(scene, LEFT); success(scene, RIGHT);
        label(scene, util, CENTER, "Real position → coverage", PonderPalette.GREEN, 30);
        finish(scene);
    }

    public static void insuranceEvidence(SceneBuilder scene, SceneBuildingUtil util) {
        begin(scene, util, "insurance_evidence", "Verified Insurance");
        show(scene, util, LEFT, ModBlocks.WAREHOUSE_CONTROLLER.get());
        show(scene, util, RIGHT, ModBlocks.COMPANY_FACTORY_CONTROLLER.get());
        show(scene, util, CENTER, ModBlocks.BANK_COUNTER.get());
        flow(scene, util, CENTER, LEFT, new ItemStack(Items.PAPER), PonderPalette.BLUE, 24);
        outline(scene, util, LEFT, PonderPalette.BLUE, 30);
        scene.addKeyframe();
        scene.effects().indicateRedstone(LEFT); scene.effects().indicateRedstone(RIGHT);
        blocked(scene, util, FRONT);
        label(scene, util, FRONT, "Server evidence", PonderPalette.RED, 26);
        flow(scene, util, CENTER, RIGHT, new ItemStack(Items.EMERALD, 6), PonderPalette.GREEN, 30);
        success(scene, RIGHT);
        label(scene, util, RIGHT, "Verified loss paid", PonderPalette.GREEN, 28);
        finish(scene);
    }

    private static void begin(SceneBuilder scene, SceneBuildingUtil util, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(.92F);
        scene.showBasePlate();
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        scene.idle(8);
    }

    private static void show(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Block block) {
        scene.world().setBlock(pos, block.defaultBlockState(), false);
        scene.world().showSection(util.select().position(pos), Direction.DOWN);
        scene.idle(7);
    }

    private static void flow(SceneBuilder scene, SceneBuildingUtil util, BlockPos from, BlockPos to,
                             ItemStack stack, PonderPalette color, int ticks) {
        Vec3 start = util.vector().topOf(from).add(0, .18, 0);
        Vec3 end = util.vector().topOf(to).add(0, .18, 0);
        scene.overlay().showBigLine(color, start, end, ticks + 4);
        ElementLink<EntityElement> item = scene.world().createItemEntity(start,
                end.subtract(start).scale(1.0D / Math.max(1, ticks)), stack);
        scene.world().modifyEntity(item, entity -> entity.setNoGravity(true));
        scene.idle(ticks);
        scene.world().modifyEntity(item, Entity::discard);
    }

    private static void orbit(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
                              ItemStack stack, PonderPalette color) {
        Vec3 center = util.vector().topOf(CENTER).add(0, .2, 0);
        Vec3 target = util.vector().topOf(pos).add(0, .2, 0);
        scene.overlay().showLine(color, center, target, 34);
        ElementLink<EntityElement> item = scene.world().createItemEntity(target, Vec3.ZERO, stack);
        scene.world().modifyEntity(item, entity -> entity.setNoGravity(true));
        scene.idle(8);
    }

    private static void connect(SceneBuilder scene, SceneBuildingUtil util, BlockPos from, BlockPos to,
                                PonderPalette color) {
        scene.overlay().showBigLine(color, util.vector().topOf(from), util.vector().topOf(to), 36);
        scene.idle(10);
    }

    private static void outline(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos,
                                PonderPalette color, int ticks) {
        scene.overlay().showOutline(color, "finance-" + pos + "-" + ticks, util.select().position(pos), ticks);
    }

    private static void blocked(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos) {
        outline(scene, util, pos, PonderPalette.RED, 28);
        scene.effects().indicateRedstone(pos);
        scene.idle(10);
    }

    private static void success(SceneBuilder scene, BlockPos pos) {
        scene.effects().indicateSuccess(pos);
        scene.idle(6);
    }

    private static void label(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String text,
                              PonderPalette color, int ticks) {
        scene.overlay().showText(ticks).colored(color).text(text)
                .pointAt(util.vector().topOf(pos)).placeNearTarget();
        scene.idle(Math.min(12, ticks));
    }

    private static void finish(SceneBuilder scene) {
        scene.idle(28);
        scene.markAsFinished();
    }
}
