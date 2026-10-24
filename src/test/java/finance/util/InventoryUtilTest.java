package finance.util;

import static org.junit.jupiter.api.Assertions.*;

import finance.testutil.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 卖给市场只收普通物品：附魔、改名、有损耗、装着东西的不按原价收走。 */
class InventoryUtilTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    @Test
    void onlyPlainStacksCountAsCommodities() {
        assertTrue(InventoryUtil.isPlain(new ItemStack(Items.IRON_INGOT, 64)));

        ItemStack renamed = new ItemStack(Items.DIAMOND);
        renamed.setHoverName(Component.literal("传家宝"));
        assertFalse(InventoryUtil.isPlain(renamed), "改过名");

        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        assertTrue(InventoryUtil.isPlain(worn), "全新的工具算普通物品");
        worn.setDamageValue(10);
        assertFalse(InventoryUtil.isPlain(worn), "有损耗");

        ItemStack enchanted = new ItemStack(Items.IRON_PICKAXE);
        enchanted.enchant(Enchantments.BLOCK_EFFICIENCY, 3);
        assertFalse(InventoryUtil.isPlain(enchanted), "附魔过");

        ItemStack filled = new ItemStack(Items.SHULKER_BOX);
        filled.getOrCreateTag().put("BlockEntityTag", new CompoundTag());
        assertFalse(InventoryUtil.isPlain(filled), "装着东西的容器");
    }
}
