package finance.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import finance.factory.FactoryRecipe;
import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 工厂方块逻辑测试 —— 配方、生产推进、停机、原子合成、NBT 往返。
 * 生产核心是纯静态逻辑，不依赖方块实体注册表，可直接单测。
 */
class FactoryBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    private static ItemStackHandler handlerWith(ItemStack input, ItemStack output) {
        ItemStackHandler handler = new ItemStackHandler(FactoryBlockEntity.SLOT_COUNT);
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, input);
        handler.setStackInSlot(FactoryBlockEntity.SLOT_OUTPUT, output);
        return handler;
    }

    /**
     * BE 真实 handler（带覆写 isItemValid 的 {@code FactoryItemHandler}，
     * 与 {@code FactoryBlockEntity} 实机用的是同一个类）。
     */
    private static FactoryBlockEntity.FactoryItemHandler realHandler() {
        return new FactoryBlockEntity.FactoryItemHandler(() -> {});
    }

    private static int tick(ItemStackHandler handler, int progress, String recipeId) {
        return FactoryBlockEntity.tickProduction(handler, progress, recipeId).progress();
    }

    // ---- 配方 ----

    @Test
    void recipesMatchExpectedItems() {
        assertNotNull(FactoryRecipe.find(new ItemStack(Items.WHEAT, 3)));
        assertNotNull(FactoryRecipe.find(new ItemStack(Items.IRON_ORE, 1)));
        assertNotNull(FactoryRecipe.find(new ItemStack(Items.OAK_LOG, 1)));
        assertNull(FactoryRecipe.find(new ItemStack(Items.STONE, 64)));
        assertNull(FactoryRecipe.find(ItemStack.EMPTY));
    }

    @Test
    void recipeRequiresExactInputCount() {
        FactoryRecipe bread = FactoryRecipe.find(new ItemStack(Items.WHEAT, 3));
        assertNotNull(bread);
        assertEquals("wheat_to_bread", bread.id());
        assertFalse(bread.matches(new ItemStack(Items.WHEAT, 2)));
        assertTrue(bread.matches(new ItemStack(Items.WHEAT, 3)));
        assertTrue(bread.matches(new ItemStack(Items.WHEAT, 64)));
    }

    // ---- 生产推进 ----

    @Test
    void progressAdvancesAndCraftsOnCompletion() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY);
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.WHEAT, 3));
        int cookTime = recipe.cookTime();
        // 首次 tick：配方切换（null → wheat_to_bread），进度归 0 并绑定配方。
        var first = FactoryBlockEntity.tickProduction(handler, 0, null);
        assertEquals(0, first.progress());
        assertEquals("wheat_to_bread", first.recipeId());

        int progress = 0;
        for (int i = 0; i < cookTime - 1; i++) {
            progress = tick(handler, progress, "wheat_to_bread");
            assertEquals(i + 1, progress);
            // 未完成前输入不动。
            assertEquals(
                    3, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
        }
        progress = tick(handler, progress, "wheat_to_bread");
        assertEquals(0, progress);
        // 3 小麦 → 1 面包，严格按配方。
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).isEmpty());
        ItemStack out = handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT);
        assertTrue(out.is(Items.BREAD));
        assertEquals(1, out.getCount());
    }

    @Test
    void noInputResetsProgress() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY);
        int progress = tick(handler, 50, "wheat_to_bread");
        assertEquals(51, progress);
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
        var result = FactoryBlockEntity.tickProduction(handler, progress, "wheat_to_bread");
        assertEquals(0, result.progress());
        assertNull(result.recipeId());
    }

    @Test
    void outputFullPausesWithoutConsumingInput() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.BREAD, 64));
        int before = handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount();
        int progress = tick(handler, 10, "wheat_to_bread");
        // 停机：进度保持，输入不扣。
        assertEquals(10, progress);
        assertEquals(
                before, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
        assertEquals(64, handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).getCount());
    }

    @Test
    void outputWithDifferentItemBlocksCraft() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.STONE, 1));
        int progress = tick(handler, 199, "wheat_to_bread");
        assertEquals(199, progress);
        assertEquals(3, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
    }

    @Test
    void partialOutputSpaceAllowsCraft() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.OAK_PLANKS, 60));
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.OAK_LOG, 1));
        int progress = recipe.cookTime() - 1;
        int next = tick(handler, progress, "oak_log_to_planks");
        assertEquals(0, next);
        // 1 原木 → 4 木板：60 + 4 = 64。
        assertEquals(64, handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).getCount());
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).isEmpty());
    }

    @Test
    void partialOutputSpaceInsufficientBlocksCraft() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.OAK_PLANKS, 61));
        int before = handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount();
        // 61 + 4 > 64，放不下 → 不扣输入。
        int progress = tick(handler, 99, "oak_log_to_planks");
        assertEquals(99, progress);
        assertEquals(
                before, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
    }

    // ---- NBT ----

    @Test
    void inventoryNbtRoundTrip() {
        ItemStackHandler handler = handlerWith(new ItemStack(Items.IRON_ORE, 5), new ItemStack(Items.IRON_INGOT, 2));
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", handler.serializeNBT());

        ItemStackHandler restored = new ItemStackHandler(FactoryBlockEntity.SLOT_COUNT);
        restored.deserializeNBT(tag.getCompound("Inventory"));
        assertEquals(5, restored.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
        assertTrue(restored.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).is(Items.IRON_ORE));
        assertEquals(2, restored.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).getCount());
        assertTrue(restored.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).is(Items.IRON_INGOT));
    }

    // ---- 攻击视角 ----

    @Test
    void inputSwapResetsProgress() {
        // 进度中途换输入 → 进度清零重来，蹭不到进度。
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY);
        int progress = tick(handler, 100, "wheat_to_bread");
        assertEquals(101, progress);
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, new ItemStack(Items.IRON_ORE, 1));
        var result = FactoryBlockEntity.tickProduction(handler, progress, "wheat_to_bread");
        assertEquals(0, result.progress());
        assertEquals("iron_ore_to_ingot", result.recipeId());
    }

    @Test
    void craftIsAtomicOnOutputRace() {
        // 输出槽在检查后被占满的竞态：doCraft 二次确认，失败则不扣输入。
        ItemStackHandler handler = handlerWith(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.BREAD, 64));
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.WHEAT, 3));
        assertFalse(FactoryBlockEntity.doCraft(handler, recipe));
        assertEquals(3, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
    }

    // ---- 真实 handler 回归（P1-3：裸 ItemStackHandler 绕开了 isItemValid 覆写，全绿是假象） ----

    @Test
    void realHandlerFullProductionCycleYieldsOutput() {
        // P1-3 回归：用 BE 真实的 FactoryItemHandler 走完整生产流程。
        // 修复前：doCraft 用 insertItem 写输出槽，被 isItemValid 恒 false 拒收，
        // 每次完成 tick 走回滚，进度卡在 cookTime-1，输出槽永远为空。
        FactoryBlockEntity.FactoryItemHandler handler = realHandler();
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT, 3));
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.WHEAT, 3));
        assertNotNull(recipe);
        int cookTime = recipe.cookTime();

        var first = FactoryBlockEntity.tickProduction(handler, 0, null);
        assertEquals(0, first.progress());
        assertEquals("wheat_to_bread", first.recipeId());

        int progress = 0;
        for (int i = 0; i < cookTime; i++) {
            var result = FactoryBlockEntity.tickProduction(handler, progress, "wheat_to_bread");
            progress = result.progress();
        }
        // 完成：3 小麦 → 1 面包，输入清空、输出有产物。
        assertEquals(0, progress);
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).isEmpty());
        ItemStack out = handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT);
        assertTrue(out.is(Items.BREAD), "真实 handler 下输出槽必须有产物（P1-3）");
        assertEquals(1, out.getCount());
    }

    @Test
    void realHandlerFullCycleWithPartialOutputStack() {
        // 真实 handler：输出槽已有同类物品时直接合并（旁路 isItemValid）。
        FactoryBlockEntity.FactoryItemHandler handler = realHandler();
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 1));
        handler.setStackInSlot(FactoryBlockEntity.SLOT_OUTPUT, new ItemStack(Items.OAK_PLANKS, 60));
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.OAK_LOG, 1));
        assertNotNull(recipe);
        int progress = recipe.cookTime() - 1;
        var result = FactoryBlockEntity.tickProduction(handler, progress, "oak_log_to_planks");
        assertEquals(0, result.progress());
        assertEquals(64, handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).getCount());
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).isEmpty());
    }

    @Test
    void realHandlerHopperSemantics() {
        // 料斗视角（P2）：输入槽只接受有配方的物品；输出槽一切放入拒收。
        FactoryBlockEntity.FactoryItemHandler handler = realHandler();

        // 非配方物品 → 拒收，原样退回。
        ItemStack stone = new ItemStack(Items.STONE, 1);
        ItemStack rejected = handler.insertItem(FactoryBlockEntity.SLOT_INPUT, stone, false);
        assertEquals(1, rejected.getCount());
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).isEmpty());

        // 输出槽：任何物品都拒收（玩家/料斗只出不进，生产内部旁路写入）。
        ItemStack bread = new ItemStack(Items.BREAD, 1);
        ItemStack rejectedOut = handler.insertItem(FactoryBlockEntity.SLOT_OUTPUT, bread, false);
        assertEquals(1, rejectedOut.getCount());
        assertTrue(handler.getStackInSlot(FactoryBlockEntity.SLOT_OUTPUT).isEmpty());

        // 配方输入 → 正常接收。
        ItemStack wheat = new ItemStack(Items.WHEAT, 3);
        ItemStack leftover = handler.insertItem(FactoryBlockEntity.SLOT_INPUT, wheat, false);
        assertTrue(leftover.isEmpty());
        assertEquals(3, handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT).getCount());
    }

    @Test
    void realHandlerRollbackKeepsInputOnBlockedCraft() {
        // 输出槽被异物占住：doCraft 失败，输入原封不动（不扣不丢）。
        FactoryBlockEntity.FactoryItemHandler handler = realHandler();
        ItemStack wheat = new ItemStack(Items.WHEAT, 3);
        wheat.setHoverName(net.minecraft.network.chat.Component.literal("命名小麦"));
        handler.setStackInSlot(FactoryBlockEntity.SLOT_INPUT, wheat);
        handler.setStackInSlot(FactoryBlockEntity.SLOT_OUTPUT, new ItemStack(Items.STONE, 1));
        FactoryRecipe recipe = FactoryRecipe.find(new ItemStack(Items.WHEAT, 3));
        assertNotNull(recipe);
        assertFalse(FactoryBlockEntity.doCraft(handler, recipe));
        ItemStack input = handler.getStackInSlot(FactoryBlockEntity.SLOT_INPUT);
        assertEquals(3, input.getCount());
        assertTrue(input.hasCustomHoverName(), "失败路径不得剥除输入的 NBT");
    }
}
