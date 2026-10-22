package finance.factory;

import static org.junit.jupiter.api.Assertions.*;

import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 加工机生产逻辑：配方、燃料、成品槽满、两种原料、存档往返。 */
class MachineCoreTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    /** 测试环境没有 Forge 燃料表：煤烧 1600 tick，和原版一样。 */
    private static final java.util.function.ToIntFunction<ItemStack> FUEL = s -> s.is(Items.COAL) ? 1600 : 0;

    private static MachineCore machine(ItemStack input, ItemStack second, ItemStack fuel) {
        MachineCore core = new MachineCore(() -> {}, FUEL);
        core.items().setStackInSlot(MachineCore.SLOT_INPUT, input);
        core.items().setStackInSlot(MachineCore.SLOT_SECOND, second);
        core.items().setStackInSlot(MachineCore.SLOT_FUEL, fuel);
        return core;
    }

    private static void run(MachineCore core, int ticks) {
        for (int i = 0; i < ticks; i++) core.tick();
    }

    private static ItemStack output(MachineCore core) {
        return core.items().getStackInSlot(MachineCore.SLOT_OUTPUT);
    }

    @Test
    void breadNeedsNoFuelAndConsumesExactlyThreeWheat() {
        MachineCore core = machine(new ItemStack(Items.WHEAT, 7), ItemStack.EMPTY, ItemStack.EMPTY);
        int cook = FactoryRecipe.byId("bread").cookTime();
        run(core, cook * 2);
        assertEquals(2, output(core).getCount());
        assertTrue(output(core).is(Items.BREAD));
        assertEquals(1, core.items().getStackInSlot(MachineCore.SLOT_INPUT).getCount());
        run(core, cook);
        assertEquals(MachineCore.IDLE, core.status(), "只剩 1 个小麦，不够做面包");
    }

    @Test
    void smeltingWaitsForFuelThenBurnsIt() {
        MachineCore core = machine(new ItemStack(Items.RAW_IRON, 2), ItemStack.EMPTY, ItemStack.EMPTY);
        run(core, 5);
        assertEquals(MachineCore.NO_FUEL, core.status());
        assertTrue(output(core).isEmpty());
        core.items().setStackInSlot(MachineCore.SLOT_FUEL, new ItemStack(Items.COAL, 1));
        run(core, FactoryRecipe.byId("iron").cookTime());
        assertEquals(1, output(core).getCount());
        assertTrue(output(core).is(Items.IRON_INGOT));
        assertTrue(core.items().getStackInSlot(MachineCore.SLOT_FUEL).isEmpty(), "烧掉一块煤");
        assertTrue(core.burnTime() > 0, "一块煤能烧好几件");
    }

    @Test
    void stopsWhenOutputIsFullWithoutTouchingInputs() {
        MachineCore core = machine(new ItemStack(Items.WHEAT, 3), ItemStack.EMPTY, ItemStack.EMPTY);
        core.items().setStackInSlot(MachineCore.SLOT_OUTPUT, new ItemStack(Items.BREAD, 64));
        run(core, FactoryRecipe.byId("bread").cookTime() * 2);
        assertEquals(MachineCore.OUTPUT_FULL, core.status());
        assertEquals(3, core.items().getStackInSlot(MachineCore.SLOT_INPUT).getCount());
        assertEquals(64, output(core).getCount());
    }

    @Test
    void bookUsesBothInputs() {
        MachineCore core = machine(new ItemStack(Items.PAPER, 3), new ItemStack(Items.LEATHER, 2), ItemStack.EMPTY);
        run(core, FactoryRecipe.byId("book").cookTime());
        assertTrue(output(core).is(Items.BOOK));
        assertTrue(core.items().getStackInSlot(MachineCore.SLOT_INPUT).isEmpty());
        assertEquals(1, core.items().getStackInSlot(MachineCore.SLOT_SECOND).getCount());
    }

    @Test
    void slotsOnlyAcceptTheirItems() {
        MachineCore.Items items = new MachineCore(() -> {}, FUEL).items();
        assertTrue(items.isItemValid(MachineCore.SLOT_INPUT, new ItemStack(Items.WHEAT)));
        assertFalse(items.isItemValid(MachineCore.SLOT_INPUT, new ItemStack(Items.DIAMOND)));
        assertTrue(items.isItemValid(MachineCore.SLOT_SECOND, new ItemStack(Items.LEATHER)));
        assertFalse(items.isItemValid(MachineCore.SLOT_SECOND, new ItemStack(Items.WHEAT)));
        assertTrue(items.isItemValid(MachineCore.SLOT_FUEL, new ItemStack(Items.COAL)));
        assertFalse(items.isItemValid(MachineCore.SLOT_FUEL, new ItemStack(Items.WHEAT)));
        assertFalse(items.isItemValid(MachineCore.SLOT_OUTPUT, new ItemStack(Items.BREAD)));
    }

    @Test
    void progressAndFuelSurviveSaveAndLoad() {
        MachineCore core = machine(new ItemStack(Items.SAND, 4), ItemStack.EMPTY, new ItemStack(Items.COAL, 3));
        run(core, 50);
        CompoundTag tag = new CompoundTag();
        core.save(tag);
        MachineCore loaded = new MachineCore(() -> {}, FUEL);
        loaded.load(tag);
        assertEquals(core.progress(), loaded.progress());
        assertEquals(core.burnTime(), loaded.burnTime());
        assertEquals("glass", loaded.activeRecipe());
        assertEquals(2, loaded.items().getStackInSlot(MachineCore.SLOT_FUEL).getCount());
    }
}
