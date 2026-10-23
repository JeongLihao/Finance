package finance.factory;

import static org.junit.jupiter.api.Assertions.*;

import finance.testutil.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 采集机的仓：放得下才动世界，放的时候合并到已有的堆。 */
class HarvestingTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void dropsFitOnlyWhenEveryStackHasRoom() {
        ItemStackHandler bin = new ItemStackHandler(2);
        bin.setStackInSlot(0, new ItemStack(Items.WHEAT, 60));
        bin.setStackInSlot(1, new ItemStack(Items.STONE, 64));
        assertTrue(Harvesting.fits(bin, List.of(new ItemStack(Items.WHEAT, 4))));
        assertFalse(Harvesting.fits(bin, List.of(new ItemStack(Items.WHEAT, 5))));
        assertFalse(
                Harvesting.fits(bin, List.of(new ItemStack(Items.WHEAT, 3), new ItemStack(Items.WHEAT, 2))),
                "同一种东西分几堆掉也按累计量算");
        assertFalse(Harvesting.fits(bin, List.of(new ItemStack(Items.WHEAT_SEEDS, 1))), "没有空格放种子");
        assertEquals(60, bin.getStackInSlot(0).getCount(), "判断时不改动真正的仓");
    }

    @Test
    void insertMergesIntoExistingStacks() {
        ItemStackHandler bin = new ItemStackHandler(3);
        bin.setStackInSlot(1, new ItemStack(Items.WHEAT, 10));
        Harvesting.insertAll(bin, List.of(new ItemStack(Items.WHEAT, 5), new ItemStack(Items.WHEAT_SEEDS, 2)));
        assertEquals(15, bin.getStackInSlot(1).getCount());
        assertTrue(bin.getStackInSlot(0).is(Items.WHEAT_SEEDS));
    }

    @Test
    void insertReturnsWhatDidNotFit() {
        ItemStackHandler bin = new ItemStackHandler(1);
        bin.setStackInSlot(0, new ItemStack(Items.WHITE_WOOL, 63));
        List<ItemStack> left = Harvesting.insertAll(bin, List.of(new ItemStack(Items.WHITE_WOOL, 3)));
        assertEquals(64, bin.getStackInSlot(0).getCount());
        assertEquals(1, left.size(), "放不下的羊毛要交回来掉在地上，不能凭空消失");
        assertEquals(2, left.get(0).getCount());
    }

    @Test
    void shearingWaitsForAnEmptySlot() {
        ItemStackHandler bin = new ItemStackHandler(2);
        bin.setStackInSlot(0, new ItemStack(Items.WHITE_WOOL, 10));
        assertTrue(Harvesting.hasEmptySlot(bin));
        bin.setStackInSlot(1, new ItemStack(Items.STONE, 1));
        assertFalse(Harvesting.hasEmptySlot(bin), "没有空格就不剪，免得羊毛放不下");
    }
}
