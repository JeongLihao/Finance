package finance.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * MC 物品栏工具 —— 扫描、移除、添加物品。
 * 用于商品存取系统：虚拟商品库存 ↔ MC 物品栏。
 */
public class InventoryUtil {

    /**
     * 能当商品按市价卖的物品：没附魔、没改名、没有损耗、不是装着东西的容器。
     * 带这些的东西按原价收走会让玩家吃亏，扫描和移除都跳过它们。
     */
    public static boolean isPlain(ItemStack stack) {
        if (stack.isDamaged() || stack.isEnchanted() || stack.hasCustomHoverName()) return false;
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains("BlockEntityTag");
    }

    /**
     * 扫描玩家 MC 物品栏中对应物品 ID 的总数（只算 {@link #isPlain} 的）。
     *
     * @param player 玩家
     * @param itemId Minecraft 物品 ID（如 "minecraft:iron_ingot"）
     * @return 物品栏中该物品的总数量
     */
    public static int countItemInInventory(ServerPlayer player, String itemId) {
        return count(player, itemId, true);
    }

    /** 物品栏里这种物品有几个带附魔、改名、损耗（不能当商品卖）。 */
    public static int countSpecialInInventory(ServerPlayer player, String itemId) {
        return count(player, itemId, false);
    }

    private static int count(ServerPlayer player, String itemId, boolean plain) {
        if (itemId == null || itemId.isEmpty()) return 0;
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (targetItem == null) return 0;

        int count = 0;
        Inventory inv = player.getInventory();
        // 遍历主背包 + 快捷栏 + 副手
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(targetItem) && isPlain(stack) == plain) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 从玩家 MC 物品栏中移除指定数量的物品（只动 {@link #isPlain} 的）。
     *
     * @return 实际移除的数量（可能小于请求的数量）
     */
    public static int removeFromInventory(ServerPlayer player, String itemId, int amount) {
        if (itemId == null || itemId.isEmpty() || amount <= 0) return 0;
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (targetItem == null) return 0;

        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(targetItem) && isPlain(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return amount - remaining;
    }

    /**
     * 向玩家 MC 物品栏添加物品，满了则掉落在地上。按一组的上限拆开：
     * 超过一组的大堆掉在地上，存档时数量只按一个字节存，超过 127 个就会坏掉。
     */
    public static void addToInventory(ServerPlayer player, String itemId, int amount) {
        if (itemId == null || itemId.isEmpty() || amount <= 0) return;
        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
        if (targetItem == null) return;

        int max = Math.max(1, new ItemStack(targetItem).getMaxStackSize());
        for (int remaining = amount; remaining > 0; ) {
            int size = Math.min(max, remaining);
            remaining -= size;
            ItemStack stack = new ItemStack(targetItem, size);
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                // 物品栏满了，掉落在地上
                player.drop(stack, false);
            }
        }
    }
}
